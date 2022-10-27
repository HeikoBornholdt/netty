package io.netty.channel.kqueue;

import io.netty.channel.FixedRecvByteBufAllocator;

public class KQueueTunChannelConfig extends KQueueChannelConfig {
    KQueueTunChannelConfig(final AbstractKQueueChannel channel) {
        super(channel, new FixedRecvByteBufAllocator(2048));
    }
}
