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
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcConstants.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link NhcAction} class represents the action Niko Home Control communication object. It contains all fields
 * representing a Niko Home Control action and has methods to trigger the action in Niko Home Control and receive action
 * updates. Specific implementation are {@link NhcIAction} and {@link NhcIIAction}.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public abstract class NhcAction {

    private final Logger logger = LoggerFactory.getLogger(NhcAction.class);

    @Nullable
    protected NikoHomeControlCommunication nhcComm;

    protected String id;
    protected String name;
    protected ActionType type;
    protected @Nullable String location;
    protected int state;
    protected int closeTime = 0;
    protected int openTime = 0;

    @Nullable
    private NhcActionEvent eventHandler;

    NhcAction(String id, String name, ActionType type, @Nullable String location) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.location = location;
    }

    /**
     * This method should be called when an object implementing the {@NhcActionEvent} interface is initialized.
     * It keeps a record of the event handler in that object so it can be updated when the action receives an update
     * from the Niko Home Control IP-interface.
     *
     * @param eventHandler
     */
    public void setEventHandler(NhcActionEvent eventHandler) {
        this.eventHandler = eventHandler;
    }

    /**
     * This method sets a pointer to the nhcComm object of class {@link NikoHomeControlCommuncation}.
     * This is then used to be able to call back the sendCommand method in this class to send a command to the
     * Niko Home Control IP-interface when..
     *
     * @param nhcComm
     */
    public void setNhcComm(NikoHomeControlCommunication nhcComm) {
        this.nhcComm = nhcComm;
    }

    /**
     * Get the id of the action.
     *
     * @return the id
     */
    public String getId() {
        return this.id;
    }

    /**
     * Get name of action.
     *
     * @return action name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get type of action identified.
     * <p>
     * ActionType can be RELAY (for simple light or socket switch), DIMMER, ROLLERSHUTTER or GENERIC.
     *
     * @return {@link ActionType}
     */
    public ActionType getType() {
        return this.type;
    }

    /**
     * Get location name of action.
     *
     * @return location name
     */
    public @Nullable String getLocation() {
        return this.location;
    }

    /**
     * Get state of action.
     * <p>
     * State is a value between 0 and 100 for a dimmer or rollershutter.
     * Rollershutter state is 0 for fully closed and 100 for fully open.
     * State is 0 or 100 for a switch.
     *
     * @return action state
     */
    public int getState() {
        return this.state;
    }

    /**
     * Get openTime of action.
     * <p>
     * openTime is the time in seconds to fully open a rollershutter.
     *
     * @return action openTime
     */
    public int getOpenTime() {
        return this.openTime;
    }

    /**
     * Get closeTime of action.
     * <p>
     * closeTime is the time in seconds to fully close a rollershutter.
     *
     * @return action closeTime
     */
    public int getCloseTime() {
        return this.closeTime;
    }

    protected void updateState() {
        NhcActionEvent eventHandler = this.eventHandler;
        if (eventHandler != null) {
            logger.debug("Niko Home Control: update channel state for {} with {}", id, state);
            eventHandler.actionEvent(state);
        }
    }

    /**
     * Sets state of action.
     *
     * @param state - The allowed values depend on the action type.
     *                  switch action: 0 or 100
     *                  dimmer action: between 0 and 100
     *                  rollershutter action: between 0 and 100
     */
    void setState(int state) {
        this.state = state;
        updateState();
    }

    /**
     * Sends action to Niko Home Control. This method is implemented in {@link NhcIAction} and {@link NhcIIAction}.
     *
     * @param command - The allowed values depend on the action type.
     *                    switch action: On or Off
     *                    dimmer action: between 0 and 100, On or Off
     *                    rollershutter action: between 0 and 100, Up, Down or Stop
     */
    public abstract void execute(String command);
}
