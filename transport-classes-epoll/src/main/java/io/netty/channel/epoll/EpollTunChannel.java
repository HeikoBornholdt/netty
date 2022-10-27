/*
 * Copyright 2022 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.epoll;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.Tun4Packet;
import io.netty.channel.socket.Tun6Packet;
import io.netty.channel.socket.TunAddress;
import io.netty.channel.socket.TunChannel;
import io.netty.channel.socket.TunPacket;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.PortUnreachableException;
import java.net.SocketAddress;

import static io.netty.channel.epoll.LinuxSocket.newSocketTun;
import static io.netty.channel.socket.TunChannelOption.TUN_MTU;

/**
 * {@link TunChannel} implementation that uses linux EPOLL Edge-Triggered Mode for maximal
 * performance.
 */
public class EpollTunChannel extends AbstractEpollChannel implements TunChannel {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(TunPacket.class) + ", " +
                    StringUtil.simpleClassName(ByteBuf.class) + ')';
    private final EpollTunChannelConfig config;

    public EpollTunChannel() {
        super(null, newSocketTun(), false);
        this.config = new EpollTunChannelConfig(this);
    }

    @Override
    protected void doWrite(final ChannelOutboundBuffer in) throws Exception {
        int maxMessagesPerWrite = maxMessagesPerWrite();
        while (maxMessagesPerWrite > 0) {
            Object msg = in.current();
            if (msg == null) {
                // Wrote all messages.
                break;
            }

            try {
                boolean done = false;
                for (int i = config().getWriteSpinCount(); i > 0; --i) {
                    if (doWriteMessage(msg)) {
                        done = true;
                        break;
                    }
                }

                if (done) {
                    in.remove();
                    maxMessagesPerWrite--;
                }
                else {
                    break;
                }
            }
            catch (IOException e) {
                maxMessagesPerWrite--;
                // Continue on write error as a DatagramChannel can write to multiple remote peers
                //
                // See https://github.com/netty/netty/issues/2665
                in.remove(e);
            }
        }

        if (in.isEmpty()) {
            // Did write all messages.
            clearFlag(Native.EPOLLOUT);
        }
        else {
            // Did not write all messages.
            setFlag(Native.EPOLLOUT);
        }
    }

    private boolean doWriteMessage(Object msg) throws Exception {
        final ByteBuf data;
        if (msg instanceof TunPacket) {
            TunPacket packet = (TunPacket) msg;
            data = packet.content();
        } else {
            data = (ByteBuf) msg;
        }

        final int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        return doWriteOrSendBytes(data, null, false) > 0;
    }

    @Override
    protected Object filterOutboundMessage(final Object msg) {
        if (msg instanceof Tun4Packet) {
            Tun4Packet packet = (Tun4Packet) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new Tun4Packet(newDirectBuffer(packet, content)) : msg;
        }

        if (msg instanceof Tun6Packet) {
            Tun6Packet packet = (Tun6Packet) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ?
                    new Tun6Packet(newDirectBuffer(packet, content)) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf) ? newDirectBuffer(buf) : buf;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public EpollChannelConfig config() {
        return config;
    }

    @Override
    public TunAddress localAddress() {
        return (TunAddress) super.localAddress();
    }

    @Override
    public int mtu() throws IOException {
        return LinuxSocket.getMtu(localAddress().ifName());
    }

    @Override
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollTunChannelUnsafe();
    }

    @Override
    protected void doRegister() {
        // do nothing
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        // doRegister muss nach bindTun erfolgen, weil sonst epollCtlAdd nicht funktioniert
        this.local = socket.bindTun(local);
        super.doRegister();
        active = true;

        final int mtu = config.getOption(TUN_MTU);
        if (mtu > 0) {
            LinuxSocket.setMtu(((TunAddress) this.local).ifName(), mtu);
        }
    }

    protected class EpollTunChannelUnsafe extends AbstractEpollUnsafe {
        @Override
        void epollInReady() {
            assert eventLoop().inEventLoop();
            EpollChannelConfig config = config();
            if (shouldBreakEpollInReady(config)) {
                clearEpollIn0();
                return;
            }
            final EpollRecvByteAllocatorHandle allocHandle = recvBufAllocHandle();
            allocHandle.edgeTriggered(isFlagSet(Native.EPOLLET));

            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            allocHandle.reset(config);
            epollInBefore();

            Throwable exception = null;
            try {
                ByteBuf byteBuf = null;
                try {
                    do {
                        byteBuf = allocHandle.allocate(allocator);
                        allocHandle.attemptedBytesRead(byteBuf.writableBytes());

                        final TunPacket packet;
                        try {
                            allocHandle.lastBytesRead(doReadBytes(byteBuf));
                        } catch (Errors.NativeIoException e) {
                            // We need to correctly translate connect errors to match NIO behaviour.
                            if (e.expectedErr() == Errors.ERROR_ECONNREFUSED_NEGATIVE) {
                                PortUnreachableException error = new PortUnreachableException(e.getMessage());
                                error.initCause(e);
                                throw error;
                            }
                            throw e;
                        }
                        if (allocHandle.lastBytesRead() <= 0) {
                            // nothing was read, release the buffer.
                            byteBuf.release();
                            byteBuf = null;
                            break;
                        }

                        final int version = byteBuf.getUnsignedByte(0) >> 4;
                        if (version == 4) {
                            packet = new Tun4Packet(byteBuf);
                        } else if (version == 6) {
                            packet = new Tun6Packet(byteBuf);
                        } else {
                            throw new IOException("Unknown internet protocol: " + version);
                        }

                        allocHandle.incMessagesRead(1);

                        readPending = false;
                        pipeline.fireChannelRead(packet);

                        byteBuf = null;

                        // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
                        // as we read anything).
                    } while (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER));
                } catch (Throwable t) {
                    if (byteBuf != null) {
                        byteBuf.release();
                    }
                    exception = t;
                }

                allocHandle.readComplete();
                pipeline.fireChannelReadComplete();

                if (exception != null) {
                    pipeline.fireExceptionCaught(exception);
                }
            } finally {
                epollInFinally(config);
            }
        }
    }
}
