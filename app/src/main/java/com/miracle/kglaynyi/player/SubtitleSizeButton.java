package com.miracle.kglaynyi.player;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.miracle.kglaynyi.R;

/** Runtime subtitle text-size control for ExoPlayer playback. */
public class SubtitleSizeButton extends AppCompatButton {
    private static final String PREFS = "Settings";
    private static final String KEY = "PLAYER_SUBTITLE_SIZE_SP";
    private static final int[] SIZES = {14, 18, 22, 26, 30, 36, 42};

    public SubtitleSizeButton(Context context) {
        super(context);
        init();
    }

    public SubtitleSizeButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SubtitleSizeButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int saved = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 22);
        setText("Sub " + saved);
        super.setOnClickListener(v -> showSizeDialog());
        post(this::applySavedSize);
    }

    private void showSizeDialog() {
        StyledPlayerView playerView = getRootView().findViewById(R.id.player_view);

        int saved = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 22);
        String[] labels = new String[SIZES.length];
        int checked = 0;
        for (int i = 0; i < SIZES.length; i++) {
            labels[i] = sizeLabel(SIZES[i]);
            if (SIZES[i] == saved) checked = i;
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Subtitle size")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    int size = SIZES[which];
                    getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putInt(KEY, size).apply();
                    if (playerView != null && playerView.getVisibility() == View.VISIBLE) {
                        applySize(playerView, size);
                    }
                    if (getContext() instanceof PlayerActivity) {
                        ((PlayerActivity) getContext()).applySubtitleSizeSetting(size);
                    }
                    setText("Sub " + size);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applySavedSize() {
        StyledPlayerView playerView = getRootView().findViewById(R.id.player_view);
        if (playerView == null) return;
        int size = getContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY, 22);
        applySize(playerView, size);
    }

    private void applySize(StyledPlayerView playerView, int sizeSp) {
        SubtitleView subtitleView = playerView.getSubtitleView();
        if (subtitleView != null) {
            subtitleView.setApplyEmbeddedStyles(true);
            subtitleView.setApplyEmbeddedFontSizes(false);
            subtitleView.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        }
    }

    private String sizeLabel(int sp) {
        if (sp <= 14) return "Very small • " + sp;
        if (sp <= 18) return "Small • " + sp;
        if (sp <= 22) return "Normal • " + sp;
        if (sp <= 26) return "Large • " + sp;
        if (sp <= 30) return "Very large • " + sp;
        if (sp <= 36) return "Extra large • " + sp;
        return "Huge • " + sp;
    }
}
