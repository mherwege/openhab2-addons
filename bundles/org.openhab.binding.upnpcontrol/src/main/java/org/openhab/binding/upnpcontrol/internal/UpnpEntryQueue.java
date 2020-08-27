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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The class {@link UpnpEntryQueue} represents a queue of UPnP media entries to be played on a renderer. It keeps track
 * of a current index in the queue. It has convenience methods to play previous/next entries, whereby the queue can be
 * organized to play from first to last (with no repetition), to restart at the start when the end is reached (in a
 * continuous loop), or to random shuffle the entries. Repeat and shuffle are off by default, but can be set using the
 * {@link setRepeat} and {@link setShuffle} methods.
 *
 * @author Mark Herwege - Initial contribution
 *
 */
@NonNullByDefault
public class UpnpEntryQueue {

    private boolean repeat = false;
    private boolean shuffle = false;

    private int currentIndex = -1;

    private volatile List<UpnpEntry> currentQueue = Collections.synchronizedList(new ArrayList<>());
    private volatile List<UpnpEntry> shuffledQueue = Collections.synchronizedList(new ArrayList<>());

    public UpnpEntryQueue() {
    }

    public UpnpEntryQueue(ArrayList<UpnpEntry> queue) {
        currentQueue = queue;
    }

    /**
     * Switch on/off repeat mode.
     *
     * @param repeat
     */
    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    /**
     * Switch on/off shuffle mode.
     *
     * @param shuffle
     */
    public void setShuffle(boolean shuffle) {
        this.shuffle = shuffle;
        if (shuffle) {
            shuffledQueue = new ArrayList<UpnpEntry>(currentQueue);
            Collections.shuffle(shuffledQueue);
            int index = currentIndex;
            if (index != -1) {
                currentIndex = shuffledQueue.indexOf(currentQueue.get(index));
            }
        } else {
            int index = currentIndex;
            if (index != -1) {
                currentIndex = currentQueue.indexOf(shuffledQueue.get(index));
            }
        }
    }

    /**
     * @return will return the next element in the queue, or null when the end of the queue has been reached. With
     *         repeat set, will restart at the beginning of the queue when the end has been reached. The method will
     *         return null if the queue is empty.
     */
    public synchronized @Nullable UpnpEntry next() {
        currentIndex++;
        if (currentIndex >= size()) {
            currentIndex = repeat ? 0 : -1;
        }
        return currentIndex >= 0 ? get(currentIndex) : null;
    }

    /**
     * @return will return the previous element in the queue, or null when the start of the queue has been reached. With
     *         repeat set, will restart at the end of the queue when the start has been reached. The method will return
     *         null if the queue is empty.
     */
    public synchronized @Nullable UpnpEntry previous() {
        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = repeat ? size() - 1 : -1;
        }
        return currentIndex >= 0 ? get(currentIndex) : null;
    }

    /**
     * @return the index of the next element in the queue that will be served if {@link next} is called, or -1 if
     *         nothing to serve for next.
     */
    public synchronized int nextIndex() {
        int index = currentIndex + 1;
        if (index >= size()) {
            index = repeat ? 0 : -1;
        }
        return index;
    }

    /**
     * @return the index of the previous element in the queue that will be served if {@link previous} is called, or -1
     *         if nothing to serve for next.
     */
    public synchronized int previousIndex() {
        int index = currentIndex - 1;
        if (index < 0) {
            index = repeat ? size() - 1 : -1;
        }
        return index;
    }

    /**
     * @return true if there is an element to server when calling {@link next}.
     */
    public synchronized boolean hasNext() {
        int size = currentQueue.size();
        if (repeat && (size > 0)) {
            return true;
        }
        return (currentIndex < (size - 1));
    }

    /**
     * @return true if there is an element to server when calling {@link previous}.
     */
    public synchronized boolean hasPrevious() {
        int size = currentQueue.size();
        if (repeat && (size > 0)) {
            return true;
        }
        return (currentIndex > 0);
    }

    /**
     * @param index
     * @return the UpnpEntry at the index position in the queue, or null when none can be retrieved.
     */
    public @Nullable synchronized UpnpEntry get(int index) {
        if ((index >= 0) && (index < size())) {
            if (shuffle) {
                return shuffledQueue.get(index);
            } else {
                return currentQueue.get(index);
            }
        } else {
            return null;
        }
    }

    /**
     * Reset the queue position indexes to the the start of the queue.
     */
    public synchronized void resetIndex() {
        currentIndex = -1;
    }

    /**
     * @return number of element in the queue.
     */
    public synchronized int size() {
        return currentQueue.size();
    }

    /**
     * @return true if the queue is empty.
     */
    public synchronized boolean isEmpty() {
        return currentQueue.isEmpty();
    }
}
