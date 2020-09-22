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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.builder.ChannelBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.openhab.binding.upnpcontrol.internal.UpnpAudioSinkReg;
import org.openhab.binding.upnpcontrol.internal.UpnpEntry;
import org.openhab.binding.upnpcontrol.internal.UpnpEntryQueue;
import org.openhab.binding.upnpcontrol.internal.UpnpEntryRes;
import org.openhab.binding.upnpcontrol.internal.UpnpXMLParser;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlRendererConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link UpnpRendererHandler}.
 *
 * @author Mark Herwege - Initial contribution
 */
@SuppressWarnings({ "null", "unchecked" })
@NonNullByDefault
public class UpnpRendererHandlerTest extends UpnpHandlerTest {

    private final Logger logger = LoggerFactory.getLogger(UpnpRendererHandlerTest.class);

    private static final String THING_TYPE_UID = "upnpcontrol:upnprenderer";
    private static final String THING_UID = THING_TYPE_UID + ":mockrenderer";

    protected @Nullable UpnpRendererHandler handler;

    private @Nullable UpnpEntryQueue upnpEntryQueue;

    private ChannelUID volumeChannelUID = new ChannelUID(THING_UID + ":" + VOLUME);
    private Channel volumeChannel = ChannelBuilder.create(volumeChannelUID, "Dimmer").build();

    private ChannelUID muteChannelUID = new ChannelUID(THING_UID + ":" + MUTE);
    private Channel muteChannel = ChannelBuilder.create(muteChannelUID, "Switch").build();

    private ChannelUID stopChannelUID = new ChannelUID(THING_UID + ":" + STOP);
    private Channel stopChannel = ChannelBuilder.create(stopChannelUID, "Switch").build();

    private ChannelUID controlChannelUID = new ChannelUID(THING_UID + ":" + CONTROL);
    private Channel controlChannel = ChannelBuilder.create(controlChannelUID, "Player").build();

    private ChannelUID repeatChannelUID = new ChannelUID(THING_UID + ":" + REPEAT);
    private Channel repeatChannel = ChannelBuilder.create(repeatChannelUID, "Switch").build();

    private ChannelUID shuffleChannelUID = new ChannelUID(THING_UID + ":" + SHUFFLE);
    private Channel shuffleChannel = ChannelBuilder.create(shuffleChannelUID, "Switch").build();

    private ChannelUID onlyPlayOneChannelUID = new ChannelUID(THING_UID + ":" + ONLY_PLAY_ONE);
    private Channel onlyPlayOneChannel = ChannelBuilder.create(onlyPlayOneChannelUID, "Switch").build();

    private ChannelUID uriChannelUID = new ChannelUID(THING_UID + ":" + URI);
    private Channel uriChannel = ChannelBuilder.create(uriChannelUID, "String").build();

    private ChannelUID favoriteSelectChannelUID = new ChannelUID(THING_UID + ":" + FAVORITE_SELECT);
    private Channel favoriteSelectChannel = ChannelBuilder.create(favoriteSelectChannelUID, "String").build();

    private ChannelUID favoriteChannelUID = new ChannelUID(THING_UID + ":" + FAVORITE);
    private Channel favoriteChannel = ChannelBuilder.create(favoriteChannelUID, "String").build();

    private ChannelUID favoriteActionChannelUID = new ChannelUID(THING_UID + ":" + FAVORITE_ACTION);
    private Channel favoriteActionChannel = ChannelBuilder.create(favoriteActionChannelUID, "String").build();

    private ChannelUID playlistSelectChannelUID = new ChannelUID(THING_UID + ":" + PLAYLIST_SELECT);
    private Channel playlistSelectChannel = ChannelBuilder.create(playlistSelectChannelUID, "String").build();

    private ChannelUID titleChannelUID = new ChannelUID(THING_UID + ":" + TITLE);
    private Channel titleChannel = ChannelBuilder.create(titleChannelUID, "String").build();

    private ChannelUID albumChannelUID = new ChannelUID(THING_UID + ":" + ALBUM);
    private Channel albumChannel = ChannelBuilder.create(albumChannelUID, "String").build();

    private ChannelUID albumArtChannelUID = new ChannelUID(THING_UID + ":" + ALBUM_ART);
    private Channel albumArtChannel = ChannelBuilder.create(albumArtChannelUID, "Image").build();

    private ChannelUID creatorChannelUID = new ChannelUID(THING_UID + ":" + CREATOR);
    private Channel creatorChannel = ChannelBuilder.create(creatorChannelUID, "String").build();

    private ChannelUID artistChannelUID = new ChannelUID(THING_UID + ":" + ARTIST);
    private Channel artistChannel = ChannelBuilder.create(artistChannelUID, "String").build();

    private ChannelUID publisherChannelUID = new ChannelUID(THING_UID + ":" + PUBLISHER);
    private Channel publisherChannel = ChannelBuilder.create(publisherChannelUID, "String").build();

    private ChannelUID genreChannelUID = new ChannelUID(THING_UID + ":" + GENRE);
    private Channel genreChannel = ChannelBuilder.create(genreChannelUID, "String").build();

    private ChannelUID trackNumberChannelUID = new ChannelUID(THING_UID + ":" + TRACK_NUMBER);
    private Channel trackNumberChannel = ChannelBuilder.create(trackNumberChannelUID, "Number").build();

    private ChannelUID trackDurationChannelUID = new ChannelUID(THING_UID + ":" + TRACK_DURATION);
    private Channel trackDurationChannel = ChannelBuilder.create(trackDurationChannelUID, "Number:Time").build();

    private ChannelUID trackPositionChannelUID = new ChannelUID(THING_UID + ":" + TRACK_POSITION);
    private Channel trackPositionChannel = ChannelBuilder.create(trackPositionChannelUID, "Number:Time").build();

    private ChannelUID relTrackPositionChannelUID = new ChannelUID(THING_UID + ":" + REL_TRACK_POSITION);
    private Channel relTrackPositionChannel = ChannelBuilder.create(relTrackPositionChannelUID, "Dimmer").build();

    @Mock
    private @Nullable UpnpAudioSinkReg audioSinkReg;

    @Override
    @Before
    public void setUp() {
        initMocks(this);

        super.setUp();

        // stub thing methods
        when(thing.getUID()).thenReturn(new ThingUID("upnpcontrol", "upnprenderer", "mockrenderer"));
        when(thing.getLabel()).thenReturn("MockRenderer");
        when(thing.getStatus()).thenReturn(ThingStatus.OFFLINE);

        // stub upnpIOService methods for initialize
        Map<String, String> result = new HashMap<>();
        result.put("Result", "");
        when(upnpIOService.invokeAction(any(), eq("ContentDirectory"), eq("Browse"), anyMap())).thenReturn(result);

        // stub channels
        when(thing.getChannel(VOLUME)).thenReturn(volumeChannel);
        when(thing.getChannel(MUTE)).thenReturn(muteChannel);
        when(thing.getChannel(STOP)).thenReturn(stopChannel);
        when(thing.getChannel(CONTROL)).thenReturn(controlChannel);
        when(thing.getChannel(REPEAT)).thenReturn(repeatChannel);
        when(thing.getChannel(SHUFFLE)).thenReturn(shuffleChannel);
        when(thing.getChannel(ONLY_PLAY_ONE)).thenReturn(onlyPlayOneChannel);
        when(thing.getChannel(URI)).thenReturn(uriChannel);
        when(thing.getChannel(FAVORITE_SELECT)).thenReturn(favoriteSelectChannel);
        when(thing.getChannel(FAVORITE)).thenReturn(favoriteChannel);
        when(thing.getChannel(FAVORITE_ACTION)).thenReturn(favoriteActionChannel);
        when(thing.getChannel(PLAYLIST_SELECT)).thenReturn(playlistSelectChannel);
        when(thing.getChannel(TITLE)).thenReturn(titleChannel);
        when(thing.getChannel(ALBUM)).thenReturn(albumChannel);
        when(thing.getChannel(ALBUM_ART)).thenReturn(albumArtChannel);
        when(thing.getChannel(CREATOR)).thenReturn(creatorChannel);
        when(thing.getChannel(ARTIST)).thenReturn(artistChannel);
        when(thing.getChannel(PUBLISHER)).thenReturn(publisherChannel);
        when(thing.getChannel(GENRE)).thenReturn(genreChannel);
        when(thing.getChannel(TRACK_NUMBER)).thenReturn(trackNumberChannel);
        when(thing.getChannel(TRACK_DURATION)).thenReturn(trackDurationChannel);
        when(thing.getChannel(TRACK_POSITION)).thenReturn(trackPositionChannel);
        when(thing.getChannel(REL_TRACK_POSITION)).thenReturn(relTrackPositionChannel);

        // stub config for initialize
        when(config.as(UpnpControlRendererConfiguration.class)).thenReturn(new UpnpControlRendererConfiguration());

        // create a media queue for playing
        List<UpnpEntry> entries = new ArrayList<>();
        UpnpEntry entry;
        List<UpnpEntryRes> resList;
        UpnpEntryRes res;
        resList = new ArrayList<>();
        res = new UpnpEntryRes("http-get:*:audio/mpeg:*", 8054458L, "10", "http://MediaServerContent_0/1/M1/");
        res.setRes("http://MediaServerContent_0/1/M1/Test_1.mp3");
        resList.add(res);
        entry = new UpnpEntry("M1", "M1", "C11", "object.item.audioItem").withTitle("Music_01").withResList(resList)
                .withAlbum("My Music 1").withCreator("Creator_1").withArtist("Artist_1").withGenre("Morning")
                .withPublisher("myself 1").withAlbumArtUri("").withTrackNumber(1);
        entries.add(entry);
        resList = new ArrayList<>();
        res = new UpnpEntryRes("http-get:*:audio/wav:*", 1156598L, "6", "http://MediaServerContent_0/1/M2/");
        res.setRes("http://MediaServerContent_0/1/M2/Test_2.wav");
        resList.add(res);
        entry = new UpnpEntry("M2", "M2", "C11", "object.item.audioItem").withTitle("Music_02").withResList(resList)
                .withAlbum("My Music 1").withCreator("Creator_2").withArtist("Artist_2").withGenre("Morning")
                .withPublisher("myself 2").withAlbumArtUri("").withTrackNumber(2);
        entries.add(entry);
        resList = new ArrayList<>();
        res = new UpnpEntryRes("http-get:*:audio/mpeg:*", 1156598L, "4", "http://MediaServerContent_0/1/M3/");
        res.setRes("http://MediaServerContent_0/1/M3/Test_3.mp3");
        resList.add(res);
        entry = new UpnpEntry("M3", "M3", "C12", "object.item.audioItem").withTitle("Music_03").withResList(resList)
                .withAlbum("My Music 2").withCreator("Creator_3").withArtist("Artist_3").withGenre("Evening")
                .withPublisher("myself 3").withAlbumArtUri("").withTrackNumber(1);
        entries.add(entry);
        upnpEntryQueue = new UpnpEntryQueue(entries, "54321");

        handler = spy(new UpnpRendererHandler(requireNonNull(thing), requireNonNull(upnpIOService),
                requireNonNull(audioSinkReg), requireNonNull(upnpStateDescriptionProvider),
                requireNonNull(upnpCommandDescriptionProvider), configuration));

        initHandler(requireNonNull(handler));

        handler.initialize();
    }

    @Override
    @After
    public void tearDown() {
        handler.dispose();

        super.tearDown();
    }

    @Test
    public void testBase() {
        logger.info("testBase");

        // Register a media queue
        handler.registerQueue(requireNonNull(upnpEntryQueue));

        // Check internal player state
        assertThat(handler.currentEntry, is(upnpEntryQueue.get(0)));
        assertThat(handler.nextEntry, is(upnpEntryQueue.get(1)));
        assertThat(handler.playerStopped, is(true));
        assertThat(handler.playing, is(false));
        assertThat(handler.registeredQueue, is(true));
        assertThat(handler.playingQueue, is(false));
        assertThat(handler.oneplayed, is(false));

        // Check that currentURI and nextURI are being set
        verify(handler).setCurrentURI(upnpEntryQueue.get(0).getRes(),
                UpnpXMLParser.compileMetadataString(requireNonNull(upnpEntryQueue.get(0))));
        verify(handler).setNextURI(upnpEntryQueue.get(1).getRes(),
                UpnpXMLParser.compileMetadataString(requireNonNull(upnpEntryQueue.get(1))));
    }
}
