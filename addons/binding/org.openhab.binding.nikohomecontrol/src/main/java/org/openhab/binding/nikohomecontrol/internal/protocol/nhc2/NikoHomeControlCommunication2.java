/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol.nhc2;

import static org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.*;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.io.transport.mqtt.MqttException;
import org.eclipse.smarthome.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.binding.nikohomecontrol.internal.handler.NikoHomeControlBridgeHandler2;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcAction;
import org.openhab.binding.nikohomecontrol.internal.protocol.NhcControllerEvent;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlCommunication;
import org.openhab.binding.nikohomecontrol.internal.protocol.NikoHomeControlConstants.ActionType;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NhcDevice2.NhcProperty;
import org.openhab.binding.nikohomecontrol.internal.protocol.nhc2.NhcMessage2.NhcMessageParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link NikoHomeControlCommunication2} class is able to do the following tasks with Niko Home Control II
 * systems:
 * <ul>
 * <li>Start and stop MQTT connection with Niko Home Control II Connected Controller.
 * <li>Read all setup and status information from the Niko Home Control Controller.
 * <li>Execute Niko Home Control commands.
 * <li>Listen for events from Niko Home Control.
 * </ul>
 *
 * Only switch and dimmer actions are currently implemented.
 *
 * A class instance is instantiated from the {@link NikoHomeControlBridgeHandler2} class initialization.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NikoHomeControlCommunication2 extends NikoHomeControlCommunication implements MqttMessageSubscriber {

    private final Logger logger = LoggerFactory.getLogger(NikoHomeControlCommunication2.class);

    private volatile NhcMqttConnection2 mqttConnection;

    private volatile List<NhcProfile2> profiles = new ArrayList<>();
    private volatile String profileUuid = "";

    private volatile CompletableFuture<Boolean> communicationStarted = new CompletableFuture<>();

    private volatile List<NhcService2> services = new ArrayList<>();

    private volatile List<NhcLocation2> locations = new ArrayList<>();

    @Nullable
    private volatile NhcSystemInfo2 nhcSystemInfo;
    @Nullable
    private volatile NhcTimeInfo2 nhcTimeInfo;

    private final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE).create();

    /**
     * Constructor for Niko Home Control communication object, manages communication with
     * Niko Home Control II Connected Controller.
     *
     * @throws CertificateException when the SSL context for MQTT communication cannot be created
     * @throws UnknownHostException when the IP address is not provided
     *
     */
    public NikoHomeControlCommunication2(NhcControllerEvent handler) throws CertificateException {
        this.handler = handler;
        mqttConnection = new NhcMqttConnection2();
    }

    @Override
    public synchronized void startCommunication() {
        InetAddress addr = handler.getAddr();
        if (addr == null) {
            logger.warn("Niko Home Control: IP address cannot be empty");
            stopCommunication();
            return;
        }
        String addrString = addr.getHostAddress();
        @SuppressWarnings("null") // default provided, so cannot be null
        int port = handler.getPort();
        logger.debug("Niko Home Control: initializing for mqtt connection to CoCo on {}:{}", addrString, port);

        try {
            mqttConnection.startConnection(this, addrString, port);
            initialize();
        } catch (MqttException e) {
            logger.warn("Niko Home Control: error in mqtt communication, {}", e);
            stopCommunication();
            return;
        }
    }

    /**
     * This method executes the second part of the communication start. After the list of profiles are received on the
     * general MQTT connection, this method should be called to stop the general connection and start a touch profile
     * specific MQTT connection. This will allow receiving state information and updating state of devices.
     */
    private synchronized void startProfileCommunication() {
        mqttConnection.stopConnection();

        String profile = handler.getProfile();
        String password = handler.getPassword();

        if (profile.isEmpty()) {
            logger.warn("Niko Home Control: no profile set");
            stopCommunication();
            return;
        }
        try {
            profileUuid = profiles.stream().filter(p -> profile.equals(p.name)).findFirst().get().uuid;
        } catch (NoSuchElementException e) {
            logger.warn("Niko Home Control: profile '{}' does not match a profile in the controller", profile);
            stopCommunication();
            return;
        }

        if (password.isEmpty()) {
            logger.warn("Niko Home Control: password for profile cannot be empty");
            stopCommunication();
            return;
        }

        try {
            mqttConnection.startProfileConnection(this, profileUuid, password);
            initializeProfile();
        } catch (MqttException e) {
            logger.warn("Niko Home Control: error in mqtt communication, {}", e);
            stopCommunication();
            return;
        }
    }

    @Override
    public synchronized void stopCommunication() {
        mqttConnection.stopConnection();
        mqttConnection.stopProfileConnection();
        handler.controllerOffline();
    }

    @Override
    public boolean communicationActive() {
        try {
            // Wait until we received all devices info to confirm we are active.
            return communicationStarted.get(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return false;
        }
    }

    /**
     * After setting up the communication with the Niko Home Control Connected Controller, send all initialization
     * messages.
     *
     */
    private void initialize() {
        NhcMessage2 message = new NhcMessage2();

        try {
            message.method = "systeminfo.publish";
            mqttConnection.connectionPublish("public/system/cmd", gson.toJson(message));

            message.method = "profiles.list";
            mqttConnection.connectionPublish("public/authentication/cmd", gson.toJson(message));

        } catch (MqttException e) {
            logger.debug("Niko Home Control: initialize failed, mqtt exception {}", e);
            connectionLost();
        }
    }

    /**
     * After setting up the profile communication with the Niko Home Control Connected Controller, send all profile
     * specific initialization messages.
     *
     */
    private void initializeProfile() {
        NhcMessage2 message = new NhcMessage2();

        try {
            message.method = "services.list";
            mqttConnection.profileConnectionPublish(profileUuid + "/authentication/cmd", gson.toJson(message));

            message.method = "devices.list";
            mqttConnection.profileConnectionPublish(profileUuid + "/control/devices/cmd", gson.toJson(message));

            message.method = "locations.list";
            mqttConnection.profileConnectionPublish(profileUuid + "/control/locations/cmd", gson.toJson(message));

            message.method = "notifications.list";
            mqttConnection.profileConnectionPublish(profileUuid + "/notification/cmd", gson.toJson(message));

        } catch (MqttException e) {
            logger.debug("Niko Home Control: initialize profile failed, mqtt exception {}", e);
            connectionLost();
        }
    }

    private void connectionLost() {
        logger.debug("Niko Home Control: connection lost");
        stopCommunication();
    }

    private void timePublishRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcTimeInfo2> timeInfo = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            if (message.params != null) {
                timeInfo = message.params.stream().filter(p -> (p.timeInfo != null)).findFirst().get().timeInfo;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException e) {
            // Nothing to do
        }
        if (timeInfo != null) {
            nhcTimeInfo = timeInfo.get(0);
        }
    }

    private void systeminfoPublishRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcSystemInfo2> systemInfo = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            if (message.params != null) {
                systemInfo = message.params.stream().filter(p -> (p.systemInfo != null)).findFirst().get().systemInfo;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException e) {
            // Nothing to do
        }
        if (systemInfo != null) {
            nhcSystemInfo = systemInfo.get(0);
        }
    }

    private void profilesListRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcProfile2> profileList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            if (message.params != null) {
                profileList = message.params.stream().filter(p -> (p.profiles != null)).findFirst().get().profiles;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException e) {
            // Nothing to do
        }
        if (profileList == null) {
            profiles = new ArrayList<>();
        } else {
            profiles = profileList;
        }
    }

    private void servicesListRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcService2> serviceList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            if (message.params != null) {
                serviceList = message.params.stream().filter(p -> (p.services != null)).findFirst().get().services;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException e) {
            // Nothing to do
        }
        if (serviceList == null) {
            services = new ArrayList<>();
        } else {
            services = serviceList;
        }
    }

    private void locationsListRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcLocation2> locationList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            if (message.params != null) {
                locationList = message.params.stream().filter(p -> (p.locations != null)).findFirst().get().locations;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException e) {
            // Nothing to do
        }
        if (locationList == null) {
            locations = new ArrayList<>();
        } else {
            locations = locationList;
        }
    }

    private void devicesListRsp(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcDevice2> deviceList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            if (message.params != null) {
                deviceList = message.params.stream().filter(p -> (p.devices != null)).findFirst().get().devices;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException e) {
            // Nothing to do
        }
        if (deviceList == null) {
            return;
        }

        for (NhcDevice2 device : deviceList) {
            if (!"action".equals(device.type)) {
                logger.debug("Niko Home Control: {} is not an action", device.name);
                continue;
            }

            ActionType actionType = ActionType.GENERIC;
            switch (device.model) {
                case "light":
                case "alloff":
                    actionType = ActionType.RELAY;
                    break;
                case "dimmer":
                    actionType = ActionType.DIMMER;
                    break;

            }

            String location;
            try {
                location = device.parameters.stream().filter(p -> (p.locationId != null)).findFirst()
                        .get().locationName;
            } catch (NoSuchElementException e) {
                location = null;
            }

            NhcAction nhcAction;

            if (!this.actions.containsKey(device.uuid)) {
                logger.debug("Niko Home Control: adding device {}", device.name);

                nhcAction = new NhcAction2(device.uuid, device.name, actionType, location);
                nhcAction.setNhcComm(this);
                this.actions.put(device.uuid, nhcAction);
            } else {
                nhcAction = this.actions.get(device.uuid);
            }

            int state = deviceState(device);
            nhcAction.setState(state);
        }

        // Once a devices list response is received, we know the communication is fully started.
        logger.debug("Niko Home Control: Communication start complete.");
        communicationStarted.complete(true);
    }

    private void devicesStatusEvt(String response) {
        Type messageType = new TypeToken<NhcMessage2>() {
        }.getType();
        List<NhcDevice2> deviceList = null;
        try {
            NhcMessage2 message = gson.fromJson(response, messageType);
            if (message.params != null) {
                deviceList = message.params.stream().filter(p -> (p.devices != null)).findFirst().get().devices;
            }
        } catch (JsonSyntaxException e) {
            logger.debug("Niko Home Control: unexpected json {}", response);
        } catch (NoSuchElementException e) {
            // Nothing to do
        }
        if (deviceList == null) {
            return;
        }

        for (NhcDevice2 device : deviceList) {
            int state = deviceState(device);

            if (this.actions.containsKey(device.uuid)) {
                this.actions.get(device.uuid).setState(state);
            }
        }
    }

    private int deviceState(NhcDevice2 device) {
        int state = 0;

        Optional<NhcProperty> statusProperty = device.properties.stream().filter(p -> (p.status != null)).findFirst();
        Optional<NhcProperty> dimmerProperty = device.properties.stream().filter(p -> (p.brightness != null))
                .findFirst();

        if (statusProperty.isPresent() && NHCON.equals(statusProperty.get().status)) {
            state = 100;
        }

        if (dimmerProperty.isPresent()) {
            if (state != 0) {
                state = Integer.valueOf(dimmerProperty.get().brightness);
            }
        }

        return state;
    }

    @Override
    public void executeAction(String actionId, String value) {
        NhcMessage2 message = new NhcMessage2();

        try {
            message.method = "devices.control";
            message.params = new ArrayList<>();
            NhcMessageParam param = message.new NhcMessageParam();
            message.params.add(param);
            param.devices = new ArrayList<>();
            NhcDevice2 device = new NhcDevice2();
            param.devices.add(device);
            device.uuid = actionId;
            device.properties = new ArrayList<>();
            NhcProperty property = device.new NhcProperty();
            device.properties.add(property);

            if (NHCON.equals(value) || NHCOFF.equals(value)) {
                property.status = value;
            } else if (actions.get(actionId).getType() == ActionType.DIMMER) {
                property.brightness = value;
            } else if (actions.get(actionId).getType() == ActionType.ROLLERSHUTTER) {
                // TODO
            }

            mqttConnection.profileConnectionPublish(profileUuid + "/control/devices/cmd", gson.toJson(message));

        } catch (MqttException e) {
            logger.debug("Niko Home Control: action command failed, mqtt exception {}", e);
            connectionLost();
        }
    }

    @Override
    public void executeThermostat(String thermostatId, int mode) {
        return;

    }

    @Override
    public void executeThermostat(String thermostatId, int overruleTemp, int overruleTime) {
        return;
    }

    @Override
    public void processMessage(String topic, byte[] payload) {
        String message = new String(payload);
        logger.debug("Niko Home Control: received topic {}, payload {}", topic, message);
        if ("public/system/evt".equals(topic)) {
            timePublishRsp(message);
        } else if ("public/system/rsp".equals(topic)) {
            systeminfoPublishRsp(message);
        } else if ("public/authentication/rsp".equals(topic)) {
            profilesListRsp(message);
            startProfileCommunication();
        } else if ((profileUuid + "/notification/rsp").equals(topic)) {
            // TODO
        } else if ((profileUuid + "/notifications/evt").equals(topic)) {
            // TODO
        } else if ((profileUuid + "/control/devices/evt").equals(topic)) {
            devicesStatusEvt(message);
        } else if ((profileUuid + "/control/devices/rsp").equals(topic)) {
            devicesListRsp(message);
        } else if ((profileUuid + "/authentication/rsp").equals(topic)) {
            servicesListRsp(message);
        } else if ((profileUuid + "/locations/rsp").equals(topic)) {
            locationsListRsp(message);
        }
    }

    /**
     * @return system info retrieved from Connected Controller
     */
    public NhcSystemInfo2 getSystemInfo() {
        NhcSystemInfo2 systemInfo = nhcSystemInfo;
        if (systemInfo == null) {
            systemInfo = new NhcSystemInfo2();
        }
        return systemInfo;
    }

    /**
     * @return time info retrieved from Connected Controller
     */
    public NhcTimeInfo2 getTimeInfo() {
        NhcTimeInfo2 timeInfo = nhcTimeInfo;
        if (timeInfo == null) {
            timeInfo = new NhcTimeInfo2();
        }
        return timeInfo;
    }

    /**
     * @return comma separated list of services retrieved from Connected Controller
     */
    public String getServices() {
        StringBuilder builder = new StringBuilder();
        for (NhcService2 service : services) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(service.name);
        }
        return builder.toString();
    }
}
