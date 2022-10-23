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
package io.netty.channel.socket;

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

    public TunAddress(String ifName) {
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
        } else {
            return ifName;
        }
    }
}