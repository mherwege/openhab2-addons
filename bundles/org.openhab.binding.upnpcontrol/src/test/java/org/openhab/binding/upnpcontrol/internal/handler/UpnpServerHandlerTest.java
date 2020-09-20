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
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerCallback;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.eclipse.smarthome.core.types.CommandOption;
import org.eclipse.smarthome.io.transport.upnp.UpnpIOService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicCommandDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicStateDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlBindingConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlServerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link UpnpServerHandler}.
 *
 * @author Mark Herwege - Initial contribution
 */
@SuppressWarnings({ "null", "unchecked" })
@NonNullByDefault
public class UpnpServerHandlerTest {

    private final Logger logger = LoggerFactory.getLogger(UpnpServerHandlerTest.class);

    private static final String THING_TYPE_UID = "upnpcontrol:upnpserver";
    private static final String THING_UID = THING_TYPE_UID + ":mockserver";

    private static final String RESPONSE_HEADER = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" "
            + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" "
            + "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">";
    private static final String RESPONSE_FOOTER = "</DIDL-Lite>";

    private static final String BASE_CONTAINER = RESPONSE_HEADER
            + "<container id=\"C1\" searchable=\"0\" parentID=\"0\" restricted=\"1\" childCount=\"2\">"
            + "<dc:title>All Audio Items</dc:title><upnp:class>object.container</upnp:class>"
            + "<upnp:writeStatus>UNKNOWN</upnp:writeStatus></container>"
            + "<container id=\"C2\" searchable=\"0\" parentID=\"0\" restricted=\"1\" childCount=\"0\">"
            + "<dc:title>All Image Items</dc:title><upnp:class>object.container</upnp:class>"
            + "<upnp:writeStatus>UNKNOWN</upnp:writeStatus></container>" + RESPONSE_FOOTER;

    private static final String SINGLE_CONTAINER = RESPONSE_HEADER
            + "<container id=\"C11\" searchable=\"0\" parentID=\"C1\" restricted=\"1\" childCount=\"2\">"
            + "<dc:title>Morning Music</dc:title><upnp:class>object.container</upnp:class>"
            + "<upnp:writeStatus>UNKNOWN</upnp:writeStatus></container>" + RESPONSE_FOOTER;

    private static final String DOUBLE_CONTAINER = RESPONSE_HEADER
            + "<container id=\"C11\" searchable=\"0\" parentID=\"C1\" restricted=\"1\" childCount=\"2\">"
            + "<dc:title>Morning Music</dc:title><upnp:class>object.container</upnp:class>"
            + "<upnp:writeStatus>UNKNOWN</upnp:writeStatus></container>"
            + "<container id=\"C12\" searchable=\"0\" parentID=\"C1\" restricted=\"1\" childCount=\"0\">"
            + "<dc:title>Evening Music</dc:title><upnp:class>object.container</upnp:class>"
            + "<upnp:writeStatus>UNKNOWN</upnp:writeStatus></container>" + RESPONSE_FOOTER;

    private static final String SINGLE_MEDIA = RESPONSE_HEADER + "<item id=\"M1\" parentID=\"C11\" restricted=\"1\">"
            + "<dc:title>Music_01</dc:title><upnp:class>object.item.audioItem</upnp:class>"
            + "<dc:creator>Creator_1</dc:creator>"
            + "<res protocolInfo=\"http-get:*:audio/mpeg:*\" size=\"8054458\" importUri=\"http://MediaServerContent_0/1/M1/\">http://MediaServerContent_0/1/M1/Test_1.mp3</res>"
            + "<upnp:writeStatus>UNKNOWN</upnp:writeStatus></item>" + RESPONSE_FOOTER;

    private static final String DOUBLE_MEDIA = RESPONSE_HEADER + "<item id=\"M1\" parentID=\"C11\" restricted=\"1\">"
            + "<dc:title>Music_01</dc:title><upnp:class>object.item.audioItem</upnp:class>"
            + "<dc:creator>Creator_1</dc:creator>"
            + "<res protocolInfo=\"http-get:*:audio/mpeg:*\" size=\"8054458\" importUri=\"http://MediaServerContent_0/1/M1/\">http://MediaServerContent_0/1/M1/Test_1.mp3</res>"
            + "<upnp:writeStatus>UNKNOWN</upnp:writeStatus></item>"
            + "<item id=\"M2\" parentID=\"C11\" restricted=\"1\">"
            + "<dc:title>Music_02</dc:title><upnp:class>object.item.audioItem</upnp:class>"
            + "<dc:creator>Creator_2</dc:creator>"
            + "<res protocolInfo=\"http-get:*:audio/wav:*\" size=\"1156598\" importUri=\"http://MediaServerContent_0/3/M2/\">http://MediaServerContent_0/3/M2/Test_2.wav</res>"
            + "<upnp:writeStatus>UNKNOWN</upnp:writeStatus></item>" + RESPONSE_FOOTER;

    private @Nullable UpnpServerHandler handler;

    private ChannelUID rendererChannelUID = new ChannelUID(THING_UID + ":" + UPNPRENDERER);
    private Channel rendererChannel = ChannelBuilder.create(rendererChannelUID, "String").build();

    private ChannelUID currentIdChannelUID = new ChannelUID(THING_UID + ":" + CURRENTID);
    private Channel currentIdChannel = ChannelBuilder.create(currentIdChannelUID, "String").build();

    private ChannelUID browseChannelUID = new ChannelUID(THING_UID + ":" + BROWSE);
    private Channel browseChannel = ChannelBuilder.create(browseChannelUID, "String").build();

    private ChannelUID searchChannelUID = new ChannelUID(THING_UID + ":" + SEARCH);
    private Channel searchChannel = ChannelBuilder.create(searchChannelUID, "String").build();

    private ChannelUID playlistSelectChannelUID = new ChannelUID(THING_UID + ":" + PLAYLIST_SELECT);
    private Channel playlistSelectChannel = ChannelBuilder.create(playlistSelectChannelUID, "String").build();

    @Mock
    private @Nullable Thing thing;

    private ConcurrentMap<String, UpnpRendererHandler> upnpRenderers = new ConcurrentHashMap<>();

    @Mock
    private @Nullable UpnpIOService upnpIOService;

    @Mock
    private @Nullable UpnpDynamicStateDescriptionProvider upnpStateDescriptionProvider;

    @Mock
    private @Nullable UpnpDynamicCommandDescriptionProvider upnpCommandDescriptionProvider;

    @Mock
    private @Nullable UpnpControlBindingConfiguration configuration;

    @Mock
    private @Nullable UpnpRendererHandler rendererHandler;
    @Mock
    private @Nullable Thing rendererThing;

    @Mock
    private @Nullable Configuration config;

    @Mock
    private @Nullable ScheduledExecutorService scheduler;

    @Mock
    private @Nullable ThingHandlerCallback callback;

    @Before
    public void setUp() {
        initMocks(this);

        // don't test for multi-threading, so avoid using extra threads
        implementAsDirectExecutor(requireNonNull(scheduler));

        // stub thing methods
        when(thing.getConfiguration()).thenReturn(requireNonNull(config));
        when(thing.getUID()).thenReturn(new ThingUID("upnpcontrol", "upnpserver", "mockserver"));
        when(thing.getLabel()).thenReturn("MockServer");
        when(thing.getStatus()).thenReturn(ThingStatus.OFFLINE);

        // stub upnpIOService methods for initialize
        Map<String, String> result = new HashMap<>();
        result.put("Result", BASE_CONTAINER);
        when(upnpIOService.invokeAction(any(), eq("ContentDirectory"), eq("Browse"), anyMap())).thenReturn(result);
        when(upnpIOService.isRegistered(any())).thenReturn(true);

        // stub rendererHandler, so that only one protocol is supported and results should be filtered when filter true
        when(rendererHandler.getSink()).thenReturn(Arrays.asList("http-get:*:audio/mpeg:*"));
        when(rendererHandler.getThing()).thenReturn(requireNonNull(rendererThing));
        when(rendererThing.getUID()).thenReturn(new ThingUID("upnpcontrol", "upnprenderer", "mockrenderer"));
        when(rendererThing.getLabel()).thenReturn("MockRenderer");
        upnpRenderers.put(rendererThing.getUID().toString(), requireNonNull(rendererHandler));

        // stub channels
        when(thing.getChannel(UPNPRENDERER)).thenReturn(rendererChannel);
        when(thing.getChannel(CURRENTID)).thenReturn(currentIdChannel);
        when(thing.getChannel(BROWSE)).thenReturn(browseChannel);
        when(thing.getChannel(SEARCH)).thenReturn(searchChannel);
        when(thing.getChannel(PLAYLIST_SELECT)).thenReturn(playlistSelectChannel);

        // stub config for initialize
        when(config.as(UpnpControlConfiguration.class)).thenReturn(new UpnpControlConfiguration());
        when(config.as(UpnpControlServerConfiguration.class)).thenReturn(new UpnpControlServerConfiguration());

        handler = spy(new UpnpServerHandler(requireNonNull(thing), requireNonNull(upnpIOService),
                requireNonNull(upnpRenderers), requireNonNull(upnpStateDescriptionProvider),
                requireNonNull(upnpCommandDescriptionProvider), requireNonNull(configuration)));
        handler.setCallback(callback);
        handler.upnpScheduler = requireNonNull(scheduler);

        doReturn("12345").when(handler).getUDN();

        handler.initialize();
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

    @After
    public void tearDown() {
        handler.dispose();

        logger.info("-----------------------------------------------------------------------------------");
    }

    @Test
    public void testBase() {
        logger.info("testBase");

        handler.config.filter = false;
        handler.config.browsedown = false;
        handler.config.searchfromroot = false;

        // Check currentEntry
        assertThat(handler.currentEntry.getId(), is(UpnpServerHandler.DIRECTORY_ROOT));

        // Check CURRENTID
        // Don't check CURRENTID channel, as the REFRESH of the channel may happen before the callback is set

        // Check entries
        assertThat(handler.entries.size(), is(2));
        assertThat(handler.entries.get(0).getId(), is("C1"));
        assertThat(handler.entries.get(0).getTitle(), is("All Audio Items"));
        assertThat(handler.entries.get(1).getId(), is("C2"));
        assertThat(handler.entries.get(1).getTitle(), is("All Image Items"));

        // Check that BROWSE channel gets the correct command options, no UP should be added
        ArgumentCaptor<List<CommandOption>> commandOptionListCaptor = ArgumentCaptor.forClass(List.class);
        verify(handler, atLeastOnce()).updateCommandDescription(eq(thing.getChannel(BROWSE).getUID()),
                commandOptionListCaptor.capture());
        assertThat(commandOptionListCaptor.getValue().size(), is(2));
        assertThat(commandOptionListCaptor.getValue().get(0).getCommand(), is("C1"));
        assertThat(commandOptionListCaptor.getValue().get(0).getLabel(), is("All Audio Items"));
        assertThat(commandOptionListCaptor.getValue().get(1).getCommand(), is("C2"));
        assertThat(commandOptionListCaptor.getValue().get(1).getLabel(), is("All Image Items"));

        // Check media queue serving
        verify(rendererHandler, times(0)).registerQueue(any());
    }

    @Test
    public void testCurrentId() {
        logger.info("testCurrentId");

        handler.config.filter = false;
        handler.config.browsedown = false;
        handler.config.searchfromroot = false;

        Map<String, String> result = new HashMap<>();
        result.put("Result", DOUBLE_MEDIA);
        doReturn(result).when(upnpIOService).invokeAction(any(), eq("ContentDirectory"), eq("Browse"), anyMap());

        handler.handleCommand(thing.getChannel(CURRENTID).getUID(), StringType.valueOf("C11"));

        // Check currentEntry
        assertThat(handler.currentEntry.getId(), is("C11"));

        // Check CURRENTID
        ArgumentCaptor<StringType> stringCaptor = ArgumentCaptor.forClass(StringType.class);
        verify(callback, atLeastOnce()).stateUpdated(eq(thing.getChannel(CURRENTID).getUID()), stringCaptor.capture());
        assertThat(stringCaptor.getValue(), is(StringType.valueOf("C11")));

        // Check entries
        assertThat(handler.entries.size(), is(2));
        assertThat(handler.entries.get(0).getId(), is("M1"));
        assertThat(handler.entries.get(0).getTitle(), is("Music_01"));
        assertThat(handler.entries.get(1).getId(), is("M2"));
        assertThat(handler.entries.get(1).getTitle(), is("Music_02"));

        // Check that BROWSE channel gets the correct command options
        ArgumentCaptor<List<CommandOption>> commandOptionListCaptor = ArgumentCaptor.forClass(List.class);
        verify(handler, atLeastOnce()).updateCommandDescription(eq(thing.getChannel(BROWSE).getUID()),
                commandOptionListCaptor.capture());
        assertThat(commandOptionListCaptor.getValue().size(), is(3));
        assertThat(commandOptionListCaptor.getValue().get(0).getCommand(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(0).getLabel(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(1).getCommand(), is("M1"));
        assertThat(commandOptionListCaptor.getValue().get(1).getLabel(), is("Music_01"));
        assertThat(commandOptionListCaptor.getValue().get(2).getCommand(), is("M2"));
        assertThat(commandOptionListCaptor.getValue().get(2).getLabel(), is("Music_02"));

        // Check media queue serving
        verify(rendererHandler, times(0)).registerQueue(any());
    }

    @Test
    public void testCurrentIdRendererFilter() {
        logger.info("testCurrentIdRendererFilter");

        handler.config.filter = true;
        handler.config.browsedown = false;
        handler.config.searchfromroot = false;

        handler.handleCommand(thing.getChannel(UPNPRENDERER).getUID(),
                StringType.valueOf(rendererThing.getUID().toString()));

        Map<String, String> result = new HashMap<>();
        result.put("Result", DOUBLE_MEDIA);
        doReturn(result).when(upnpIOService).invokeAction(any(), eq("ContentDirectory"), eq("Browse"), anyMap());

        handler.handleCommand(thing.getChannel(CURRENTID).getUID(), StringType.valueOf("C11"));

        // Check currentEntry
        assertThat(handler.currentEntry.getId(), is("C11"));

        // Check CURRENTID
        ArgumentCaptor<StringType> stringCaptor = ArgumentCaptor.forClass(StringType.class);
        verify(callback, atLeastOnce()).stateUpdated(eq(thing.getChannel(CURRENTID).getUID()), stringCaptor.capture());
        assertThat(stringCaptor.getValue(), is(StringType.valueOf("C11")));

        // Check entries
        assertThat(handler.entries.size(), is(1));
        assertThat(handler.entries.get(0).getId(), is("M1"));
        assertThat(handler.entries.get(0).getTitle(), is("Music_01"));

        // Check that BROWSE channel gets the correct command options
        ArgumentCaptor<List<CommandOption>> commandOptionListCaptor = ArgumentCaptor.forClass(List.class);
        verify(handler, atLeastOnce()).updateCommandDescription(eq(thing.getChannel(BROWSE).getUID()),
                commandOptionListCaptor.capture());
        assertThat(commandOptionListCaptor.getValue().size(), is(2));
        assertThat(commandOptionListCaptor.getValue().get(0).getCommand(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(0).getLabel(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(1).getCommand(), is("M1"));
        assertThat(commandOptionListCaptor.getValue().get(1).getLabel(), is("Music_01"));

        // Check media queue serving
        verify(callback, atLeastOnce()).stateUpdated(eq(thing.getChannel(UPNPRENDERER).getUID()),
                stringCaptor.capture());
        assertThat(stringCaptor.getValue(), is(StringType.valueOf(rendererThing.getUID().toString())));

        // Check media queue serving
        verify(rendererHandler).registerQueue(any());
    }

    @Test
    public void testBrowseContainers() {
        logger.info("testBrowseContainers");

        handler.config.filter = false;
        handler.config.browsedown = false;
        handler.config.searchfromroot = false;

        Map<String, String> result = new HashMap<>();
        result.put("Result", DOUBLE_CONTAINER);
        doReturn(result).when(upnpIOService).invokeAction(any(), eq("ContentDirectory"), eq("Browse"), anyMap());

        handler.handleCommand(thing.getChannel(BROWSE).getUID(), StringType.valueOf("C1"));

        // Check currentEntry
        assertThat(handler.currentEntry.getId(), is("C1"));

        // Check CURRENTID
        ArgumentCaptor<StringType> stringCaptor = ArgumentCaptor.forClass(StringType.class);
        verify(callback, atLeastOnce()).stateUpdated(eq(thing.getChannel(CURRENTID).getUID()), stringCaptor.capture());
        assertThat(stringCaptor.getValue(), is(StringType.valueOf("C1")));

        // Check entries
        assertThat(handler.entries.size(), is(2));
        assertThat(handler.entries.get(0).getId(), is("C11"));
        assertThat(handler.entries.get(0).getTitle(), is("Morning Music"));
        assertThat(handler.entries.get(1).getId(), is("C12"));
        assertThat(handler.entries.get(1).getTitle(), is("Evening Music"));

        // Check that BROWSE channel gets the correct command options
        ArgumentCaptor<List<CommandOption>> commandOptionListCaptor = ArgumentCaptor.forClass(List.class);
        verify(handler, atLeastOnce()).updateCommandDescription(eq(thing.getChannel(BROWSE).getUID()),
                commandOptionListCaptor.capture());
        assertThat(commandOptionListCaptor.getValue().size(), is(3));
        assertThat(commandOptionListCaptor.getValue().get(0).getCommand(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(0).getLabel(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(1).getCommand(), is("C11"));
        assertThat(commandOptionListCaptor.getValue().get(1).getLabel(), is("Morning Music"));
        assertThat(commandOptionListCaptor.getValue().get(2).getCommand(), is("C12"));
        assertThat(commandOptionListCaptor.getValue().get(2).getLabel(), is("Evening Music"));

        // Check media queue serving
        verify(rendererHandler, times(0)).registerQueue(any());
    }

    @Test
    public void testBrowseOneContainerNoBrowseDown() {
        logger.info("testBrowseOneContainerNoBrowseDown");

        handler.config.filter = false;
        handler.config.browsedown = false;
        handler.config.searchfromroot = false;

        Map<String, String> resultContainer = new HashMap<>();
        resultContainer.put("Result", SINGLE_CONTAINER);
        Map<String, String> resultMedia = new HashMap<>();
        resultMedia.put("Result", DOUBLE_MEDIA);
        doReturn(resultContainer).doReturn(resultMedia).when(upnpIOService).invokeAction(any(), eq("ContentDirectory"),
                eq("Browse"), anyMap());

        handler.handleCommand(thing.getChannel(BROWSE).getUID(), StringType.valueOf("C1"));

        // Check currentEntry
        assertThat(handler.currentEntry.getId(), is("C1"));

        // Check CURRENTID
        ArgumentCaptor<StringType> stringCaptor = ArgumentCaptor.forClass(StringType.class);
        verify(callback, atLeastOnce()).stateUpdated(eq(thing.getChannel(CURRENTID).getUID()), stringCaptor.capture());
        assertThat(stringCaptor.getValue(), is(StringType.valueOf("C1")));

        // Check entries
        assertThat(handler.entries.size(), is(1));
        assertThat(handler.entries.get(0).getId(), is("C11"));
        assertThat(handler.entries.get(0).getTitle(), is("Morning Music"));

        // Check that BROWSE channel gets the correct command options
        ArgumentCaptor<List<CommandOption>> commandOptionListCaptor = ArgumentCaptor.forClass(List.class);
        verify(handler, atLeastOnce()).updateCommandDescription(eq(thing.getChannel(BROWSE).getUID()),
                commandOptionListCaptor.capture());
        assertThat(commandOptionListCaptor.getValue().size(), is(2));
        assertThat(commandOptionListCaptor.getValue().get(0).getCommand(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(0).getLabel(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(1).getCommand(), is("C11"));
        assertThat(commandOptionListCaptor.getValue().get(1).getLabel(), is("Morning Music"));

        // Check that a no media queue is being served as there is no renderer selected
        verify(rendererHandler, times(0)).registerQueue(any());
    }

    @Test
    public void testBrowseOneContainerBrowseDown() {
        logger.info("testBrowseOneContainerBrowseDown");

        handler.config.filter = false;
        handler.config.browsedown = true;
        handler.config.searchfromroot = false;

        Map<String, String> resultContainer = new HashMap<>();
        resultContainer.put("Result", SINGLE_CONTAINER);
        Map<String, String> resultMedia = new HashMap<>();
        resultMedia.put("Result", DOUBLE_MEDIA);
        doReturn(resultContainer).doReturn(resultMedia).when(upnpIOService).invokeAction(any(), eq("ContentDirectory"),
                eq("Browse"), anyMap());

        handler.handleCommand(thing.getChannel(BROWSE).getUID(), StringType.valueOf("C1"));

        // Check currentEntry
        assertThat(handler.currentEntry.getId(), is("C11"));

        // Check CURRENTID
        ArgumentCaptor<StringType> stringCaptor = ArgumentCaptor.forClass(StringType.class);
        verify(callback, atLeastOnce()).stateUpdated(eq(thing.getChannel(CURRENTID).getUID()), stringCaptor.capture());
        assertThat(stringCaptor.getValue(), is(StringType.valueOf("C11")));

        // Check entries
        assertThat(handler.entries.size(), is(2));
        assertThat(handler.entries.get(0).getId(), is("M1"));
        assertThat(handler.entries.get(0).getTitle(), is("Music_01"));
        assertThat(handler.entries.get(1).getId(), is("M2"));
        assertThat(handler.entries.get(1).getTitle(), is("Music_02"));

        // Check that BROWSE channel gets the correct command options
        ArgumentCaptor<List<CommandOption>> commandOptionListCaptor = ArgumentCaptor.forClass(List.class);
        verify(handler, atLeastOnce()).updateCommandDescription(eq(thing.getChannel(BROWSE).getUID()),
                commandOptionListCaptor.capture());
        assertThat(commandOptionListCaptor.getValue().size(), is(3));
        assertThat(commandOptionListCaptor.getValue().get(0).getCommand(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(0).getLabel(), is(".."));
        assertThat(commandOptionListCaptor.getValue().get(1).getCommand(), is("M1"));
        assertThat(commandOptionListCaptor.getValue().get(1).getLabel(), is("Music_01"));
        assertThat(commandOptionListCaptor.getValue().get(2).getCommand(), is("M2"));
        assertThat(commandOptionListCaptor.getValue().get(2).getLabel(), is("Music_02"));

        // Check media queue serving
        verify(rendererHandler, times(0)).registerQueue(any());
    }
}
