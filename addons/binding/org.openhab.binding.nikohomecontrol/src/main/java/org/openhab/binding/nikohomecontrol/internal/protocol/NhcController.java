/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.openhab.binding.nikohomecontrol.internal.protocol;

import java.net.InetAddress;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link NhcController} interface is used to get configuration information and to pass alarm or notice events
 * received from the Niko Home Control controller to the consuming client. It is designed to pass events to openHAB
 * handlers that implement this interface. Because of the design, the
 * org.openhab.binding.nikohomecontrol.internal.protocol package can be extracted and used independent of openHAB.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public interface NhcController {

    /**
     * Get the IP-address of the Niko Home Control IP-interface.
     *
     * @return the addr
     */
    public @Nullable InetAddress getAddr();

    /**
     * Get the listening port of the Niko Home Control IP-interface.
     *
     * @return the port
     */
    public @Nullable Integer getPort();

    /**
     * Called to indicate the connection with the Niko Home Control Controller is offline.
     *
     */
    public void controllerOffline();

    /**
     * This method is called when an alarm event is received from the Niko Home Control controller.
     *
     * @param alarmText
     */
    public void alarmEvent(String alarmText);

    /**
     * This method is called when a notice event is received from the Niko Home Control controller.
     *
     * @param alarmText
     */
    public void noticeEvent(String noticeText);

}
