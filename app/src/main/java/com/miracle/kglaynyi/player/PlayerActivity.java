package com.miracle.kglaynyi.player;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.graphics.Insets;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

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
import com.google.android.exoplayer2.ui.TrackSelectionDialogBuilder;
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
    private static final String SUBTITLE_SIZE_KEY = "PLAYER_SUBTITLE_SIZE_SP";

    public static final String EXTRA_QUALITY_URLS = "quality_urls";
    public static final String EXTRA_QUALITY_LABELS = "quality_labels";
    public static final String EXTRA_RESUME_KEY = "resume_key";
    public static final String EXTRA_MEDIA_GROUP_KEY = "media_group_key";
    public static final String EXTRA_NEXT_URL = "next_url";
    public static final String EXTRA_NEXT_RESUME_KEY = "next_resume_key";
    public static final String EXTRA_NEXT_TITLE = "next_title";

    private static final int VIEW_FIT = 0;
    private static final int VIEW_ORIGINAL = 1;
    private static final int VIEW_FILL = 2;
    private static final int VIEW_CROP = 3;
    private static final int VIEW_16_9 = 4;
    private static final int VIEW_4_3 = 5;
    private static final String[] VIEW_MODE_LABELS = {"Fit", "Original", "Stretch", "Fill", "16:9", "4:3"};
    private static final String PROFILE_PREFS = "miracle_player_profiles";
    private static final String SPEED_KEY = "PLAYER_SPEED";
    private static final float[] PLAYBACK_SPEEDS =
            new float[]{0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

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
    private View exoSafeControls;
    private View exoBuiltInController;
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
    private String[] qualityUrls;
    private String[] qualityLabels;

    private String currentUrl;
    private String resumeKey;
    private String mediaGroupKey;
    private FrameLayout playerSettingsPanel;
    private Button playerSettingsButton;
    private Button tabVideo;
    private Button tabAudio;
    private Button tabSubtitle;
    private View videoSettingsSection;
    private View audioSettingsSection;
    private View subtitleSettingsSection;
    private SwitchCompat autoSkipSwitch;
    private TextView introSkipValue;
    private TextView endSkipValue;
    private Button playbackSpeedButton;
    private int introSkipSeconds;
    private int endSkipSeconds;
    private boolean autoSkipEnabled;
    private boolean introSkipApplied;
    private boolean endSkipApplied;
    private float playbackSpeed = 1.0f;
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

    private final Runnable skipWatcher = new Runnable() {
        @Override public void run() {
            applyAutoSkipIfNeeded();
            handler.postDelayed(this, 500L);
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
        enableDisplayCutoutPlayback();

        dataSourceFactory = DemoUtil.getDataSourceFactory(this);
        setContentView(R.layout.activity_player);

        playerView = findViewById(R.id.player_view);
        vlcVideoLayout = findViewById(R.id.vlc_video_layout);
        gestureFeedback = findViewById(R.id.gesture_feedback);
        decoderBadge = findViewById(R.id.decoder_badge);

        vlcController = findViewById(R.id.vlc_controller);
        exoSafeControls = findViewById(R.id.exo_safe_controls);
        exoBuiltInController = playerView.findViewById(
                com.google.android.exoplayer2.ui.R.id.exo_controller);
        vlcAudioTracks = findViewById(R.id.vlc_audio_tracks);
        vlcSubtitleTracks = findViewById(R.id.vlc_subtitle_tracks);
        vlcPlayPause = findViewById(R.id.vlc_play_pause);
        vlcViewMode = findViewById(R.id.video_view_mode);
        exoViewMode = findViewById(R.id.exo_view_mode);
        vlcSeekBar = findViewById(R.id.vlc_seek_bar);
        vlcCurrentTime = findViewById(R.id.vlc_current_time);
        vlcTotalTime = findViewById(R.id.vlc_total_time);

        playerSettingsPanel = findViewById(R.id.player_settings_panel);
        playerSettingsButton = findViewById(R.id.player_settings_button);
        tabVideo = findViewById(R.id.tab_video);
        tabAudio = findViewById(R.id.tab_audio);
        tabSubtitle = findViewById(R.id.tab_subtitle);
        videoSettingsSection = findViewById(R.id.video_settings_section);
        audioSettingsSection = findViewById(R.id.audio_settings_section);
        subtitleSettingsSection = findViewById(R.id.subtitle_settings_section);
        autoSkipSwitch = findViewById(R.id.auto_skip_switch);
        introSkipValue = findViewById(R.id.intro_skip_value);
        endSkipValue = findViewById(R.id.end_skip_value);
        playbackSpeedButton = findViewById(R.id.playback_speed_button);

        viewMode = getSharedPreferences(PLAYER_SETTINGS, MODE_PRIVATE)
                .getInt(VIEW_MODE_KEY, VIEW_FIT);
        if (viewMode < VIEW_FIT || viewMode > VIEW_4_3) viewMode = VIEW_FIT;
        playbackSpeed = getSharedPreferences(PLAYER_SETTINGS, MODE_PRIVATE)
                .getFloat(SPEED_KEY, 1.0f);
        updateViewModeLabels();
        readQualitySources(getIntent());
        readMediaIdentity(getIntent());
        loadPlayerProfile();
        bindSettingsPanel();

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
        playerView.setControllerShowTimeoutMs(3000);
        playerView.setControllerAutoShow(true);
        playerView.setControllerHideOnTouch(true);
        makeExoControllerTransparent();
        syncExoSafeControls();
        playerView.requestFocus();

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        maxVolume = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        gestureDetector = new GestureDetector(this, new PlayerGestureListener());

        View.OnTouchListener touchListener = (v, event) -> gestureDetector.onTouchEvent(event);
        playerView.setOnTouchListener(touchListener);
        vlcVideoLayout.setOnTouchListener(touchListener);
        installCutoutSafeInsets();

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

    private void enableDisplayCutoutPlayback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(attributes);
    }

    private void installCutoutSafeInsets() {
        View root = findViewById(R.id.root);
        if (root == null) return;

        final int baseSide = dpToPx(14);
        final int baseTop = dpToPx(14);
        final int baseBottom = dpToPx(10);

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int safeLeft = 0;
            int safeTop = 0;
            int safeRight = 0;
            int safeBottom = 0;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && insets.getDisplayCutout() != null) {
                safeLeft = Math.max(safeLeft, insets.getDisplayCutout().getSafeInsetLeft());
                safeTop = Math.max(safeTop, insets.getDisplayCutout().getSafeInsetTop());
                safeRight = Math.max(safeRight, insets.getDisplayCutout().getSafeInsetRight());
                safeBottom = Math.max(safeBottom, insets.getDisplayCutout().getSafeInsetBottom());
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Insets gestures = insets.getSystemGestureInsets();
                safeLeft = Math.max(safeLeft, gestures.left);
                safeRight = Math.max(safeRight, gestures.right);
                safeBottom = Math.max(safeBottom, gestures.bottom);
            }

            applySafeMargins(exoSafeControls,
                    baseSide + safeLeft,
                    baseTop + safeTop,
                    baseSide + safeRight,
                    baseTop);
            applySafeMargins(decoderBadge,
                    baseSide,
                    baseTop + safeTop,
                    baseSide + safeRight,
                    baseTop);
            applySafeMargins(playerSettingsButton,
                    baseSide,
                    baseTop + safeTop,
                    baseSide + safeRight,
                    baseTop);
            if (playerSettingsPanel != null) {
                playerSettingsPanel.setPadding(0, safeTop, safeRight, safeBottom);
            }

            if (vlcController != null) {
                vlcController.setPadding(
                        baseSide + safeLeft,
                        dpToPx(8),
                        baseSide + safeRight,
                        baseBottom + safeBottom);
            }

            if (exoBuiltInController != null) {
                exoBuiltInController.setPadding(
                        safeLeft,
                        safeTop,
                        safeRight,
                        safeBottom);
            }

            return insets;
        });
        root.requestApplyInsets();
    }

    private void applySafeMargins(View target, int left, int top, int right, int bottom) {
        if (target == null) return;
        ViewGroup.LayoutParams raw = target.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) raw;
        params.leftMargin = left;
        params.topMargin = top;
        params.rightMargin = right;
        params.bottomMargin = bottom;
        target.setLayoutParams(params);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        saveResumePosition();
        releaseExoPlayer();
        releaseVlcPlayer();
        clearStartPosition();
        setIntent(intent);
        readQualitySources(intent);
        readMediaIdentity(intent);
        loadPlayerProfile();
        introSkipApplied = false;
        endSkipApplied = false;
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
        handler.removeCallbacks(skipWatcher);
        handler.postDelayed(skipWatcher, 500L);
        if (usingVlc) {
            handler.removeCallbacks(vlcProgressUpdater);
            handler.post(vlcProgressUpdater);
        }
    }

    @Override
    public void onPause() {
        saveResumePosition();
        handler.removeCallbacks(periodicResumeSave);
        handler.removeCallbacks(skipWatcher);
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
        handler.removeCallbacks(skipWatcher);
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

    @Override
    public void onVisibilityChanged(int visibility) {
        if (usingVlc || exoSafeControls == null) return;
        exoSafeControls.animate().cancel();
        if (visibility == View.VISIBLE) {
            exoSafeControls.setAlpha(1f);
            exoSafeControls.setVisibility(View.VISIBLE);
        } else {
            exoSafeControls.animate()
                    .alpha(0f)
                    .setDuration(120L)
                    .withEndAction(() -> {
                        if (!usingVlc && exoSafeControls != null) {
                            exoSafeControls.setVisibility(View.GONE);
                            exoSafeControls.setAlpha(1f);
                        }
                    })
                    .start();
        }
    }

    @Override public void onClick(View view) {}

    private void syncExoSafeControls() {
        if (exoSafeControls == null || playerView == null || usingVlc) return;
        boolean visible = playerView.isControllerFullyVisible();
        exoSafeControls.setAlpha(1f);
        exoSafeControls.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void makeExoControllerTransparent() {
        if (playerView == null) return;

        // StyledPlayerView normally draws a large semi-transparent black layer
        // and a bottom gradient whenever its controls/settings are visible.
        // Keep only the controls themselves so the video remains fully visible.
        clearPlayerOverlayBackground("exo_controls_background", true);
        clearPlayerOverlayBackground("exo_bottom_bar", false);
        clearPlayerOverlayBackground("exo_basic_controls", false);
        clearPlayerOverlayBackground("exo_extra_controls", false);
    }

    private void clearPlayerOverlayBackground(String idName, boolean hideView) {
        int id = getResources().getIdentifier(idName, "id", getPackageName());
        if (id == 0) return;
        View target = playerView.findViewById(id);
        if (target == null) return;
        target.setBackgroundColor(Color.TRANSPARENT);
        if (hideView) {
            target.setAlpha(0f);
        }
    }

    private void readMediaIdentity(Intent intent) {
        if (intent == null) {
            resumeKey = null;
            mediaGroupKey = null;
            return;
        }
        resumeKey = intent.getStringExtra(EXTRA_RESUME_KEY);
        mediaGroupKey = intent.getStringExtra(EXTRA_MEDIA_GROUP_KEY);
    }

    private String playerProfileKey() {
        String value = mediaGroupKey;
        if (value == null || value.trim().isEmpty()) value = resumeKey;
        if (value == null || value.trim().isEmpty()) value = currentUrl;
        if (value == null) value = "default";
        return Integer.toHexString(value.hashCode());
    }

    private void loadPlayerProfile() {
        android.content.SharedPreferences prefs =
                getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE);
        String key = playerProfileKey();
        autoSkipEnabled = prefs.getBoolean("auto_" + key, false);
        introSkipSeconds = Math.max(0, prefs.getInt("intro_" + key, 0));
        endSkipSeconds = Math.max(0, prefs.getInt("end_" + key, 0));
        introSkipApplied = false;
        endSkipApplied = false;
        updateSkipUi();
    }

    private void savePlayerProfile() {
        String key = playerProfileKey();
        getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean("auto_" + key, autoSkipEnabled)
                .putInt("intro_" + key, introSkipSeconds)
                .putInt("end_" + key, endSkipSeconds)
                .apply();
    }

    private void bindSettingsPanel() {
        if (playerSettingsButton == null || playerSettingsPanel == null) return;

        playerSettingsButton.setOnClickListener(v -> {
            boolean opening = playerSettingsPanel.getVisibility() != View.VISIBLE;
            playerSettingsPanel.setVisibility(opening ? View.VISIBLE : View.GONE);
            if (opening) {
                showSettingsTab(0);
                playerView.showController();
            }
        });

        tabVideo.setOnClickListener(v -> showSettingsTab(0));
        tabAudio.setOnClickListener(v -> showSettingsTab(1));
        tabSubtitle.setOnClickListener(v -> showSettingsTab(2));

        findViewById(R.id.aspect_default).setOnClickListener(v -> setViewMode(VIEW_FIT));
        findViewById(R.id.aspect_stretch).setOnClickListener(v -> setViewMode(VIEW_FILL));
        findViewById(R.id.aspect_fill).setOnClickListener(v -> setViewMode(VIEW_CROP));
        findViewById(R.id.aspect_16_9).setOnClickListener(v -> setViewMode(VIEW_16_9));
        findViewById(R.id.aspect_4_3).setOnClickListener(v -> setViewMode(VIEW_4_3));

        autoSkipSwitch.setChecked(autoSkipEnabled);
        autoSkipSwitch.setOnCheckedChangeListener((button, checked) -> {
            autoSkipEnabled = checked;
            introSkipApplied = false;
            endSkipApplied = false;
            savePlayerProfile();
        });

        findViewById(R.id.intro_skip_minus).setOnClickListener(v -> adjustSkip(true, -5));
        findViewById(R.id.intro_skip_plus).setOnClickListener(v -> adjustSkip(true, 5));
        findViewById(R.id.end_skip_minus).setOnClickListener(v -> adjustSkip(false, -5));
        findViewById(R.id.end_skip_plus).setOnClickListener(v -> adjustSkip(false, 5));

        playbackSpeedButton.setOnClickListener(v -> cyclePlaybackSpeed());
        updatePlaybackSpeedLabel();

        findViewById(R.id.panel_audio_tracks).setOnClickListener(v -> showTrackSelection(false));
        findViewById(R.id.panel_subtitle_tracks).setOnClickListener(v -> showTrackSelection(true));
        updateAspectButtons();
    }

    private void showSettingsTab(int tab) {
        videoSettingsSection.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        audioSettingsSection.setVisibility(tab == 1 ? View.VISIBLE : View.GONE);
        subtitleSettingsSection.setVisibility(tab == 2 ? View.VISIBLE : View.GONE);
        int active = getResources().getColor(R.color.download_button_bg_color);
        int normal = Color.WHITE;
        tabVideo.setTextColor(tab == 0 ? active : normal);
        tabAudio.setTextColor(tab == 1 ? active : normal);
        tabSubtitle.setTextColor(tab == 2 ? active : normal);
    }

    private void adjustSkip(boolean intro, int deltaSeconds) {
        if (intro) {
            introSkipSeconds = Math.max(0, Math.min(3600, introSkipSeconds + deltaSeconds));
            introSkipApplied = false;
        } else {
            endSkipSeconds = Math.max(0, Math.min(3600, endSkipSeconds + deltaSeconds));
            endSkipApplied = false;
        }
        updateSkipUi();
        savePlayerProfile();
    }

    private void updateSkipUi() {
        if (introSkipValue != null) introSkipValue.setText(formatSkipTime(introSkipSeconds));
        if (endSkipValue != null) endSkipValue.setText(formatSkipTime(endSkipSeconds));
        if (autoSkipSwitch != null) {
            autoSkipSwitch.setOnCheckedChangeListener(null);
            autoSkipSwitch.setChecked(autoSkipEnabled);
            autoSkipSwitch.setOnCheckedChangeListener((button, checked) -> {
                autoSkipEnabled = checked;
                introSkipApplied = false;
                endSkipApplied = false;
                savePlayerProfile();
            });
        }
    }

    private String formatSkipTime(int totalSeconds) {
        int minutes = Math.max(0, totalSeconds) / 60;
        int seconds = Math.max(0, totalSeconds) % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private void cyclePlaybackSpeed() {
        int current = 0;
        float best = Float.MAX_VALUE;
        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            float diff = Math.abs(PLAYBACK_SPEEDS[i] - playbackSpeed);
            if (diff < best) {
                best = diff;
                current = i;
            }
        }
        playbackSpeed = PLAYBACK_SPEEDS[(current + 1) % PLAYBACK_SPEEDS.length];
        getSharedPreferences(PLAYER_SETTINGS, MODE_PRIVATE)
                .edit().putFloat(SPEED_KEY, playbackSpeed).apply();
        applyPlaybackSpeed();
        updatePlaybackSpeedLabel();
        showFeedback(String.format(Locale.US, "Speed • %.2gx", playbackSpeed));
    }

    private void updatePlaybackSpeedLabel() {
        if (playbackSpeedButton == null) return;
        String value = playbackSpeed == 1f
                ? "1.0x"
                : String.format(Locale.US, "%.2gx", playbackSpeed);
        playbackSpeedButton.setText("Playback Speed • " + value);
    }

    private void applyPlaybackSpeed() {
        if (usingVlc && vlcPlayer != null) {
            try { vlcPlayer.setRate(playbackSpeed); } catch (Exception ignored) {}
        } else if (player != null) {
            try { player.setPlaybackSpeed(playbackSpeed); } catch (Exception ignored) {}
        }
    }

    private void showTrackSelection(boolean subtitle) {
        if (usingVlc) {
            showVlcTrackDialog(subtitle);
            return;
        }
        if (player == null) {
            showToast("Player is still loading");
            return;
        }
        int type = subtitle ? C.TRACK_TYPE_TEXT : C.TRACK_TYPE_AUDIO;
        String title = subtitle ? "Subtitle Tracks" : "Audio Tracks";
        try {
            new TrackSelectionDialogBuilder(this, title, player, type)
                    .setAllowAdaptiveSelections(true)
                    .build()
                    .show();
        } catch (Throwable error) {
            showToast("Track selection is unavailable for this source");
        }
    }

    private void setViewMode(int mode) {
        viewMode = Math.max(VIEW_FIT, Math.min(VIEW_4_3, mode));
        getSharedPreferences(PLAYER_SETTINGS, MODE_PRIVATE)
                .edit().putInt(VIEW_MODE_KEY, viewMode).apply();
        updateViewModeLabels();
        updateAspectButtons();
        applyViewMode();
        showFeedback("Aspect • " + VIEW_MODE_LABELS[viewMode]);
    }

    private void updateAspectButtons() {
        int active = getResources().getColor(R.color.download_button_bg_color);
        int normal = Color.WHITE;
        View[] views = new View[]{
                findViewById(R.id.aspect_default),
                findViewById(R.id.aspect_stretch),
                findViewById(R.id.aspect_fill),
                findViewById(R.id.aspect_16_9),
                findViewById(R.id.aspect_4_3)};
        int[] modes = new int[]{VIEW_FIT, VIEW_FILL, VIEW_CROP, VIEW_16_9, VIEW_4_3};
        for (int i = 0; i < views.length; i++) {
            if (views[i] instanceof Button) {
                ((Button) views[i]).setTextColor(viewMode == modes[i] ? active : normal);
            }
        }
    }

    private void applyAutoSkipIfNeeded() {
        if (!autoSkipEnabled || (player == null && vlcPlayer == null)) return;
        long position = getPlaybackPosition();
        long duration = getPlaybackDuration();

        if (!introSkipApplied && introSkipSeconds > 0
                && position >= 500L
                && position < introSkipSeconds * 1000L - 250L) {
            introSkipApplied = true;
            seekAbsolute(introSkipSeconds * 1000L);
            showFeedback("Intro skipped");
            return;
        }

        if (!endSkipApplied && endSkipSeconds > 0 && duration > 0
                && position > MIN_RESUME_MS
                && duration - position <= endSkipSeconds * 1000L) {
            endSkipApplied = true;
            clearResume();
            String nextUrl = getIntent().getStringExtra(EXTRA_NEXT_URL);
            if (nextUrl != null && !nextUrl.trim().isEmpty()) {
                playNextEpisode(nextUrl);
            } else {
                seekAbsolute(Math.max(0, duration - 500L));
            }
        }
    }

    private void seekAbsolute(long targetMs) {
        long duration = getPlaybackDuration();
        long target = Math.max(0, targetMs);
        if (duration > 0) target = Math.min(duration, target);
        if (usingVlc && vlcPlayer != null) {
            try { vlcPlayer.setTime(target); } catch (Exception ignored) {}
            updateVlcProgress();
        } else if (player != null) {
            player.seekTo(target);
        }
    }

    private void playNextEpisode(String nextUrl) {
        Intent next = new Intent(this, PlayerActivity.class);
        next.putExtra("url", nextUrl);
        next.putExtra(EXTRA_RESUME_KEY,
                getIntent().getStringExtra(EXTRA_NEXT_RESUME_KEY));
        next.putExtra(EXTRA_MEDIA_GROUP_KEY, mediaGroupKey);
        String title = getIntent().getStringExtra(EXTRA_NEXT_TITLE);
        if (title != null) next.putExtra(EXTRA_NEXT_TITLE, title);
        startActivity(next);
        finish();
    }

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
            long saved = ResumeUtils.getPositionForMedia(this, resumeKey, currentUrl);
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
        makeExoControllerTransparent();
        syncExoSafeControls();
        applyViewMode();

        player.setMediaItem(mediaItem);
        if (startItemIndex != C.INDEX_UNSET && startPosition != C.TIME_UNSET) {
            player.seekTo(Math.max(0, startPosition));
            showFeedback("Resume • " + formatTime(startPosition));
        }
        player.prepare();
        applyPlaybackSpeed();
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
        if (exoSafeControls != null) exoSafeControls.setVisibility(View.GONE);
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
        int subtitleSize = getSharedPreferences(PLAYER_SETTINGS, MODE_PRIVATE)
                .getInt(SUBTITLE_SIZE_KEY, 22);
        options.add("--freetype-rel-fontsize=" + getVlcRelativeFontSize(subtitleSize));

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
                if (position > 0) {
                    try { vlcPlayer.setTime(position); } catch (Exception ignored) {}
                }
                if (!startAutoPlay) {
                    try { vlcPlayer.pause(); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> {
                    refreshVlcTrackControls();
                    vlcPlayPause.setText("Pause");
                    applyViewMode();
                    applyPlaybackSpeed();
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
        if (exoSafeControls != null) exoSafeControls.setVisibility(View.GONE);
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
        if (viewMode == VIEW_16_9 || viewMode == VIEW_4_3) {
            applyExplicitAspectRatio(viewMode == VIEW_16_9 ? 16f / 9f : 4f / 3f);
        } else {
            resetVideoLayoutSize();
        }

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
                case VIEW_16_9:
                case VIEW_4_3:
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
                case VIEW_16_9:
                    vlcPlayer.setScale(0f);
                    vlcPlayer.setAspectRatio("16:9");
                    break;
                case VIEW_4_3:
                    vlcPlayer.setScale(0f);
                    vlcPlayer.setAspectRatio("4:3");
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

    private void resetVideoLayoutSize() {
        if (playerView != null) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            p.gravity = Gravity.CENTER;
            playerView.setLayoutParams(p);
        }
        if (vlcVideoLayout != null) {
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            p.gravity = Gravity.CENTER;
            vlcVideoLayout.setLayoutParams(p);
        }
    }

    private void applyExplicitAspectRatio(float ratio) {
        View root = findViewById(R.id.root);
        if (root == null || root.getWidth() <= 0 || root.getHeight() <= 0) {
            if (root != null) root.post(() -> applyExplicitAspectRatio(ratio));
            return;
        }
        int width = root.getWidth();
        int height = root.getHeight();
        float rootRatio = width / (float) height;
        int targetWidth;
        int targetHeight;
        if (rootRatio > ratio) {
            targetHeight = height;
            targetWidth = Math.round(height * ratio);
        } else {
            targetWidth = width;
            targetHeight = Math.round(width / ratio);
        }
        FrameLayout.LayoutParams params =
                new FrameLayout.LayoutParams(targetWidth, targetHeight, Gravity.CENTER);
        if (playerView != null) playerView.setLayoutParams(new FrameLayout.LayoutParams(params));
        if (vlcVideoLayout != null) vlcVideoLayout.setLayoutParams(new FrameLayout.LayoutParams(params));
    }

    public boolean showSourceQualityDialog(PlayerQualityButton button) {
        if (qualityUrls == null || qualityUrls.length < 2) return false;

        int checked = findCurrentSourceIndex();
        new AlertDialog.Builder(this)
                .setTitle("Video quality")
                .setSingleChoiceItems(qualityLabels, checked, (dialog, which) -> {
                    if (which < 0 || which >= qualityUrls.length) return;
                    String label = qualityLabels[which];
                    dialog.dismiss();
                    switchSourceQuality(which);
                    if (button != null) button.setText(compactQualityLabel(label));
                })
                .setNegativeButton("Cancel", null)
                .show();
        return true;
    }

    public void applySubtitleSizeSetting(int sizeSp) {
        getSharedPreferences(PLAYER_SETTINGS, MODE_PRIVATE)
                .edit().putInt(SUBTITLE_SIZE_KEY, sizeSp).apply();
        if (usingVlc && vlcPlayer != null) {
            restartVlcForSubtitleSize();
        }
    }

    private int getVlcRelativeFontSize(int sizeSp) {
        if (sizeSp <= 14) return 22;
        if (sizeSp <= 18) return 19;
        if (sizeSp <= 22) return 16;
        if (sizeSp <= 26) return 13;
        if (sizeSp <= 30) return 12;
        if (sizeSp <= 36) return 10;
        return 8;
    }

    private void restartVlcForSubtitleSize() {
        if (!usingVlc || vlcPlayer == null) return;
        long position = getPlaybackPosition();
        boolean playWhenReady = vlcPlayer.isPlaying();
        releaseVlcPlayer();
        startItemIndex = 0;
        startPosition = position;
        startAutoPlay = playWhenReady;
        startSoftwareFallback();
        showFeedback("Subtitle size updated");
    }

    private void readQualitySources(Intent intent) {
        if (intent == null) {
            qualityUrls = null;
            qualityLabels = null;
            return;
        }
        qualityUrls = intent.getStringArrayExtra(EXTRA_QUALITY_URLS);
        qualityLabels = intent.getStringArrayExtra(EXTRA_QUALITY_LABELS);
        if (qualityUrls == null || qualityUrls.length == 0) {
            qualityUrls = null;
            qualityLabels = null;
            return;
        }
        if (qualityLabels == null || qualityLabels.length != qualityUrls.length) {
            qualityLabels = new String[qualityUrls.length];
            for (int i = 0; i < qualityUrls.length; i++) {
                qualityLabels[i] = "Source " + (i + 1);
            }
        }
    }

    private int findCurrentSourceIndex() {
        if (qualityUrls == null || currentUrl == null) return -1;
        for (int i = 0; i < qualityUrls.length; i++) {
            if (currentUrl.equals(qualityUrls[i])) return i;
        }
        return -1;
    }

    private void switchSourceQuality(int index) {
        if (qualityUrls == null || index < 0 || index >= qualityUrls.length) return;
        String newUrl = qualityUrls[index];
        if (newUrl == null || newUrl.trim().isEmpty() || newUrl.equals(currentUrl)) return;

        long position = getPlaybackPosition();
        boolean playWhenReady = isPlayingRequested();
        saveResumePosition();
        releaseExoPlayer();
        releaseVlcPlayer();

        currentUrl = newUrl;
        getIntent().putExtra("url", newUrl);
        trackSelectionParameters = trackSelectionParameters.buildUpon()
                .clearVideoSizeConstraints()
                .setForceHighestSupportedBitrate(false)
                .build();
        startItemIndex = 0;
        startPosition = position;
        startAutoPlay = playWhenReady;
        softwareFallbackScheduled = false;
        initializePlayer();

        String label = qualityLabels != null && index < qualityLabels.length
                ? qualityLabels[index] : "Source " + (index + 1);
        showFeedback("Quality • " + label);
    }

    private String compactQualityLabel(String label) {
        if (label == null || label.trim().isEmpty()) return "Quality";
        String lower = label.toLowerCase(Locale.US);
        if (lower.contains("2160") || lower.contains("4k")) return "4K";
        if (lower.contains("1440")) return "1440p";
        if (lower.contains("1080")) return "1080p";
        if (lower.contains("720")) return "720p";
        if (lower.contains("480")) return "480p";
        return label.length() > 12 ? "Quality" : label;
    }

    private void saveResumePosition() {
        if (currentUrl == null || currentUrl.isEmpty()) return;

        long position = getPlaybackPosition();
        long duration = getPlaybackDuration();

        if (position < MIN_RESUME_MS
                || (duration > 0 && duration - position <= FINISHED_THRESHOLD_MS)) {
            clearResume();
        } else {
            ResumeUtils.saveForMedia(this, resumeKey, currentUrl, position);
        }
    }

    private void clearResume() {
        ResumeUtils.removeForMedia(this, resumeKey, currentUrl);
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
