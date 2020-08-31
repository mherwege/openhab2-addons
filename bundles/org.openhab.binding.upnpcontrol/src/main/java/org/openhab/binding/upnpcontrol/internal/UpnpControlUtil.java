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

import static org.openhab.binding.upnpcontrol.internal.UpnpControlBindingConstants.*;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * Class with some static utility methods for the upnpcontrol binding.
 *
 * @author Mark Herwege - Initial contribution
 *
 */
@NonNullByDefault
public final class UpnpControlUtil {

    /**
     * Get names of saved playlists.
     *
     * @param path of playlist directory
     * @return playlists
     */
    public static List<String> playlists(@Nullable String path) {
        return list(path, PLAYLIST_FILE_EXTENSION);
    }

    /**
     * Delete a saved playlist.
     *
     * @param name of playlist to delete
     * @param path of playlist directory
     */
    public static void deletePlaylist(String name, @Nullable String path) {
        delete(name, path, PLAYLIST_FILE_EXTENSION);
    }

    /**
     * Get names of saved favorites.
     *
     * @param path of favorite directory
     * @return favorites
     */
    public static List<String> favorites(@Nullable String path) {
        return list(path, FAVORITE_FILE_EXTENSION);
    }

    /**
     * Delete a saved favorite.
     *
     * @param name of favorite to delete
     * @param path of favorite directory
     */
    public static void deleteFavorite(String name, @Nullable String path) {
        delete(name, path, FAVORITE_FILE_EXTENSION);
    }

    private static List<String> list(@Nullable String path, String extension) {
        if (path == null) {
            return Collections.emptyList();
        }

        File directory = new File(path);
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(extension));
        List<String> result = (Arrays.asList(files)).stream().map(p -> p.getName().replace(extension, ""))
                .collect(Collectors.toList());
        return result;
    }

    public static void delete(String name, @Nullable String path, String extension) {
        if (path == null) {
            return;
        }

        File file = new File(path + name + extension);
        file.delete();
    }
}
