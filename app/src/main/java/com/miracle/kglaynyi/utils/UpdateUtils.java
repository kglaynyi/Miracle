package com.miracle.kglaynyi.utils;

import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.fragments.UpdateAppFragment;
import com.miracle.kglaynyi.model.GitHubResponse;

import java.util.concurrent.Executors;

public class UpdateUtils {
    private static final String TAG = "UpdateUtils";

    public static void checkForUpdates(AppCompatActivity appCompatActivity) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                GitHubResponse[] gitHubResponses = new CheckForUpdates().checkForUpdates();
                if (gitHubResponses == null || gitHubResponses.length == 0) {
                    return;
                }

                appCompatActivity.runOnUiThread(() -> {
                    if (appCompatActivity.isFinishing() || appCompatActivity.isDestroyed()) {
                        return;
                    }
                    try {
                        UpdateAppFragment updateAppFragment = new UpdateAppFragment(gitHubResponses);
                        appCompatActivity.getSupportFragmentManager()
                                .beginTransaction()
                                .add(R.id.container, updateAppFragment)
                                .addToBackStack(null)
                                .commitAllowingStateLoss();
                    } catch (RuntimeException e) {
                        Log.e(TAG, "Unable to show update dialog", e);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Update check failed; continuing without update prompt", e);
            }
        });
    }
}
