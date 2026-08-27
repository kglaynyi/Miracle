package com.miracle.kglaynyi.player;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManagerProvider;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.EventLogger;
import com.miracle.kglaynyi.R;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;
import java.util.Locale;

public class PlayerActivity extends AppCompatActivity
        implements View.OnClickListener, StyledPlayerView.ControllerVisibilityListener {

    private static final String KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters";
    private static final String KEY_ITEM_INDEX = "item_index";
    private static final String KEY_POSITION = "position";
    private static final String KEY_AUTO_PLAY = "auto_play";
    private static final String RESUME_PREFS = "miracle_video_resume";

    private static final long SEEK_STEP_MS = 10_000L;
    private static final long MIN_RESUME_MS = 10_000L;
    private static final long FINISHED_THRESHOLD_MS = 30_000L;

    public static final String PREFER_EXTENSION_DECODERS_EXTRA = "prefer_extension_decoders";

    protected StyledPlayerView playerView;
    protected @Nullable ExoPlayer player;

    private VLCVideoLayout vlcVideoLayout;
    private LibVLC libVLC;
    private MediaPlayer vlcPlayer;
    private boolean usingVlc;

    private DataSource.Factory dataSourceFactory;
    private MediaItem mediaItem;
    private TrackSelectionParameters trackSelectionParameters;
    private boolean startAutoPlay;
    private int startItemIndex;
    private long startPosition;

    private View decorView;
    private int uiOptions;
    private TextView gestureFeedback;
    private TextView decoderBadge;
    private GestureDetector gestureDetector;
    private AudioManager audioManager;
    private int maxVolume;
    private int gestureStartVolume;

    private String currentUrl;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideFeedback = () -> {
        if (gestureFeedback != null) {
            gestureFeedback.animate().alpha(0f).setDuration(160)
                    .withEndAction(() -> gestureFeedback.setVisibility(View.GONE)).start();
        }
    };
    private final Runnable periodicResumeSave = new Runnable() {
        @Override public void run() {
            saveResumePosition();
            handler.postDelayed(this, 5000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        decorView = getWindow().getDecorView();
        uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decorView.setSystemUiVisibility(uiOptions);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        dataSourceFactory = DemoUtil.getDataSourceFactory(this);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        vlcVideoLayout = findViewById(R.id.vlc_video_layout);
        gestureFeedback = findViewById(R.id.gesture_feedback);
        decoderBadge = findViewById(R.id.decoder_badge);

        playerView.setControllerVisibilityListener(this);
        playerView.requestFocus();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        maxVolume = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        gestureDetector = new GestureDetector(this, new PlayerGestureListener());

        View.OnTouchListener touchListener = (v, event) -> gestureDetector.onTouchEvent(event);
        playerView.setOnTouchListener(touchListener);
        vlcVideoLayout.setOnTouchListener(touchListener);

        if (savedInstanceState != null) {
            Bundle trackBundle = savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS);
            trackSelectionParameters = trackBundle == null
                    ? new TrackSelectionParameters.Builder(this).build()
                    : TrackSelectionParameters.fromBundle(trackBundle);
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
            startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX);
            startPosition = savedInstanceState.getLong(KEY_POSITION);
        } else {
            trackSelectionParameters = new TrackSelectionParameters.Builder(this).build();
            clearStartPosition();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        saveResumePosition();
        releaseExoPlayer();
        releaseVlcPlayer();
        clearStartPosition();
        setIntent(intent);
        currentUrl = null;
        initializePlayer();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT > 23) {
            initializePlayer();
            playerView.onResume();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT <= 23 || (player == null && !usingVlc)) {
            initializePlayer();
            playerView.onResume();
        }
        handler.removeCallbacks(periodicResumeSave);
        handler.postDelayed(periodicResumeSave, 5000L);
    }

    @Override
    public void onPause() {
        saveResumePosition();
        handler.removeCallbacks(periodicResumeSave);
        super.onPause();
        if (Build.VERSION.SDK_INT <= 23) {
            playerView.onPause();
            releaseExoPlayer();
            releaseVlcPlayer();
        }
    }

    @Override
    public void onStop() {
        saveResumePosition();
        handler.removeCallbacks(periodicResumeSave);
        super.onStop();
        if (Build.VERSION.SDK_INT > 23) {
            playerView.onPause();
            releaseExoPlayer();
            releaseVlcPlayer();
        }
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseExoPlayer();
        releaseVlcPlayer();
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        updateTrackSelectorParameters();
        updateStartPosition();
        outState.putBundle(KEY_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle());
        outState.putBoolean(KEY_AUTO_PLAY, isPlayingRequested());
        outState.putInt(KEY_ITEM_INDEX, startItemIndex);
        outState.putLong(KEY_POSITION, getPlaybackPosition());
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return (!usingVlc && playerView.dispatchKeyEvent(event)) || super.dispatchKeyEvent(event);
    }

    @Override public void onVisibilityChanged(int visibility) {}
    @Override public void onClick(View view) {}

    protected boolean initializePlayer() {
        if (player != null || usingVlc) return true;

        Intent intent = getIntent();
        currentUrl = intent.getStringExtra("url");
        if (currentUrl == null || currentUrl.trim().isEmpty()) {
            showToast("No video URL was provided");
            finish();
            return false;
        }

        Uri uri = Uri.parse(currentUrl);
        Log.i("Inside Player", uri.toString());
        mediaItem = MediaItem.fromUri(uri);

        if (startItemIndex == C.INDEX_UNSET) {
            long saved = getResumePreferences().getLong(resumeKey(currentUrl), C.TIME_UNSET);
            if (saved >= MIN_RESUME_MS) {
                startItemIndex = 0;
                startPosition = saved;
            }
        }

        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory())
                .setSeekBackIncrementMs(SEEK_STEP_MS)
                .setSeekForwardIncrementMs(SEEK_STEP_MS);
        setRenderersFactory(playerBuilder,
                intent.getBooleanExtra(PREFER_EXTENSION_DECODERS_EXTRA, true));

        player = playerBuilder.build();
        player.setTrackSelectionParameters(trackSelectionParameters);
        player.addListener(new PlayerEventListener());
        player.addAnalyticsListener(new EventLogger());
        player.setAudioAttributes(AudioAttributes.DEFAULT, true);
        player.setPlayWhenReady(startAutoPlay);
        playerView.setPlayer(player);
        playerView.setVisibility(View.VISIBLE);
        vlcVideoLayout.setVisibility(View.GONE);
        decoderBadge.setVisibility(View.GONE);

        player.setMediaItem(mediaItem);
        if (startItemIndex != C.INDEX_UNSET && startPosition != C.TIME_UNSET) {
            player.seekTo(Math.max(0, startPosition));
            showFeedback("Resume • " + formatTime(startPosition));
        }
        player.prepare();
        return true;
    }

    private MediaSource.Factory createMediaSourceFactory() {
        DefaultDrmSessionManagerProvider drmSessionManagerProvider =
                new DefaultDrmSessionManagerProvider();
        drmSessionManagerProvider.setDrmHttpDataSourceFactory(
                DemoUtil.getHttpDataSourceFactory(this));
        return new DefaultMediaSourceFactory(this)
                .setDataSourceFactory(dataSourceFactory)
                .setDrmSessionManagerProvider(drmSessionManagerProvider);
    }

    private void setRenderersFactory(ExoPlayer.Builder playerBuilder, boolean preferExtensionDecoders) {
        RenderersFactory renderersFactory =
                DemoUtil.buildRenderersFactory(this, preferExtensionDecoders);
        playerBuilder.setRenderersFactory(renderersFactory);
    }

    private void startSoftwareFallback() {
        if (usingVlc || currentUrl == null) return;

        long resumeAt = getPlaybackPosition();
        if (player != null) {
            player.release();
            player = null;
            playerView.setPlayer(null);
        }

        usingVlc = true;
        playerView.setVisibility(View.GONE);
        vlcVideoLayout.setVisibility(View.VISIBLE);
        decoderBadge.setVisibility(View.VISIBLE);
        showFeedback("Software decoder");

        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=2000");
        options.add("--avcodec-hw=none");
        options.add("--no-drop-late-frames");
        options.add("--no-skip-frames");

        libVLC = new LibVLC(this, options);
        vlcPlayer = new MediaPlayer(libVLC);
        vlcPlayer.attachViews(vlcVideoLayout, null, false, false);

        Media media = new Media(libVLC, Uri.parse(currentUrl));
        media.setHWDecoderEnabled(false, false);
        vlcPlayer.setMedia(media);
        media.release();

        final long position = Math.max(0, resumeAt);
        vlcPlayer.setEventListener(event -> {
            if (event.type == MediaPlayer.Event.Playing && position >= MIN_RESUME_MS) {
                try {
                    vlcPlayer.setTime(position);
                } catch (Exception ignored) {}
            } else if (event.type == MediaPlayer.Event.EndReached) {
                clearResume();
            }
        });
        vlcPlayer.play();
    }

    private void releaseExoPlayer() {
        if (player == null) return;
        updateTrackSelectorParameters();
        updateStartPosition();
        player.release();
        player = null;
        if (playerView != null) playerView.setPlayer(null);
    }

    private void releaseVlcPlayer() {
        if (vlcPlayer != null) {
            try { vlcPlayer.stop(); } catch (Exception ignored) {}
            try { vlcPlayer.detachViews(); } catch (Exception ignored) {}
            vlcPlayer.release();
            vlcPlayer = null;
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
        usingVlc = false;
    }

    private void updateTrackSelectorParameters() {
        if (player != null) trackSelectionParameters = player.getTrackSelectionParameters();
    }

    private void updateStartPosition() {
        if (player != null) {
            startAutoPlay = player.getPlayWhenReady();
            startItemIndex = Math.max(0, player.getCurrentMediaItemIndex());
            startPosition = Math.max(0, player.getContentPosition());
        } else if (usingVlc && vlcPlayer != null) {
            startAutoPlay = vlcPlayer.isPlaying();
            startItemIndex = 0;
            startPosition = Math.max(0, vlcPlayer.getTime());
        }
    }

    protected void clearStartPosition() {
        startAutoPlay = true;
        startItemIndex = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }

    private long getPlaybackPosition() {
        if (usingVlc && vlcPlayer != null) return Math.max(0, vlcPlayer.getTime());
        if (player != null) return Math.max(0, player.getCurrentPosition());
        return startPosition == C.TIME_UNSET ? 0 : Math.max(0, startPosition);
    }

    private long getPlaybackDuration() {
        if (usingVlc && vlcPlayer != null) return Math.max(0, vlcPlayer.getLength());
        if (player != null) return Math.max(0, player.getDuration());
        return 0;
    }

    private boolean isPlayingRequested() {
        if (usingVlc && vlcPlayer != null) return vlcPlayer.isPlaying();
        return player == null ? startAutoPlay : player.getPlayWhenReady();
    }

    private void seekBy(long deltaMs) {
        long duration = getPlaybackDuration();
        long target = Math.max(0, getPlaybackPosition() + deltaMs);
        if (duration > 0) target = Math.min(duration, target);

        if (usingVlc && vlcPlayer != null) {
            try { vlcPlayer.setTime(target); } catch (Exception ignored) {}
        } else if (player != null) {
            player.seekTo(target);
        }
        showFeedback((deltaMs > 0 ? "+10s" : "−10s") + " • " + formatTime(target));
    }

    private SharedPreferences getResumePreferences() {
        return getSharedPreferences(RESUME_PREFS, MODE_PRIVATE);
    }

    private String resumeKey(String url) {
        return "resume_" + Integer.toHexString(url.hashCode());
    }

    private void saveResumePosition() {
        if (currentUrl == null || currentUrl.isEmpty()) return;

        long position = getPlaybackPosition();
        long duration = getPlaybackDuration();

        if (position < MIN_RESUME_MS
                || (duration > 0 && duration - position <= FINISHED_THRESHOLD_MS)) {
            clearResume();
        } else {
            getResumePreferences().edit().putLong(resumeKey(currentUrl), position).apply();
        }
    }

    private void clearResume() {
        if (currentUrl != null) {
            getResumePreferences().edit().remove(resumeKey(currentUrl)).apply();
        }
    }

    private void showFeedback(String message) {
        if (gestureFeedback == null) return;
        handler.removeCallbacks(hideFeedback);
        gestureFeedback.setText(message);
        gestureFeedback.setAlpha(0f);
        gestureFeedback.setVisibility(View.VISIBLE);
        gestureFeedback.animate().alpha(1f).setDuration(100).start();
        handler.postDelayed(hideFeedback, 900L);
    }

    private String formatTime(long millis) {
        long total = Math.max(0, millis / 1000L);
        long hours = total / 3600L;
        long minutes = (total % 3600L) / 60L;
        long seconds = total % 60L;
        if (hours > 0) return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) {
            gestureStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (!usingVlc) {
                if (playerView.isControllerFullyVisible()) playerView.hideController();
                else playerView.showController();
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            float half = Math.max(1f, decorView.getWidth()) / 2f;
            seekBy(e.getX() >= half ? SEEK_STEP_MS : -SEEK_STEP_MS);
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (e1 == null || e2 == null) return false;
            float vertical = e1.getY() - e2.getY();
            float horizontal = e2.getX() - e1.getX();
            if (Math.abs(vertical) < Math.abs(horizontal) || Math.abs(vertical) < 20f) return false;

            float fraction = vertical / Math.max(1f, decorView.getHeight());
            int target = gestureStartVolume + Math.round(fraction * maxVolume * 1.5f);
            target = Math.max(0, Math.min(maxVolume, target));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0);
            int percent = Math.round(target * 100f / maxVolume);
            showFeedback("Volume " + percent + "%");
            return true;
        }
    }

    private class PlayerEventListener implements Player.Listener {
        @Override
        public void onPlaybackStateChanged(@Player.State int playbackState) {
            if (playbackState == Player.STATE_ENDED) clearResume();
            decorView.setSystemUiVisibility(uiOptions);
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            // ExoPlayer decoder errors occupy the 4001-4004 range.
            // Try system/extension decoders first, then libVLC software decode.
            if (error.errorCode >= 4001 && error.errorCode <= 4004) {
                startSoftwareFallback();
            } else if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                if (player != null) {
                    player.seekToDefaultPosition();
                    player.prepare();
                }
            } else {
                showToast("Playback error: " + error.getErrorCodeName());
            }
        }
    }
}
