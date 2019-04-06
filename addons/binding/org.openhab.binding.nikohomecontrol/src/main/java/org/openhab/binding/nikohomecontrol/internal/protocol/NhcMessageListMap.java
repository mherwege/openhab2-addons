/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
package org.openhab.binding.nikohomecontrol.internal.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class {@link NhcMessageListMap} used as output from gson for cmd or event feedback from Niko Home Control where the
 * data part is enclosed by [] and contains a list of json strings. Extends {@link NhcMessageBase}.
 * <p>
 * Example: <code>{"cmd":"listactions","data":[{"id":1,"name":"Garage","type":1,"location":1,"value1":0},
 * {"id":25,"name":"Frontdoor","type":2,"location":2,"value1":0}]}</code>
 *
 * @author Mark Herwege - Initial Contribution
 */
class NhcMessageListMap extends NhcMessageBase {

    private List<Map<String, String>> data = new ArrayList<>();

    List<Map<String, String>> getData() {
        return this.data;
    }

    void setData(List<Map<String, String>> data) {
        this.data = data;
    }
}