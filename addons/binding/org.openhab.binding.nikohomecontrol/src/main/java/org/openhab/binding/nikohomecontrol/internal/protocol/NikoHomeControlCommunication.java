/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NikoHomeControlCommunication} class is an abstract class representing the communication objects with the
 * Niko Home Control System. {@link NhcICommunication} or {@link NhcIICommunication} should be used for the respective
 * version of Niko Home Control.
 * <ul>
 * <li>Start and stop communication with the Niko Home Control System.
 * <li>Read all setup and status information from the Niko Home Control Controller.
 * <li>Execute Niko Home Control commands.
 * <li>Listen to events from Niko Home Control.
 * </ul>
 *
 * Only switch, dimmer and rollershutter actions are currently implemented.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
abstract public class NikoHomeControlCommunication {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlCommunication.class);

    protected final NhcSystemInfo systemInfo = new NhcSystemInfo();
    protected final Map<String, NhcLocation> locations = new HashMap<>();
    protected final Map<String, NhcAction> actions = new HashMap<>();
    protected final Map<String, NhcThermostat> thermostats = new HashMap<>();

    @Nullable
    protected NhcController handler;

    /**
     * Start Communication with Niko Home Control system.
     */
    abstract public void startCommunication();

    /**
     * Stop Communication with Niko Home Control system.
     */
    abstract public void stopCommunication();

    /**
     * Close and restart communication with Niko Home Control system.
     */
    public synchronized void restartCommunication() {
        stopCommunication();

        logger.debug("Niko Home Control: restart communication from thread {}", Thread.currentThread().getId());

        startCommunication();
    }

    /**
     * Method to check if communication with Niko Home Control is active.
     *
     * @return True if active
     */
    abstract public boolean communicationActive();

    /**
     * Return all actions in the Niko Home Control Controller.
     *
     * @return <code>Map&ltInteger, {@link NhcAction}></code>
     */
    public Map<String, NhcAction> getActions() {
        return this.actions;
    }

    /**
     * Return all thermostats in the Niko Home Control Controller.
     *
     * @return <code>Map&ltInteger, {@link NhcThermostat}></code>
     */
    public Map<String, NhcThermostat> getThermostats() {
        return this.thermostats;
    }

    /**
     * @param handler representing the callback interface {@link NhcController} for events
     */
    public void setNhcController(NhcController handler) {
        this.handler = handler;
    }

    /**
     * Execute a command by sending it to Niko Home Control.
     *
     * @param actionId
     * @param value
     */
    abstract public void execute(String actionId, String value);
}
