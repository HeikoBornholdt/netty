package io.netty.channel.epoll;

import io.netty.channel.FixedRecvByteBufAllocator;

public class EpollTunChannelConfig extends EpollChannelConfig {
    EpollTunChannelConfig(final AbstractEpollChannel channel) {
        super(channel, new FixedRecvByteBufAllocator(2048));
    }
}
