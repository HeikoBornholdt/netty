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

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueTunChannel;
import io.netty.channel.socket.Tun4Packet;
import io.netty.channel.socket.TunAddress;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

import static io.netty.channel.socket.Tun4Packet.INET4_DESTINATION_ADDRESS;
import static io.netty.channel.socket.Tun4Packet.INET4_HEADER_LENGTH;
import static io.netty.channel.socket.Tun4Packet.INET4_SOURCE_ADDRESS;
import static io.netty.channel.socket.TunChannelOption.TUN_MTU;

public class TunEchoDevice {
    static final String NAME = System.getProperty("name", null);
    static final InetAddress ADDRESS;
    static final int NETMASK = Integer.parseInt(System.getProperty("netmask", "24"));

    static {
        try {
            ADDRESS = InetAddress.getByName(System.getProperty("address", "10.10.10.10"));
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
//        for (int i = 0; i < 10; i++) {
//            System.out.println("Wait " + (10 - i) + "s...");
//            Thread.sleep(1000);
//        }
//        System.out.println("geht los");

        EventLoopGroup group;
        Class<? extends Channel> channelClass;
//        if (PlatformDependent.isOsx()) {
            group = new KQueueEventLoopGroup(1);
            channelClass = KQueueTunChannel.class;
//        }
//        else if (!PlatformDependent.isWindows()) {
//            group = new EpollEventLoopGroup(1);
//            channelClass = EpollTunChannel.class;
//        }
//        else {
//            throw new RuntimeException("Unsupported platform: This example only work on Linux or macOS");
//        }

        try {
            Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(channelClass)
                    .option(TUN_MTU, 1200)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();

                            p.addLast(new Echo4Handler());
                        }
                    });
            Channel ch = b.bind(new TunAddress(NAME)).syncUninterruptibly().channel();

            String name = ch.localAddress().toString();
            System.out.println("TUN device created: " + name);

            if (PlatformDependent.isOsx()) {
                if (ADDRESS instanceof Inet6Address) {
                    exec("/sbin/ifconfig", name, "inet6", "add", ADDRESS.getHostAddress() + "/" + NETMASK);
                    exec("/sbin/route", "add", "-inet6", ADDRESS.getHostAddress(), "-iface", name);
                } else {
                    exec("/sbin/ifconfig", name, "add", ADDRESS.getHostAddress(), ADDRESS.getHostAddress());
                    exec("/sbin/route", "add", "-net", ADDRESS.getHostAddress() + '/' + NETMASK, "-iface", name);
                }
            } else {
                String version = ADDRESS instanceof Inet6Address ? "-6" : "-4";
                exec("/sbin/ip", version, "addr", "add", ADDRESS.getHostAddress() + '/' + NETMASK, "dev", name);
                exec("/sbin/ip", "link", "set", "dev", name, "up");
            }

            System.out.println("Address and netmask assigned: " + ADDRESS.getHostAddress() + '/' + NETMASK);
            System.out.println("All UDP datagrams addressed to this subnet should now be echoed back.");

            ch.closeFuture().syncUninterruptibly();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            group.shutdownGracefully();
        }
    }

    private static void exec(String... command) throws IOException {
        try {
            int exitCode = Runtime.getRuntime().exec(command).waitFor();
            if (exitCode != 0) {
                CharSequence arguments = StringUtil.join(" ", Arrays.asList(command));
                throw new IOException("Executing `" + arguments + "` returned non-zero exit code (" + exitCode + ").");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Echo4Handler extends SimpleChannelInboundHandler<Tun4Packet> {
        protected Echo4Handler() {
            super(false);
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx,
                                    Tun4Packet packet) throws Exception {
            // switch IP addresses
            InetAddress sourceAddress = packet.sourceAddress();
            InetAddress destinationAddress = packet.destinationAddress();
            System.out.println("READ srcAddr=" + packet + ", dstAddr=" + destinationAddress);
            packet.content().setBytes(INET4_SOURCE_ADDRESS, destinationAddress.getAddress());
            packet.content().setBytes(INET4_DESTINATION_ADDRESS, sourceAddress.getAddress());

            // switch UDP ports
            int sourcePort = packet.content().getUnsignedShort(INET4_HEADER_LENGTH + 0);
            int destinationPort= packet.content().getUnsignedShort(INET4_HEADER_LENGTH + 2);
            System.out.println("READ srcPrt=" + sourcePort + ", dstPrt=" + destinationPort);
            packet.content().setShort(INET4_HEADER_LENGTH + 0, destinationPort);
            packet.content().setShort(INET4_HEADER_LENGTH + 2, sourcePort);

            System.out.println("WRITE srcAddr=" + packet + ", dstAddr=" + destinationAddress);
            System.out.println("WRITE srcPrt=" + sourcePort + ", dstPrt=" + destinationPort);

            ctx.write(packet);
        }

        @Override
        public void channelReadComplete(ChannelHandlerContext ctx) {
            ctx.fireChannelReadComplete();
            ctx.flush();
        }
    }
}
