package io.netty.example.tun;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollTunChannel;
//import io.netty.channel.kqueue.KQueueEventLoopGroup;
//import io.netty.channel.kqueue.KQueueTunChannel;
import io.netty.channel.socket.TunAddress;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Creates an TUN device that will echo back all received packets.
 *
 * <h2>How to use this example</h2>
 *
 * <pre>
 *
 * </pre>
 */
public class TunEchoDevice {
    static final String NAME = System.getProperty("name", null);
    static final InetAddress ADDRESS;

    static {
        try {
            ADDRESS = InetAddress.getByName(System.getProperty("address", "10.10.10.10")); // fc00::1
        }
        catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
//        System.out.println("10s ab jetzt!");
//        Thread.sleep(10 * 1000);
//        System.out.println("geht los");

        EventLoopGroup group;
        Class<? extends Channel> channelClass;
        /*if (PlatformDependent.isOsx()) {
            group = new KQueueEventLoopGroup(1);
            channelClass = KQueueTunChannel.class;
        }
        else*/ if (!PlatformDependent.isWindows()) {
            group = new EpollEventLoopGroup(1);
            channelClass = EpollTunChannel.class;
        }
        else {
            throw new RuntimeException("Unsupported platform: This example only work on Linux or macOS");
        }

        try {
            final Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(channelClass)
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(final Channel ch) {
                            final ChannelPipeline p = ch.pipeline();

                            p.addLast(new TunEchoDeviceHandler());
                        }
                    });
            final Channel ch = b.bind(new TunAddress(NAME)).syncUninterruptibly().channel();

            final String name = ch.localAddress().toString();
            System.out.println("TUN device created: " + name);

            if (PlatformDependent.isOsx()) {
                if (ADDRESS instanceof Inet6Address) {
                    exec("/sbin/ifconfig", name, "inet6", "add", ADDRESS.getHostAddress() + "/128");
                    exec("/sbin/route", "add", "-inet6", ADDRESS.getHostAddress(), "-iface", name);
                }
                else {
                    exec("/sbin/ifconfig", name, "add", ADDRESS.getHostAddress(), ADDRESS.getHostAddress());
                    throw new RuntimeException("Unhandled address type: " + ADDRESS);
                }
            }
            else if (!PlatformDependent.isWindows()) {
                // FIXME: IPv6?
                exec("/sbin/ip", ADDRESS instanceof Inet6Address ? "-6" : "-4", "addr", "add", ADDRESS.getHostAddress() + '/' + 24, "dev", name);
                exec("/sbin/ip", "link", "set", "dev", name, "up");
            }

            System.out.println("Address assigned: " + ADDRESS.getHostAddress());
            System.out.println("All packets sent to this address will be echoed.");

            ch.closeFuture().syncUninterruptibly();
        }
        catch (final IOException e) {
            throw new RuntimeException(e);
        }
        finally {
            group.shutdownGracefully();
        }
    }

    private static void exec(final String... command) throws IOException {
        try {
            final int exitCode = Runtime.getRuntime().exec(command).waitFor();
            if (exitCode != 0) {
                throw new IOException("Executing `" + StringUtil.join(" ", Arrays.asList(command)) + "` returned non-zero exit code (" + exitCode + ").");
            }
        }
        catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
