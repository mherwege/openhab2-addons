/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
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
package org.openhab.io.hueemulation.internal.rest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.common.registry.DefaultAbstractManagedProvider;
import org.eclipse.smarthome.core.storage.StorageService;
import org.openhab.io.hueemulation.internal.ConfigStore;
import org.openhab.io.hueemulation.internal.NetworkUtils;
import org.openhab.io.hueemulation.internal.dto.HueUserAuth;
import org.openhab.io.hueemulation.internal.dto.HueUserAuthWithSecrets;
import org.openhab.io.hueemulation.internal.dto.changerequest.HueCreateUser;
import org.openhab.io.hueemulation.internal.dto.response.HueResponse;
import org.openhab.io.hueemulation.internal.dto.response.HueSuccessResponseCreateUser;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Manages users of this emulated HUE bridge. Stores users in the frameworks storage backend.
 * <p>
 * This is an OSGi component. Usage:
 *
 * <pre>
 * &#64;Reference
 * UserManagement userManagment;
 * </pre>
 *
 * @author David Graeff - Initial contribution
 */
@Component(immediate = false, service = { UserManagement.class }, property = "com.eclipsesource.jaxrs.publish=false")
@NonNullByDefault
@Path("")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class UserManagement extends DefaultAbstractManagedProvider<HueUserAuthWithSecrets, String> {
    private final Logger logger = LoggerFactory.getLogger(UserManagement.class);

    @Reference
    protected @NonNullByDefault({}) ConfigStore cs;
    private boolean storageSet = false;

    @Override
    @Reference(policy = ReferencePolicy.DYNAMIC)
    public void setStorageService(@Nullable StorageService storageService) {
        super.setStorageService(storageService);
        storageSet = true;
        if (cs == null) {
            return;
        }
        for (HueUserAuthWithSecrets userAuth : getAll()) {
            cs.ds.config.whitelist.put(userAuth.getUID(), userAuth);
        }
    }

    @Activate
    public void activate() {
        if (storageSet) {
            for (HueUserAuthWithSecrets userAuth : getAll()) {
                cs.ds.config.whitelist.put(userAuth.getUID(), userAuth);
            }
        }
    }

    @Override
    protected void unsetStorageService(@Nullable StorageService storageService) {
        super.unsetStorageService(storageService);
        storageSet = false;
    }

    /**
     * Checks if the username exists in the whitelist
     */
    @SuppressWarnings("null")
    public boolean authorizeUser(String userName) {
        HueUserAuth userAuth = cs.ds.config.whitelist.get(userName);

        if (cs.ds.config.linkbutton && cs.ds.config.createNewUserOnEveryEndpoint) {
            addUser(userName, userName, "On-the-go-user");
            userAuth = cs.ds.config.whitelist.get(userName);
        }

        if (userAuth != null) {
            userAuth.lastUseDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            update((HueUserAuthWithSecrets) userAuth);
        }

        return userAuth != null;
    }

    /**
     * Adds a user to the whitelist and persist the user file
     *
     * @param apiKey The hue "username" which is actually an API key
     * @param clientKey The UDP/DTLS client key
     * @param The user visible name
     */
    private void addUser(String apiKey, String clientKey, String label) {
        if (cs.ds.config.whitelist.containsKey(apiKey)) {
            return;
        }
        logger.debug("APIKey {} added", apiKey);
        String l[] = label.split("#");
        HueUserAuthWithSecrets hueUserAuth = new HueUserAuthWithSecrets(l[0], l.length == 2 ? l[1] : "openhab", apiKey,
                clientKey);
        cs.ds.config.whitelist.put(apiKey, hueUserAuth);
        add(hueUserAuth);
    }

    @SuppressWarnings("null")
    private synchronized void removeUser(String apiKey) {
        HueUserAuth userAuth = cs.ds.config.whitelist.remove(apiKey);
        if (userAuth != null) {
            logger.debug("APIKey {} removed", apiKey);
        }
        remove(apiKey);
    }

    @Override
    protected String getStorageName() {
        return "hueEmulationUsers";
    }

    @Override
    protected String keyToString(String key) {
        return key;
    }

    @GET
    public Response illegalGetUserAccessApi(@Context UriInfo uri) {
        return NetworkUtils.singleError(cs.gson, uri, HueResponse.METHOD_NOT_ALLOWED, "Not Authorized");
    }

    @POST
    @ApiOperation(value = "Create an API Key")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "API Key created"),
            @ApiResponse(code = 403, message = "Link button not pressed") })
    public Response createNewUser(@Context UriInfo uri, String body) {
        if (!cs.ds.config.linkbutton) {
            return NetworkUtils.singleError(cs.gson, uri, HueResponse.LINK_BUTTON_NOT_PRESSED,
                    "link button not pressed");
        }

        final HueCreateUser userRequest;
        userRequest = cs.gson.fromJson(body, HueCreateUser.class);
        if (userRequest.devicetype.isEmpty()) {
            return NetworkUtils.singleError(cs.gson, uri, HueResponse.INVALID_JSON,
                    "Invalid request: No devicetype set");
        }

        String apiKey = UUID.randomUUID().toString();
        String clientKey = UUID.randomUUID().toString();
        addUser(apiKey, clientKey, userRequest.devicetype);
        HueSuccessResponseCreateUser h = new HueSuccessResponseCreateUser(apiKey, clientKey);
        String result = cs.gson.toJson(Collections.singleton(new HueResponse(h)), new TypeToken<List<?>>() {
        }.getType());

        return Response.ok(result).build();
    }

    @GET
    @Path("{username}/config/whitelist/{userid}")
    @ApiOperation(value = "Return a user")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK") })
    public Response getUserApi(@Context UriInfo uri,
            @PathParam("username") @ApiParam(value = "username") String username,
            @PathParam("userid") @ApiParam(value = "User ID") String userid) {
        if (!authorizeUser(username)) {
            return NetworkUtils.singleError(cs.gson, uri, HueResponse.UNAUTHORIZED, "Not Authorized");
        }
        return Response.ok(cs.gson.toJson(cs.ds.config.whitelist.get(userid))).build();
    }

    @GET
    @Path("{username}/config/whitelist")
    @ApiOperation(value = "Return all users")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK") })
    public Response getAllUsersApi(@Context UriInfo uri,
            @PathParam("username") @ApiParam(value = "username") String username) {
        if (!authorizeUser(username)) {
            return NetworkUtils.singleError(cs.gson, uri, HueResponse.UNAUTHORIZED, "Not Authorized");
        }
        return Response.ok(cs.gson.toJson(cs.ds.config.whitelist)).build();
    }

    @DELETE
    @Path("{username}/config/whitelist/{id}")
    @ApiOperation(value = "Deletes a user")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "The user got removed"),
            @ApiResponse(code = 403, message = "Access denied") })
    public Response removeUserApi(@Context UriInfo uri,
            @PathParam("username") @ApiParam(value = "username") String username,
            @PathParam("id") @ApiParam(value = "User to remove") String id) {
        if (!authorizeUser(username)) {
            return NetworkUtils.singleError(cs.gson, uri, HueResponse.UNAUTHORIZED, "Not Authorized");
        }
        if (!username.equals(id)) {
            return NetworkUtils.singleError(cs.gson, uri, HueResponse.UNAUTHORIZED,
                    "You can only remove yourself not someone else!");
        }

        removeUser(username);

        return NetworkUtils.singleSuccess(cs.gson, "/config/whitelist/" + username + " deleted.");
    }
}
