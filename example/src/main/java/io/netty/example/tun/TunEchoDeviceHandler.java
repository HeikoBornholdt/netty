package io.netty.example.tun;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Handler implementation for {@link TunEchoDevice}. It writes all received messages back to the TUN
 * device.
 */
public class TunEchoDeviceHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) {
        System.out.println("Received " + msg);
        ctx.write(msg);
    }

    @Override
    public void channelReadComplete(final ChannelHandlerContext ctx) {
        ctx.flush();
    }
}
