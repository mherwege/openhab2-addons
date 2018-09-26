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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.io.transport.mqtt.MqttActionCallback;
import org.eclipse.smarthome.io.transport.mqtt.MqttBrokerConnection;
import org.eclipse.smarthome.io.transport.mqtt.MqttConnectionState;
import org.eclipse.smarthome.io.transport.mqtt.MqttException;
import org.eclipse.smarthome.io.transport.mqtt.MqttMessageSubscriber;
import org.eclipse.smarthome.io.transport.mqtt.sslcontext.SSLContextProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link NhcMqttConnection2} manages the MQTT connections to the Connected Controller. The initial secured connection
 * is used for general system communication. This communication also communicates the profile uuid's needed as username
 * for touch profile specific communication. The touch profile specific communication uses the same secure communication
 * with added username and password. It allows receiving state information about specific devices and sending updates to
 * specific devices.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class NhcMqttConnection2 implements MqttActionCallback {

    private final Logger logger = LoggerFactory.getLogger(NhcMqttConnection2.class);

    @Nullable
    private volatile MqttBrokerConnection mqttConnection;
    @Nullable
    private volatile MqttBrokerConnection mqttProfileConnection;

    @Nullable
    private volatile CompletableFuture<Boolean> subscribedFuture;
    @Nullable
    private volatile CompletableFuture<Boolean> profileSubscribedFuture;

    private SSLContextProvider sslContextProvider;

    private volatile String cocoAddress = "";
    private volatile int port;

    NhcMqttConnection2() throws CertificateException {
        this.sslContextProvider = getSSLContext();
    }

    private SSLContextProvider getSSLContext() throws CertificateException {
        try {
            // Load server public certificates into key store
            CertificateFactory cf = CertificateFactory.getInstance("X509");
            InputStream certificateStream = new ByteArrayInputStream(CERTCA.getBytes(StandardCharsets.UTF_8));
            X509Certificate caCertificate = (X509Certificate) cf.generateCertificate(certificateStream);
            certificateStream = new ByteArrayInputStream(CERTINTERMEDIATE.getBytes(StandardCharsets.UTF_8));
            X509Certificate intCertificate = (X509Certificate) cf.generateCertificate(certificateStream);
            certificateStream = new ByteArrayInputStream(CERTNEWCA.getBytes(StandardCharsets.UTF_8));
            X509Certificate newCaCertificate = (X509Certificate) cf.generateCertificate(certificateStream);
            certificateStream = new ByteArrayInputStream(CERTNEWINTERMEDIATE.getBytes(StandardCharsets.UTF_8));
            X509Certificate newIntCertificate = (X509Certificate) cf.generateCertificate(certificateStream);

            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca-certificate", caCertificate);
            keyStore.setCertificateEntry("intermediate-certificate", intCertificate);
            keyStore.setCertificateEntry("newca-certificate", newCaCertificate);
            keyStore.setCertificateEntry("newintermediate-certificate", newIntCertificate);

            // Create trust managers used to validate server
            TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmFactory.init(keyStore);
            TrustManager[] trustManagers = tmFactory.getTrustManagers();

            // Return the SSL context provider
            return new NhcSSLContextProvider2(trustManagers);

        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException | IOException e) {
            logger.warn("Niko Home Control: error with SSL context creation", e.getMessage());
            throw new CertificateException("SSL context creation exception", e);
        }
    }

    /**
     * Start a secure MQTT connection and subscribe to all topics. This is the general connection, not touch profile
     * specific.
     *
     * @param subscriber  MqttMessageSubscriber that will handle received messages
     * @param cocoAddress IP Address of the Niko Connected Controller
     * @param port        Port for MQTT communication with the Niko Connected Controller
     * @throws MqttException
     */
    void startConnection(MqttMessageSubscriber subscriber, String cocoAddress, int port) throws MqttException {
        this.cocoAddress = cocoAddress;
        this.port = port;
        MqttBrokerConnection connection = createMqttConnection(subscriber, null, null);
        try {
            if (connection.start().get()) {
                mqttConnection = connection;
                subscribedFuture = connection.subscribe("#", subscriber);
            } else {
                throw new MqttException(0);
            }
        } catch (InterruptedException e) {
            stopConnection();
        } catch (ExecutionException e) {
            stopConnection();
            throw new MqttException(0);
        }
    }

    /**
     * Start a secure MQTT connection and subscribe to all topics. This is the touch profile specific connection.
     * Note that {@link startConnection} must be called before this method. This method does not have cocoAddress and
     * port as parameters. The class fields will already have been set by {@link startConnection}.
     *
     * @param subscriber MqttMessageSubscriber that will handle received messages
     * @param username   MQTT username that identifies the specific touch profile. It should be the uuid retrieved from
     *                       the profile list in the general communication that matches the touch profile name.
     * @param password   Password for the touch profile
     * @throws MqttException
     */
    void startProfileConnection(MqttMessageSubscriber subscriber, String username, String password)
            throws MqttException {
        MqttBrokerConnection connection = createMqttConnection(subscriber, username, password);
        try {
            if (connection.start().get()) {
                mqttProfileConnection = connection;
                profileSubscribedFuture = connection.subscribe("#", subscriber);
            } else {
                throw new MqttException(0);
            }
        } catch (InterruptedException e) {
            stopProfileConnection();
        } catch (ExecutionException e) {
            stopProfileConnection();
            throw new MqttException(0);
        }
    }

    private MqttBrokerConnection createMqttConnection(MqttMessageSubscriber subscriber, @Nullable String username,
            @Nullable String password) throws MqttException {
        MqttBrokerConnection connection = null;
        connection = new MqttBrokerConnection(cocoAddress, port, true, null);
        connection.setSSLContextProvider(sslContextProvider);
        connection.setCredentials(username, password);
        return connection;
    }

    /**
     * Stop the general MQTT connection.
     */
    void stopConnection() {
        if (mqttConnection != null) {
            mqttConnection.stop();
            mqttConnection = null;
        }
    }

    /**
     * Stop the profile specific MQTT connection.
     */
    void stopProfileConnection() {
        if (mqttProfileConnection != null) {
            mqttProfileConnection.stop();
            mqttProfileConnection = null;
        }
    }

    /**
     * @return true if connection established and subscribed to all topics
     */
    private boolean isConnected() {
        MqttBrokerConnection connection = mqttConnection;
        if (connection == null) {
            return false;
        } else {
            try {
                if ((subscribedFuture != null) && subscribedFuture.get()) {
                    for (int i = 1; (connection.connectionState() == MqttConnectionState.CONNECTING) && (i <= 5); i++) {
                        Thread.sleep(500);
                    }
                    return connection.connectionState() == MqttConnectionState.CONNECTED;
                }
            } catch (InterruptedException | ExecutionException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * @return true if touch profile specific connection is established and subscribed to all topics
     */
    private boolean isProfileConnected() {
        MqttBrokerConnection connection = mqttProfileConnection;
        if (connection == null) {
            return false;
        } else {
            try {
                if ((profileSubscribedFuture != null) && profileSubscribedFuture.get()) {
                    for (int i = 1; (connection.connectionState() == MqttConnectionState.CONNECTING) && (i <= 5); i++) {
                        Thread.sleep(500);
                    }
                    return connection.connectionState() == MqttConnectionState.CONNECTED;
                }
            } catch (InterruptedException | ExecutionException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Publish a message on the general connection.
     *
     * @param topic
     * @param payload
     * @throws MqttException
     */
    void connectionPublish(String topic, String payload) throws MqttException {
        MqttBrokerConnection connection = mqttConnection;
        if (connection == null) {
            logger.debug("Niko Home Control: cannot publish, no connection");
            throw new MqttException(0);
        }

        if (isConnected()) {
            publish(connection, topic, payload);
        } else {
            logger.debug("Niko Home Control: cannot publish, not subscribed to connection messages");
        }
    }

    /**
     * Publish a message on the touch profile specific connection.
     *
     * @param topic
     * @param payload
     * @throws MqttException
     */
    void profileConnectionPublish(String topic, String payload) throws MqttException {
        MqttBrokerConnection connection = mqttProfileConnection;
        if (connection == null) {
            logger.debug("Niko Home Control: cannot publish, no profile connection");
            throw new MqttException(0);
        }

        if (isProfileConnected()) {
            publish(connection, topic, payload);
        } else {
            logger.debug("Niko Home Control: cannot publish, not subscribed to profile connection messages");
        }
    }

    private void publish(MqttBrokerConnection connection, String topic, String payload) {
        logger.debug("Niko Home Control: publish {}, {}", topic, payload);
        connection.publish(topic, payload.getBytes());
    }

    @Override
    public void onSuccess(String topic) {
        logger.debug("Niko Home Control: publish succeeded {}", topic);
    }

    @Override
    public void onFailure(String topic, Throwable error) {
        logger.debug("Niko Home Control: publish failed {}, {}", topic, error);
    }
}
