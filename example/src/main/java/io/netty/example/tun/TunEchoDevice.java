package io.netty.example.tun;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueTunChannel;
import io.netty.channel.kqueue.TunAddress;
import io.netty.util.internal.PlatformDependent;
import io.netty.util.internal.StringUtil;

import java.io.IOException;
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
        EventLoopGroup group = new KQueueEventLoopGroup(1);
        try {
            final Bootstrap b = new Bootstrap()
                    .group(group)
                    .channel(KQueueTunChannel.class)
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
                exec("/sbin/ifconfig", name, "add", ADDRESS.getHostAddress(), ADDRESS.getHostAddress()); // sudo ifconfig utun3 inet6 add fc00::1/128
                exec("/sbin/ifconfig", name, "up");
                exec("/sbin/route", "add", "-net", ADDRESS.getHostAddress() + '/' + 32, "-iface", name); // sudo /sbin/route add -net fc00::1 -iface utun3 // Überhaupt notwendig bei nur einer IP?
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
