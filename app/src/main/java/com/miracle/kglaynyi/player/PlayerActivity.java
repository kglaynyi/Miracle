package com.miracle.kglaynyi.player;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
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
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManagerProvider;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.MimeTypes;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.utils.ResumeUtils;

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
    private static final long SEEK_STEP_MS = 10_000L;
    private static final long MIN_RESUME_MS = 10_000L;
    private static final long FINISHED_THRESHOLD_MS = 30_000L;
    private static final String PLAYER_SETTINGS = "Settings";
    private static final String VIEW_MODE_KEY = "PLAYER_VIEW_MODE";

    private static final int VIEW_FIT = 0;
    private static final int VIEW_ORIGINAL = 1;
    private static final int VIEW_FILL = 2;
    private static final int VIEW_CROP = 3;
    private static final String[] VIEW_MODE_LABELS = {"Fit", "Original", "Fill", "Crop"};

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

    private View vlcController;
    private Button vlcAudioTracks;
    private Button vlcSubtitleTracks;
    private Button vlcPlayPause;
    private Button vlcViewMode;
    private Button exoViewMode;
    private SeekBar vlcSeekBar;
    private TextView vlcCurrentTime;
    private TextView vlcTotalTime;
    private boolean userSeeking;

    private Boolean hardwareHevcAvailable;
    private boolean softwareFallbackScheduled;
    private GestureDetector gestureDetector;
    private AudioManager audioManager;
    private int maxVolume;
    private int gestureStartVolume;
    private int viewMode;

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

    private final Runnable vlcProgressUpdater = new Runnable() {
        @Override public void run() {
            updateVlcProgress();
            if (usingVlc && vlcPlayer != null) {
                handler.postDelayed(this, 500L);
            }
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

        vlcController = findViewById(R.id.vlc_controller);
        vlcAudioTracks = findViewById(R.id.vlc_audio_tracks);
        vlcSubtitleTracks = findViewById(R.id.vlc_subtitle_tracks);
        vlcPlayPause = findViewById(R.id.vlc_play_pause);
        vlcViewMode = findViewById(R.id.video_view_mode);
        exoViewMode = findViewById(R.id.exo_view_mode);
        vlcSeekBar = findViewById(R.id.vlc_seek_bar);
        vlcCurrentTime = findViewById(R.id.vlc_current_time);
        vlcTotalTime = findViewById(R.id.vlc_total_time);

        viewMode = getSharedPreferences(PLAYER_SETTINGS, MODE_PRIVATE)
                .getInt(VIEW_MODE_KEY, VIEW_FIT);
        if (viewMode < VIEW_FIT || viewMode > VIEW_CROP) viewMode = VIEW_FIT;
        updateViewModeLabels();

        vlcAudioTracks.setOnClickListener(v -> showVlcTrackDialog(false));
        vlcSubtitleTracks.setOnClickListener(v -> showVlcTrackDialog(true));
        vlcPlayPause.setOnClickListener(v -> toggleVlcPlayback());
        vlcViewMode.setOnClickListener(v -> cycleViewMode());
        exoViewMode.setOnClickListener(v -> cycleViewMode());

        vlcSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                long duration = getPlaybackDuration();
                long target = duration > 0 ? (duration * progress / 1000L) : 0L;
                vlcCurrentTime.setText(formatTime(target));
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {
                userSeeking = true;
            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                long duration = getPlaybackDuration();
                if (vlcPlayer != null && duration > 0) {
                    long target = duration * seekBar.getProgress() / 1000L;
                    try { vlcPlayer.setTime(target); } catch (Exception ignored) {}
                }
                userSeeking = false;
            }
        });

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
        if (usingVlc) {
            handler.removeCallbacks(vlcProgressUpdater);
            handler.post(vlcProgressUpdater);
        }
    }

    @Override
    public void onPause() {
        saveResumePosition();
        handler.removeCallbacks(periodicResumeSave);
        handler.removeCallbacks(vlcProgressUpdater);
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
        handler.removeCallbacks(vlcProgressUpdater);
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
            long saved = ResumeUtils.getPosition(this, currentUrl);
            if (saved >= MIN_RESUME_MS) {
                startItemIndex = 0;
                startPosition = saved;
            }
        }

        if (isLikelyHevcSource(currentUrl) && !hasHardwareHevcDecoder()) {
            startSoftwareFallback();
            return true;
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
        vlcController.setVisibility(View.GONE);
        decoderBadge.setVisibility(View.GONE);
        exoViewMode.setVisibility(View.VISIBLE);
        applyViewMode();

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
        exoViewMode.setVisibility(View.GONE);
        vlcVideoLayout.setVisibility(View.VISIBLE);
        vlcController.setVisibility(View.VISIBLE);
        decoderBadge.setVisibility(View.VISIBLE);
        vlcAudioTracks.setEnabled(false);
        vlcSubtitleTracks.setEnabled(false);
        vlcSeekBar.setProgress(0);
        vlcCurrentTime.setText("00:00");
        vlcTotalTime.setText("00:00");
        vlcPlayPause.setText("Pause");
        showFeedback("Software decoder");

        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=1000");
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
            if (event.type == MediaPlayer.Event.Playing) {
                if (position >= MIN_RESUME_MS) {
                    try { vlcPlayer.setTime(position); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    refreshVlcTrackControls();
                    vlcPlayPause.setText("Pause");
                    applyViewMode();
                    handler.removeCallbacks(vlcProgressUpdater);
                    handler.post(vlcProgressUpdater);
                });
            } else if (event.type == MediaPlayer.Event.Paused) {
                runOnUiThread(() -> vlcPlayPause.setText("Play"));
            } else if (event.type == MediaPlayer.Event.EndReached) {
                clearResume();
                runOnUiThread(() -> {
                    vlcPlayPause.setText("Play");
                    updateVlcProgress();
                });
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
        handler.removeCallbacks(vlcProgressUpdater);
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
        softwareFallbackScheduled = false;
        if (vlcController != null) vlcController.setVisibility(View.GONE);
        if (decoderBadge != null) decoderBadge.setVisibility(View.GONE);
        if (exoViewMode != null) exoViewMode.setVisibility(View.VISIBLE);
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
            updateVlcProgress();
        } else if (player != null) {
            player.seekTo(target);
        }
        showFeedback((deltaMs > 0 ? "+10s" : "−10s") + " • " + formatTime(target));
    }

    private void toggleVlcPlayback() {
        if (!usingVlc || vlcPlayer == null) return;
        if (vlcPlayer.isPlaying()) {
            vlcPlayer.pause();
            vlcPlayPause.setText("Play");
        } else {
            vlcPlayer.play();
            vlcPlayPause.setText("Pause");
        }
    }

    private void updateVlcProgress() {
        if (!usingVlc || vlcPlayer == null) return;

        long position = Math.max(0, vlcPlayer.getTime());
        long duration = Math.max(0, vlcPlayer.getLength());

        if (!userSeeking) {
            int progress = duration > 0 ? (int) Math.min(1000L, position * 1000L / duration) : 0;
            vlcSeekBar.setProgress(progress);
            vlcCurrentTime.setText(formatTime(position));
        }
        vlcTotalTime.setText(formatTime(duration));
        vlcPlayPause.setText(vlcPlayer.isPlaying() ? "Pause" : "Play");
    }

    private void cycleViewMode() {
        viewMode = (viewMode + 1) % VIEW_MODE_LABELS.length;
        getSharedPreferences(PLAYER_SETTINGS, MODE_PRIVATE)
                .edit().putInt(VIEW_MODE_KEY, viewMode).apply();
        updateViewModeLabels();
        applyViewMode();
        showFeedback("View • " + VIEW_MODE_LABELS[viewMode]);
    }

    private void updateViewModeLabels() {
        String label = VIEW_MODE_LABELS[viewMode];
        if (vlcViewMode != null) vlcViewMode.setText(label);
        if (exoViewMode != null) exoViewMode.setText(label);
    }

    private void applyViewMode() {
        if (!usingVlc) {
            if (playerView == null) return;
            switch (viewMode) {
                case VIEW_ORIGINAL:
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH);
                    break;
                case VIEW_FILL:
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                    break;
                case VIEW_CROP:
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                    break;
                case VIEW_FIT:
                default:
                    playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                    break;
            }
            return;
        }

        if (vlcPlayer == null || vlcVideoLayout == null) return;
        try {
            vlcVideoLayout.setScaleX(1f);
            vlcVideoLayout.setScaleY(1f);

            switch (viewMode) {
                case VIEW_ORIGINAL:
                    vlcPlayer.setAspectRatio(null);
                    vlcPlayer.setScale(1f);
                    break;
                case VIEW_FILL:
                    DisplayMetrics metrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(metrics);
                    vlcPlayer.setScale(0f);
                    vlcPlayer.setAspectRatio(metrics.widthPixels + ":" + metrics.heightPixels);
                    break;
                case VIEW_CROP:
                    vlcPlayer.setAspectRatio(null);
                    vlcPlayer.setScale(0f);
                    vlcVideoLayout.setScaleX(1.18f);
                    vlcVideoLayout.setScaleY(1.18f);
                    break;
                case VIEW_FIT:
                default:
                    vlcPlayer.setAspectRatio(null);
                    vlcPlayer.setScale(0f);
                    break;
            }
        } catch (Throwable t) {
            Log.w("PlayerActivity", "Unable to apply VLC view mode", t);
        }
    }

    private void saveResumePosition() {
        if (currentUrl == null || currentUrl.isEmpty()) return;

        long position = getPlaybackPosition();
        long duration = getPlaybackDuration();

        if (position < MIN_RESUME_MS
                || (duration > 0 && duration - position <= FINISHED_THRESHOLD_MS)) {
            clearResume();
        } else {
            ResumeUtils.save(this, currentUrl, position);
        }
    }

    private void clearResume() {
        if (currentUrl != null) ResumeUtils.remove(this, currentUrl);
    }

    private boolean isLikelyHevcSource(String url) {
        if (url == null) return false;
        String name = Uri.decode(Uri.parse(url).getLastPathSegment() == null
                ? url : Uri.parse(url).getLastPathSegment()).toLowerCase(Locale.US);
        return name.contains("x265") || name.contains("h265")
                || name.contains("h.265") || name.contains("hevc");
    }

    private boolean hasHardwareHevcDecoder() {
        if (hardwareHevcAvailable != null) return hardwareHevcAvailable;
        boolean available = false;
        try {
            MediaCodecInfo[] infos = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
            for (MediaCodecInfo info : infos) {
                if (info == null || info.isEncoder()) continue;

                boolean supportsHevc = false;
                for (String type : info.getSupportedTypes()) {
                    if (MimeTypes.VIDEO_H265.equalsIgnoreCase(type)) {
                        supportsHevc = true;
                        break;
                    }
                }
                if (!supportsHevc) continue;

                if (Build.VERSION.SDK_INT >= 29) {
                    if (info.isHardwareAccelerated()) {
                        available = true;
                        break;
                    }
                } else {
                    String codecName = info.getName() == null
                            ? "" : info.getName().toLowerCase(Locale.US);
                    boolean software = codecName.contains("google")
                            || codecName.contains("android")
                            || codecName.contains("software")
                            || codecName.contains("sw")
                            || codecName.contains("ffmpeg");
                    if (!software) {
                        available = true;
                        break;
                    }
                }
            }
        } catch (Throwable ignored) {}
        hardwareHevcAvailable = available;
        return available;
    }

    private void scheduleSoftwareFallback() {
        if (usingVlc || softwareFallbackScheduled) return;
        softwareFallbackScheduled = true;
        handler.post(() -> {
            softwareFallbackScheduled = false;
            if (!usingVlc) startSoftwareFallback();
        });
    }

    private void refreshVlcTrackControls() {
        if (!usingVlc || vlcPlayer == null) return;
        MediaPlayer.TrackDescription[] audioTracks = vlcPlayer.getAudioTracks();
        MediaPlayer.TrackDescription[] subtitleTracks = vlcPlayer.getSpuTracks();
        vlcAudioTracks.setEnabled(audioTracks != null && audioTracks.length > 0);
        vlcSubtitleTracks.setEnabled(subtitleTracks != null && subtitleTracks.length > 0);
    }

    private void showVlcTrackDialog(boolean subtitles) {
        if (!usingVlc || vlcPlayer == null) return;

        MediaPlayer.TrackDescription[] tracks =
                subtitles ? vlcPlayer.getSpuTracks() : vlcPlayer.getAudioTracks();
        if (tracks == null || tracks.length == 0) {
            showToast(subtitles ? "No subtitle tracks found" : "No audio tracks found");
            return;
        }

        String[] labels = new String[tracks.length];
        for (int i = 0; i < tracks.length; i++) {
            String name = tracks[i].name;
            labels[i] = (name == null || name.trim().isEmpty())
                    ? (subtitles ? "Subtitle " : "Audio ") + (i + 1)
                    : name;
        }

        new AlertDialog.Builder(this)
                .setTitle(subtitles ? "Subtitles" : "Audio track")
                .setItems(labels, (dialog, which) -> {
                    if (which < 0 || which >= tracks.length) return;
                    if (subtitles) {
                        vlcPlayer.setSpuTrack(tracks[which].id);
                    } else {
                        vlcPlayer.setAudioTrack(tracks[which].id);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
            if (usingVlc) {
                vlcController.setVisibility(
                        vlcController.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            } else {
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
        public void onTracksChanged(Tracks tracks) {
            if (tracks == null || hasHardwareHevcDecoder()) return;
            for (Tracks.Group group : tracks.getGroups()) {
                for (int i = 0; i < group.length; i++) {
                    String mime = group.getTrackFormat(i).sampleMimeType;
                    if (MimeTypes.VIDEO_H265.equals(mime)) {
                        scheduleSoftwareFallback();
                        return;
                    }
                }
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            if (error.errorCode >= 4001 && error.errorCode <= 4004) {
                scheduleSoftwareFallback();
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
