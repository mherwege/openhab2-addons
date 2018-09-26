/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol.nhc2;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcAction;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NhcAction2} class represents the action Niko Home Control II communication object. It contains all fields
 * representing a Niko Home Control action and has methods to trigger the action in Niko Home Control and receive action
 * updates.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NhcAction2 extends NhcAction {

    private final Logger logger = LoggerFactory.getLogger(NhcAction2.class);

    NhcAction2(String id, String name, ActionType type, @Nullable String location) {
        super(id, name, type, location);
    }

    /**
     * Sends action to Niko Home Control. This version is used for Niko Home Control II, that has extra status options.
     *
     * @param command - The allowed values depend on the action type.
     *                    switch action: On or Off
     *                    dimmer action: between 0 and 100, On or Off
     *                    rollershutter action: between 0 and 100, Up, Down or Stop
     */
    @Override
    public void execute(String command) {
        logger.debug("Niko Home Control: execute action {} of type {} for {}", command, this.type, this.id);

        if (nhcComm != null) {
            nhcComm.executeAction(this.id, command);
        }
    }
}
