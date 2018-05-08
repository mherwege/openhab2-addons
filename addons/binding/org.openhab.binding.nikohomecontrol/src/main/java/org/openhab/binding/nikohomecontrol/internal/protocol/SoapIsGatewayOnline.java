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
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * The {@link SoapIsGatewayOnline} class defines fields and methods for the SoapIsGatewayOnline SOAP request to the Niko
 * Home Control
 * Cloud.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class SoapIsGatewayOnline extends SoapClient {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    String CONTRACT_NS = "http://schemas.datacontract.org/2004/07/Fifthplay.API.Mobile.Contracts.v8.Data.Gateway";
    String SERVICE_NS = "api.fifthplay.com/mobile/v8/IGatewayService";

    String SOAP_ACTION = "api.fifthplay.com/mobile/v8/IGatewayService/IGatewayService/IsGatewayOnline";

    String ENDPOINT_URL = "https://api.fifthplay.com/mobile/v8/GatewayService.svc";

    String USER_TOKEN = "UsernameToken-8A35F22AD7D52ADB711487208964763108";

    /**
     * Method to compose GetDevice SOAP request and call the webservice.
     *
     * @param username
     * @param password
     * @param httpClient
     * @param timeout
     * @param gatewayId gatewayId of the Niko Home Control II Connected Controller
     * @return XML response with devices
     * @throws SOAPException
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     * @throws ExecutionException
     * @throws SAXException
     */
    protected Document soapRequest(String username, String password, HttpClient httpClient, int timeout,
            String gatewayId) throws SOAPException, IOException, InterruptedException, TimeoutException,
            ExecutionException, SAXException {
        SOAPMessage soapMessage = createSoapRequest(username, password, SOAP_ACTION, USER_TOKEN);

        SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
        envelope.addNamespaceDeclaration("contract", CONTRACT_NS);
        envelope.addNamespaceDeclaration("service", SERVICE_NS);

        SOAPBody soapBody = envelope.getBody();
        soapBody.addChildElement("IsGatewayOnline", "service").addChildElement("isGatewayOnlineRequest", "service")
                .addChildElement("GatewayId", "contract").addTextNode(gatewayId);

        soapMessage.saveChanges();

        return callSoapWebService(ENDPOINT_URL, soapMessage, httpClient, timeout);
    }

    /**
     * Method to check if the Connected Controller is online. it therefore send a SOAP request to the Niko Home Control
     * cloud webservice and parses the status from the XML response.
     *
     * @param username
     * @param password
     * @param httpClient
     * @param timeout
     * @param gatewayId gatewayId of the Niko Home Control II Connected Controller
     * @return
     */
    public boolean isGatewayOnline(String username, String password, HttpClient httpClient, int timeout,
            String gatewayId) {
        boolean online = false;
        Document response;
        try {
            response = soapRequest(username, password, httpClient, timeout, gatewayId);
            online = Boolean.parseBoolean(getString("b:IsOnline", response.getDocumentElement()));
        } catch (SOAPException | IOException | InterruptedException | TimeoutException | ExecutionException
                | SAXException e) {
            logger.debug("Niko Home Control: error with soap request, cannot get gateway status.");
        }
        return online;
    }
}
