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
package io.netty.example.tun;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.Tun6Packet;

import static io.netty.channel.socket.Tun4Packet.INET4_DESTINATION_ADDRESS;
import static io.netty.channel.socket.Tun4Packet.INET4_SOURCE_ADDRESS;
import static io.netty.channel.socket.Tun6Packet.INET6_DESTINATION_ADDRESS;
import static io.netty.channel.socket.Tun6Packet.INET6_SOURCE_ADDRESS;

/**
 * Echoes received IPv6 packets by swapping source and destination addresses.
 */
class Echo6Handler extends SimpleChannelInboundHandler<Tun6Packet> {
    protected Echo6Handler() {
        super(false);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx,
                                Tun6Packet packet) throws Exception {
        // swap source and destination addresses. Depending on the Layer 4 protocol used, this may
        // require recalculation of existing checksums. However, UDP and TCP work without
        // recalculation.
        int sourceAddress = packet.content().getInt(INET6_SOURCE_ADDRESS);
        int destinationAddress = packet.content().getInt(INET6_DESTINATION_ADDRESS);
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
