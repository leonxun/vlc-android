package org.videolan.medialibrary;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import org.videolan.libvlc.util.VLCUtil;
import org.videolan.medialibrary.interfaces.DevicesDiscoveryCb;
import org.videolan.medialibrary.interfaces.EntryPointsEventsCb;
import org.videolan.medialibrary.interfaces.MediaAddedCb;
import org.videolan.medialibrary.interfaces.MediaUpdatedCb;
import org.videolan.medialibrary.media.Album;
import org.videolan.medialibrary.media.Artist;
import org.videolan.medialibrary.media.Genre;
import org.videolan.medialibrary.media.HistoryItem;
import org.videolan.medialibrary.media.MediaSearchAggregate;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.medialibrary.media.Playlist;
import org.videolan.medialibrary.media.SearchAggregate;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class Medialibrary {

    private static final String TAG = "VLC/JMedialibrary";

    public static final int FLAG_MEDIA_UPDATED_AUDIO        = 1 << 0;
    public static final int FLAG_MEDIA_UPDATED_AUDIO_EMPTY  = 1 << 1;
    public static final int FLAG_MEDIA_UPDATED_VIDEO        = 1 << 2;
    public static final int FLAG_MEDIA_ADDED_AUDIO          = 1 << 3;
    public static final int FLAG_MEDIA_ADDED_AUDIO_EMPTY    = 1 << 4;
    public static final int FLAG_MEDIA_ADDED_VIDEO          = 1 << 5;

    private static final String extDirPath = Environment.getExternalStorageDirectory().getAbsolutePath();

    private static final MediaWrapper[] EMPTY_COLLECTION = {};

    private long mInstanceID;
    private volatile boolean mIsInitiated = false;

    private MediaUpdatedCb mediaUpdatedCb = null;
    private MediaAddedCb mediaAddedCb = null;
    private ArtistsAddedCb mArtistsAddedCb = null;
    private ArtistsModifiedCb mArtistsModifiedCb = null;
    private AlbumsAddedCb mAlbumsAddedCb = null;
    private AlbumsModifiedCb mAlbumsModifiedCb = null;
    private final List<DevicesDiscoveryCb> devicesDiscoveryCbList = new ArrayList<>();
    private final List<EntryPointsEventsCb> entryPointsEventsCbList = new ArrayList<>();

    private static Medialibrary sInstance;

    static {
        System.loadLibrary("c++_shared");
        System.loadLibrary("mla");
    }

    public void setup() {
        nativeSetup();
    }

    public boolean init(Context context) {
        mIsInitiated = nativeInit(context.getCacheDir()+"/vlc_media.db", context.getExternalFilesDir(null).getAbsolutePath()+"/thumbs");
        return mIsInitiated;
    }

    public void banFolder(String path) {
        if (mIsInitiated && new File(path).exists())
            nativeBanFolder(Tools.encodeVLCMrl(path));
    }

    public String[] getDevices() {
        return mIsInitiated ? nativeDevices() : new String[0];
    }

    public void addDevice(String uuid, String path, boolean removable) {
        nativeAddDevice(uuid, Tools.encodeVLCMrl(path), removable);
    }

    public void discover(String path) {
        if (mIsInitiated)
            nativeDiscover(Tools.encodeVLCMrl(path));
    }

    public void removeFolder(String path) {
        if (mIsInitiated)
            nativeRemoveEntryPoint(Tools.encodeVLCMrl(path));
    }

    public String[] getFoldersList() {
        if (!mIsInitiated)
            return new String[0];
        return nativeEntryPoints();
    }

    public boolean removeDevice(String uuid) {
        return mIsInitiated && nativeRemoveDevice(uuid);
    }

    @Override
    protected void finalize() throws Throwable {
        if (mIsInitiated)
            nativeRelease();
        super.finalize();
    }

    public static synchronized Medialibrary getInstance() {
        if (sInstance == null)
            sInstance = new Medialibrary();
        return sInstance;
    }

    public MediaWrapper[] getVideos() {
        return mIsInitiated ? nativeGetVideos() : new MediaWrapper[0];
    }

    public MediaWrapper[] getAudio() {
        return mIsInitiated ? nativeGetAudio() : new MediaWrapper[0];
    }

    public MediaWrapper[] getRecentAudio() {
        return mIsInitiated ? nativeGetRecentAudio() : new MediaWrapper[0];
    }

    public int getVideoCount() {
        return mIsInitiated ? nativeGetVideoCount() : 0;
    }

    public int getAudioCount() {
        return mIsInitiated ? nativeGetAudioCount() : 0;
    }

    public Album[] getAlbums() {
        return mIsInitiated ? nativeGetAlbums() : new Album[0];
    }

    public Album getAlbum(long albumId) {
        return mIsInitiated ? nativeGetAlbum(albumId) : null;
    }

    public Artist[] getArtists() {
        return mIsInitiated ? nativeGetArtists() : new Artist[0];
    }

    public Artist getArtist(long artistId) {
        return mIsInitiated ? nativeGetArtist(artistId) : null;
    }

    public Genre[] getGenres() {
        return mIsInitiated ? nativeGetGenres() : new Genre[0];
    }

    public Genre getGenre(long genreId) {
        return mIsInitiated ? nativeGetGenre(genreId) : null;
    }

    public Playlist[] getPlaylists() {
        return mIsInitiated ? nativeGetPlaylists() : new Playlist[0];
    }

    public Playlist getPlaylist(long playlistId) {
        return mIsInitiated ? nativeGetPlaylist(playlistId) : null;
    }

    public Playlist createPlaylist(String name) {
        return mIsInitiated ? nativePlaylistCreate(name) : null;
    }

    public void pauseBackgroundOperations() {
        if (mIsInitiated)
            nativePauseBackgroundOperations();
    }

    public void resumeBackgroundOperations() {
        if (mIsInitiated)
            nativeResumeBackgroundOperations();
    }

    public void reload() {
        if (mIsInitiated && !isWorking())
            nativeReload();
    }

    public void reload(String entryPoint) {
        if (mIsInitiated)
            nativeReload(entryPoint);
    }

    public void forceParserRetry() {
        if (mIsInitiated)
            nativeForceParserRetry();
    }

    public MediaWrapper[] lastMediaPlayed() {
        return mIsInitiated ? nativeLastMediaPlayed() : EMPTY_COLLECTION;
    }

    public HistoryItem[] lastStreamsPlayed() {
        return mIsInitiated ? nativeLastStreamsPlayed() : new HistoryItem[0];
    }

    public boolean clearHistory() {
        return mIsInitiated && nativeClearHistory();
    }

    public boolean addToHistory(String mrl, String title) {
        return mIsInitiated && nativeAddToHistory(mrl, title);
    }

    public MediaWrapper getMedia(long id) {
        return mIsInitiated ? nativeGetMedia(id) : null;
    }

    public MediaWrapper getMedia(Uri uri) {
        return mIsInitiated ? nativeGetMediaFromMrl(VLCUtil.locationFromUri(uri)) : null;
    }

    public MediaWrapper getMedia(String mrl) {
        return mIsInitiated ? nativeGetMediaFromMrl(Tools.encodeVLCMrl(mrl)) : null;
    }

    public MediaWrapper addMedia(String mrl) {
        return mIsInitiated ? nativeAddMedia(Tools.encodeVLCMrl(mrl)) : null;
    }

    public long getId() {
        return mInstanceID;
    }

    public boolean isWorking() {
        return !mIsInitiated || nativeIsWorking();
    }

    public boolean isInitiated() {
        return mIsInitiated;
    }

    public boolean increasePlayCount(long mediaId) {
        return mIsInitiated && mediaId > 0 && nativeIncreasePlayCount(mediaId);
    }

    // If media is not in ML, find it with its path
    public MediaWrapper findMedia(MediaWrapper mw) {
        if (mIsInitiated && mw != null && mw.getId() == 0L) {
            Uri uri = mw.getUri();
            mw = getMedia(uri);
            if (mw == null  && TextUtils.equals("file", uri.getScheme()) &&
                    uri.getPath() != null && uri.getPath().startsWith("/sdcard")) {
                uri = Tools.convertLocalUri(uri);
                mw = getMedia(uri);
            }
        }
        return mw;
    }

    public void onMediaAdded(MediaWrapper[] mediaList) {
        if (mediaAddedCb != null)
            mediaAddedCb.onMediaAdded(mediaList);
    }

    public void onMediaUpdated(MediaWrapper[] mediaList) {
        if (mediaUpdatedCb != null)
            mediaUpdatedCb.onMediaUpdated(mediaList);
    }

    public void onMediaDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onMediaDeleted: "+id);
    }

    public void onArtistsAdded() {
        if (mArtistsAddedCb != null)
            mArtistsAddedCb.onArtistsAdded();
    }

    public void onArtistsModified() {
        if (mArtistsModifiedCb != null)
            mArtistsModifiedCb.onArtistsModified();
    }

    public void onAlbumsAdded() {
        if (mAlbumsAddedCb != null)
            mAlbumsAddedCb.onAlbumsAdded();
    }

    public void onAlbumsModified() {
        if (mAlbumsModifiedCb != null)
            mAlbumsModifiedCb.onAlbumsModified();
    }

    public void onArtistsDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onArtistsDeleted: "+id);
    }

    public void onAlbumsDeleted(long[] ids) {
        for (long id : ids)
            Log.d(TAG, "onAlbumsDeleted: "+id);
    }

    public void onDiscoveryStarted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryStarted(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryStarted(entryPoint);
        }
    }

    public void onDiscoveryProgress(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryProgress(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryProgress(entryPoint);
        }
    }

    public void onDiscoveryCompleted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onDiscoveryCompleted(entryPoint);
        }
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onDiscoveryCompleted(entryPoint);
        }
    }

    public void onParsingStatsUpdated(int percent) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onParsingStatsUpdated(percent);
        }
    }

    void onReloadStarted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadStarted(entryPoint);
        }
    }
    void onReloadCompleted(String entryPoint) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.isEmpty())
                for (DevicesDiscoveryCb cb : devicesDiscoveryCbList)
                    cb.onReloadCompleted(entryPoint);
        }
    }

    void onEntryPointBanned(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointBanned(entryPoint, success);
        }
    }
    void onEntryPointUnbanned(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointUnbanned(entryPoint, success);
        }
    }
    void onEntryPointRemoved(String entryPoint, boolean success) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.isEmpty())
                for (EntryPointsEventsCb cb : entryPointsEventsCbList)
                    cb.onEntryPointRemoved(entryPoint, success);
        }
    }

    public void setMediaUpdatedCb(MediaUpdatedCb mediaUpdatedCb, int flags) {
        if (!mIsInitiated)
            return;
        this.mediaUpdatedCb = mediaUpdatedCb;
        nativeSetMediaUpdatedCbFlag(flags);
    }

    public void removeMediaUpdatedCb() {
        if (!mIsInitiated)
            return;
        setMediaUpdatedCb(null, 0);
    }

    public void setMediaAddedCb(MediaAddedCb mediaAddedCb, int flags) {
        if (!mIsInitiated)
            return;
        this.mediaAddedCb = mediaAddedCb;
        nativeSetMediaAddedCbFlag(flags);
    }

    public void setArtistsAddedCb(ArtistsAddedCb artistsAddedCb) {
        if (!mIsInitiated)
            return;
        this.mArtistsAddedCb = artistsAddedCb;
        nativeSetMediaAddedCbFlag(artistsAddedCb == null ? 0 : FLAG_MEDIA_ADDED_AUDIO_EMPTY);
    }

    public void setArtistsModifiedCb(ArtistsModifiedCb artistsModifiedCb) {
        if (!mIsInitiated)
            return;
        this.mArtistsModifiedCb = artistsModifiedCb;
        nativeSetMediaUpdatedCbFlag(artistsModifiedCb == null ? 0 : FLAG_MEDIA_UPDATED_AUDIO_EMPTY);
    }

    public void setAlbumsAddedCb(AlbumsAddedCb AlbumsAddedCb) {
        if (!mIsInitiated)
            return;
        this.mAlbumsAddedCb = AlbumsAddedCb;
        nativeSetMediaAddedCbFlag(AlbumsAddedCb == null ? 0 : FLAG_MEDIA_ADDED_AUDIO_EMPTY);
    }

    public void setAlbumsModifiedCb(AlbumsModifiedCb AlbumsModifiedCb) {
        if (!mIsInitiated)
            return;
        this.mAlbumsModifiedCb = AlbumsModifiedCb;
        nativeSetMediaUpdatedCbFlag(AlbumsModifiedCb == null ? 0 : FLAG_MEDIA_UPDATED_AUDIO_EMPTY);
    }

    public SearchAggregate search(String query) {
        return mIsInitiated ? nativeSearch(query) : null;
    }

    public MediaSearchAggregate searchMedia(String query) {
        return mIsInitiated ? nativeSearchMedia(query) : null;
    }

    public Artist[] searchArtist(String query) {
        return mIsInitiated ? nativeSearchArtist(query) : null;
    }

    public Album[] searchAlbum(String query) {
        return mIsInitiated ? nativeSearchAlbum(query) : null;
    }

    public Genre[] searchGenre(String query) {
        return mIsInitiated ? nativeSearchGenre(query) : null;
    }

    public Playlist[] searchPlaylist(String query) {
        return mIsInitiated ? nativeSearchPlaylist(query) : null;
    }

    public void addDeviceDiscoveryCb(DevicesDiscoveryCb cb) {
        synchronized (devicesDiscoveryCbList) {
            if (!devicesDiscoveryCbList.contains(cb))
                devicesDiscoveryCbList.add(cb);
        }
    }

    public void removeDeviceDiscoveryCb(DevicesDiscoveryCb cb) {
        synchronized (devicesDiscoveryCbList) {
            devicesDiscoveryCbList.remove(cb);
        }
    }

    public void addEntryPointsEventsCb(EntryPointsEventsCb cb) {
        synchronized (entryPointsEventsCbList) {
            if (!entryPointsEventsCbList.contains(cb))
                entryPointsEventsCbList.add(cb);
        }
    }

    public void removeEntryPointsEventsCb(EntryPointsEventsCb cb) {
        synchronized (entryPointsEventsCbList) {
            entryPointsEventsCbList.remove(cb);
        }
    }

    public void removeMediaAddedCb() {
        if (!mIsInitiated)
            return;
        setMediaAddedCb(null, 0);
    }

    public static String[] getBlackList() {
        return new String[] {
                "/Android/data/",
                "/Android/media/",
                "/Alarms/",
                "/Ringtones/",
                "/Notifications/",
                "/alarms/",
                "/ringtones/",
                "/notifications/",
                "/audio/Alarms/",
                "/audio/Ringtones/",
                "/audio/Notifications/",
                "/audio/alarms/",
                "/audio/ringtones/",
                "/audio/notifications/",
                "/WhatsApp/",
        };
    }

    public static File[] getDefaultFolders() {
        return new File[]{
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
        };
    }

    /* used only before API 13: substitute for NewWeakGlobalRef */
    @SuppressWarnings("unused") /* Used from JNI */
    private Object getWeakReference() {
        return new WeakReference<>(this);
    }


    // Native methods
    private native void nativeSetup();
    private native boolean nativeInit(String dbPath, String thumbsPath);
    private native void nativeRelease();
    private native void nativeBanFolder(String path);
    private native void nativeAddDevice(String uuid, String path, boolean removable);
    private native String[] nativeDevices();
    private native void nativeDiscover(String path);
    private native void nativeRemoveEntryPoint(String path);
    private native String[] nativeEntryPoints();
    private native boolean nativeRemoveDevice(String uuid);
    private native MediaWrapper[] nativeLastMediaPlayed();
    private native HistoryItem[] nativeLastStreamsPlayed();
    private native  boolean nativeAddToHistory(String mrl, String title);
    private native  boolean nativeClearHistory();
    private native MediaWrapper nativeGetMedia(long id);
    private native MediaWrapper nativeGetMediaFromMrl(String mrl);
    private native MediaWrapper nativeAddMedia(String mrl);
    private native MediaWrapper[] nativeGetVideos();
    private native MediaWrapper[] nativeGetAudio();
    private native MediaWrapper[] nativeGetRecentAudio();
    private native int nativeGetVideoCount();
    private native int nativeGetAudioCount();
    private native  boolean nativeIsWorking();
    private native Album[] nativeGetAlbums();
    private native Album nativeGetAlbum(long albumtId);
    private native Artist[] nativeGetArtists();
    private native Artist nativeGetArtist(long artistId);
    private native Genre[] nativeGetGenres();
    private native Genre nativeGetGenre(long genreId);
    private native Playlist[] nativeGetPlaylists();
    private native Playlist nativeGetPlaylist(long playlistId);
    private native Playlist nativePlaylistCreate(String name);
    private native void nativePauseBackgroundOperations();
    private native void nativeResumeBackgroundOperations();
    private native void nativeReload();
    private native void nativeReload(String entryPoint);
    private native void nativeForceParserRetry();
    private native boolean nativeIncreasePlayCount(long mediaId);
    private native void nativeSetMediaUpdatedCbFlag(int flags);
    private native void nativeSetMediaAddedCbFlag(int flags);
    private native SearchAggregate nativeSearch(String query);
    private native MediaSearchAggregate nativeSearchMedia(String query);
    private native Artist[] nativeSearchArtist(String query);
    private native Album[] nativeSearchAlbum(String query);
    private native Genre[] nativeSearchGenre(String query);
    private native Playlist[] nativeSearchPlaylist(String query);

    private boolean canReadStorage(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || ContextCompat.checkSelfPermission(context,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public interface ArtistsAddedCb {
        void onArtistsAdded();
    }

    public interface ArtistsModifiedCb {
        void onArtistsModified();
    }

    public interface AlbumsAddedCb {
        void onAlbumsAdded();
    }

    public interface AlbumsModifiedCb {
        void onAlbumsModified();
    }
}
