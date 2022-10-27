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
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.socket.Tun4Packet;
import io.netty.channel.socket.Tun6Packet;
import io.netty.channel.socket.TunChannel;
import io.netty.channel.socket.TunPacket;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.SocketAddress;

import static io.netty.channel.epoll.LinuxSocket.newSocketTun;

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
//                // Check if sendmmsg(...) is supported which is only the case for GLIBC 2.14+
//                if (Native.IS_SUPPORTING_SENDMMSG && in.size() > 1 ||
//                        // We only handle UDP_SEGMENT in sendmmsg.
//                        in.current() instanceof io.netty.channel.unix.SegmentedDatagramPacket) {
//                    NativeDatagramPacketArray array = cleanDatagramPacketArray();
//                    array.add(in, isConnected(), maxMessagesPerWrite);
//                    int cnt = array.count();
//
//                    if (cnt >= 1) {
//                        // Try to use gathering writes via sendmmsg(...) syscall.
//                        int offset = 0;
//                        NativeDatagramPacketArray.NativeDatagramPacket[] packets = array.packets();
//
//                        int send = socket.sendmmsg(packets, offset, cnt);
//                        if (send == 0) {
//                            // Did not write all messages.
//                            break;
//                        }
//                        for (int i = 0; i < send; i++) {
//                            in.remove();
//                        }
//                        maxMessagesPerWrite -= send;
//                        continue;
//                    }
//                }
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
        System.out.println("EpollTunChannel.doWriteMessage");
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
    protected AbstractEpollUnsafe newUnsafe() {
        return new EpollTunChannelUnsafe();
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        socket.bindTun(local);
        this.local = local;
        active = true;
    }

    protected class EpollTunChannelUnsafe extends AbstractEpollUnsafe {
        @Override
        void epollInReady() {
            System.out.println("EpollTunChannelUnsafe.epollInReady");
        }
    }
}
