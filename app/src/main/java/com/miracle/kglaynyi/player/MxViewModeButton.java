package com.miracle.kglaynyi.player;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

/**
 * Turns Miracle's existing one-tap view-mode cycle into an MX Player style picker.
 * PlayerActivity still owns the actual ExoPlayer/libVLC resize logic; this view
 * simply calls its registered click listener enough times to reach the chosen mode.
 */
public class MxViewModeButton extends AppCompatButton {
    private static final String[] INTERNAL_LABELS = {"Fit", "Original", "Full", "Crop"};
    private static final String[] MENU_LABELS = {
            "Fit to screen",
            "Original / 100%",
            "Stretch to full screen",
            "Crop / Zoom to fill"
    };

    private OnClickListener playerDelegate;
    private boolean installingInternalListener;

    public MxViewModeButton(Context context) {
        super(context);
        init();
    }

    public MxViewModeButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MxViewModeButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        installingInternalListener = true;
        super.setOnClickListener(v -> showModeDialog());
        installingInternalListener = false;
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        if (installingInternalListener) {
            super.setOnClickListener(l);
        } else {
            // PlayerActivity registers its normal cycleViewMode() callback here.
            // Keep it as a delegate while preserving our picker UI.
            playerDelegate = l;
        }
    }

    private void showModeDialog() {
        int current = currentModeIndex();
        new AlertDialog.Builder(getContext())
                .setTitle("Screen")
                .setSingleChoiceItems(MENU_LABELS, current, (dialog, which) -> {
                    selectMode(which);
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int currentModeIndex() {
        String value = getText() == null ? "" : getText().toString();
        for (int i = 0; i < INTERNAL_LABELS.length; i++) {
            if (INTERNAL_LABELS[i].equalsIgnoreCase(value)) return i;
        }
        return 0;
    }

    private void selectMode(int target) {
        if (playerDelegate == null || target < 0 || target >= INTERNAL_LABELS.length) return;
        int current = currentModeIndex();
        int steps = (target - current + INTERNAL_LABELS.length) % INTERNAL_LABELS.length;
        for (int i = 0; i < steps; i++) {
            playerDelegate.onClick(this);
        }
        if (steps == 0) {
            // Keep the current mode; no resize work is necessary.
            setText(INTERNAL_LABELS[target]);
        }
    }
}
