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

import static org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants.*;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.audio.AudioFormat;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.NextPreviousType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.PlayPauseType;
import org.eclipse.smarthome.core.library.types.QuantityType;
import org.eclipse.smarthome.core.library.types.RewindFastforwardType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.unit.SmartHomeUnits;
import org.eclipse.smarthome.core.thing.Channel;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.eclipse.smarthome.core.types.State;
import org.eclipse.smarthome.core.types.StateOption;
import org.eclipse.smarthome.core.types.UnDefType;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.eclipse.smarthome.io.transport.upnp.UpnpIOService;
import org.openhab.binding.upnpcontrol.internal.UpnpAudioSink;
import org.openhab.binding.upnpcontrol.internal.UpnpAudioSinkReg;
import org.openhab.binding.upnpcontrol.internal.UpnpChannelName;
import org.openhab.binding.upnpcontrol.internal.UpnpControlUtil;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicCommandDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.UpnpDynamicStateDescriptionProvider;
import org.openhab.binding.upnpcontrol.internal.UpnpEntry;
import org.openhab.binding.upnpcontrol.internal.UpnpEntryQueue;
import org.openhab.binding.upnpcontrol.internal.UpnpFavorite;
import org.openhab.binding.upnpcontrol.internal.UpnpXMLParser;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlBindingConfiguration;
import org.openhab.binding.upnpcontrol.internal.config.UpnpControlRendererConfiguration;
import org.openhab.binding.upnpcontrol.internal.services.UpnpRenderingControlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UpnpRendererHandler} is responsible for handling commands sent to the UPnP Renderer. It extends
 * {@link UpnpHandler} with UPnP renderer specific logic. It implements UPnP AVTransport and RenderingControl service
 * actions.
 *
 * @author Mark Herwege - Initial contribution
 * @author Karel Goderis - Based on UPnP logic in Sonos binding
 */
@NonNullByDefault
public class UpnpRendererHandler extends UpnpHandler {

    private final Logger logger = LoggerFactory.getLogger(UpnpRendererHandler.class);

    // UPnP protocol pattern
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("(?:.*):(?:.*):(.*):(?:.*)");

    private volatile boolean audioSupport;
    protected volatile Set<AudioFormat> supportedAudioFormats = new HashSet<>();
    private volatile boolean audioSinkRegistered;

    private volatile UpnpAudioSinkReg audioSinkReg;

    private volatile Set<UpnpServerHandler> serverHandlers = ConcurrentHashMap.newKeySet();

    protected @NonNullByDefault({}) UpnpControlRendererConfiguration config;
    private @Nullable UpnpRenderingControlConfiguration renderingControlConfiguration;

    private volatile List<StateOption> favoriteStateOptionList = Collections.synchronizedList(new ArrayList<>());

    private @NonNullByDefault({}) ChannelUID favoriteSelectChannelUID;

    private volatile String transportState = "";

    private volatile PercentType soundVolume = new PercentType();
    private volatile List<String> sink = new ArrayList<>();

    private volatile UpnpEntryQueue currentQueue = new UpnpEntryQueue();
    private boolean repeat = false;
    private boolean shuffle = false;
    private volatile @Nullable UpnpEntry currentEntry = null;
    private volatile @Nullable UpnpEntry nextEntry = null;
    private volatile String nowPlayingUri = ""; // used to block waiting for setting URI when it is the same as current
    private volatile String favoriteName = "";
    private volatile boolean playerStopped;
    private volatile boolean playing;
    private volatile boolean playingQueue;
    private volatile @Nullable ScheduledFuture<?> paused;
    private volatile @Nullable CompletableFuture<Boolean> isSettingURI;
    private volatile int trackDuration = 0;
    private volatile int trackPosition = 0;
    private volatile long expectedTrackend = 0;
    private volatile @Nullable ScheduledFuture<?> trackPositionRefresh;

    public UpnpRendererHandler(Thing thing, UpnpIOService upnpIOService, UpnpAudioSinkReg audioSinkReg,
            UpnpDynamicStateDescriptionProvider upnpStateDescriptionProvider,
            UpnpDynamicCommandDescriptionProvider upnpCommandDescriptionProvider,
            UpnpControlBindingConfiguration configuration) {
        super(thing, upnpIOService, configuration, upnpStateDescriptionProvider, upnpCommandDescriptionProvider);

        serviceSubscriptions.add("AVTransport");
        serviceSubscriptions.add("RenderingControl");

        this.audioSinkReg = audioSinkReg;
    }

    @Override
    public void initialize() {
        super.initialize();
        config = getConfigAs(UpnpControlRendererConfiguration.class);
        if (config.seekstep < 1) {
            config.seekstep = 1;
        }

        logger.debug("Initializing handler for media renderer device {}", thing.getLabel());

        Channel favoriteSelectChannel = thing.getChannel(FAVORITE_SELECT);
        if (favoriteSelectChannel != null) {
            favoriteSelectChannelUID = favoriteSelectChannel.getUID();
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Channel " + FAVORITE_SELECT + " not defined");
            return;
        }

        initDevice();
    }

    @Override
    public void dispose() {
        cancelTrackPositionRefresh();
        resetPaused();
        CompletableFuture<Boolean> settingURI = isSettingURI;
        if (settingURI != null) {
            settingURI.complete(false); // We have received current URI, so can allow play to start
        }

        super.dispose();
    }

    @Override
    protected void initJob() {
        synchronized (jobLock) {
            if (!ThingStatus.ONLINE.equals(getThing().getStatus())) {
                if (pollingJob == null) {
                    return;
                }

                if (!service.isRegistered(this)) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "UPnP device with UDN " + getUDN() + " not yet registered");
                    return;
                }

                String descriptor = thing.getProperties().get("RenderingControlDescrURL");
                try {
                    UpnpRenderingControlConfiguration config = UpnpXMLParser
                            .parseRenderingControlDescription(new URL(descriptor));
                    Set<String> audioChannels = config.audioChannels;
                    renderingControlConfiguration = config;
                    for (String audioChannel : audioChannels) {
                        createAudioChannels(audioChannel);
                    }
                } catch (MalformedURLException e) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "Mal formed Rendering Control descriptor URL: " + descriptor);
                    return;
                }

                getProtocolInfo();

                if (!upnpSubscribed) {
                    addSubscriptions();
                }

                updateChannels();

                getCurrentConnectionInfo();
                if (!checkForConnectionIds()) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "No connection Id's set for UPnP device with UDN " + getUDN());
                    return;
                }

                getTransportState();

                updateStatus(ThingStatus.ONLINE);
            }
        }
    }

    private void createAudioChannels(String audioChannel) {
        UpnpRenderingControlConfiguration config = renderingControlConfiguration;
        if (config != null) {
            if (config.volume && !UPNP_MASTER.equals(audioChannel)) {
                String name = audioChannel + "volume";
                if (UpnpChannelName.channelIdToUpnpChannelName(name) != null) {
                    createChannel(UpnpChannelName.channelIdToUpnpChannelName(name));
                } else {
                    createChannel(name, name, "Dimmer", "Vendor specific UPnP volume channel", "SoundVolume", true);
                }
            }
            if (config.mute && !UPNP_MASTER.equals(audioChannel)) {
                String name = audioChannel + "mute";
                if (UpnpChannelName.channelIdToUpnpChannelName(name) != null) {
                    createChannel(UpnpChannelName.channelIdToUpnpChannelName(name));
                } else {
                    createChannel(name, name, "Switch", "Vendor specific  UPnP mute channel", "SoundVolume", true);
                }
            }
            if (config.loudness) {
                String name = (UPNP_MASTER.equals(audioChannel) ? "" : audioChannel) + "loudness";
                if (UpnpChannelName.channelIdToUpnpChannelName(name) != null) {
                    createChannel(UpnpChannelName.channelIdToUpnpChannelName(name));
                } else {
                    createChannel(name, name, "Switch", "Vendor specific  UPnP loudness channel", "SoundVolume", true);
                }
            }
        }
    }

    /**
     * Invoke Stop on UPnP AV Transport.
     */
    public void stop() {
        playerStopped = true;

        Map<String, String> inputs = Collections.singletonMap("InstanceID", Integer.toString(avTransportId));

        invokeAction("AVTransport", "Stop", inputs);
    }

    /**
     * Invoke Play on UPnP AV Transport.
     */
    public void play() {
        CompletableFuture<Boolean> settingURI = isSettingURI;
        boolean uriSet = true;
        try {
            if (settingURI != null) {
                // wait for maximum 2.5s until the media URI is set before playing
                uriSet = settingURI.get(UPNP_RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Timeout exception, media URI not yet set in the renderer, trying to play anyway");
        }

        if (uriSet) {
            Map<String, String> inputs = new HashMap<>();
            inputs.put("InstanceID", Integer.toString(avTransportId));
            inputs.put("Speed", "1");

            invokeAction("AVTransport", "Play", inputs);
        } else {
            logger.debug("Cannot play, cancelled setting URI in the renderer");
        }
    }

    /**
     * Invoke Pause on UPnP AV Transport.
     */
    public void pause() {
        Map<String, String> inputs = Collections.singletonMap("InstanceID", Integer.toString(avTransportId));

        invokeAction("AVTransport", "Pause", inputs);
    }

    /**
     * Invoke Next on UPnP AV Transport.
     */
    protected void next() {
        Map<String, String> inputs = Collections.singletonMap("InstanceID", Integer.toString(avTransportId));

        invokeAction("AVTransport", "Next", inputs);
    }

    /**
     * Invoke Previous on UPnP AV Transport.
     */
    protected void previous() {
        Map<String, String> inputs = Collections.singletonMap("InstanceID", Integer.toString(avTransportId));

        invokeAction("AVTransport", "Previous", inputs);
    }

    /**
     * Invoke Seek on UPnP AV Transport.
     *
     * @param seekTarget relative position in current track, format HH:mm:ss
     */
    protected void seek(String seekTarget) {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(avTransportId));
        inputs.put("Unit", "REL_TIME");
        inputs.put("Target", seekTarget);

        invokeAction("AVTransport", "Seek", inputs);
    }

    /**
     * Invoke SetAVTransportURI on UPnP AV Transport.
     *
     * @param URI
     * @param URIMetaData
     */
    public void setCurrentURI(String URI, String URIMetaData) {
        String uri = "";
        try {
            uri = URLDecoder.decode(URI.trim(), StandardCharsets.UTF_8.name());
            // Some renderers don't send a URI Last Changed event when the same URI is requested, so don't wait for it
            // before starting to play
            if (!uri.equals(nowPlayingUri)) {
                CompletableFuture<Boolean> settingURI = isSettingURI;
                if (settingURI != null) {
                    settingURI.complete(false);
                }
                isSettingURI = new CompletableFuture<Boolean>(); // set this so we don't start playing when not finished
                                                                 // setting URI
            } else {
                logger.debug("New URI {} is same as previous", nowPlayingUri);
            }
        } catch (UnsupportedEncodingException ignore) {
            uri = URI;
        }

        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(avTransportId));
        inputs.put("CurrentURI", uri);
        inputs.put("CurrentURIMetaData", URIMetaData);

        invokeAction("AVTransport", "SetAVTransportURI", inputs);
    }

    /**
     * Invoke SetNextAVTransportURI on UPnP AV Transport.
     *
     * @param nextURI
     * @param nextURIMetaData
     */
    public void setNextURI(String nextURI, String nextURIMetaData) {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(avTransportId));
        inputs.put("NextURI", nextURI);
        inputs.put("NextURIMetaData", nextURIMetaData);

        invokeAction("AVTransport", "SetNextAVTransportURI", inputs);
    }

    /**
     * Invoke GetTransportState on UPnP AV Transport.
     * Result is received in {@link onValueReceived}.
     */
    protected void getTransportState() {
        Map<String, String> inputs = Collections.singletonMap("InstanceID", Integer.toString(avTransportId));

        invokeAction("AVTransport", "GetTransportInfo", inputs);
    }

    /**
     * Invoke getPositionInfo on UPnP AV Transport.
     * Result is received in {@link onValueReceived}.
     */
    protected void getPositionInfo() {
        Map<String, String> inputs = Collections.singletonMap("InstanceID", Integer.toString(avTransportId));

        invokeAction("AVTransport", "GetPositionInfo", inputs);
    }

    /**
     * Invoke GetMediaInfo on UPnP AV Transport.
     * Result is received in {@link onValueReceived}.
     */
    public void getMediaInfo() {
        Map<String, String> inputs = Collections.singletonMap("InstanceID", Integer.toString(avTransportId));

        invokeAction("AVTransport", "smarthome:audio stream http://icecast.vrtcdn.be/stubru_tijdloze-high.mp3", inputs);
    }

    /**
     * Retrieves the current volume known to the control point, gets updated by GENA events or after UPnP Rendering
     * Control GetVolume call. This method is used to retrieve volume by {@link UpnpAudioSink.getVolume}.
     *
     * @return current volume
     */
    public PercentType getCurrentVolume() {
        return soundVolume;
    }

    /**
     * Invoke GetVolume on UPnP Rendering Control.
     * Result is received in {@link onValueReceived}.
     *
     * @param channel
     */
    protected void getVolume(String channel) {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(rcsId));
        inputs.put("Channel", channel);

        invokeAction("RenderingControl", "GetVolume", inputs);
    }

    /**
     * Invoke SetVolume on UPnP Rendering Control.
     *
     * @param channel
     * @param volume
     */
    protected void setVolume(String channel, PercentType volume) {
        UpnpRenderingControlConfiguration config = renderingControlConfiguration;

        int newVolume = volume.intValue();
        if (config != null) {
            newVolume = newVolume * config.maxvolume / 100;
        }
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(rcsId));
        inputs.put("Channel", channel);
        inputs.put("DesiredVolume", String.valueOf(newVolume));

        invokeAction("RenderingControl", "SetVolume", inputs);
    }

    /**
     * Invoke SetVolume for Master channel on UPnP Rendering Control.
     *
     * @param volume
     */
    public void setVolume(PercentType volume) {
        setVolume(UPNP_MASTER, volume);
    }

    /**
     * Invoke getMute on UPnP Rendering Control.
     * Result is received in {@link onValueReceived}.
     *
     * @param channel
     */
    protected void getMute(String channel) {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(rcsId));
        inputs.put("Channel", channel);

        invokeAction("RenderingControl", "GetMute", inputs);
    }

    /**
     * Invoke SetMute on UPnP Rendering Control.
     *
     * @param channel
     * @param mute
     */
    protected void setMute(String channel, OnOffType mute) {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(rcsId));
        inputs.put("Channel", channel);
        inputs.put("DesiredMute", mute == OnOffType.ON ? "1" : "0");

        invokeAction("RenderingControl", "SetMute", inputs);
    }

    /**
     * Invoke getMute on UPnP Rendering Control.
     * Result is received in {@link onValueReceived}.
     *
     * @param channel
     */
    protected void getLoudness(String channel) {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(rcsId));
        inputs.put("Channel", channel);

        invokeAction("RenderingControl", "GetLoudness", inputs);
    }

    /**
     * Invoke SetMute on UPnP Rendering Control.
     *
     * @param channel
     * @param mute
     */
    protected void setLoudness(String channel, OnOffType mute) {
        Map<String, String> inputs = new HashMap<>();
        inputs.put("InstanceID", Integer.toString(rcsId));
        inputs.put("Channel", channel);
        inputs.put("DesiredLoudness", mute == OnOffType.ON ? "1" : "0");

        invokeAction("RenderingControl", "SetLoudness", inputs);
    }

    /**
     * Called from server handler for renderer to be able to send back status to server handler
     *
     * @param handler
     */
    void setServerHandler(UpnpServerHandler handler) {
        logger.debug("Set server handler {} on renderer {}", handler.getThing().getLabel(), thing.getLabel());
        serverHandlers.add(handler);
    }

    /**
     * Should be called from server handler when server stops serving this renderer
     */
    void unsetServerHandler() {
        logger.debug("Unset server handler on renderer {}", thing.getLabel());
        for (UpnpServerHandler handler : serverHandlers) {
            Thing serverThing = handler.getThing();
            Channel serverChannel;
            for (String channel : SERVER_CONTROL_CHANNELS) {
                if ((serverChannel = serverThing.getChannel(channel)) != null) {
                    handler.updateServerState(serverChannel.getUID(), UnDefType.UNDEF);
                }
            }

            serverHandlers.remove(handler);
        }
    }

    @Override
    protected void updateState(ChannelUID channelUID, State state) {
        // override to be able to propagate channel state updates to corresponding channels on the server
        if (SERVER_CONTROL_CHANNELS.contains(channelUID.getId())) {
            for (UpnpServerHandler handler : serverHandlers) {
                Thing serverThing = handler.getThing();
                Channel serverChannel = serverThing.getChannel(channelUID.getId());
                if (serverChannel != null) {
                    logger.debug("Update server {} channel {} state from renderer {}", serverThing.getLabel(),
                            channelUID, thing.getLabel());
                    handler.updateServerState(serverChannel.getUID(), state);
                }
            }
        }
        super.updateState(channelUID, state);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} for channel {} on renderer {}", command, channelUID, thing.getLabel());

        String id = channelUID.getId();

        if (id.endsWith("volume")) {
            handleCommandVolume(command, id);
        } else if (id.endsWith("mute")) {
            handleCommandMute(command, id);
        } else if (id.endsWith("loudness")) {
            handleCommandLoudness(command, id);
        } else {
            switch (id) {
                case STOP:
                    handleCommandStop(command);
                    break;
                case CONTROL:
                    handleCommandControl(channelUID, command);
                    break;
                case REPEAT:
                    handleCommandRepeat(channelUID, command);
                    break;
                case SHUFFLE:
                    handleCommandShuffle(channelUID, command);
                    break;
                case URI:
                    handleCommandUri(channelUID, command);
                    break;
                case FAVORITE_SELECT:
                    handleCommandFavoriteSelect(channelUID, command);
                    break;
                case FAVORITE:
                    handleCommandFavorite(channelUID, command);
                    break;
                case FAVORITE_SAVE:
                    handleCommandFavoriteSave(command);
                    break;
                case FAVORITE_DELETE:
                    handleCommandFavoriteDelete(command);
                    break;
                case TRACK_POSITION:
                    handleCommandTrackPosition(channelUID, command);
                    break;
                case REL_TRACK_POSITION:
                    handleCommandRelTrackPosition(channelUID, command);
                    break;
                default:
                    break;
            }
        }
    }

    private void handleCommandVolume(Command command, String id) {
        if (command instanceof RefreshType) {
            getVolume("volume".equals(id) ? UPNP_MASTER : id.replace("volume", ""));
        } else if (command instanceof PercentType) {
            setVolume("volume".equals(id) ? UPNP_MASTER : id.replace("volume", ""), (PercentType) command);
        }
    }

    private void handleCommandMute(Command command, String id) {
        if (command instanceof RefreshType) {
            getMute("mute".equals(id) ? UPNP_MASTER : id.replace("mute", ""));
        } else if (command instanceof OnOffType) {
            setMute("mute".equals(id) ? UPNP_MASTER : id.replace("mute", ""), (OnOffType) command);
        }
    }

    private void handleCommandLoudness(Command command, String id) {
        if (command instanceof RefreshType) {
            getLoudness("loudness".equals(id) ? UPNP_MASTER : id.replace("loudness", ""));
        } else if (command instanceof OnOffType) {
            setLoudness("loudness".equals(id) ? UPNP_MASTER : id.replace("loudness", ""), (OnOffType) command);
        }
    }

    private void handleCommandStop(Command command) {
        if (OnOffType.ON.equals(command)) {
            updateState(CONTROL, PlayPauseType.PAUSE);
            stop();
            updateState(TRACK_POSITION, new QuantityType<>(0, SmartHomeUnits.SECOND));
        }
    }

    private void handleCommandControl(ChannelUID channelUID, Command command) {
        String transportState;
        if (command instanceof RefreshType) {
            transportState = this.transportState;
            State newState = UnDefType.UNDEF;
            if ("PLAYING".equals(transportState)) {
                newState = PlayPauseType.PLAY;
            } else if ("STOPPED".equals(transportState)) {
                newState = PlayPauseType.PAUSE;
            } else if ("PAUSED_PLAYBACK".equals(transportState)) {
                newState = PlayPauseType.PAUSE;
            }
            updateState(channelUID, newState);
        } else if (command instanceof PlayPauseType) {
            if (PlayPauseType.PLAY.equals(command)) {
                play();
            } else if (PlayPauseType.PAUSE.equals(command)) {
                checkPaused();
                pause();
            }
        } else if (command instanceof NextPreviousType) {
            if (NextPreviousType.NEXT.equals(command)) {
                serveNext();
            } else if (NextPreviousType.PREVIOUS.equals(command)) {
                servePrevious();
            }
        } else if (command instanceof RewindFastforwardType) {
            int pos = 0;
            if (RewindFastforwardType.FASTFORWARD.equals(command)) {
                pos = Integer.min(trackDuration, trackPosition + config.seekstep);
            } else if (command == RewindFastforwardType.REWIND) {
                pos = Integer.max(0, trackPosition - config.seekstep);
            }
            seek(String.format("%02d:%02d:%02d", pos / 3600, (pos % 3600) / 60, pos % 60));
        }
    }

    private void handleCommandRepeat(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, OnOffType.from(repeat));
        } else {
            repeat = (OnOffType.ON.equals(command));
            currentQueue.setRepeat(repeat);
            updateState(channelUID, (State) command);
        }
    }

    private void handleCommandShuffle(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, OnOffType.from(shuffle));
        } else {
            shuffle = (OnOffType.ON.equals(command));
            currentQueue.setShuffle(shuffle);
            if (!playing) {
                resetToStartQueue();
            }
            updateState(channelUID, (State) command);
        }
    }

    private void handleCommandUri(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, StringType.valueOf(nowPlayingUri));
        } else if (command instanceof StringType) {
            setCurrentURI(command.toString(), "");
            play();
        }
    }

    private void handleCommandFavoriteSelect(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            if (favoriteName.isEmpty()
                    || !favoriteStateOptionList.stream().anyMatch(o -> favoriteName.equals(o.getLabel()))) {
                updateState(channelUID, UnDefType.UNDEF);
            } else {
                updateState(channelUID, StringType.valueOf(favoriteName));
            }
        } else if (command instanceof StringType) {
            favoriteName = command.toString();
            updateState(FAVORITE, StringType.valueOf(favoriteName));
            playFavorite();
            updateState(channelUID, StringType.valueOf(favoriteName));
        }
    }

    private void handleCommandFavorite(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, StringType.valueOf(favoriteName));
        } else if (command instanceof StringType) {
            favoriteName = command.toString();
            if (favoriteStateOptionList.contains(new StateOption(favoriteName, favoriteName))) {
                updateState(FAVORITE_SELECT, StringType.valueOf(favoriteName));
                playFavorite();
            } else {
                updateState(FAVORITE_SELECT, UnDefType.UNDEF);
            }
            updateState(channelUID, StringType.valueOf(favoriteName));
        }
    }

    private void handleCommandFavoriteSave(Command command) {
        if (OnOffType.ON.equals(command) && !favoriteName.isEmpty()) {
            UpnpFavorite favorite = new UpnpFavorite(favoriteName, nowPlayingUri, currentEntry);
            favorite.saveFavorite(favoriteName, bindingConfig.path);
            updateFavoritesList();
            updateState(FAVORITE_SELECT, StringType.valueOf(favoriteName));
        }
    }

    private void handleCommandFavoriteDelete(Command command) {
        if (OnOffType.ON.equals(command) && !favoriteName.isEmpty()) {
            UpnpControlUtil.deleteFavorite(favoriteName, bindingConfig.path);
            updateFavoritesList();
            updateState(FAVORITE, UnDefType.UNDEF);
            updateState(FAVORITE_SELECT, UnDefType.UNDEF);
        }
    }

    private void handleCommandTrackPosition(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateState(channelUID, new QuantityType<>(trackPosition, SmartHomeUnits.SECOND));
        } else if (command instanceof QuantityType<?>) {
            QuantityType<?> position = ((QuantityType<?>) command).toUnit(SmartHomeUnits.SECOND);
            if (position != null) {
                int pos = Integer.min(trackDuration, position.intValue());
                seek(String.format("%02d:%02d:%02d", pos / 3600, (pos % 3600) / 60, pos % 60));
            }
        }
    }

    private void handleCommandRelTrackPosition(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            int relPosition = (trackDuration != 0) ? (trackPosition * 100) / trackDuration : 0;
            updateState(channelUID, new PercentType(relPosition));
        } else if (command instanceof PercentType) {
            int pos = ((PercentType) command).intValue() * trackDuration / 100;
            seek(String.format("%02d:%02d:%02d", pos / 3600, (pos % 3600) / 60, pos % 60));
        }
    }

    private void playFavorite() {
        UpnpFavorite favorite = new UpnpFavorite(favoriteName, bindingConfig.path);
        String uri = favorite.getUri();
        UpnpEntry entry = favorite.getUpnpEntry();
        if (!uri.isEmpty()) {
            String metadata = "";
            if (entry != null) {
                metadata = UpnpXMLParser.compileMetadataString(entry);
            }
            setCurrentURI(uri, metadata);
            play();
        }
    }

    private void updateFavoritesList() {
        favoriteStateOptionList = UpnpControlUtil.favorites(bindingConfig.path).stream()
                .map(p -> (new StateOption(p, p))).collect(Collectors.toList());
        updateStateDescription(favoriteSelectChannelUID, favoriteStateOptionList);
    }

    @Override
    public void onStatusChanged(boolean status) {
        logger.debug("Renderer status changed to {}", status);
        if (status) {
            initJob();
        } else {
            removeSubscriptions();

            updateState(CONTROL, PlayPauseType.PAUSE);
            cancelTrackPositionRefresh();

            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Communication lost with " + thing.getLabel());
        }
        super.onStatusChanged(status);
    }

    @Override
    protected @Nullable String preProcessValueReceived(Map<String, String> inputs, @Nullable String variable,
            @Nullable String value, @Nullable String service, @Nullable String action) {
        if (variable == null) {
            return null;
        } else {
            switch (variable) {
                case "CurrentVolume":
                    return (inputs.containsKey("Channel") ? inputs.get("Channel") : UPNP_MASTER) + "Volume";
                case "CurrentMute":
                    return (inputs.containsKey("Channel") ? inputs.get("Channel") : UPNP_MASTER) + "Mute";
                case "CurrentLoudness":
                    return (inputs.containsKey("Channel") ? inputs.get("Channel") : UPNP_MASTER) + "Loudness";
                default:
                    return variable;
            }
        }
    }

    @Override
    public void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service) {
        if (logger.isTraceEnabled()) {
            logger.trace("Upnp device {} received variable {} with value {} from service {}", thing.getLabel(),
                    variable, value, service);
        } else {
            if (logger.isDebugEnabled() && !("AbsTime".equals(variable) || "RelCount".equals(variable)
                    || "RelTime".equals(variable) || "AbsCount".equals(variable) || "Track".equals(variable)
                    || "TrackDuration".equals(variable))) {
                // don't log all variables received when updating the track position every second
                logger.debug("Upnp device {} received variable {} with value {} from service {}", thing.getLabel(),
                        variable, value, service);
            }
        }
        if (variable == null) {
            return;
        }

        if (variable.endsWith("Volume")) {
            onValueReceivedVolume(variable, value);
        } else if (variable.endsWith("Mute")) {
            onValueReceivedMute(variable, value);
        } else if (variable.endsWith("Loudness")) {
            onValueReceivedLoudness(variable, value);
        } else {
            switch (variable) {
                case "LastChange":
                    onValueReceivedLastChange(value, service);
                    break;
                case "CurrentTransportState":
                case "TransportState":
                    onValueReceivedTransportState(value);
                    break;
                case "CurrentTrackURI":
                case "CurrentURI":
                    onValueReceivedCurrentURI(value);
                    break;
                case "CurrentTrackMetaData":
                case "CurrentURIMetaData":
                    onValueReceivedCurrentMetaData(value);
                    break;
                case "NextAVTransportURIMetaData":
                case "NextURIMetaData":
                    onValueReceivedNextMetaData(value);
                    break;
                case "CurrentTrackDuration":
                case "TrackDuration":
                    onValueReceivedDuration(value);
                    break;
                case "RelTime":
                    onValueReceivedRelTime(value);
                    break;
                default:
                    super.onValueReceived(variable, value, service);
                    break;
            }
        }
    }

    private void onValueReceivedVolume(String variable, @Nullable String value) {
        if (!((value == null) || (value.isEmpty()))) {
            UpnpRenderingControlConfiguration config = renderingControlConfiguration;

            int volume = Integer.valueOf(value);
            if (config != null) {
                volume = volume * 100 / config.maxvolume;
            }

            String upnpChannel = variable.replace("Volume", "volume").replace("Master", "");
            updateState(upnpChannel, new PercentType(volume));
        }
    }

    private void onValueReceivedMute(String variable, @Nullable String value) {
        if (!((value == null) || (value.isEmpty()))) {
            String upnpChannel = variable.replace("Mute", "mute").replace("Master", "");
            updateState(upnpChannel,
                    ("1".equals(value) || "true".equals(value.toLowerCase())) ? OnOffType.ON : OnOffType.OFF);
        }
    }

    private void onValueReceivedLoudness(String variable, @Nullable String value) {
        if (!((value == null) || (value.isEmpty()))) {
            String upnpChannel = variable.replace("Mute", "mute").replace("Master", "");
            updateState(upnpChannel,
                    ("1".equals(value) || "true".equals(value.toLowerCase())) ? OnOffType.ON : OnOffType.OFF);
        }
    }

    private void onValueReceivedLastChange(@Nullable String value, @Nullable String service) {
        // This is returned from a GENA subscription. The jupnp library does not allow receiving new GENA subscription
        // messages as long as this thread has not finished. As we may trigger long running processes based on this
        // result, we run it in a separate thread.
        upnpScheduler.submit(() -> {
            // pre-process some variables, eg XML processing
            if (!((value == null) || value.isEmpty())) {
                if ("AVTransport".equals(service)) {
                    Map<String, String> parsedValues = UpnpXMLParser.getAVTransportFromXML(value);
                    for (Map.Entry<String, String> entrySet : parsedValues.entrySet()) {
                        switch (entrySet.getKey()) {
                            case "TransportState":
                                // Update the transport state after the update of the media information
                                // to not break the notification mechanism
                                break;
                            case "AVTransportURI":
                                onValueReceived("CurrentTrackURI", entrySet.getValue(), service);
                                break;
                            case "AVTransportURIMetaData":
                                onValueReceived("CurrentTrackMetaData", entrySet.getValue(), service);
                                break;
                            default:
                                onValueReceived(entrySet.getKey(), entrySet.getValue(), service);
                        }
                    }
                    if (parsedValues.containsKey("TransportState")) {
                        onValueReceived("TransportState", parsedValues.get("TransportState"), service);
                    }
                } else if ("RenderingControl".equals(service)) {
                    Map<String, @Nullable String> parsedValues = UpnpXMLParser.getRenderingControlFromXML(value);
                    for (String parsedValue : parsedValues.keySet()) {
                        onValueReceived(parsedValue, parsedValues.get(parsedValue), "RenderingControl");
                    }
                }
            }
        });
    }

    private void onValueReceivedTransportState(@Nullable String value) {
        transportState = (value == null) ? "" : value;
        if ("STOPPED".equals(value)) {
            cancelCheckPaused();
            updateState(CONTROL, PlayPauseType.PAUSE);
            cancelTrackPositionRefresh();
            // Only go to next for first STOP command, then wait until we received PLAYING before moving
            // to next (avoids issues with renderers sending multiple stop states)
            if (playing) {
                // playerStopped is true if stop came from openHAB. This allows us to identify if we played to the
                // end of an entry, because STOP would come from the player and not from openHAB. We should then
                // move to the next entry if the queue is not at the end already.
                if (!playerStopped) {
                    if (Instant.now().toEpochMilli() >= expectedTrackend) {
                        // If we are receiving track duration info, we know when the track is expected to end. If we
                        // received STOP before track end, and it is not coming from openHAB, it must have been stopped
                        // from the renderer directly, and we do not want to play the next entry.
                        if (playingQueue) {
                            serveNext();
                        }
                    }
                }
            }
            playing = false;
        } else if ("PLAYING".equals(value)) {
            playerStopped = false;
            playing = true;
            updateState(CONTROL, PlayPauseType.PLAY);
            scheduleTrackPositionRefresh();
        } else if ("PAUSED_PLAYBACK".equals(value)) {
            cancelCheckPaused();
            updateState(CONTROL, PlayPauseType.PAUSE);
        }
    }

    private void onValueReceivedCurrentURI(@Nullable String value) {
        CompletableFuture<Boolean> settingURI = isSettingURI;
        if (settingURI != null) {
            settingURI.complete(true); // We have received current URI, so can allow play to start
        }

        UpnpEntry current = currentEntry;
        UpnpEntry next = nextEntry;

        String uri = "";
        String currentUri = "";
        String nextUri = "";
        try {
            if (value != null) {
                uri = URLDecoder.decode(value.trim(), StandardCharsets.UTF_8.name());
            }
            if (current != null) {
                currentUri = URLDecoder.decode(current.getRes().trim(), StandardCharsets.UTF_8.name());
            }
            if (next != null) {
                nextUri = URLDecoder.decode(next.getRes(), StandardCharsets.UTF_8.name());
            }
        } catch (UnsupportedEncodingException ignore) {
            // If not valid current URI, we assume there is none
        }

        nowPlayingUri = uri;
        updateState(URI, StringType.valueOf(uri));

        if (!uri.equals(currentUri)) {
            if (uri.equals(nextUri) && (next != null)) {
                // Renderer advanced to next entry independent of openHAB UPnP control point.
                // Advance in the queue to keep proper position status.
                // Make the next entry available to renderers that support it.
                logger.trace("Renderer moved from '{}' to next entry '{}' in queue", current, next);
                currentEntry = currentQueue.next();
                nextEntry = currentQueue.get(currentQueue.nextIndex());
                logger.trace("Auto move forward, current queue index: {}", currentQueue.index());

                updateMetaDataState(next);

                // look one further to get next entry for next URI
                next = nextEntry;
                if (next != null) {
                    setNextURI(next.getRes(), UpnpXMLParser.compileMetadataString(next));
                }
            } else {
                // A new entry is being served that does not match the next entry in the queue. This can be because a
                // sound or stream is being played through an action, or another control point started a new entry.
                // We should clear the metadata in this case and wait for new metadata to arrive.
                playingQueue = false;
                clearMetaDataState();
            }
        }
    }

    private void onValueReceivedCurrentMetaData(@Nullable String value) {
        if (!((value == null) || (value.isEmpty()))) {
            List<UpnpEntry> list = UpnpXMLParser.getEntriesFromXML(value);
            if (!list.isEmpty()) {
                updateMetaDataState(list.get(0));
                return;
            }
        }
        clearMetaDataState();
    }

    private void onValueReceivedNextMetaData(@Nullable String value) {
        if (!((value == null) || (value.isEmpty() || "NOT_IMPLEMENTED".equals(value)))) {
            List<UpnpEntry> list = UpnpXMLParser.getEntriesFromXML(value);
            if (!list.isEmpty()) {
                nextEntry = list.get(0);
            }
        }
    }

    private void onValueReceivedDuration(@Nullable String value) {
        // track duration and track position have format H+:MM:SS[.F+] or H+:MM:SS[.F0/F1]. We are not
        // interested in the fractional seconds, so drop everything after . and calculate in seconds.
        if ((value == null) || ("NOT_IMPLEMENTED".equals(value))) {
            trackDuration = 0;
            updateState(TRACK_DURATION, UnDefType.UNDEF);
            updateState(REL_TRACK_POSITION, UnDefType.UNDEF);
        } else {
            trackDuration = Arrays.stream(value.split("\\.")[0].split(":")).mapToInt(n -> Integer.parseInt(n)).reduce(0,
                    (n, m) -> n * 60 + m);
            updateState(TRACK_DURATION, new QuantityType<>(trackDuration, SmartHomeUnits.SECOND));
        }
        setExpectedTrackend();
    }

    private void onValueReceivedRelTime(@Nullable String value) {
        if ((value == null) || ("NOT_IMPLEMENTED".equals(value))) {
            trackPosition = 0;
            updateState(TRACK_POSITION, UnDefType.UNDEF);
            updateState(REL_TRACK_POSITION, UnDefType.UNDEF);
        } else {
            trackPosition = Arrays.stream(value.split("\\.")[0].split(":")).mapToInt(n -> Integer.parseInt(n)).reduce(0,
                    (n, m) -> n * 60 + m);
            updateState(TRACK_POSITION, new QuantityType<>(trackPosition, SmartHomeUnits.SECOND));
            int relPosition = (trackDuration != 0) ? trackPosition * 100 / trackDuration : 0;
            updateState(REL_TRACK_POSITION, new PercentType(relPosition));
        }
        setExpectedTrackend();
    }

    @Override
    protected void updateProtocolInfo(String value) {
        sink.clear();
        supportedAudioFormats.clear();
        audioSupport = false;

        sink.addAll(Arrays.asList(value.split(",")));

        for (String protocol : sink) {
            Matcher matcher = PROTOCOL_PATTERN.matcher(protocol);
            if (matcher.find()) {
                String format = matcher.group(1);
                switch (format) {
                    case "audio/mpeg3":
                    case "audio/mp3":
                    case "audio/mpeg":
                        supportedAudioFormats.add(AudioFormat.MP3);
                        break;
                    case "audio/wav":
                    case "audio/wave":
                        supportedAudioFormats.add(AudioFormat.WAV);
                        break;
                }
                audioSupport = audioSupport || Pattern.matches("audio.*", format);
            }
        }

        if (audioSupport) {
            logger.debug("Device {} supports audio", thing.getLabel());
            registerAudioSink();
        }
    }

    private void clearCurrentEntry() {
        clearMetaDataState();

        trackDuration = 0;
        updateState(TRACK_DURATION, UnDefType.UNDEF);
        trackPosition = 0;
        updateState(TRACK_POSITION, UnDefType.UNDEF);
        updateState(REL_TRACK_POSITION, UnDefType.UNDEF);

        currentEntry = null;
    }

    /**
     * Register a new queue with media entries to the renderer. Set the next position at the first entry in the list.
     * If the renderer is currently playing, set the first entry in the list as the next media. If not playing, set it
     * as current media.
     *
     * @param queue
     */
    public void registerQueue(UpnpEntryQueue queue) {
        logger.debug("Registering queue on renderer {}", thing.getLabel());
        currentQueue = queue;
        currentQueue.setRepeat(repeat);
        currentQueue.setShuffle(shuffle);
        if (playingQueue) {
            nextEntry = currentQueue.get(currentQueue.nextIndex());
            UpnpEntry next = nextEntry;
            if (next != null) {
                // make the next entry available to renderers that support it
                logger.trace("Still playing, set new queue as next entry");
                setNextURI(next.getRes(), UpnpXMLParser.compileMetadataString(next));
            }
        } else {
            resetToStartQueue();
        }
    }

    /**
     * Move to next position in queue and start playing.
     */
    private void serveNext() {
        if (currentQueue.hasNext()) {
            currentEntry = currentQueue.next();
            nextEntry = currentQueue.get(currentQueue.nextIndex());
            logger.trace("Serve next, current queue index: {}", currentQueue.index());
            logger.debug("Serve next media '{}' from queue on renderer {}", currentEntry, thing.getLabel());

            serve();
        } else {
            logger.debug("Cannot serve next, end of queue on renderer {}", thing.getLabel());
            resetToStartQueue();
        }
    }

    /**
     * Move to previous position in queue and start playing.
     */
    private void servePrevious() {
        if (currentQueue.hasPrevious()) {
            currentEntry = currentQueue.previous();
            nextEntry = currentQueue.get(currentQueue.nextIndex());
            logger.trace("Serve previous, current queue index: {}", currentQueue.index());
            logger.debug("Serve previous media '{}' from queue on renderer {}", currentEntry, thing.getLabel());

            serve();
        } else {
            logger.debug("Cannot serve previous, already at start of queue on renderer {}", thing.getLabel());
            resetToStartQueue();
        }
    }

    private void resetToStartQueue() {
        playing = false;
        stop();

        currentQueue.resetIndex(); // reset to beginning of queue
        currentEntry = currentQueue.next();
        nextEntry = currentQueue.get(currentQueue.nextIndex());
        logger.trace("Reset queue, current queue index: {}", currentQueue.index());
        UpnpEntry entry = currentEntry;
        if (entry != null) {
            updateMetaDataState(entry);
            setCurrentURI(entry.getRes(), UpnpXMLParser.compileMetadataString(entry));
        } else {
            clearCurrentEntry();
        }
    }

    /**
     * Play media.
     *
     * @param media
     */
    private void serve() {
        UpnpEntry entry = currentEntry;
        if (entry != null) {
            updateMetaDataState(entry);
            String res = entry.getRes();
            if (res.isEmpty()) {
                logger.debug("Cannot serve media '{}', no URI", currentEntry);
                playingQueue = true;
                return;
            }
            setCurrentURI(res, UpnpXMLParser.compileMetadataString(entry));

            if (playingQueue || playing) {
                logger.trace("Ready to play '{}' from queue", currentEntry);

                trackDuration = 0;
                trackPosition = 0;
                expectedTrackend = 0;
                play();

                // make the next entry available to renderers that support it
                UpnpEntry next = nextEntry;
                if (next != null) {
                    setNextURI(next.getRes(), UpnpXMLParser.compileMetadataString(next));
                }
            }

            playingQueue = true;
        }
    }

    /**
     * Called before handling a pause CONTROL command. If we do not received PAUSED_PLAYBACK or STOPPED back within
     * timeout, we will revert to playing state. This takes care of renderers that cannot pause playback.
     */
    private void checkPaused() {
        paused = upnpScheduler.schedule(this::resetPaused, UPNP_RESPONSE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void resetPaused() {
        updateState(CONTROL, PlayPauseType.PLAY);
    }

    private void cancelCheckPaused() {
        ScheduledFuture<?> future = paused;
        if (future != null) {
            future.cancel(true);
            paused = null;
        }
    }

    protected void setExpectedTrackend() {
        expectedTrackend = Instant.now().toEpochMilli() + (trackDuration - trackPosition) * 1000
                - UPNP_RESPONSE_TIMEOUT_MILLIS;
    }

    /**
     * Update the current track position every second if the channel is linked.
     */
    private void scheduleTrackPositionRefresh() {
        cancelTrackPositionRefresh();
        if (!(isLinked(TRACK_POSITION) || isLinked(REL_TRACK_POSITION))) {
            // only get it once, so we can use the track end to correctly identify STOP pressed directly on renderer
            getPositionInfo();
        } else {
            if (trackPositionRefresh == null) {
                trackPositionRefresh = upnpScheduler.scheduleWithFixedDelay(this::getPositionInfo, 1, 1,
                        TimeUnit.SECONDS);
            }
        }
    }

    private void cancelTrackPositionRefresh() {
        ScheduledFuture<?> refresh = trackPositionRefresh;

        if (refresh != null) {
            refresh.cancel(true);
        }
        trackPositionRefresh = null;

        trackPosition = 0;
        updateState(TRACK_POSITION, new QuantityType<>(trackPosition, SmartHomeUnits.SECOND));
        int relPosition = (trackDuration != 0) ? trackPosition / trackDuration : 0;
        updateState(REL_TRACK_POSITION, new PercentType(relPosition));
    }

    /**
     * Update metadata channels for media with data received from the Media Server or AV Transport.
     *
     * @param media
     */
    private void updateMetaDataState(UpnpEntry media) {
        // We don't want to update metadata if the metadata from the AVTransport is less complete than in the current
        // entry.
        boolean isCurrent = false;
        UpnpEntry entry = null;
        if (playingQueue) {
            entry = currentEntry;
        }
        String mediaRes = media.getRes();
        String entryRes = (entry != null) ? entry.getRes() : "";

        try {
            String mediaUrl = URLDecoder.decode(mediaRes, StandardCharsets.UTF_8.name());
            String entryUrl = URLDecoder.decode(entryRes, StandardCharsets.UTF_8.name());
            isCurrent = mediaUrl.equals(entryUrl);
        } catch (UnsupportedEncodingException e) {
            logger.debug("Unsupported encoding for new {} or current {} res URL, trying string compare", mediaRes,
                    entryRes);
            isCurrent = mediaRes.equals(entryRes);
        }

        logger.trace("Media ID: {}", media.getId());
        logger.trace("Current queue res: {}", entryRes);
        logger.trace("Updated media res: {}", mediaRes);
        logger.trace("Received meta data is for current entry: {}", isCurrent);

        if (!(isCurrent && media.getTitle().isEmpty())) {
            updateState(TITLE, StringType.valueOf(media.getTitle()));
        }
        if (!(isCurrent && (media.getAlbum().isEmpty() || media.getAlbum().matches("Unknown.*")))) {
            updateState(ALBUM, StringType.valueOf(media.getAlbum()));
        }
        if (!(isCurrent
                && (media.getAlbumArtUri().isEmpty() || media.getAlbumArtUri().contains("DefaultAlbumCover")))) {
            if (media.getAlbumArtUri().isEmpty() || media.getAlbumArtUri().contains("DefaultAlbumCover")) {
                updateState(ALBUM_ART, UnDefType.UNDEF);
            } else {
                State albumArt = HttpUtil.downloadImage(media.getAlbumArtUri());
                if (albumArt == null) {
                    logger.debug("Failed to download the content of album art from URL {}", media.getAlbumArtUri());
                    if (!isCurrent) {
                        updateState(ALBUM_ART, UnDefType.UNDEF);
                    }
                } else {
                    updateState(ALBUM_ART, albumArt);
                }
            }
        }
        if (!(isCurrent && (media.getCreator().isEmpty() || media.getCreator().matches("Unknown.*")))) {
            updateState(CREATOR, StringType.valueOf(media.getCreator()));
        }
        if (!(isCurrent && (media.getArtist().isEmpty() || media.getArtist().matches("Unknown.*")))) {
            updateState(ARTIST, StringType.valueOf(media.getArtist()));
        }
        if (!(isCurrent && (media.getPublisher().isEmpty() || media.getPublisher().matches("Unknown.*")))) {
            updateState(PUBLISHER, StringType.valueOf(media.getPublisher()));
        }
        if (!(isCurrent && (media.getGenre().isEmpty() || media.getGenre().matches("Unknown.*")))) {
            updateState(GENRE, StringType.valueOf(media.getGenre()));
        }
        if (!(isCurrent && (media.getOriginalTrackNumber() == null))) {
            Integer trackNumber = media.getOriginalTrackNumber();
            State trackNumberState = (trackNumber != null) ? new DecimalType(trackNumber) : UnDefType.UNDEF;
            updateState(TRACK_NUMBER, trackNumberState);
        }
    }

    private void clearMetaDataState() {
        updateState(TITLE, UnDefType.UNDEF);
        updateState(ALBUM, UnDefType.UNDEF);
        updateState(ALBUM_ART, UnDefType.UNDEF);
        updateState(CREATOR, UnDefType.UNDEF);
        updateState(ARTIST, UnDefType.UNDEF);
        updateState(PUBLISHER, UnDefType.UNDEF);
        updateState(GENRE, UnDefType.UNDEF);
        updateState(TRACK_NUMBER, UnDefType.UNDEF);
    }

    /**
     * @return Audio formats supported by the renderer.
     */
    public Set<AudioFormat> getSupportedAudioFormats() {
        return supportedAudioFormats;
    }

    private void registerAudioSink() {
        if (audioSinkRegistered) {
            logger.debug("Audio Sink already registered for renderer {}", thing.getLabel());
            return;
        } else if (!service.isRegistered(this)) {
            logger.debug("Audio Sink registration for renderer {} failed, no service", thing.getLabel());
            return;
        }
        logger.debug("Registering Audio Sink for renderer {}", thing.getLabel());
        audioSinkReg.registerAudioSink(this);
        audioSinkRegistered = true;
    }

    /**
     * @return UPnP sink definitions supported by the renderer.
     */
    protected List<String> getSink() {
        return sink;
    }
}
