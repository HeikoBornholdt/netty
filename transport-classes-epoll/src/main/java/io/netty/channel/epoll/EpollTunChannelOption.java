package io.netty.channel.epoll;

import io.netty.channel.ChannelOption;
import io.netty.channel.socket.TunChannelOption;

/**
 * Provides {@link ChannelOption}s for {@link EpollTunChannel}s.
 */
public final class EpollTunChannelOption<T> extends TunChannelOption<T> {
    /**
     * Enables/Disables the IFF_MULTI_QUEUE flag.
     * <p>
     * If enabled, multiple {@link EpollTunChannel}s can be assigned to the same device to parallelize packet sending or receiving.
     */
    public static final ChannelOption<Boolean> IFF_MULTI_QUEUE = valueOf("IFF_MULTI_QUEUE");
}
