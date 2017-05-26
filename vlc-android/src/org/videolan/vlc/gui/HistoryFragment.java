/*****************************************************************************
 * HistoryFragment.java
 *****************************************************************************
 * Copyright © 2012-2015 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/
package org.videolan.vlc.gui;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import org.videolan.libvlc.util.AndroidUtil;
import org.videolan.medialibrary.media.MediaLibraryItem;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.browser.MediaBrowserFragment;
import org.videolan.vlc.gui.view.SwipeRefreshLayout;
import org.videolan.vlc.interfaces.IEventsHandler;
import org.videolan.vlc.interfaces.IHistory;
import org.videolan.vlc.interfaces.IRefreshable;
import org.videolan.vlc.media.MediaUtils;

import java.util.List;

public class HistoryFragment extends MediaBrowserFragment implements IRefreshable, IHistory, SwipeRefreshLayout.OnRefreshListener, IEventsHandler {

    public final static String TAG = "VLC/HistoryFragment";

    private static final int UPDATE_LIST = 0;

    private HistoryAdapter mHistoryAdapter;
    private RecyclerView mRecyclerView;
    private View mEmptyView;

    /* All subclasses of Fragment must include a public empty constructor. */
    public HistoryFragment() {
        mHistoryAdapter = new HistoryAdapter(this);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState){

        View v = inflater.inflate(R.layout.history_list, container, false);
        mRecyclerView = (RecyclerView)v.findViewById(android.R.id.list);
        mEmptyView = v.findViewById(android.R.id.empty);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mRecyclerView.setAdapter(mHistoryAdapter);
        mRecyclerView.setNextFocusUpId(R.id.ml_menu_search);
        mRecyclerView.setNextFocusLeftId(android.R.id.list);
        mRecyclerView.setNextFocusRightId(android.R.id.list);
        if (AndroidUtil.isHoneycombOrLater)
            mRecyclerView.setNextFocusForwardId(android.R.id.list);
        mRecyclerView.requestFocus();
        registerForContextMenu(mRecyclerView);

        mSwipeRefreshLayout = (SwipeRefreshLayout) v.findViewById(R.id.swipeLayout);

        mSwipeRefreshLayout.setOnRefreshListener(this);
        return v;
    }

    public void onStart(){
        super.onStart();
        if (mReadyToDisplay && mHistoryAdapter.isEmpty())
            display();
    }

    @Override
    public void refresh() {
        VLCApplication.runBackground(new Runnable() {
            @Override
            public void run() {
                MediaWrapper[] list = VLCApplication.getMLInstance().lastMediaPlayed();
                mHandler.obtainMessage(UPDATE_LIST, list).sendToTarget();
            }
        });
    }

    @Override
    public void onRefresh() {
        refresh();
    }

    @Override
    public void display() {
        mReadyToDisplay = true;
        refresh();
    }

    @Override
    protected String getTitle() {
        return getString(R.string.history);
    }

    public void clear(){}

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case UPDATE_LIST:
                    if (getActivity() == null)
                        return;
                    mHistoryAdapter.setList((MediaWrapper[]) msg.obj);
                    updateEmptyView();
                    mHistoryAdapter.notifyDataSetChanged();
                    mSwipeRefreshLayout.setRefreshing(false);
                    getActivity().supportInvalidateOptionsMenu();
            }
        }
    };

    private void updateEmptyView() {
        if (mHistoryAdapter.isEmpty()){
            mRecyclerView.setVisibility(View.GONE);
            mEmptyView.setVisibility(View.VISIBLE);
        } else {
            mEmptyView.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    public boolean isEmpty() {
        return mHistoryAdapter.isEmpty();
    }

    @Override
    public void clearHistory() {
        mMediaLibrary.clearHistory();
        mHistoryAdapter.clear();
        updateEmptyView();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.action_mode_history, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        int selectionCount = mHistoryAdapter.getSelection().size();
        if (selectionCount == 0) {
            stopActionMode();
            return false;
        }
        menu.findItem(R.id.action_history_info).setVisible(selectionCount == 1);
        menu.findItem(R.id.action_history_play).setVisible(AndroidUtil.isHoneycombOrLater || selectionCount == 1);
        menu.findItem(R.id.action_history_append).setVisible(mService.hasMedia() && AndroidUtil.isHoneycombOrLater);
        return true;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_history_play:
                MediaUtils.openList(getActivity(), mHistoryAdapter.getSelection(), 0);
                break;
            case R.id.action_history_append:
                MediaUtils.appendMedia(getActivity(), mHistoryAdapter.getSelection());
                break;
            case R.id.action_history_info:
                showInfoDialog(mHistoryAdapter.getSelection().get(0));
                break;
            default:
                stopActionMode();
                return false;
        }
        stopActionMode();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mActionMode = null;
        int index = -1;
        for (MediaWrapper media : mHistoryAdapter.getAll()) {
            ++index;
            if (media.hasStateFlags(MediaLibraryItem.FLAG_SELECTED)) {
                media.removeStateFlags(MediaLibraryItem.FLAG_SELECTED);
                mHistoryAdapter.notifyItemChanged(index, media);
            }
        }
    }

    @Override
    public void onClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null) {
            item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
            mHistoryAdapter.notifyItemChanged(position, item);
            invalidateActionMode();
            return;
        }

        if (position != 0) {
            List<MediaWrapper> mediaList = mHistoryAdapter.getAll();
            mediaList.remove(position);
            mediaList.add(0, (MediaWrapper) item);
            mHistoryAdapter.notifyItemMoved(position, 0);
        }
        MediaUtils.openMedia(v.getContext(), (MediaWrapper) item);
    }

    @Override
    public boolean onLongClick(View v, int position, MediaLibraryItem item) {
        if (mActionMode != null)
            return false;
        item.toggleStateFlag(MediaLibraryItem.FLAG_SELECTED);
        mHistoryAdapter.notifyItemChanged(position, item);
        startActionMode();
        return true;
    }

    @Override
    public void onCtxClick(View v, int position, MediaLibraryItem item) {}

    @Override
    public void onUpdateFinished(RecyclerView.Adapter adapter) {}
}
