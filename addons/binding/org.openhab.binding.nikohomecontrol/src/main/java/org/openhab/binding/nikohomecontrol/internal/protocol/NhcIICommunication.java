/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Authentication;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.ActionType;
import org.openhab.binding.nikohomecontrol.internal.handler.NhcIIBridgeHandler;
import org.openhab.binding.nikohomecontrol.internal.handler.NikoHomeControlBridgeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link NhcIICommunication} class is able to do the following tasks with Niko Home Control II
 * systems:
 * <ul>
 * <li>Start and stop connection with Niko Home Control II Connected Controller.
 * <li>Read all setup and status information from the Niko Home Control Controller.
 * <li>Execute Niko Home Control commands.
 * <li>Poll for events from Niko Home Control.
 * </ul>
 *
 * Only switch, dimmer and rollershutter actions are currently implemented.
 *
 * A class instance is instantiated from the {@link NhcIIBridgeHandler} class initialization.
 *
 * @author Mark Herwege
 */
@NonNullByDefault
public class NhcIICommunication extends NikoHomeControlCommunication {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final String GET_GATEWAYS_URI = "https://nrt.fifthplay.com/getgateways";
    private final String APPLICATION_TOKEN = "616C13BE-14BE-4BCA-ADC8-0E3E4490779A";

    private final int SOAP_TIMEOUT = 5000; // timout in ms

    @Nullable
    private NhcIIGateway nhcGateway;

    private HttpClient httpClient;

    @Nullable
    private ScheduledFuture<?> pollingFuture;
    private ScheduledExecutorService scheduler;
    private long pollingInterval;

    protected NhcIIBridgeHandler bridgeCallBack;

    /**
     * Constructor for Niko Home Control communication object, manages communication with
     * Niko Home Control II Connected Controller.
     *
     */
    public NhcIICommunication(NikoHomeControlBridgeHandler bridgeCallBack, HttpClient httpClient,
            ScheduledExecutorService scheduler, long pollingInterval) {
        this.bridgeCallBack = (NhcIIBridgeHandler) bridgeCallBack;
        this.httpClient = httpClient;
        this.scheduler = scheduler;
        this.pollingInterval = pollingInterval;
    }

    @Override
    public synchronized void startCommunication() {
        String username = bridgeCallBack.getUsername();
        String password = bridgeCallBack.getPassword();

        try {
            if (username.isEmpty()) {
                logger.warn("Niko Home Control: no username set.");
                stopCommunication();
                return;
            }

            nhcGateway = getGateway(username, password);
            NhcIIGateway gateway = nhcGateway;
            if (gateway == null) {
                logger.warn("Niko Home Control: could not find NHC II gateway.");
                stopCommunication();
                return;
            }

            if (!isGatewayOnline(username, password, gateway.getId())) {
                logger.debug("Niko Home Control: NHC II gateway not online.");
                stopCommunication();
                return;
            }

            initialize(username, password);

            startPolling(username, password);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            logger.warn("Niko Home Control: error initializing communication from thread {}",
                    Thread.currentThread().getId());
            stopCommunication();
        }
    }

    @Override
    public synchronized void stopCommunication() {
        if (pollingFuture != null) {
            pollingFuture.cancel(true);
            pollingFuture = null;
        }
        bridgeCallBack.bridgeOffline();
    }

    @Override
    public boolean communicationActive() {
        if (nhcGateway != null) {
            return nhcGateway.getIsOnline();
        } else {
            return false;
        }
    }

    /**
     * After setting up the communication with the Niko Home Control Connected Controller, send all initialization
     * messages.
     *
     */
    private void initialize(String username, String password) {
        getDevices(username, password);
    }

    /**
     * Retrieves the gateway from the Niko cloud in a Niko Home Control II installation
     *
     * @return the nhcGateway, null if not found
     */
    private @Nullable NhcIIGateway getGateway(String username, String password)
            throws InterruptedException, TimeoutException, ExecutionException {
        NhcIIGateway gateway = null;

        URI uri = URI.create(GET_GATEWAYS_URI);
        Authentication.Result authn = new BasicAuthentication.BasicResult(uri, username, password);
        Request request = httpClient.newRequest(uri).method(HttpMethod.GET).header("Application", APPLICATION_TOKEN);
        authn.apply(request);

        logger.debug("Request: {}", request);

        ContentResponse response = request.send();

        logger.debug("Response: {}", response);

        List<NhcIIGateway> gateways = new ArrayList<>();

        Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();
        Type listType = new TypeToken<List<NhcIIGateway>>() {
        }.getType();
        gateways = gson.fromJson(response.getContentAsString(), listType);

        // Use the first gateway returned
        if (gateways.isEmpty()) {
            logger.debug("Niko Home Control: No gateway found in cloud for NHC II");
        } else {
            gateway = gateways.get(0);
            String gatewayId = gateway.getId();
            logger.debug("Niko Home Control: NHC II gateway found with ID {}", gatewayId);
        }
        return gateway;
    }

    public @Nullable NhcIIGateway getGateway() {
        return this.nhcGateway;
    }

    private boolean isGatewayOnline(String username, String password, String gatewayId) {
        SoapIsGatewayOnline soap = new SoapIsGatewayOnline();
        boolean online = soap.isGatewayOnline(username, password, httpClient, SOAP_TIMEOUT, gatewayId);
        if (nhcGateway != null) {
            nhcGateway.setIsOnline(online);
        }
        return online;
    }

    private void getDevices(String username, String password) {
        SoapGetDevices soap = new SoapGetDevices();
        List<NhcIIAction> actionList = soap.getDevices(username, password, httpClient, SOAP_TIMEOUT);

        for (NhcIIAction action : actionList) {
            if (!actions.containsKey(action.id)) {
                actions.put(action.id, action);
            } else {
                actions.get(action.id).withOpenTime(action.getOpenTime()).withCloseTime(action.getCloseTime())
                        .setState(action.getState());
            }
        }

        locations = soap.getLocations();
    }

    private void startPolling(String username, String password) {
        logger.debug("Niko Home Control: set up remote polling every {} s.", pollingInterval);

        pollingFuture = scheduler.scheduleWithFixedDelay(() -> {
            logger.debug("Niko Home Control: remote polling for status.");

            SoapGetDevices soap = new SoapGetDevices();
            List<NhcIIAction> actionList = soap.updateDevices(username, password, httpClient, SOAP_TIMEOUT);

            for (NhcIIAction action : actionList) {
                if (actions.containsKey(action.id)) {
                    actions.get(action.id).setState(action.getState());
                }
            }
        }, pollingInterval, pollingInterval, TimeUnit.SECONDS);
    }

    @Override
    public void execute(String actionId, String value) {
        String username = bridgeCallBack.getUsername();
        String password = bridgeCallBack.getPassword();

        ActionType actionType = actions.get(actionId).getType();

        SoapControlDevice soap = new SoapControlDevice();
        soap.controlDevice(username, password, httpClient, SOAP_TIMEOUT, actionId, actionType, value);
    }
}
