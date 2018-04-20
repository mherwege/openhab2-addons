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
package org.openhab.binding.upnpcontrol.internal.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.io.transport.upnp.UpnpIOParticipant;
import org.eclipse.smarthome.io.transport.upnp.UpnpIOService;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpHandler} is the base class for {@link UpnpRendererHandler} and {@link UpnpServerHandler}. The base
 * class implements UPnPConnectionManager service actions.
 *
 * @author Mark Herwege - Initial contribution
 * @author Karel Goderis - Based on UPnP logic in Sonos binding
 */
@NonNullByDefault
public abstract class UpnpHandler extends BaseThingHandler implements UpnpIOParticipant {

    private final Logger logger = LoggerFactory.getLogger(UpnpHandler.class);

    protected UpnpIOService service;

    protected volatile int connectionId = 0; // UPnP Connection Id
    protected volatile int avTransportId = 0; // UPnP AVTtransport Id
    protected volatile int rcsId = 0; // UPnP Rendering Control Id

    protected @NonNullByDefault({}) UpnpControlConfiguration config;

    protected @Nullable ScheduledFuture<?> pollingJob;
    protected final Object jobLock = new Object();

    protected volatile @Nullable CompletableFuture<Boolean> connectionIdSet;
    protected volatile @Nullable CompletableFuture<Boolean> avTransportIdSet;
    protected volatile @Nullable CompletableFuture<Boolean> rcsIdSet;
    protected static final int UPNP_RESPONSE_TIMEOUT_MILLIS = 2500;

    protected static final int SUBSCRIPTION_DURATION_SECONDS = 3600;
    protected List<String> serviceSubscriptions = new ArrayList<>();
    protected volatile @Nullable ScheduledFuture<?> subscriptionRefreshJob;
    protected final Runnable subscriptionRefresh = () -> {
        for (String subscription : serviceSubscriptions) {
            removeSubscription(subscription);
            addSubscription(subscription, SUBSCRIPTION_DURATION_SECONDS);
        }
    };
    protected volatile boolean upnpSubscribed;

    public UpnpHandler(Thing thing, UpnpIOService upnpIOService) {
        super(thing);

        this.service = upnpIOService;
    }

    @Override
    public void initialize() {
        config = getConfigAs(UpnpControlConfiguration.class);
        service.registerParticipant(this);
    }

    @Override
    public void dispose() {
        removeSubscriptions();
        cancelPollingJob();

        service.unregisterParticipant(this);
    }

    private void cancelPollingJob() {
        ScheduledFuture<?> job = pollingJob;

        if (job != null) {
            job.cancel(true);
        }
        pollingJob = null;
    }

    /**
     * To be called from implementing classes when initializing the device, to start initialization refresh
     */
    protected void initDevice() {
        if ((config.udn != null) && !config.udn.isEmpty()) {
            if (config.refresh == 0) {
                scheduler.submit(this::initJob);
            } else {
                pollingJob = scheduler.scheduleWithFixedDelay(this::initJob, 0, config.refresh, TimeUnit.SECONDS);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "No UDN configured for " + thing.getLabel());
        }
    }

    /**
     * Job to be executed in an asynchronous process when initializing a device. This checks if the connection id's are
     * correctly set up for the connection. It can also be called from a polling job to get the thing back online when
     * connection is lost.
     */
    protected abstract void initJob();

    protected boolean checkForConnectionIds() {
        return checkForConnectionId(connectionIdSet) & checkForConnectionId(avTransportIdSet)
                & checkForConnectionId(rcsIdSet);
    }

    private boolean checkForConnectionId(@Nullable CompletableFuture<Boolean> future) {
        try {
            if (future != null) {
                return future.get(UPNP_RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        }
        return true;
    }

    /**
     * Invoke PrepareForConnection on the UPnP Connection Manager.
     * Result is received in {@link onValueReceived}.
     *
     * @param remoteProtocolInfo
     * @param peerConnectionManager
     * @param peerConnectionId
     * @param direction
     */
    protected void prepareForConnection(String remoteProtocolInfo, String peerConnectionManager, int peerConnectionId,
            String direction) {
        CompletableFuture<Boolean> settingConnection = connectionIdSet;
        CompletableFuture<Boolean> settingAVTransport = avTransportIdSet;
        CompletableFuture<Boolean> settingRcs = rcsIdSet;
        if (settingConnection != null) {
            settingConnection.complete(false);
        }
        if (settingAVTransport != null) {
            settingAVTransport.complete(false);
        }
        if (settingRcs != null) {
            settingRcs.complete(false);
        }

        // Set new futures, so we don't try to use service when connection id's are not known yet
        connectionIdSet = new CompletableFuture<Boolean>();
        avTransportIdSet = new CompletableFuture<Boolean>();
        rcsIdSet = new CompletableFuture<Boolean>();

        HashMap<String, String> inputs = new HashMap<String, String>();
        inputs.put("RemoteProtocolInfo", remoteProtocolInfo);
        inputs.put("PeerConnectionManager", peerConnectionManager);
        inputs.put("PeerConnectionID", Integer.toString(peerConnectionId));
        inputs.put("Direction", direction);

        invokeAction("ConnectionManager", "PrepareForConnection", inputs);
    }

    /**
     * Invoke ConnectionComplete on UPnP Connection Manager.
     */
    protected void connectionComplete() {
        Map<String, String> inputs = Collections.singletonMap("ConnectionId", Integer.toString(connectionId));

        invokeAction("ConnectionManager", "ConnectionComplete", inputs);
    }

    /**
     * Invoke GetCurrentConnectionInfo on the UPnP Connection Manager.
     * Result is received in {@link onValueReceived}.
     */
    protected void getCurrentConnectionInfo() {
        CompletableFuture<Boolean> settingAVTransport = avTransportIdSet;
        CompletableFuture<Boolean> settingRcs = rcsIdSet;
        if (settingAVTransport != null) {
            settingAVTransport.complete(false);
        }
        if (settingRcs != null) {
            settingRcs.complete(false);
        }

        // Set new futures, so we don't try to use service when connection id's are not known yet
        avTransportIdSet = new CompletableFuture<Boolean>();
        rcsIdSet = new CompletableFuture<Boolean>();

        // ConnectionID will default to 0 if not set through prepareForConnection method
        Map<String, String> inputs = Collections.singletonMap("ConnectionId", Integer.toString(connectionId));

        invokeAction("ConnectionManager", "GetCurrentConnectionInfo", inputs);
    }

    /**
     * Invoke GetProtocolInfo on UPnP Connection Manager.
     * Result is received in {@link onValueReceived}.
     */
    protected void getProtocolInfo() {
        Map<String, String> inputs = Collections.emptyMap();

        invokeAction("ConnectionManager", "GetProtocolInfo", inputs);
    }

    @Override
    public void onServiceSubscribed(@Nullable String service, boolean succeeded) {
        logger.debug("Upnp device {} received subscription reply {} from service {}", thing.getLabel(), succeeded,
                service);
        if (succeeded) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Could not subscribe to service " + service + "for" + thing.getLabel());
        }
    }

    @Override
    public void onStatusChanged(boolean status) {
        if (status) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Communication lost with " + thing.getLabel());
        }
    }

    @Override
    public @Nullable String getUDN() {
        return config.udn;
    }

    /**
     * This method wraps {@link org.eclipse.smarthome.io.transport.upnp.UpnpIOService.invokeAction}. It schedules and
     * submits the call and calls {@link onValueReceived} upon completion. All state updates or other actions depending
     * on the results should be triggered from {@link onValueReceived} because the class fields with results will be
     * filled asynchronously.
     *
     * @param serviceId
     * @param actionId
     * @param inputs
     */
    protected void invokeAction(String serviceId, String actionId, Map<String, String> inputs) {
        scheduler.submit(() -> {
            Map<String, String> result = service.invokeAction(this, serviceId, actionId, inputs);
            if (logger.isDebugEnabled() && !"GetPositionInfo".equals(actionId)) {
                // don't log position info refresh every second
                logger.debug("Upnp device {} invoke upnp action {} on service {} with inputs {}", thing.getLabel(),
                        actionId, serviceId, inputs);
                logger.debug("Upnp device {} invoke upnp action {} on service {} reply {}", thing.getLabel(), actionId,
                        serviceId, result);
            }
            for (String variable : result.keySet()) {
                String newVariable = preProcessValueReceived(inputs, variable, result.get(variable), serviceId);
                onValueReceived(newVariable, result.get(variable), serviceId);
            }
        });
    }

    /**
     * Some received values need info on inputs of action. Therefore we allow to pre-process in a separate step.
     *
     * @param inputs
     * @param variable
     * @param value
     * @param service
     * @return
     */
    protected @Nullable String preProcessValueReceived(Map<String, String> inputs, @Nullable String variable,
            @Nullable String value, @Nullable String service) {
        return variable;
    }

    @Override
    public void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service) {
        if (variable == null) {
            return;
        }
        switch (variable) {
            case "ConnectionID":
                try {
                    connectionId = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    connectionId = 0;
                }
                CompletableFuture<Boolean> connectionIdFuture = connectionIdSet;
                if (connectionIdFuture != null) {
                    connectionIdFuture.complete(true);
                }
                break;
            case "AVTransportID":
                try {
                    avTransportId = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    avTransportId = 0;
                }
                CompletableFuture<Boolean> avTransportIdFuture = avTransportIdSet;
                if (avTransportIdFuture != null) {
                    avTransportIdFuture.complete(true);
                }
                break;
            case "RcsID":
                try {
                    rcsId = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    rcsId = 0;
                }
                CompletableFuture<Boolean> rcsIdFuture = rcsIdSet;
                if (rcsIdFuture != null) {
                    rcsIdFuture.complete(true);
                }
                break;
            default:
                break;
        }
    }

    /**
     * Subscribe this handler as a participant to a GENA subscription.
     *
     * @param serviceId
     * @param duration
     */
    protected void addSubscription(String serviceId, int duration) {
        if (service.isRegistered(this)) {
            logger.debug("Upnp device {} add upnp subscription on {}", thing.getLabel(), serviceId);
            service.addSubscription(this, serviceId, duration);
        }
    }

    /**
     * Remove this handler from the GENA subscriptions.
     *
     * @param serviceId
     */
    protected void removeSubscription(String serviceId) {
        if (service.isRegistered(this)) {
            service.removeSubscription(this, serviceId);
        }
    }

    protected void addSubscriptions() {
        for (String subscription : serviceSubscriptions) {
            addSubscription(subscription, SUBSCRIPTION_DURATION_SECONDS);
        }
        subscriptionRefreshJob = scheduler.scheduleWithFixedDelay(subscriptionRefresh,
                SUBSCRIPTION_DURATION_SECONDS / 2, SUBSCRIPTION_DURATION_SECONDS / 2, TimeUnit.SECONDS);

        upnpSubscribed = true;
    }

    protected void removeSubscriptions() {
        cancelSubscriptionRefreshJob();

        for (String subscription : serviceSubscriptions) {
            removeSubscription(subscription);
        }

        upnpSubscribed = false;
    }

    private void cancelSubscriptionRefreshJob() {
        ScheduledFuture<?> refreshJob = subscriptionRefreshJob;

        if (refreshJob != null) {
            refreshJob.cancel(true);
        }
        subscriptionRefreshJob = null;
    }
}
