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
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * The {@link SoapGetDevice} class defines fields and methods for the GetDevice SOAP request to the Niko Home Control
 * Cloud.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class SoapGetDevice extends SoapClient {

    String CONTRACT_NS = "http://schemas.datacontract.org/2004/07/Fifthplay.API.Mobile.Contracts.v8.Data.Gateway";
    String SERVICE_NS = "api.fifthplay.com/mobile/v8/IDeviceService";

    String SOAP_ACTION = "api.fifthplay.com/mobile/v8/IDeviceService/IDeviceService/GetDevice";

    String ENDPOINT_URL = "https://api.fifthplay.com/mobile/v8/DeviceService.svc";

    String USER_TOKEN = "UsernameToken-4B366D46B65218EE3015036307736093";

    /**
     * Method to compose GetDevice SOAP request and call the webservice.
     *
     * @param username
     * @param password
     * @param httpClient
     * @param timeout
     * @param deviceUuid Niko Home Control deviceUuid
     * @param withParameters Flag to indicate if parameters should be returned (contains location data)
     * @param withTraits Flag to indicate if traits should be returned (no apparent use for binding)
     * @return XML response with devices
     * @throws SOAPException
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     * @throws ExecutionException
     * @throws SAXException
     */
    public Document soapRequest(String username, String password, HttpClient httpClient, int timeout, String deviceUuid,
            boolean withParameters, boolean withTraits) throws SOAPException, IOException, InterruptedException,
            TimeoutException, ExecutionException, SAXException {
        SOAPMessage soapMessage = createSoapRequest(username, password, SOAP_ACTION, USER_TOKEN);

        SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
        envelope.addNamespaceDeclaration("contract", CONTRACT_NS);
        envelope.addNamespaceDeclaration("service", SERVICE_NS);

        SOAPBody soapBody = envelope.getBody();
        SOAPElement request = soapBody.addChildElement("GetDevice", "service").addChildElement("request", "service");
        request.addChildElement("DeviceUuid", "contract").addTextNode(deviceUuid);
        request.addChildElement("WithParameters", "contract").addTextNode(String.valueOf(withParameters));
        request.addChildElement("WithTraits", "contract").addTextNode(String.valueOf(withTraits));

        soapMessage.saveChanges();

        return callSoapWebService(ENDPOINT_URL, soapMessage, httpClient, timeout);
    }
}
