package io.netty.channel.kqueue;

import io.netty.channel.Channel;

/**
 * Just here to do not break API
 */
abstract class AbstractKQueueDatagramChannel extends AbstractKQueueMessageChannel {
    AbstractKQueueDatagramChannel(final Channel parent, final BsdSocket fd, final boolean active) {
        super(parent, fd, active);
    }
}
