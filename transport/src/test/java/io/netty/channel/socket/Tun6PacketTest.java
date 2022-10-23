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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Tun6PacketTest {
    private Tun6Packet packet;

    @BeforeEach
    void setUp() {
        ByteBuf data = Unpooled.wrappedBuffer(new byte[]{
                96,
                38,
                12,
                0,
                0,
                117,
                6,
                64,
                -2,
                -128,
                0,
                0,
                0,
                0,
                0,
                0,
                28,
                -33,
                23,
                75,
                -111,
                -33,
                100,
                7,
                -2,
                -128,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                102,
                68,
                94,
                -66,
                -33,
                -8,
                67,
                -61,
                -126,
                27,
                88,
                -28,
                2,
                55,
                90,
                -113,
                -37,
                113,
                -65,
                -128,
                24,
                8,
                0,
                -66,
                -37,
                0,
                0,
                1,
                1,
                8,
                10,
                23,
                -117,
                42,
                80,
                -34,
                37,
                8,
                -13
        });
        packet = new Tun6Packet(data);
    }

    @Test
    void testVersion() {
        assertEquals(6, packet.version());
    }

    @Test
    void testTrafficClass() {
        assertEquals(2, packet.trafficClass());
    }

    @Test
    void testFlowLabel() {
        assertEquals(396288, packet.flowLabel());
    }

    @Test
    void testPayloadLength() {
        assertEquals(117, packet.payloadLength());
    }

    @Test
    void testNextHeader() {
        assertEquals(6, packet.nextHeader());
    }

    @Test
    void testHopLimit() {
        assertEquals(64, packet.hopLimit());
    }
    @Test
    void testSourceAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("fe80:0:0:0:1cdf:174b:91df:6407"), packet.sourceAddress());
    }

    @Test
    void testDestinationAddress() throws UnknownHostException {
        assertEquals(InetAddress.getByName("fe80:0:0:0:66:445e:bedf:f843"), packet.destinationAddress());
    }

    @Test
    void testData() {
        assertArrayEquals(new byte[]{
                -61,
                -126,
                27,
                88,
                -28,
                2,
                55,
                90,
                -113,
                -37,
                113,
                -65,
                -128,
                24,
                8,
                0,
                -66,
                -37,
                0,
                0,
                1,
                1,
                8,
                10,
                23,
                -117,
                42,
                80,
                -34,
                37,
                8,
                -13
        }, packet.data());
    }

    @Test
    void testToString() {
        assertEquals("Tun6Packet[len=117, src=fe80:0:0:0:1cdf:174b:91df:6407, dst=fe80:0:0:0:66:445e:bedf:f843]", packet.toString());
    }
}
