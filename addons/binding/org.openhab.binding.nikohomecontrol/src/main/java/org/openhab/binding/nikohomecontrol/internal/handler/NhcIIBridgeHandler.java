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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcIICommunication;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcIIGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NhcIIBridgeHandler} is the handler for a Niko Home Control II Connected Controller and connects it
 * to the framework.
 *
 * @author Mark Herwege
 */
@NonNullByDefault
public class NhcIIBridgeHandler extends NikoHomeControlBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(NhcIIBridgeHandler.class);

    private HttpClient httpClient;

    public NhcIIBridgeHandler(Bridge nikoHomeControlBridge, HttpClient httpClient) {
        super(nikoHomeControlBridge);
        this.httpClient = httpClient;
    }

    @Override
    public void initialize() {
        logger.debug("Niko Home Control: initializing NHC II bridge handler");

        String username = getUsername();
        logger.debug("Niko Home Control: bridge handler username {}", username);

        if (!username.isEmpty()) {
            nhcComm = new NhcIICommunication(this, httpClient, scheduler,
                    ((Number) this.getConfig().get(CONFIG_POLLING)).intValue());
            startCommunication(nhcComm);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                    "Niko Home Control: username not set.");
        }
    }

    @Override
    protected void updateProperties() {
        Map<String, String> properties = new HashMap<>();

        NhcIICommunication comm = (NhcIICommunication) nhcComm;
        if (comm != null) {
            NhcIIGateway gateway = comm.getGateway();
            if (gateway != null) {
                properties.put("GatewayId", gateway.getId());
                properties.put("Hostname", gateway.getHostname());
                properties.put("NetworkName", gateway.getNetworkName());
                properties.put("NHCRevision", gateway.getNhcRevision());
                properties.put("Model", gateway.getModel());
            }
        }
    }

    /**
     * Get the username for the Niko Home Control II cloud service.
     *
     * @return the username
     */
    public String getUsername() {
        Configuration config = this.getConfig();
        String username = (String) config.get(CONFIG_USERNAME);
        return username;
    }

    /**
     * Get the password for the Niko Home Control II cloud service.
     *
     * @return the password
     */
    public String getPassword() {
        Configuration config = this.getConfig();
        String password = (String) config.get(CONFIG_PASSWORD);
        if (password.isEmpty()) {
            logger.debug("Niko Home Control: no password set.");
        }
        return password;
    }
}
