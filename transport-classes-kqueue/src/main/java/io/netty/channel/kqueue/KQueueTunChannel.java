package io.netty.channel.kqueue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.Tun4Packet;
import io.netty.channel.socket.Tun6Packet;
import io.netty.channel.socket.TunPacket;
import io.netty.channel.unix.Errors;
import io.netty.channel.unix.IovArray;
import io.netty.channel.unix.UnixChannelUtil;
import io.netty.util.UncheckedBooleanSupplier;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.PortUnreachableException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;

import static io.netty.channel.kqueue.BsdSocket.newSocketTun;

public class KQueueTunChannel extends AbstractKQueueMessageChannel {
    private static final String EXPECTED_TYPES =
            " (expected: " + StringUtil.simpleClassName(TunPacket.class) + ", " +
                    StringUtil.simpleClassName(ByteBuf.class) + ')';
    private final KQueueTunChannelConfig config;

    public KQueueTunChannel() {
        super(null, newSocketTun(), false);
        this.config = new KQueueTunChannelConfig(this);
    }

    @Override
    protected AbstractKQueueUnsafe newUnsafe() {
        return new KQueueTunChannelUnsafe();
    }

    protected boolean doWriteMessage(final Object msg) throws Exception {
        final ByteBuf data;
        if (msg instanceof TunPacket) {
            TunPacket packet = (TunPacket) msg;
            data = packet.content();
        }
        else {
            data = (ByteBuf) msg;
        }

        final int dataLen = data.readableBytes();
        if (dataLen == 0) {
            return true;
        }

        final long writtenBytes;
        if (data.hasMemoryAddress()) {
            long memoryAddress = data.memoryAddress();
            writtenBytes = socket.writeAddress(memoryAddress, data.readerIndex(), data.writerIndex());
        }
        else if (data.nioBufferCount() > 1) {
            IovArray array = ((KQueueEventLoop) eventLoop()).cleanArray();
            array.add(data, data.readerIndex(), data.readableBytes());
            int cnt = array.count();
            assert cnt != 0;

            writtenBytes = socket.writevAddresses(array.memoryAddress(0), cnt);
        }
        else {
            ByteBuffer nioData = data.internalNioBuffer(data.readerIndex(), data.readableBytes());
            writtenBytes = socket.write(nioData, nioData.position(), nioData.limit());
        }

        return writtenBytes > 0;
    }

    @Override
    protected Object filterOutboundMessage(final Object msg) {
        if (msg instanceof Tun4Packet) {
            Tun4Packet packet = (Tun4Packet) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ? new Tun4Packet(newDirectBuffer(packet, content)) : msg;
        }

        if (msg instanceof Tun6Packet) {
            Tun6Packet packet = (Tun6Packet) msg;
            ByteBuf content = packet.content();
            return UnixChannelUtil.isBufferCopyNeededForWrite(content) ? new Tun6Packet(newDirectBuffer(packet, content)) : msg;
        }

        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            return UnixChannelUtil.isBufferCopyNeededForWrite(buf) ? newDirectBuffer(buf) : buf;
        }

        throw new UnsupportedOperationException(
                "unsupported message type: " + StringUtil.simpleClassName(msg) + EXPECTED_TYPES);
    }

    @Override
    public KQueueChannelConfig config() {
        return config;
    }

    @Override
    protected void doBind(SocketAddress local) throws Exception {
        socket.bindTun(local);
        this.local = socket.localAddressTun();
        active = true;
    }

    final class KQueueTunChannelUnsafe extends AbstractKQueueUnsafe {
        @Override
        void readReady(final KQueueRecvByteAllocatorHandle allocHandle) {
            assert eventLoop().inEventLoop();
            final KQueueChannelConfig config = config();
            if (shouldBreakReadReady(config)) {
                clearReadFilter0();
                return;
            }
            final ChannelPipeline pipeline = pipeline();
            final ByteBufAllocator allocator = config.getAllocator();
            allocHandle.reset(config);
            readReadyBefore();

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
                        }
                        catch (Errors.NativeIoException e) {
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

                        // extract ip version
                        //byteBuf.readerIndex(4); // FIXME: ja?

                        final int version = (byteBuf.getByte(4) & 0xff) >> 4;
                        if (version == 4) {
                            packet = new Tun4Packet(byteBuf);
                        }
                        else if (version == 6) {
                            packet = new Tun6Packet(byteBuf);
                        }
                        else {
                            // FIXME: throw channel exception?
                            throw new IOException("Unknown protocol: " + version);
                        }

                        allocHandle.incMessagesRead(1);

                        readPending = false;
                        pipeline.fireChannelRead(packet);

                        byteBuf = null;

                        // We use the TRUE_SUPPLIER as it is also ok to read less then what we did try to read (as long
                        // as we read anything).
                    } while (allocHandle.continueReading(UncheckedBooleanSupplier.TRUE_SUPPLIER));
                }
                catch (Throwable t) {
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
            }
            finally {
                readReadyFinally(config);
            }
        }
    }
}
