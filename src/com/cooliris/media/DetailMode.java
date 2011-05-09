/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.cooliris.media;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.io.File;

import android.content.Context;
import android.content.res.Resources;
import android.media.ExifInterface;

import com.cooliris.app.App;
import com.cooliris.app.Res;

import com.drew.metadata.*;
import com.drew.metadata.exif.*;
import com.drew.imaging.jpeg.*;

public final class DetailMode {
    public static CharSequence[] populateDetailModeStrings(Context context, ArrayList<MediaBucket> buckets) {
        int numBuckets = buckets.size();
        if (MediaBucketList.isSetSelection(buckets) && numBuckets == 1) {
            // If just 1 set was selected, save the trouble of processing the
            // items in the set again.
            // We have already processed details for that set.
            return populateSetViewDetailModeStrings(context, MediaBucketList.getFirstSetSelection(buckets), 1);
        } else if (MediaBucketList.isSetSelection(buckets) || MediaBucketList.isMultipleItemSelection(buckets)) {
            // Cycle through the items and add them to the selection items set.
            MediaSet selectedItemsSet = new MediaSet();
            for (int i = 0; i < numBuckets; i++) {
                MediaBucket bucket = buckets.get(i);
                ArrayList<MediaItem> currItems = null;
                int numCurrItems = 0;
                if (MediaBucketList.isSetSelection(bucket)) {
                    MediaSet currSet = bucket.mediaSet;
                    if (currSet != null) {
                        currItems = currSet.getItems();
                        numCurrItems = currSet.getNumItems();
                    }
                } else {
                    currItems = bucket.mediaItems;
                    numCurrItems = currItems.size();
                }
                if (currItems != null) {
                    for (int j = 0; j < numCurrItems; j++) {
                        selectedItemsSet.addItem(currItems.get(j));
                    }
                }
            }
            return populateSetViewDetailModeStrings(context, selectedItemsSet, numBuckets);
        } else {
            return populateItemViewDetailModeStrings(context, MediaBucketList.getFirstItemSelection(buckets));
        }
    }

    private static CharSequence[] populateSetViewDetailModeStrings(Context context, MediaSet selectedItemsSet, int numOriginalSets) {
        if (selectedItemsSet == null) {
            return null;
        }
        Resources resources = context.getResources();
        ArrayList<CharSequence> strings = new ArrayList<CharSequence>();

        // Number of albums selected.
        if (numOriginalSets == 1) {
            strings.add("1 " + resources.getString(Res.string.album_selected));
        } else {
            strings.add(Integer.toString(numOriginalSets) + " " + resources.getString(Res.string.albums_selected));
        }

        // Number of items selected.
        int numItems = selectedItemsSet.mNumItemsLoaded;
        if (numItems == 1) {
            strings.add("1 " + resources.getString(Res.string.item_selected));
        } else {
            strings.add(Integer.toString(numItems) + " " + resources.getString(Res.string.items_selected));
        }

        DateFormat dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

        // Start and end times of the selected items.
        if (selectedItemsSet.areTimestampsAvailable()) {
            long minTimestamp = selectedItemsSet.mMinTimestamp;
            long maxTimestamp = selectedItemsSet.mMaxTimestamp;
            strings.add(resources.getString(Res.string.start) + ": " + dateTimeFormat.format(new Date(minTimestamp)));
            strings.add(resources.getString(Res.string.end) + ": " + dateTimeFormat.format(new Date(maxTimestamp)));
        } else if (selectedItemsSet.areAddedTimestampsAvailable()) {
            long minTimestamp = selectedItemsSet.mMinAddedTimestamp;
            long maxTimestamp = selectedItemsSet.mMaxAddedTimestamp;
            strings.add(resources.getString(Res.string.start) + ": " + dateTimeFormat.format(new Date(minTimestamp)));
            strings.add(resources.getString(Res.string.end) + ": " + dateTimeFormat.format(new Date(maxTimestamp)));
        } else {
            strings.add(resources.getString(Res.string.start) + ": " + resources.getString(Res.string.date_unknown));
            strings.add(resources.getString(Res.string.end) + ": " + resources.getString(Res.string.date_unknown));
        }

        // The location of the selected items.
        String locationString = null;
        if (selectedItemsSet.mLatLongDetermined) {
            locationString = selectedItemsSet.mReverseGeocodedLocation;
            if (locationString == null) {
                // Try computing the location if it does not exist.
                ReverseGeocoder reverseGeocoder = App.get(context).getReverseGeocoder();
                locationString = reverseGeocoder.computeMostGranularCommonLocation(selectedItemsSet);
            }
        }
        if (locationString != null && locationString.length() > 0) {
            strings.add(resources.getString(Res.string.location) + ": " + locationString);
        }
        int numStrings = strings.size();
        CharSequence[] stringsArr = new CharSequence[numStrings];
        for (int i = 0; i < numStrings; ++i) {
            stringsArr[i] = strings.get(i);
        }
        return stringsArr;
    }

    private static CharSequence[] populateItemViewDetailModeStrings(Context context, MediaItem item) {
        if (item == null) {
            return null;
        }
        Resources resources = context.getResources();
        ArrayList<CharSequence> exifItems = new ArrayList<CharSequence>();
        
        exifItems.add(resources.getString(Res.string.title) + ": " + item.mCaption);
        exifItems.add(resources.getString(Res.string.type) + ": " + item.getDisplayMimeType());

        DateFormat dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

        try {
            ExifInterface exif = new ExifInterface(item.mFilePath);
            String imgSizeX = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
            String imgSizeY = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);
            
            if (imgSizeX != null && imgSizeX.length() > 0 && imgSizeY != null && imgSizeY.length() > 0) {
                exifItems.add("Image Size: " + imgSizeX + "x" + imgSizeY);
            }

            if (item.mLocaltime == null) {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
                String localtime = exif.getAttribute(ExifInterface.TAG_DATETIME);
                if (localtime != null) {
                    item.mLocaltime = formatter.parse(localtime, new ParsePosition(0));
                }
                if (item.mLocaltime == null && item.mCaption != null) {
                    formatter = new SimpleDateFormat("yyyyMMdd'_'HHmmss");
                    // skip initial IMG_ or VND_
                    item.mLocaltime = formatter.parse(item.mCaption, new ParsePosition(4));
                }
            }
        } catch (IOException ex) {
            // ignore it.
        }
        
        if (item.mLocaltime != null) {
            exifItems.add(resources.getString(Res.string.taken_on) + ": " + dateTimeFormat.format(item.mLocaltime));
        } else if (item.isDateTakenValid()) {
            long dateTaken = item.mDateTakenInMs;
            exifItems.add(resources.getString(Res.string.taken_on) + ": " + dateTimeFormat.format(new Date(dateTaken)));
        } else if (item.isDateAddedValid()) {
            long dateAdded = item.mDateAddedInSec * 1000;
            // TODO: Make this added_on as soon as translations are ready.
            // strings[2] = resources.getString(Res.string.added_on) + ": " +
            // DateFormat.format("h:mmaa MMM dd yyyy", dateAdded);
            exifItems.add(resources.getString(Res.string.taken_on) + ": " + dateTimeFormat.format(new Date(dateAdded)));
        } else {
            exifItems.add(resources.getString(Res.string.taken_on) + ": " + resources.getString(Res.string.date_unknown));
        }
        MediaSet parentMediaSet = item.mParentMediaSet;
        if (parentMediaSet == null) {
            exifItems.add(resources.getString(Res.string.album) + ":");
        } else {
            exifItems.add(resources.getString(Res.string.album) + ": " + parentMediaSet.mName);
        }
        
        ReverseGeocoder reverseGeocoder = App.get(context).getReverseGeocoder();
        String locationString = item.getReverseGeocodedLocation(reverseGeocoder);
        if (locationString != null && locationString.length() > 0) {
            exifItems.add(resources.getString(Res.string.location) + ": " + locationString);
        }

        File jpegFile = new File(item.mFilePath);
        try {
            JpegSegmentReader segmentReader = new JpegSegmentReader(jpegFile);
            byte[] exifSegment = segmentReader.readSegment(JpegSegmentReader.SEGMENT_APP1);
            Metadata metadata = new Metadata();
            new ExifReader(exifSegment).extract(metadata);
            Directory exifDir = metadata.getDirectory(ExifDirectory.class);

            String tag = null;
            String str = null;
            int idx = 0;
            
            // EXIF - Camera Maker & Model
            String maker = exifDir.getString(ExifDirectory.TAG_MAKE).trim();
            String model = exifDir.getString(ExifDirectory.TAG_MODEL).trim();
            if (maker != null && maker.length() > 0) {
                if (model != null && model.length() > 0) {
                    if (model.contains(maker)) {
                        str = model;
                    } else {
                        str = maker + " " + model;
                    }
                } else {
                    str = maker;
                }
                exifItems.add("Camera: " + str);
            }
            
            // TODO: EXIF - Lens Model
            
            // EXIF - Focus Length
            str = exifDir.getString(ExifDirectory.TAG_FOCAL_LENGTH);
            if (str != null && str.length() > 0) {
                // EXIF - 35mm Equivalent
                String tmp = "Focal Length: " + str + " mm";
                String eqv = exifDir.getString(ExifDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH);
                if (eqv != null && eqv.length() > 0) {
                    tmp += " (35mm equivalent: " + eqv + "mm)";
                }
                exifItems.add(tmp);
            }
            
            // EXIF - Aperture
            str = exifDir.getString(ExifDirectory.TAG_FNUMBER);
            if (str != null && str.length() > 0) {
                exifItems.add("Aperture: " + "f/" + str);
            }
            
            // EXIF - Exposure Time
            str = exifDir.getString(ExifDirectory.TAG_EXPOSURE_TIME);
            if (str != null && str.length() > 0) {
                exifItems.add("Exposure Time: " + str + " s");
            }
            
            // EXIF - ISO Speed
            str = exifDir.getString(ExifDirectory.TAG_ISO_EQUIVALENT);
            if (str != null && str.length() > 0) {
                exifItems.add("ISO Speed: " + str);
            }
            
            // EXIF - Exposure Bias
            str = exifDir.getString(ExifDirectory.TAG_EXPOSURE_BIAS);
            if (str != null && str.length() > 0) {
                exifItems.add("Exposure Bias: " + str + " eV");
            }
            
            // EXIF - Metering Mode
            idx = exifDir.getInt(ExifDirectory.TAG_METERING_MODE);
            tag = "Metering Mode: ";
            switch (idx) {
            /**
             * Exposure metering method. '0' means unknown, '1' average, '2' center
             * weighted average, '3' spot, '4' multi-spot, '5' multi-segment, '6' partial,
             * '255' other.
             */
            case 0:
                exifItems.add(tag + "Unknown");
                break;
            case 1:
                exifItems.add(tag + "Average");
                break;
            case 2:
                exifItems.add(tag + "Center Weighted Average");
                break;
            case 3:
                exifItems.add(tag + "Spot");
                break;
            case 4:
                exifItems.add(tag + "Multi-spot");
                break;
            case 5:
                exifItems.add(tag + "Multi-segment");
                break;
            case 6:
                exifItems.add(tag + "Partial");
                break;
            case 255:
                exifItems.add(tag + "Other");
                break;
            }
            
            // EXIF - Exposure Program
            idx = exifDir.getInt(ExifDirectory.TAG_EXPOSURE_PROGRAM);
            tag = "Exposure: ";
            switch (idx) {
            /**
             * Exposure program that the camera used when image was taken. '1'
             * means manual control, '2' program normal, '3' aperture priority,
             * '4' shutter priority, '5' program creative (slow program), '6'
             * program action (high-speed program), '7' portrait mode, '8'
             * landscape mode.
             */
            case 1:
                exifItems.add(tag + "Manual Control");
                break;
            case 2:
                exifItems.add(tag + "Program Normal");
                break;
            case 3:
                exifItems.add(tag + "Aperture Priority");
                break;
            case 4:
                exifItems.add(tag + "Shutter Priority");
                break;
            case 5:
                exifItems.add(tag + "Program Creative (slow program)");
                break;
            case 6:
                exifItems.add(tag + "Program Action (High-speed Program)");
                break;
            case 7:
                exifItems.add(tag + "Portrait Mode");
                break;
            case 8:
                exifItems.add(tag + "Landscape Mode");
                break;
            }
            
            // TODO: EXIF - White Balance
        } catch (Exception ex) {
            // ignore it.
        }

        int numStrings = exifItems.size();
        CharSequence[] strings = new CharSequence[numStrings];
        for (int i = 0; i < numStrings; ++i) {
            strings[i] = exifItems.get(i);
        }
        return strings;
    }
}
