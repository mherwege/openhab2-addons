/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.nikohomecontrol.internal;

import static org.openhab.binding.nikohomecontrol.internal.NikoHomeControlBindingConstants.*;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.eclipse.smarthome.io.net.http.HttpClientFactory;
import org.openhab.binding.nikohomecontrol.internal.discovery.NikoHomeControlDiscoveryService;
import org.openhab.binding.nikohomecontrol.internal.handler.NhcIBridgeHandler;
import org.openhab.binding.nikohomecontrol.internal.handler.NhcIIBridgeHandler;
import org.openhab.binding.nikohomecontrol.internal.handler.NikoHomeControlActionHandler;
import org.openhab.binding.nikohomecontrol.internal.handler.NikoHomeControlBridgeHandler;
import org.openhab.binding.nikohomecontrol.internal.handler.NikoHomeControlThermostatHandler;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link NikoHomeControlHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Mark Herwege - Initial Contribution
 */

@Component(service = ThingHandlerFactory.class, configurationPid = "binding.nikohomecontrol")
@NonNullByDefault
public class NikoHomeControlHandlerFactory extends BaseThingHandlerFactory {

    private Map<ThingUID, ServiceRegistration<?>> discoveryServiceRegs = new HashMap<>();

    @NonNullByDefault({})
    private HttpClient httpClient;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID) || BRIDGE_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        if (BRIDGE_THING_TYPES_UIDS.contains(thing.getThingTypeUID())) {
            NikoHomeControlBridgeHandler handler;
            if (BRIDGEII_THING_TYPE.equals(thing.getThingTypeUID())) {
                handler = new NhcIIBridgeHandler((Bridge) thing, httpClient);
            } else {
                handler = new NhcIBridgeHandler((Bridge) thing);
            }
            registerNikoHomeControlDiscoveryService(handler);
            return handler;
        } else if (THING_TYPE_THERMOSTAT.equals(thing.getThingTypeUID())) {
            return new NikoHomeControlThermostatHandler(thing);
        } else if (ACTION_THING_TYPES_UIDS.contains(thing.getThingTypeUID())) {
            return new NikoHomeControlActionHandler(thing);
        }

        return null;
    }

    private synchronized void registerNikoHomeControlDiscoveryService(NikoHomeControlBridgeHandler bridgeHandler) {
        NikoHomeControlDiscoveryService nhcDiscoveryService = new NikoHomeControlDiscoveryService(bridgeHandler);
        this.discoveryServiceRegs.put(bridgeHandler.getThing().getUID(), bundleContext.registerService(
                DiscoveryService.class.getName(), nhcDiscoveryService, new Hashtable<String, Object>()));
        nhcDiscoveryService.activate();
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof NikoHomeControlBridgeHandler) {
            ServiceRegistration<?> serviceReg = this.discoveryServiceRegs.remove(thingHandler.getThing().getUID());
            if (serviceReg != null) {
                // remove discovery service, if bridge handler is removed
                NikoHomeControlDiscoveryService service = (NikoHomeControlDiscoveryService) bundleContext
                        .getService(serviceReg.getReference());
                serviceReg.unregister();
                if (service != null) {
                    service.deactivate();
                }
            }
        }
    }

    @Reference
    protected void setHttpClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClient = httpClientFactory.getCommonHttpClient();
    }

    protected void unsetHttpClientFactory(HttpClientFactory httpClientFactory) {
        this.httpClient = null;
    }
}
