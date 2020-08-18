/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.upnpcontrol.internal.services;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class UpnpRenderingControlConfiguration {
    public Set<String> audioChannels = new HashSet<>();

    public boolean volume;
    public boolean mute;
    public boolean loudness;

    public int maxvolume = 100;
}
