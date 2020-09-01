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
package org.openhab.binding.upnpcontrol.internal.config;

import static org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants.DEFAULT_PATH;

import java.io.File;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 *
 * @author Mark Herwege - Initial contribution
 */
@NonNullByDefault
public class UpnpControlBindingConfiguration {

    public @Nullable String path = DEFAULT_PATH;

    public void update(UpnpControlBindingConfiguration newConfig) {
        String newPath = newConfig.path;
        if (newPath == null) {
            path = DEFAULT_PATH;
            return;
        }

        File file = new File(newPath);
        if (!file.isDirectory()) {
            file = file.getParentFile();
        }
        if (file.exists()) {
            if (!(newPath.endsWith(File.separator) || newPath.endsWith("/"))) {
                newPath = newPath + File.separator;
            }
            path = newPath;
        } else {
            path = DEFAULT_PATH;
        }
    }
}
