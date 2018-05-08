/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.handler;

import static org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcICommunication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NhcIBridgeHandler} is the handler for a Niko Home Control I IP-interface and connects it to
 * the framework.
 *
 * @author Mark Herwege
 */
@NonNullByDefault
public class NhcIBridgeHandler extends NikoHomeControlBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(NhcIBridgeHandler.class);

    public NhcIBridgeHandler(Bridge nikoHomeControlBridge) {
        super(nikoHomeControlBridge);
    }

    @Override
    public void initialize() {
        logger.debug("Niko Home Control: initializing bridge handler");

        Configuration config = this.getConfig();
        InetAddress addr = getAddr();
        Integer port = getPort();

        logger.debug("Niko Home Control: bridge handler host {}, port {}", addr, port);

        if (addr != null) {
            nhcComm = new NhcICommunication(this);
            startCommunication(nhcComm);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    "Niko Home Control: cannot resolve bridge IP with hostname " + config.get(CONFIG_HOST_NAME));
        }
    }

    @Override
    protected void updateProperties() {
        Map<String, String> properties = new HashMap<>();

        NhcICommunication comm = (NhcICommunication) nhcComm;
        if (comm != null) {
            properties.put("softwareVersion", comm.getSystemInfo().getSwVersion());
            properties.put("apiVersion", comm.getSystemInfo().getApi());
            properties.put("language", comm.getSystemInfo().getLanguage());
            properties.put("currency", comm.getSystemInfo().getCurrency());
            properties.put("units", comm.getSystemInfo().getUnits());
            properties.put("tzOffset", comm.getSystemInfo().getTz());
            properties.put("dstOffset", comm.getSystemInfo().getDst());
            properties.put("configDate", comm.getSystemInfo().getLastConfig());
            properties.put("energyEraseDate", comm.getSystemInfo().getLastEnergyErase());
            properties.put("connectionStartDate", comm.getSystemInfo().getTime());

            thing.setProperties(properties);
        }
    }

    @Override
    public @Nullable InetAddress getAddr() {
        Configuration config = this.getConfig();
        InetAddress addr = null;
        try {
            addr = InetAddress.getByName((String) config.get(CONFIG_HOST_NAME));
        } catch (UnknownHostException e) {
            logger.debug("Niko Home Control: Cannot resolve hostname {} to IP adress", config.get(CONFIG_HOST_NAME));
        }
        return addr;
    }

    @Override
    public @Nullable Integer getPort() {
        Configuration config = this.getConfig();
        return ((Number) config.get(CONFIG_PORT)).intValue();
    }
}
