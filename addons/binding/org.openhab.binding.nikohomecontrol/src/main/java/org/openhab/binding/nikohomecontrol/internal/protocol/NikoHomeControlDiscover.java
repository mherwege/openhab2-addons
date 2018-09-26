/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class {@link NikoHomeControlDiscover} is used to get the Niko Home Control IP-interface IP address for bridge
 * discovery.
 * <p>
 * The constructor broadcasts a UDP packet with content 0x44 on port 10000.
 * The Niko Home Control IP-interface responds to this UDP packet.
 * The IP-address from the Niko Home Control IP-interface is then extracted from the response packet.
 * The data content of the response packet is used as a unique identifier for the bridge.
 *
 * @author Mark Herwege - Initial Contribution
 */
/**
 * @author Mark Herwege - Initial Contribution
 *
 */
@NonNullByDefault
public final class NikoHomeControlDiscover {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlDiscover.class);

    private InetAddress addr;
    private String nhcBridgeId = "";
    private boolean isNhcII;

    /**
     * Discover a Niko Home Control IP interface by broadcasting UDP packet 0x44 to port 10000. The IP interface will
     * reply. The address of the IP interface is than derived from that response.
     *
     * @param broadcast Broadcast address of the network
     * @throws IOException
     */
    public NikoHomeControlDiscover(String broadcast) throws IOException {
        final byte[] discoverBuffer = { (byte) 0x44 };
        final InetAddress broadcastAddr = InetAddress.getByName(broadcast);
        final int broadcastPort = 10000;

        DatagramPacket discoveryPacket = new DatagramPacket(discoverBuffer, discoverBuffer.length, broadcastAddr,
                broadcastPort);
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try (DatagramSocket datagramSocket = new DatagramSocket(null)) {
            datagramSocket.setBroadcast(true);
            datagramSocket.setSoTimeout(500);
            datagramSocket.send(discoveryPacket);
            datagramSocket.receive(packet);
            this.addr = packet.getAddress();
            setNhcBridgeId(packet);
            setIsNhcII(packet);
            logger.debug("Niko Home Control: IP address is {}, unique ID is {}", this.addr, this.nhcBridgeId);
        }
    }

    /**
     * @return the addr
     */
    public InetAddress getAddr() {
        return this.addr;
    }

    /**
     * @return the nhcBridgeId
     */
    public String getNhcBridgeId() {
        return this.nhcBridgeId;
    }

    /**
     * Retrieves a unique ID from the returned datagram packet received after sending the UDP discovery message.
     *
     * @param packet
     */
    private void setNhcBridgeId(DatagramPacket packet) {
        byte[] packetData = packet.getData();
        int packetLength = packet.getLength();
        packetLength = packetLength > 6 ? 6 : packetLength;
        StringBuilder sb = new StringBuilder(packetLength);
        for (int i = 0; i < packetLength; i++) {
            sb.append(String.format("%02x", packetData[i]));
        }
        this.nhcBridgeId = sb.toString();
    }

    /**
     * Checks if this is a NHC II Connected Controller
     *
     * @param packet
     */
    private void setIsNhcII(DatagramPacket packet) {
        byte[] packetData = packet.getData();
        int packetLength = packet.getLength();
        // The 16th byte in the packet is 2 for a NHC II Connected Controller
        if ((packetLength >= 16) && (packetData[15] >= 2)) {
            this.isNhcII = true;
        } else {
            this.isNhcII = false;
        }
    }

    /**
     * Test if the installation is a Niko Home Control II installation
     *
     * @return true if this is a Niko Home Control II installation
     */
    public boolean isNhcII() {
        return isNhcII;
    }
}
