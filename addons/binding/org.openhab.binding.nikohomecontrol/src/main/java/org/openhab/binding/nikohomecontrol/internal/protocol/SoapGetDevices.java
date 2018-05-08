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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.ActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The {@link SoapGetDevices} class defines fields and methods for the GetDevices SOAP request to the Niko Home Control
 * Cloud.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class SoapGetDevices extends SoapClient {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    String CONTRACT_NS = "http://schemas.datacontract.org/2004/07/Fifthplay.API.Mobile.Contracts.v8.Data.Gateway";
    String SERVICE_NS = "api.fifthplay.com/mobile/v8/IDeviceService";

    String SOAP_ACTION = "api.fifthplay.com/mobile/v8/IDeviceService/IDeviceService/GetDevices";

    String ENDPOINT_URL = "https://api.fifthplay.com/mobile/v8/DeviceService.svc";

    String USER_TOKEN = "UsernameToken-4B366D46B65218EE3015036248634901";

    Map<String, NhcLocation> locations = new HashMap<>();

    /**
     * Method to compose GetDevices SOAP request and call the webservice.
     *
     * @param username
     * @param password
     * @param httpClient
     * @param timeout
     * @param deviceType Niko Home Control device types (only 'action' used by binding)
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
    public Document soapRequest(String username, String password, HttpClient httpClient, int timeout, String deviceType,
            boolean withParameters, boolean withTraits) throws SOAPException, IOException, InterruptedException,
            TimeoutException, ExecutionException, SAXException {
        SOAPMessage soapMessage = createSoapRequest(username, password, SOAP_ACTION, USER_TOKEN);

        SOAPEnvelope envelope = soapMessage.getSOAPPart().getEnvelope();
        envelope.addNamespaceDeclaration("contract", CONTRACT_NS);
        envelope.addNamespaceDeclaration("service", SERVICE_NS);

        SOAPBody soapBody = envelope.getBody();
        SOAPElement request = soapBody.addChildElement("GetDevices", "service").addChildElement("request", "service");
        request.addChildElement("DeviceType", "contract").addTextNode(deviceType);
        request.addChildElement("WithParameters", "contract").addTextNode(String.valueOf(withParameters));
        request.addChildElement("WithTraits", "contract").addTextNode(String.valueOf(withTraits));

        soapMessage.saveChanges();

        return callSoapWebService(ENDPOINT_URL, soapMessage, httpClient, timeout);
    }

    /**
     * Method to get a List of {@link NhcIIAction}. This method will call {@link soapRequest} and parse the resulting
     * XML.
     * <p>
     * A List of {@link NhcLocation} will be parsed from the XML at the same time. The {@Link getLocations} method will
     * provide this List.
     *
     * @param username
     * @param password
     * @param httpClient
     * @param timeout
     * @return
     */
    public List<NhcIIAction> getDevices(String username, String password, HttpClient httpClient, int timeout) {
        Document response = null;
        List<NhcIIAction> actionList = new ArrayList<>();

        try {
            response = soapRequest(username, password, httpClient, timeout, "action", true, false);
            NodeList list = response.getDocumentElement().getElementsByTagName("b:DeviceDataContract");

            if (list == null) {
                return actionList;
            }

            for (int i = 0; i < list.getLength(); i++) {
                NodeList subList = list.item(i).getChildNodes();
                String id = getString("b:Uuid", (Element) subList);
                String name = getString("b:Name", (Element) subList);
                String type = getString("b:HWModel", (Element) subList);

                if ((id == null) || (name == null) || (type == null)) {
                    logger.debug("Niko Home Control: data error getting devices from: {}.", subList);
                    continue;
                }

                NodeList parameterList = ((Element) subList).getElementsByTagName("b:ParameterDataContract");
                String locationId = null;
                String locationName = null;
                for (int j = 0; j < parameterList.getLength(); j++) {
                    NodeList locationList = parameterList.item(j).getChildNodes();
                    String paramName = getString("b:Name", (Element) locationList);
                    String paramValue = getString("b:Value", (Element) locationList);
                    if ("LocationId".equals(paramName)) {
                        locationId = paramValue;
                    } else if ("LocationName".equals(paramValue)) {
                        locationName = paramValue;
                    }
                }

                if ((locationId != null) && (locationName != null) && !locationName.isEmpty()) {
                    locations.put(locationId, new NhcLocation(locationName));
                }

                actionList = addAction(actionList, subList, id, name, type, locationName);
            }
        } catch (SOAPException | IOException | InterruptedException | TimeoutException | ExecutionException
                | SAXException e) {
            logger.debug("Niko Home Control: error with soap request, cannot get devices.");
        }

        return actionList;
    }

    /**
     * Method to update the list of {@link NhcIIAction} with their current state. This method will call
     * {@link soapRequest} and parse the resulting XML.
     *
     * @param username
     * @param password
     * @param httpClient
     * @param timeout
     * @return
     */
    public List<NhcIIAction> updateDevices(String username, String password, HttpClient httpClient, int timeout) {
        Document response;
        List<NhcIIAction> actionList = new ArrayList<>();

        try {
            response = soapRequest(username, password, httpClient, timeout, "action", false, false);
            NodeList list = response.getDocumentElement().getElementsByTagName("b:DeviceDataContract");
            if (list == null) {
                return actionList;
            }

            for (int i = 0; i < list.getLength(); i++) {
                NodeList subList = list.item(i).getChildNodes();
                String id = getString("b:Uuid", (Element) subList);
                String name = getString("b:Name", (Element) subList);
                String type = getString("b:HWModel", (Element) subList);

                if ((id == null) || (name == null) || (type == null)) {
                    logger.debug("Niko Home Control: data error getting devices from: {}.", subList);
                    continue;
                }

                actionList = addAction(actionList, subList, id, name, type, null);
            }
        } catch (SOAPException | IOException | InterruptedException | TimeoutException | ExecutionException
                | SAXException e) {
            logger.debug("Niko Home Control: error with soap request, cannot get devices.");
        }

        return actionList;
    }

    private List<NhcIIAction> addAction(List<NhcIIAction> actionList, NodeList list, String id, String name,
            String type, @Nullable String location) {
        ActionType actionType;
        switch (type) {
            case "light":
            case "socket":
                actionType = ActionType.RELAY;
                break;
            case "dimmer":
                actionType = ActionType.DIMMER;
                break;
            case "rollershutter":
                actionType = ActionType.ROLLERSHUTTER;
                break;
            case "generic":
                actionType = ActionType.GENERIC;
            default:
                logger.debug("Niko Home Control: unknown action type {} for action {}", type, id);
                return actionList;
        }
        NhcIIAction action = new NhcIIAction(id, name, actionType, location);

        NodeList subList = ((Element) list).getElementsByTagName("b:Property");
        LocalDateTime lastUpdateTime = null;
        NodeList lastUpdatePropertyList = null;
        for (int i = 0; i < subList.getLength(); i++) {
            NodeList propertyList = subList.item(i).getChildNodes();
            if ("true".equals(getString("b:CanControl", (Element) propertyList))) {
                LocalDateTime currentTime = LocalDateTime
                        .parse(getString("b:StatusLastUpdate", (Element) propertyList));
                if ((lastUpdateTime == null) || currentTime.isAfter(lastUpdateTime)) {
                    lastUpdateTime = currentTime;
                    lastUpdatePropertyList = propertyList;
                }
            }

            if (lastUpdatePropertyList != null) {
                action.setState(getState(lastUpdatePropertyList));
            } else {
                logger.debug("Niko Home Control: could not get state for action {}", id);
            }
            actionList.add(action);
        }

        return actionList;
    }

    private String getState(NodeList propertyList) {
        String propertyStatus = getString("b:Status", (Element) propertyList);
        return (propertyStatus == null ? "" : propertyStatus);
    }

    /**
     * @return Map of locations retrieved from Niko Home Control.
     */
    public Map<String, NhcLocation> getLocations() {
        return locations;
    }
}
