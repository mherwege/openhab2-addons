/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * The {@link SoapControlDevice} class defines fields and methods for the SoapControlDevice SOAP request to the Niko
 * Home Control
 * Cloud. This SOAP request updates the state of a device in Niko Home Control.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class SoapControlDevice extends SoapClient {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    String CONTRACT_NS = "http://schemas.datacontract.org/2004/07/Fifthplay.API.Mobile.Contracts.v7.Data.Gateway";
    String SERVICE_NS = "api.fifthplay.com/mobile/v7/IDeviceService";

    String SOAP_ACTION = "api.fifthplay.com/mobile/v7/IDeviceService/IDeviceService/ControlDevice";

    String ENDPOINT_URL = "https://api.fifthplay.com/mobile/v7/ControlDevice.svc";

    String USER_TOKEN = "UsernameToken-E138269A97ED45AC9C149317358206810";

    /**
     * Method to compose ControlDevice SOAP request and call the webservice.
     *
     * @param username
     * @param password
     * @param httpClient
     * @param timeout
     * @param deviceUuid Niko Home Control deviceUuid
     * @param key Property key containing the status to change
     * @param value new value of the status
     * @return XML response with devices
     * @throws SOAPException
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     * @throws ExecutionException
     * @throws SAXException
     */
    public Document soapRequest(String username, String password, HttpClient httpClient, int timeout, String deviceUuid,
            String key, String value) throws SOAPException, IOException, InterruptedException, TimeoutException,
            ExecutionException, SAXException {
        SOAPMessage soapMessage = createSoapRequest(username, password, SOAP_ACTION, USER_TOKEN);

        SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
        envelope.addNamespaceDeclaration("contract", CONTRACT_NS);
        envelope.addNamespaceDeclaration("service", SERVICE_NS);

        SOAPBody soapBody = envelope.getBody();
        SOAPElement controlDevice = soapBody.addChildElement("ControlDevice", "service");
        controlDevice.addChildElement("deviceId", "service").addTextNode(deviceUuid);
        SOAPElement request = controlDevice.addChildElement("request", "service");
        SOAPElement controlProperty = request.addChildElement("ControlProperty", "contract");
        controlProperty.addChildElement("Key", "contract").addTextNode(key);
        controlProperty.addChildElement("Value", "contract").addTextNode(value);

        soapMessage.saveChanges();

        return callSoapWebService(ENDPOINT_URL, soapMessage, httpClient, timeout);
    }

    /**
     * Method to sand a status value. This method will call {@link soapRequest}.
     * 
     * @param username
     * @param password
     * @param httpClient
     * @param timeout
     * @param deviceUuid Niko Home Control deviceUuid
     * @param deviceType {@link ActionType} for the device.
     * @param value New status value.
     */
    public void controlDevice(String username, String password, HttpClient httpClient, int timeout, String deviceUuid,
            ActionType deviceType, String value) {
        String key = "";
        switch (deviceType) {
            case RELAY:
                key = "Status";
                break;
            case DIMMER:
                if ("On".equals(value) || "Off".equals(value)) {
                    key = "Status";
                } else {
                    key = "Brightness";
                }
                break;
            case ROLLERSHUTTER:
                if ("Up".equals(value) || "Down".equals(value) || "Stop".equals(value)) {
                    key = "Command"; // to validate
                } else {
                    key = "Position"; // to validate
                }
                break;
            case GENERIC:
                key = "BasicState";
                break;
        }

        if (key != "") {
            try {
                soapRequest(username, password, httpClient, timeout, deviceUuid, key, value);
            } catch (SOAPException | IOException | InterruptedException | TimeoutException | ExecutionException
                    | SAXException e) {
                logger.debug("Niko Home Control: error with soap request, cannot control device {}.", deviceUuid);
            }
        }
    }
}
