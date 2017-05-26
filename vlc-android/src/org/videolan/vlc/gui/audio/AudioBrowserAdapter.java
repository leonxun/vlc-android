/*
 * *************************************************************************
 *  BaseAdapter.java
 * **************************************************************************
 *  Copyright © 2016-2017 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc.gui.audio;

import android.app.Activity;
import android.content.Context;
import android.databinding.ViewDataBinding;
import android.graphics.drawable.BitmapDrawable;
import android.support.annotation.MainThread;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import org.videolan.medialibrary.Tools;
import org.videolan.medialibrary.media.DummyItem;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.vlc.BR;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.databinding.AudioBrowserItemBinding;
import org.videolan.vlc.databinding.AudioBrowserSeparatorBinding;
import org.videolan.vlc.gui.BaseQueuedAdapter;
import org.videolan.vlc.gui.helpers.AsyncImageLoader;
import org.videolan.vlc.gui.helpers.UiTools;
import org.videolan.vlc.gui.view.FastScroller;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.util.MediaItemDiffCallback;
import org.videolan.vlc.util.MediaItemFilter;
import org.videolan.vlc.util.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class AudioBrowserAdapter extends BaseQueuedAdapter<MediaLibraryItem[], AudioBrowserAdapter.ViewHolder> implements FastScroller.SeparatedAdapter, Filterable {

    private static final String TAG = "VLC/AudioBrowserAdapter";

    private boolean mMakeSections = true;

    private MediaLibraryItem[] mDataList;
    private MediaLibraryItem[] mOriginalDataSet = null;
    private ItemFilter mFilter = new ItemFilter();
    private Activity mContext;
    private IEventsHandler mIEventsHandler;
    private int mSelectionCount = 0;
    private int mType;
    private BitmapDrawable mDefaultCover;

    public AudioBrowserAdapter(Activity context, int type, IEventsHandler eventsHandler, boolean sections) {
        mContext = context;
        mIEventsHandler = eventsHandler;
        mMakeSections = sections;
        mType = type;
        mDefaultCover = getIconDrawable();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = (LayoutInflater) parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if (viewType == MediaLibraryItem.TYPE_DUMMY) {
            AudioBrowserSeparatorBinding binding = AudioBrowserSeparatorBinding.inflate(inflater, parent, false);
            return new ViewHolder(binding);
        } else {
            AudioBrowserItemBinding binding = AudioBrowserItemBinding.inflate(inflater, parent, false);
            return new MediaItemViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        if (position >= mDataList.length)
            return;
        holder.vdb.setVariable(BR.item, mDataList[position]);
        if (holder.getType() == MediaLibraryItem.TYPE_MEDIA) {
            boolean isSelected = mDataList[position].hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
            ((MediaItemViewHolder)holder).setCoverlay(isSelected);
            ((MediaItemViewHolder)holder).setViewBackground(((MediaItemViewHolder) holder).itemView.hasFocus(), isSelected);
        }
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position, List<Object> payloads) {
        if (Util.isListEmpty(payloads))
            super.onBindViewHolder(holder, position, payloads);
        else {
            boolean isSelected = ((MediaLibraryItem)payloads.get(0)).hasStateFlags(MediaLibraryItem.FLAG_SELECTED);
            MediaItemViewHolder miv = (MediaItemViewHolder) holder;
            miv.setCoverlay(isSelected);
            miv.setViewBackground(miv.itemView.hasFocus(), isSelected);
        }

    }

    @Override
    public void onViewRecycled(ViewHolder holder) {
        if (mDefaultCover != null)
            holder.vdb.setVariable(BR.cover, mDefaultCover);
    }

    @Override
    public int getItemCount() {
        return mDataList == null ? 0 :  mDataList.length;
    }

    public MediaLibraryItem getItem(int position) {
        return isPositionValid(position) ? mDataList[position] : null;
    }

    private boolean isPositionValid(int position) {
        return position >= 0 || position < mDataList.length;
    }

    public MediaLibraryItem[] getAll() {
        return mDataList;
    }

    ArrayList<MediaLibraryItem> getMediaItems() {
        ArrayList<MediaLibraryItem> list = new ArrayList<>();
        int count = getItemCount();
        for (int i = 0; i < count; ++i)
            if (!(mDataList[i].getItemType() == MediaLibraryItem.TYPE_DUMMY))
                list.add(mDataList[i]);
        return list;
    }

    int getListWithPosition(ArrayList<MediaLibraryItem> list, int position) {
        int offset = 0, count = getItemCount();
        for (int i = 0; i < count; ++i)
            if (mDataList[i].getItemType() == MediaLibraryItem.TYPE_DUMMY) {
                if (i < position)
                    ++offset;
            } else
                list.add(mDataList[i]);
        return position-offset;
    }

    @Override
    public long getItemId(int position) {
        return isPositionValid(position) ? mDataList[position].getId() : -1;
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getItemType();
    }

    public boolean hasSections() {
        return mMakeSections;
    }

    @Override
    public String getSectionforPosition(int position) {
        if (mMakeSections)
            for (int i = position; i >= 0; --i)
                if (mDataList[i].getItemType() == MediaLibraryItem.TYPE_DUMMY)
                    return mDataList[i].getTitle();
        return "";
    }

    @MainThread
    public boolean isEmpty() {
        return Tools.isArrayEmpty(peekLast());
    }

    public void clear() {
        mDataList = null;
        mOriginalDataSet = null;
    }

    public void addAll(MediaLibraryItem[] items) {
        addAll(items, mMakeSections);
    }

    public void addAll(MediaLibraryItem[] items, boolean generateSections) {
        if (mContext == null)
            return;
        mDataList = generateSections ? generateList(items) : items;
        for (int i = 0; i<getItemCount(); ++i) {
            if (mDataList[i].getItemType() == MediaLibraryItem.TYPE_DUMMY)
                continue;
            if (mDataList[i].getTitle().isEmpty()) {
                if (mDataList[i].getItemType() == MediaLibraryItem.TYPE_ARTIST) {
                    if (mDataList[i].getId() == 1L)
                        mDataList[i].setTitle(mContext.getString(R.string.unknown_artist));
                    else if (mDataList[i].getId() == 2L)
                        mDataList[i].setTitle(mContext.getString(R.string.various_artists));
                } else if (mDataList[i].getItemType() == MediaLibraryItem.TYPE_ALBUM) {
                    mDataList[i].setTitle(mContext.getString(R.string.unknown_album));
                    if (TextUtils.isEmpty(mDataList[i].getDescription()))
                        mDataList[i].setDescription(mContext.getString(R.string.unknown_artist));
                }
            } else if (generateSections)
                break;
        }
    }

    private MediaLibraryItem[] generateList(MediaLibraryItem[] items) {
        ArrayList<MediaLibraryItem> datalist = new ArrayList<>();
        boolean isLetter, emptyTitle;
        String firstLetter = null, currentLetter = null;
        int count = items.length;
        for (int i = 0; i < count; ++i) {
            MediaLibraryItem item = items[i];
            if (item.getItemType() == MediaLibraryItem.TYPE_DUMMY)
                continue;
            String title = item.getTitle();
            emptyTitle = title.isEmpty();
            isLetter = !emptyTitle && Character.isLetter(title.charAt(0));
            if (isLetter)
                firstLetter = title.substring(0, 1).toUpperCase();
            if (currentLetter == null) {
                currentLetter = isLetter ? firstLetter : "#";
                DummyItem sep = new DummyItem(currentLetter);
                datalist.add(sep);
            }
            //Add a new separator
            if (isLetter && !TextUtils.equals(currentLetter, firstLetter)) {
                currentLetter = firstLetter;
                DummyItem sep = new DummyItem(currentLetter);
                datalist.add(sep);
            }
            datalist.add(item);
        }
        return datalist.toArray(new MediaLibraryItem[datalist.size()]);
    }

    public void remove(final MediaLibraryItem item) {
        final MediaLibraryItem[] referenceList = peekLast();
        if (Tools.isArrayEmpty(referenceList))
            return;
        final MediaLibraryItem[] dataList = new MediaLibraryItem[referenceList.length-1];
        Util.removeItemInArray(referenceList, item, dataList);
        update(dataList);
    }

    public void addItem(final int position, final MediaLibraryItem item) {
        final MediaLibraryItem[] referenceList = peekLast();
        final MediaLibraryItem[] dataList = Tools.isArrayEmpty(referenceList)
                ? new MediaLibraryItem[]{item} : new MediaLibraryItem[referenceList.length+1];
        Util.addItemInArray(referenceList, position, item, dataList);
        update(dataList);
    }

    @Override
    public MediaLibraryItem[] peekLast() {
        return hasPendingUpdates() ? super.peekLast() : mDataList;
    }

    public void restoreList() {
        if (mOriginalDataSet != null) {
            update(Arrays.copyOf(mOriginalDataSet, mOriginalDataSet.length));
            mOriginalDataSet = null;
        }
    }

    protected void internalUpdate(final MediaLibraryItem[] items) {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                final MediaLibraryItem[] newList = mOriginalDataSet == null && hasSections() ? generateList(items) : items;
                final DiffUtil.DiffResult result = DiffUtil.calculateDiff(new MediaItemDiffCallback(mDataList, newList), false);
                VLCApplication.runOnMainThread(new Runnable() {
                    @Override
                    public void run() {
                        addAll(newList, false);
                        result.dispatchUpdatesTo(AudioBrowserAdapter.this);
                        mIEventsHandler.onUpdateFinished(AudioBrowserAdapter.this);
                        processQueue();
                    }
                });
            }
        });
    }

    @MainThread
    public List<MediaLibraryItem> getSelection() {
        List<MediaLibraryItem> selection = new LinkedList<>();
        int count = getItemCount();
        for (int i = 0; i < count; ++i)
            if (mDataList[i].hasStateFlags(MediaLibraryItem.FLAG_SELECTED))
                selection.add(mDataList[i]);
        return selection;
    }

    @MainThread
    public int getSelectionCount() {
        return mSelectionCount;
    }

    @MainThread
    public void resetSelectionCount() {
        mSelectionCount = 0;
    }

    @MainThread
    public void updateSelectionCount(boolean selected) {
        mSelectionCount += selected ? 1 : -1;
    }

    private BitmapDrawable getIconDrawable() {
        switch (mType) {
            case MediaLibraryItem.TYPE_ALBUM:
                return AsyncImageLoader.DEFAULT_COVER_ALBUM_DRAWABLE;
            case MediaLibraryItem.TYPE_ARTIST:
                return AsyncImageLoader.DEFAULT_COVER_ARTIST_DRAWABLE;
            case MediaLibraryItem.TYPE_MEDIA:
                return AsyncImageLoader.DEFAULT_COVER_AUDIO_DRAWABLE;
            default:
                return null;
        }
    }

    public class ViewHolder< T extends ViewDataBinding> extends RecyclerView.ViewHolder {
        T vdb;

        public ViewHolder(T vdb) {
            super(vdb.getRoot());
            this.vdb = vdb;
        }

        public int getType() {
            return MediaLibraryItem.TYPE_DUMMY;
        }
    }

    public class MediaItemViewHolder extends ViewHolder<AudioBrowserItemBinding> implements View.OnFocusChangeListener {

        MediaItemViewHolder(AudioBrowserItemBinding binding) {
            super(binding);
            binding.setHolder(this);
            itemView.setOnFocusChangeListener(this);
            if (mDefaultCover != null)
                binding.setCover(mDefaultCover);
        }

        public void onClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onClick(v, position, mDataList[position]);
            }
        }

        public void onMoreClick(View v) {
            if (mIEventsHandler != null) {
                int position = getLayoutPosition();
                mIEventsHandler.onCtxClick(v, position, mDataList[position]);
            }
        }

        public boolean onLongClick(View view) {
            int position = getLayoutPosition();
            return mIEventsHandler.onLongClick(view, position, mDataList[position]);
        }

        private void setCoverlay(boolean selected) {
            vdb.mediaCover.setImageResource(selected ? R.drawable.ic_action_mode_select : 0);
        }

        public int getType() {
            return MediaLibraryItem.TYPE_MEDIA;
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            setViewBackground(hasFocus, vdb.getItem().hasStateFlags(MediaLibraryItem.FLAG_SELECTED));
        }

        private void setViewBackground(boolean focused, boolean selected) {
            itemView.setBackgroundColor(focused ? UiTools.ITEM_FOCUS_ON : UiTools.ITEM_FOCUS_OFF);
            int selectionColor = selected ? UiTools.ITEM_SELECTION_ON : 0;
            itemView.setBackgroundColor(selectionColor);
        }
    }

    @Override
    public Filter getFilter() {
        return mFilter;
    }

    private class ItemFilter extends MediaItemFilter {

        @Override
        protected List<MediaLibraryItem> initData() {
            if (mOriginalDataSet == null)
                mOriginalDataSet = Arrays.copyOf(mDataList, mDataList.length);
            if (referenceList == null)
                referenceList = new ArrayList<>(Arrays.asList(mDataList));
            return referenceList;
        }

        @Override
        protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
            update(((ArrayList<MediaLibraryItem>) filterResults.values).toArray(new MediaLibraryItem[filterResults.count]));
        }
    }
}
