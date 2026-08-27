package com.miracle.kglaynyi.player;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.miracle.kglaynyi.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** MX-style quality picker for adaptive/multi-track ExoPlayer sources. */
public class PlayerQualityButton extends AppCompatButton {

    public PlayerQualityButton(Context context) {
        super(context);
        init();
    }

    public PlayerQualityButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PlayerQualityButton(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        super.setOnClickListener(v -> showQualityDialog());
    }

    private void showQualityDialog() {
        if (getContext() instanceof PlayerActivity
                && ((PlayerActivity) getContext()).showSourceQualityDialog(this)) {
            return;
        }
        View root = getRootView();
        StyledPlayerView playerView = root.findViewById(R.id.player_view);
        if (playerView == null || playerView.getVisibility() != View.VISIBLE) {
            new AlertDialog.Builder(getContext())
                    .setTitle("Quality")
                    .setMessage("Software decoder uses the original source quality.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        Player player = playerView.getPlayer();
        if (player == null) return;

        Set<Integer> heightSet = new LinkedHashSet<>();
        int selectedHeight = 0;
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                if (format.height > 0) heightSet.add(format.height);
                if (group.isTrackSelected(i) && format.height > 0) selectedHeight = format.height;
            }
        }

        List<Integer> heights = new ArrayList<>(heightSet);
        Collections.sort(heights, Collections.reverseOrder());

        if (heights.size() <= 1) {
            String source = heights.isEmpty() ? "Original source" : heights.get(0) + "p (source)";
            new AlertDialog.Builder(getContext())
                    .setTitle("Quality")
                    .setMessage(source + "\n\nThis file does not expose multiple video qualities.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        String[] labels = new String[heights.size() + 1];
        labels[0] = "Auto";
        int checked = 0;
        for (int i = 0; i < heights.size(); i++) {
            labels[i + 1] = heights.get(i) + "p";
            if (heights.get(i) == selectedHeight) checked = i + 1;
        }

        final int currentChecked = checked;
        new AlertDialog.Builder(getContext())
                .setTitle("Quality")
                .setSingleChoiceItems(labels, currentChecked, (dialog, which) -> {
                    TrackSelectionParameters.Builder builder = player.getTrackSelectionParameters().buildUpon();
                    builder.clearVideoSizeConstraints();
                    if (which == 0) {
                        builder.setForceHighestSupportedBitrate(false);
                        setText("Quality");
                    } else {
                        int targetHeight = heights.get(which - 1);
                        builder.setMaxVideoSize(Integer.MAX_VALUE, targetHeight);
                        builder.setForceHighestSupportedBitrate(true);
                        setText(targetHeight + "p");
                    }
                    player.setTrackSelectionParameters(builder.build());
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
