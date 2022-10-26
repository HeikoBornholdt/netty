package io.netty.example.tun;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.Tun4Packet;

import static io.netty.channel.socket.Tun4Packet.INET4_DESTINATION_ADDRESS;
import static io.netty.channel.socket.Tun4Packet.INET4_SOURCE_ADDRESS;

/**
 * Echoes received IPv4 packets by swapping source and destination addresses.
 */
class Echo4Handler extends SimpleChannelInboundHandler<Tun4Packet> {
    protected Echo4Handler() {
        super(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Tun4Packet packet) throws Exception {
        // swap source and destination addresses. Depending on the used layer 4 protocol this
        // might require recalculating any present checksum. But UDP and TCP will work fine.
        int sourceAddress = packet.content().getInt(INET4_SOURCE_ADDRESS);
        int destinationAddress = packet.content().getInt(INET4_DESTINATION_ADDRESS);
        packet.content().setInt(INET4_SOURCE_ADDRESS, destinationAddress);
        packet.content().setInt(INET4_DESTINATION_ADDRESS, sourceAddress);

        ctx.write(packet);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.fireChannelReadComplete();
        ctx.flush();
    }
}
