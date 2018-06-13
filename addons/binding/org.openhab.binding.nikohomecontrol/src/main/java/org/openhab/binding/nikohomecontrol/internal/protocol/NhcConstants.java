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

    // dimmer constants
    public static final int NHCON = 254;
    public static final int NHCOFF = 255;

    // rollershutter constants
    public static final int NHCDOWN = 254;
    public static final int NHCUP = 255;
    public static final int NHCSTOP = 253;

}
