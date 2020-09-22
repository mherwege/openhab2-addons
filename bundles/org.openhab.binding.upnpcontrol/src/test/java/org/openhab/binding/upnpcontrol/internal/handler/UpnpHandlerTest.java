/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
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
package org.openhab.binding.upnpcontrol.internal.handler;

import static org.eclipse.jdt.annotation.Checks.requireNonNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerCallback;
import org.eclipse.smarthome.io.transport.upnp.UpnpIOService;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicCommandDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicStateDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlBindingConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for {@link UpnpServerHandlerTest} and {@link UpnpRendererHandlerTest}.
 *
 * @author Mark Herwege - Initial contribution
 */
@SuppressWarnings({ "null", "unchecked" })
@NonNullByDefault
public class UpnpHandlerTest {

    private final Logger logger = LoggerFactory.getLogger(UpnpHandlerTest.class);

    protected @Nullable UpnpHandler handler;

    @Mock
    protected @Nullable Thing thing;

    @Mock
    protected @Nullable UpnpIOService upnpIOService;

    @Mock
    protected @Nullable UpnpDynamicStateDescriptionProvider upnpStateDescriptionProvider;

    @Mock
    protected @Nullable UpnpDynamicCommandDescriptionProvider upnpCommandDescriptionProvider;

    protected UpnpControlBindingConfiguration configuration = new UpnpControlBindingConfiguration();

    @Mock
    protected @Nullable Configuration config;

    // Use temporary folder for favorites and playlists testing
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Mock
    @Nullable
    protected ScheduledExecutorService scheduler;

    @Mock
    protected @Nullable ThingHandlerCallback callback;

    public void setUp() {
        // don't test for multi-threading, so avoid using extra threads
        implementAsDirectExecutor(requireNonNull(scheduler));

        try {
            String path = tempFolder.newFolder().getPath();
            if (!(path.endsWith(File.separator) || path.endsWith("/"))) {
                path = path + File.separator;
            }
            configuration.path = path;
        } catch (IOException e) {
            // This will fail tests with playlists and favorites
            configuration.path = "";
        }

        // stub thing methods
        when(thing.getConfiguration()).thenReturn(requireNonNull(config));
        when(thing.getStatus()).thenReturn(ThingStatus.OFFLINE);

        // stub upnpIOService methods for initialize
        when(upnpIOService.isRegistered(any())).thenReturn(true);

        // stub config for initialize
        when(config.as(UpnpControlConfiguration.class)).thenReturn(new UpnpControlConfiguration());
    }

    protected void initHandler(UpnpHandler handler) {
        handler.setCallback(callback);
        handler.upnpScheduler = requireNonNull(scheduler);

        doReturn("12345").when(handler).getUDN();
    }

    /**
     * Mock the {@link ScheduledExecutorService}, so all testing is done in the current thread. We do not test
     * request/response with a real media server, so do not need the executor to avoid long running processes.
     *
     * @param executor
     */
    private void implementAsDirectExecutor(ScheduledExecutorService executor) {
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(executor).submit(any(Runnable.class));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArguments()[0]).run();
            return null;
        }).when(executor).scheduleWithFixedDelay(any(Runnable.class), eq(0L), anyLong(), any(TimeUnit.class));
    }

    public void tearDown() {
        logger.info("-----------------------------------------------------------------------------------");
    }
}
