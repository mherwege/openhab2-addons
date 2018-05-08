/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import static org.openhab.binding.nikohomecontrol.internal.protocol.NhcConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcConstants.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NhcIIAction} class represents the action Niko Home Control II communication object. It contains all fields
 * representing a Niko Home Control action and has methods to trigger the action in Niko Home Control and receive action
 * updates.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NhcIIAction extends NhcAction {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected int prevState;
    protected String stringState = "";

    NhcIIAction(String id, String name, ActionType type, @Nullable String location) {
        super(id, name, type, location);
    }

    /**
     * Sets state of action.
     *
     * @param state - Sets a state as a string.
     *                  switch action: 0 or 100, On or Off
     *                  dimmer action: between 0 and 100, On or Off
     *                  rollershutter action: between 0 and 100
     */
    void setState(String state) {
        if (NHCON.equals(state)) {
            this.stringState = state;
            if (this.prevState != 0) {
                this.state = this.prevState;
            } else {
                this.state = 100;
            }
        } else if (NHCOFF.equals(state)) {
            this.stringState = state;
            this.prevState = this.state;
            this.state = 0;
        } else {
            this.prevState = this.state;
            try {
                this.state = Double.valueOf(state).intValue();
                if ((this.state < 0) || (this.state > 100)) {
                    this.state = this.prevState;
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                logger.debug("Niko Home Control: illegal state {} for action {}", state, id);
            }
        }

        updateState();
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
            nhcComm.execute(this.id, command);
        }
    }
}
