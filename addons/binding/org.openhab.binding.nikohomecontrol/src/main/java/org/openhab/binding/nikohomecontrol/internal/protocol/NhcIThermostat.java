/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NhcIThermostat} class represents the thermostat Niko Home Control I communication object. It contains all
 * fields representing a Niko Home Control thermostat and has methods to set the thermostat in Niko Home Control and
 * receive thermostat updates.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NhcIThermostat extends NhcThermostat {

    private final Logger logger = LoggerFactory.getLogger(NhcIThermostat.class);

    NhcIThermostat(String id, String name, @Nullable String location) {
        super(id, name, location);
    }

    /**
     * Sends thermostat mode to Niko Home Control.
     *
     * @param mode
     */
    @Override
    public void executeMode(int mode) {
        logger.debug("Niko Home Control: execute thermostat mode {} for {}", mode, this.id);

        NhcMessageCmd nhcCmd = new NhcMessageCmd("executethermostat", this.id).withMode(mode);

        NikoHomeControlCommunication comm = nhcComm;
        if (comm != null) {
            comm.sendMessage(nhcCmd);
        }
    }

    /**
     * Sends thermostat setpoint to Niko Home Control.
     *
     * @param overrule temperature to overrule the setpoint in 0.1°C multiples
     * @param time     time duration in min for overrule
     */
    @Override
    public void executeOverrule(int overrule, int overruletime) {
        logger.debug("Niko Home Control: execute thermostat overrule {} during {} min for {}", overrule, overruletime,
                this.id);

        String overruletimeString = String.format("%1$02d:%2$02d", overruletime / 60, overruletime % 60);
        NhcMessageCmd nhcCmd = new NhcMessageCmd("executethermostat", this.id).withOverrule(overrule)
                .withOverruletime(overruletimeString);

        NikoHomeControlCommunication comm = nhcComm;
        if (comm != null) {
            comm.sendMessage(nhcCmd);
        }
    }
}
