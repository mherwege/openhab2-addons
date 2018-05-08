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

/**
 * The {@link NhcConstants} class defines common constants used in the Niko Home Control communication.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NhcConstants {

    // Action types abstracted from NhcI and NhcII action types
    public static enum ActionType {
        RELAY,
        DIMMER,
        ROLLERSHUTTER,
        GENERIC
    };

    // switch and dimmer constants in the Nhc layer
    public static final String NHCON = "On";
    public static final String NHCOFF = "Off";

    public static final String NHCTRIGGERED = "Triggered";

    // rollershutter constants in the Nhc layer
    public static final String NHCDOWN = "Down";
    public static final String NHCUP = "Up";
    public static final String NHCSTOP = "Stop";

}
