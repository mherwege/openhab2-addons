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
package org.openhab.binding.upnpcontrol.internal;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Mark Herwege - Initial contribution
 *
 */
@NonNullByDefault
public enum UpnpChannelName {

    // Volume channels
    LF_VOLUME("lfvolume", "Left Front Volume", "Left front volume, will be left volume with stereo sound", "Dimmer",
            "SoundVolume", false, "LF"),
    RF_VOLUME("rfvolume", "Right Front Volume", "Right front volume, will be left volume with stereo sound", "Dimmer",
            "SoundVolume", false, "RF"),
    CF_VOLUME("cfvolume", "Center Front Volume", "Center front volume", "Dimmer", "SoundVolume", false, "CF"),
    LFE_VOLUME("lfevolume", "Frequency Enhancement Volume", "Low frequency enhancement volume (subwoofer)", "Dimmer",
            "SoundVolume", false, "LFE"),
    LS_VOLUME("lsvolume", "Left Surround Volume", "Left surround volume", "Dimmer", "SoundVolume", false, "LS"),
    RS_VOLUME("rsvolume", "Right Surround Volume", "Right surround volume", "Dimmer", "SoundVolume", false, "RS"),
    LFC_VOLUME("lfcvolume", "Left of Center Volume", "Left of center (in front) volume", "Dimmer", "SoundVolume", false,
            "LFC"),
    RFC_VOLUME("rfcvolume", "Right of Center Volume", "Right of center (in front) volume", "Dimmer", "SoundVolume",
            false, "RFC"),
    SD_VOLUME("sdvolume", "Surround Volume", "Surround (rear) volume", "Dimmer", "SoundVolume", false, "SD"),
    SL_VOLUME("slvolume", "Side Left Volume", "Side left (left wall) volume", "Dimmer", "SoundVolume", false, "SL"),
    SR_VOLUME("srvolume", "Side Right Volume", "Side right (right wall) volume", "Dimmer", "SoundVolume", false, "SR"),
    T_VOLUME("tvolume", "Top Volume", "Top (overhead) volume", "Dimmer", "SoundVolume", false, "T"),
    B_VOLUME("bvolume", "Bottom Volume", "Bottom volume", "Dimmer", "SoundVolume", false, "B"),
    BC_VOLUME("bcvolume", "Back Center Volume", "Back center volume", "Dimmer", "SoundVolume", false, "BC"),
    BL_VOLUME("blvolume", "Back Left Volume", "Back Left Volume", "Dimmer", "SoundVolume", false, "BL"),
    BR_VOLUME("brvolume", "Back Right Volume", "Back right volume", "Dimmer", "SoundVolume", false, "BR"),

    // Mute channels
    LF_MUTE("lfmute", "Left Front Mute", "Left front mute, will be left mute with stereo sound", "Switch",
            "SoundVolume", false, "LF"),
    RF_MUTE("rfmute", "Right Front Mute", "Right front mute, will be left mute with stereo sound", "Switch",
            "SoundVolume", false, "RF"),
    CF_MUTE("cfmute", "Center Front Mute", "Center front mute", "Switch", "SoundVolume", false, "CF"),
    LFE_MUTE("lfemute", "Frequency Enhancement Mute", "Low frequency enhancement mute (subwoofer)", "Switch",
            "SoundVolume", false, "LFE"),
    LS_MUTE("lsmute", "Left Surround Mute", "Left surround mute", "Switch", "SoundVolume", false, "LS"),
    RS_MUTE("rsmute", "Right Surround Mute", "Right surround mute", "Switch", "SoundVolume", false, "RS"),
    LFC_MUTE("lfcmute", "Left of Center Mute", "Left of center (in front) mute", "Switch", "SoundVolume", false, "LFC"),
    RFC_MUTE("rfcmute", "Right of Center Mute", "Right of center (in front) mute", "Switch", "SoundVolume", false,
            "RFC"),
    SD_MUTE("sdmute", "Surround Mute", "Surround (rear) mute", "Switch", "SoundVolume", false, "SD"),
    SL_MUTE("slmute", "Side Left Mute", "Side left (left wall) mute", "Switch", "SoundVolume", false, "SL"),
    SR_MUTE("srmute", "Side Right Mute", "Side right (right wall) mute", "Switch", "SoundVolume", false, "SR"),
    T_MUTE("tmute", "Top Mute", "Top (overhead) mute", "Switch", "SoundVolume", false, "T"),
    B_MUTE("bmute", "Bottom Mute", "Bottom mute", "Switch", "SoundVolume", false, "B"),
    BC_MUTE("bcmute", "Back Center Mute", "Back center mute", "Switch", "SoundVolume", false, "BC"),
    BL_MUTE("blmute", "Back Left Mute", "Back Left Mute", "Switch", "SoundVolume", false, "BL"),
    BR_MUTE("brmute", "Back Right Mute", "Back right mute", "Switch", "SoundVolume", false, "BR"),

    // Loudness channels
    LOUDNESS("loudness", "Loudness", "Master loudness", "Switch", "SoundVolume", false, "Master"),
    LF_LOUDNESS("lfloudness", "Left Front Loudness", "Left front loudness, will be left loudness with stereo sound",
            "Switch", "SoundVolume", false, "LF"),
    RF_LOUDNESS("rfloudness", "Right Front Loudness", "Right front loudness, will be left loudness with stereo sound",
            "Switch", "SoundVolume", false, "RF"),
    CF_LOUDNESS("cfloudness", "Center Front Loudness", "Center front loudness", "Switch", "SoundVolume", false, "CF"),
    LFE_LOUDNESS("lfeloudness", "Frequency Enhancement Loudness", "Low frequency enhancement loudness (subwoofer)",
            "Switch", "SoundVolume", false, "LFE"),
    LS_LOUDNESS("lsloudness", "Left Surround Loudness", "Left surround loudness", "Switch", "SoundVolume", false, "LS"),
    RS_LOUDNESS("rsloudness", "Right Surround Loudness", "Right surround loudness", "Switch", "SoundVolume", false,
            "RS"),
    LFC_LOUDNESS("lfcloudness", "Left of Center Loudness", "Left of center (in front) loudness", "Switch",
            "SoundVolume", false, "LFC"),
    RFC_LOUDNESS("rfcloudness", "Right of Center Loudness", "Right of center (in front) loudness", "Switch",
            "SoundVolume", false, "RFC"),
    SD_LOUDNESS("sdloudness", "Surround Loudness", "Surround (rear) loudness", "Switch", "SoundVolume", false, "SD"),
    SL_LOUDNESS("slloudness", "Side Left Loudness", "Side left (left wall) loudness", "Switch", "SoundVolume", false,
            "SL"),
    SR_LOUDNESS("srloudness", "Side Right Loudness", "Side right (right wall) loudness", "Switch", "SoundVolume", false,
            "SR"),
    T_LOUDNESS("tloudness", "Top Loudness", "Top (overhead) loudness", "Switch", "SoundVolume", false, "T"),
    B_LOUDNESS("bloudness", "Bottom Loudness", "Bottom loudness", "Switch", "SoundVolume", false, "B"),
    BC_LOUDNESS("bcloudness", "Back Center Loudness", "Back center loudness", "Switch", "SoundVolume", false, "BC"),
    BL_LOUDNESS("blloudness", "Back Left Loudness", "Back Left Loudness", "Switch", "SoundVolume", false, "BL"),
    BR_LOUDNESS("brloudness", "Back Right Loudness", "Back right loudness", "Switch", "SoundVolume", false, "BR");

    private static final Map<String, UpnpChannelName> UPNP_CHANNEL_NAME_MAP = Stream.of(UpnpChannelName.values())
            .collect(Collectors.toMap(UpnpChannelName::getChannelId, Function.identity()));
    private static final Map<String, UpnpChannelName> UPNP_VOLUME_CHANNEL_DESCRIPTOR_MAP = Stream
            .of(UpnpChannelName.values()).filter(UpnpChannelName::isVolumeChannel)
            .collect(Collectors.toMap(UpnpChannelName::getChannelDescriptor, Function.identity()));
    private static final Map<String, UpnpChannelName> UPNP_MUTE_CHANNEL_DESCRIPTOR_MAP = Stream
            .of(UpnpChannelName.values()).filter(UpnpChannelName::isMuteChannel)
            .collect(Collectors.toMap(UpnpChannelName::getChannelDescriptor, Function.identity()));
    private static final Map<String, UpnpChannelName> UPNP_LOUDNESS_CHANNEL_DESCRIPTOR_MAP = Stream
            .of(UpnpChannelName.values()).filter(UpnpChannelName::isLoudnessChannel)
            .collect(Collectors.toMap(UpnpChannelName::getChannelDescriptor, Function.identity()));

    private final String channelId;
    private final String label;
    private final String description;
    private final String itemType;
    private final String category;
    private final boolean advanced;
    private final String channelDescriptor;

    UpnpChannelName(final String channelId, final String label, final String description, final String itemType,
            final String category, final boolean advanced, final String channelDescriptor) {
        this.channelId = channelId;
        this.label = label;
        this.description = description;
        this.itemType = itemType;
        this.category = category;
        this.advanced = advanced;
        this.channelDescriptor = channelDescriptor;
    }

    /**
     * @return The name of the Channel
     */
    public String getChannelId() {
        return channelId;
    }

    /**
     * @return The label for the Channel Type
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return The description for the Channel Type
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The item type for the Channel
     */
    public String getItemType() {
        return itemType;
    }

    /**
     * @return The category for the Channel Type
     */
    public String getCategory() {
        return category;
    }

    /**
     * @return If the Channel Type is advanced
     */
    public boolean isAdvanced() {
        return advanced;
    }

    /**
     * @return The UPnP channel descriptor of the Channel
     */
    public String getChannelDescriptor() {
        return channelDescriptor;
    }

    /**
     * Returns the UPnP Channel enum for the given channel id or null if there is no enum available for the given
     * channel.
     *
     * @param channelId Channel to find
     * @return The UPnP Channel enum or null if there is none.
     */
    public static @Nullable UpnpChannelName channelIdToUpnpChannelName(final String channelId) {
        return UPNP_CHANNEL_NAME_MAP.get(channelId);
    }

    public static @Nullable UpnpChannelName volumeChannelId(final String upnpChannelDescriptor) {
        return UPNP_VOLUME_CHANNEL_DESCRIPTOR_MAP.get(upnpChannelDescriptor);
    }

    public static @Nullable UpnpChannelName muteChannelId(final String upnpChannelDescriptor) {
        return UPNP_MUTE_CHANNEL_DESCRIPTOR_MAP.get(upnpChannelDescriptor);
    }

    public static @Nullable UpnpChannelName loudnessChannelId(final String upnpChannelDescriptor) {
        return UPNP_LOUDNESS_CHANNEL_DESCRIPTOR_MAP.get(upnpChannelDescriptor);
    }

    public boolean isVolumeChannel() {
        return getChannelId().endsWith("volume");
    }

    public boolean isMuteChannel() {
        return getChannelId().endsWith("mute");
    }

    public boolean isLoudnessChannel() {
        return getChannelId().endsWith("loudness");
    }

}
