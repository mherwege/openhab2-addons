/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import com.google.gson.annotations.SerializedName;

/**
 * @author Mark Herwege
 *
 */
public class NhcIIGateway {

    private String id;
    private String hostname;
    private String networkName;
    @SerializedName("NHCRevision")
    private String nhcRevision;
    private String model;
    private Boolean isOnline;
    private Integer remoteStatus;

    public String getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public String getNetworkName() {
        return networkName;
    }

    public String getNhcRevision() {
        return nhcRevision;
    }

    public String getModel() {
        return model;
    }

    public Boolean getIsOnline() {
        return isOnline;
    }

    public void setIsOnline(boolean online) {
        isOnline = online;
    }

    public Integer getRemoteStatus() {
        return remoteStatus;
    }
}
