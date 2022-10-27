package io.netty.channel.kqueue;

import java.net.SocketAddress;
import java.util.Objects;

/**
 * A {@link SocketAddress} implementation that identifies a tun device to which a {@link TunChannel}
 * can be bound to.
 */
// FIXME: move to transport module?
public class TunAddress extends SocketAddress {
    private static final long serialVersionUID = -584786182484350484L; // NOSONAR
    private final String ifName;

    public TunAddress(final String ifName) {
        this.ifName = ifName;
    }

    public TunAddress() {
        this(null);
    }

    /**
     * Returns the name of the tun device.
     *
     * @return the name of the tun device
     */
    public String ifName() {
        return ifName;
    }

    @Override
    public String toString() {
        if (ifName == null) {
            return "";
        }
        else {
            return ifName;
        }
    }
}
