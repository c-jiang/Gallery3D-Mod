/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.gallery3d.data;

// Concatenate multiple media sets into one.
// This only handles SubMediaSets, not MediaItems. (That's all we need now)
public class ComboAlbumSet extends MediaSet implements MediaSet.MediaSetListener {
    private static final String TAG = "ComboAlbumSet";
    private final MediaSet[] mSets;
    private long mUniqueId;

    public ComboAlbumSet(long uniqueId, MediaSet ... mediaSets) {
        mUniqueId = uniqueId;
        mSets = mediaSets;
        for (MediaSet set : mSets) {
            set.setContentListener(this);
        }
    }

    public long getId() {
        return mUniqueId;
    }

    public MediaSet getSubMediaSet(int index) {
        for (MediaSet set : mSets) {
            int size = set.getSubMediaSetCount();
            if (index < size) {
                return set.getSubMediaSet(index);
            }
            index -= size;
        }
        throw new IndexOutOfBoundsException();
    }

    public int getSubMediaSetCount() {
        int count = 0;
        for (MediaSet set : mSets) {
            count += set.getSubMediaSetCount();
        }
        return count;
    }

    public String getName() {
        return TAG;
    }

    public int getTotalMediaItemCount() {
        int count = 0;
        for (MediaSet set : mSets) {
            count += set.getTotalMediaItemCount();
        }
        return count;
    }

    public void reload() {
        for (MediaSet set : mSets) {
            set.reload();
        }
    }

    public void onContentChanged() {
        if (mListener != null) {
            mListener.onContentChanged();
        }
    }
}