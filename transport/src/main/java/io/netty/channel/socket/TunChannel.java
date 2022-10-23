package io.netty.channel.socket;

import io.netty.channel.Channel;

/**
 * A TUN device-backed {@link Channel}.
 */
public interface TunChannel extends Channel {
    @Override
    TunAddress localAddress();
}
