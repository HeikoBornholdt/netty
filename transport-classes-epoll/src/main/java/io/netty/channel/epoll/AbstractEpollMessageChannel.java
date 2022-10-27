package io.netty.channel.epoll;

abstract class AbstractEpollMessageChannel extends AbstractEpollChannel {
    AbstractEpollMessageChannel(final LinuxSocket fd) {
        super(fd);
    }
}
