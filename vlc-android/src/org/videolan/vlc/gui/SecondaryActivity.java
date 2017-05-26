/*
 * *************************************************************************
 *  SecondaryActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
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

package org.videolan.vlc.gui;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.view.Menu;
import android.view.MenuItem;

import org.videolan.medialibrary.Medialibrary;
import org.videolan.vlc.MediaParsingService;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment;
import org.videolan.vlc.gui.audio.AudioBrowserFragment;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.browser.StorageBrowserFragment;
import org.videolan.vlc.gui.preferences.PreferencesActivity;
import org.videolan.vlc.gui.tv.TvUtil;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.video.VideoListAdapter;
import org.videolan.vlc.interfaces.ISortable;

public class SecondaryActivity extends AudioPlayerContainerActivity {
    public final static String TAG = "VLC/SecondaryActivity";

    public static final int ACTIVITY_RESULT_SECONDARY = 3;

    public static final String KEY_FRAGMENT = "fragment";

    public static final String ALBUMS_SONGS = "albumsSongs";
    public static final String EQUALIZER = "equalizer";
    public static final String ABOUT = "about";
    public static final String VIDEO_GROUP_LIST = "videoGroupList";
    public static final String STORAGE_BROWSER = "storage_browser";

    Fragment mFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.secondary);

        initAudioPlayerContainerActivity();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (getSupportFragmentManager().getFragments() == null) {
            String fragmentId = getIntent().getStringExtra(KEY_FRAGMENT);
            fetchSecondaryFragment(fragmentId);
            if (mFragment == null){
                finish();
                return;
            }
            getSupportFragmentManager().beginTransaction()
            .add(R.id.fragment_placeholder, mFragment)
            .commit();
            if (VLCApplication.showTvUi() && STORAGE_BROWSER.equals(fragmentId))
                Snackbar.make(getWindow().getDecorView(), R.string.tv_settings_hint, Snackbar.LENGTH_LONG).show();
        }

        if (VLCApplication.showTvUi())
            TvUtil.applyOverscanMargin(this);
    }

    @Override
    protected void onResume() {
        overridePendingTransition(0,0);
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (isFinishing())
            overridePendingTransition(0, 0);
        super.onPause();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ACTIVITY_RESULT_SECONDARY) {
            if (resultCode == PreferencesActivity.RESULT_RESCAN) {
                startService(new Intent(MediaParsingService.ACTION_RELOAD, null,this, MediaParsingService.class));
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (mFragment instanceof VideoGridFragment)
            getMenuInflater().inflate(R.menu.video_group, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.ml_menu_sortby_name:
            case R.id.ml_menu_sortby_length:
                ((ISortable) mFragment).sortBy(item.getItemId() == R.id.ml_menu_sortby_name
                ? VideoListAdapter.SORT_BY_TITLE
                : VideoListAdapter.SORT_BY_LENGTH);
                break;
            case R.id.ml_menu_refresh:
                Medialibrary ml = VLCApplication.getMLInstance();
                if (!ml.isWorking())
                    startService(new Intent(MediaParsingService.ACTION_RELOAD, null,this, MediaParsingService.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void fetchSecondaryFragment(String id) {
        if (id.equals(ALBUMS_SONGS)) {
            mFragment = new AudioAlbumsSongsFragment();
            Bundle args = new Bundle();
            args.putParcelable(AudioBrowserFragment.TAG_ITEM, getIntent().getParcelableExtra(AudioBrowserFragment.TAG_ITEM));
            mFragment.setArguments(args);
        } else if(id.equals(EQUALIZER)) {
            mFragment = new EqualizerFragment();
        } else if(id.equals(ABOUT)) {
            mFragment = new AboutFragment();
        } else if(id.equals(VIDEO_GROUP_LIST)) {
            mFragment = new VideoGridFragment();
            ((VideoGridFragment) mFragment).setGroup(getIntent().getStringExtra("param"));
        } else if (id.equals(STORAGE_BROWSER)){
            mFragment = new StorageBrowserFragment();
        } else {
            throw new IllegalArgumentException("Wrong fragment id.");
        }
    }
}
