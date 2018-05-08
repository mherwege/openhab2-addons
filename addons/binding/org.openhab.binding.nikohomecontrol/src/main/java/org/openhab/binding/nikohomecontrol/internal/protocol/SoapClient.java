/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.Name;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPEnvelope;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * The {@link SoapClient} class defines the common set of fields and methods used in all SOAP requests to the Niko Home
 * Control cloud.
 *
 * @author Mark Herwege - Initial Contribution
 */
@NonNullByDefault
public class SoapClient {

    String APPLICATION_TOKEN = "616C13BE-14BE-4BCA-ADC8-0E3E4490779A";
    String APTO_NS = "http://schemas.datacontract.org/2004/07/Fifthplay.Shared.API.ApplicationToken";

    String WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd";
    String WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd";
    String ADDR_NS = "http://www.w3.org/2005/08/addressing";
    String SOAP_NS = "http://www.w3.org/2003/05/soap-envelope";

    String PW_TYPE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText";
    String NONCE_TYPE = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary";

    @Nullable
    private MessageFactory messageFactory = null;
    @Nullable
    private DocumentBuilder parser = null;
    @Nullable
    private Encoder encoder = null;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    SoapClient() {
        try {
            messageFactory = MessageFactory.newInstance();
            parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            encoder = Base64.getUrlEncoder();
        } catch (SOAPException | ParserConfigurationException e) {
            logger.error("Niko Home Control: fatal error creating SOAP Client");
        }
    }

    /**
     * Method to call the Niko Home Control cloud SOAP webservice.
     *
     * @param soapEndpointUrl Niko Home Control cloud SOAP endpoint URL
     * @param message         SOAP message
     * @param httpClient      {@link HttpClient} instance to use for the call
     * @param timeout         timeout
     * @return {@link Document} with XML response
     * @throws SOAPException
     * @throws IOException
     * @throws InterruptedException
     * @throws TimeoutException
     * @throws ExecutionException
     * @throws SAXException
     */
    protected Document callSoapWebService(String soapEndpointUrl, SOAPMessage message, HttpClient httpClient,
            int timeout) throws SOAPException, IOException, InterruptedException, TimeoutException, ExecutionException,
            SAXException {

        Document result;

        final Request request = httpClient.POST(soapEndpointUrl);
        request.timeout(timeout, TimeUnit.MILLISECONDS);
        request.header(HttpHeader.CONTENT_TYPE, "application/soap+xml");

        try (final ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            message.writeTo(os);
            request.content(new BytesContentProvider(os.toByteArray()));

            logger.debug("SOAP request: {}", request);

            final ContentResponse response = request.send();
            try (final ByteArrayInputStream is = new ByteArrayInputStream(response.getContent())) {
                result = parser.parse(is);
            }
        }

        logger.debug("SOAP response: {}", result);

        return result;
    };

    /**
     * Create SOAP request with all elements common to all SOAP requests to the Niko Home Control Cloud.
     *
     * @param username
     * @param password
     * @param soapAction
     * @param userToken
     * @return
     * @throws SOAPException
     */
    protected SOAPMessage createSoapRequest(String username, String password, String soapAction, String userToken)
            throws SOAPException {
        SOAPMessage soapMessage = messageFactory.createMessage();

        SOAPPart soapPart = soapMessage.getSOAPPart();

        SOAPEnvelope envelope = soapPart.getEnvelope();
        envelope.addNamespaceDeclaration("apto", APTO_NS);
        envelope.addNamespaceDeclaration("soap", SOAP_NS);

        SOAPHeader header = envelope.getHeader();
        header.addNamespaceDeclaration("addr", ADDR_NS);

        SOAPElement security = header.addChildElement("Security", "wsse").addNamespaceDeclaration("wsse", WSSE_NS)
                .addNamespaceDeclaration("wsu", WSU_NS);
        QName mustUnderstand = security.createQName("mustUnderstand", "soap");
        security.addAttribute(mustUnderstand, "true");

        SOAPElement userNameToken = security.addChildElement("UserNameToken", "wsse");
        QName id = userNameToken.createQName("Id", "wsu");
        userNameToken.addAttribute(id, userToken);
        userNameToken.addChildElement("Username", "wsse").addTextNode(username);
        SOAPElement pw = userNameToken.addChildElement("Password", "wsse");
        Name pwType = envelope.createName("Type");
        pw.addAttribute(pwType, PW_TYPE);
        pw.addTextNode(password);
        SOAPElement nonce = userNameToken.addChildElement("Nonce", "wsse");
        Name nonceType = envelope.createName("EncodingType");
        nonce.addAttribute(nonceType, NONCE_TYPE);
        nonce.addTextNode(randomId());
        userNameToken.addChildElement("Created", "wsu")
                .addTextNode(ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT));

        header.addChildElement("Application").addChildElement("Token", "apto").addTextNode(APPLICATION_TOKEN);
        header.addChildElement("Action", "addr").addTextNode(soapAction);

        return soapMessage;
    };

    /**
     * Create random Id for security Nonce in SOAP request.
     *
     * @return random Id
     */
    protected String randomId() {

        UUID uuid = UUID.randomUUID();

        byte[] src = ByteBuffer.wrap(new byte[16]).putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits()).array();

        // Encode to Base64 and remove trailing ==
        return encoder.encodeToString(src).substring(0, 22);
    }

    /**
     * Helper method to query an XML Element for a tag and return the first node value for that tag.
     *
     * @param tagName
     * @param element
     * @return
     */
    protected @Nullable String getString(String tagName, Element element) {
        NodeList list = element.getElementsByTagName(tagName);
        if (list != null && list.getLength() > 0) {
            NodeList subList = list.item(0).getChildNodes();

            if (subList != null && subList.getLength() > 0) {
                return subList.item(0).getNodeValue();
            }
        }

        return null;
    }
}
