package com.miracle.kglaynyi.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.IndexLink;
import com.miracle.kglaynyi.utils.GdiJsIndexClient;
import com.miracle.kglaynyi.utils.IndexFolderSelectionUtils;
import com.miracle.kglaynyi.utils.IndexUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SelectIndexFoldersFragment extends BaseFragment {

    private static final String ARG_INDEX_ID = "index_id";

    private TextView indexText;
    private TextView statusText;
    private ProgressBar progress;
    private LinearLayout checkboxContainer;
    private Button cancelButton;
    private Button saveButton;

    private IndexLink indexLink;
    private final Map<String, CheckBox> checkBoxes = new LinkedHashMap<>();
    private boolean bindingChecks;

    public SelectIndexFoldersFragment() {
        // Required empty constructor.
    }

    public SelectIndexFoldersFragment(IndexLink indexLink) {
        Bundle args = new Bundle();
        args.putInt(ARG_INDEX_ID, indexLink == null ? 0 : indexLink.getId());
        setArguments(args);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_select_index_folders, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        indexText = view.findViewById(R.id.folderPickerIndex);
        statusText = view.findViewById(R.id.folderPickerStatus);
        progress = view.findViewById(R.id.folderPickerProgress);
        checkboxContainer = view.findViewById(R.id.folderCheckboxContainer);
        cancelButton = view.findViewById(R.id.folderPickerCancel);
        saveButton = view.findViewById(R.id.folderPickerSave);

        cancelButton.setOnClickListener(v ->
                mActivity.getSupportFragmentManager().popBackStack());
        saveButton.setOnClickListener(v -> saveAndScan());

        int indexId = getArguments() == null ? 0 : getArguments().getInt(ARG_INDEX_ID, 0);
        if (indexId <= 0) {
            showError("Could not open this index.");
            return;
        }

        loadIndexAndFolders(indexId);
    }

    private void loadIndexAndFolders(int indexId) {
        setBusy(true, "Loading folders from GDI-JS…");

        new Thread(() -> {
            try {
                IndexLink saved = DatabaseClient.getInstance(mActivity)
                        .getAppDatabase().indexLinksDao().findById(indexId);
                if (saved == null) {
                    mActivity.runOnUiThread(() -> showError("Index was not found."));
                    return;
                }
                if (!"GDI-JS".equals(saved.getIndexType())) {
                    mActivity.runOnUiThread(() -> showError("Only GDI-JS indexes are supported."));
                    return;
                }

                List<GdiJsIndexClient.FolderOption> folders =
                        GdiJsIndexClient.listFolders(
                                saved.getLink(), saved.getUsername(), saved.getPassword());

                List<String> stored = IndexFolderSelectionUtils.parse(saved.getSelectedFoldersJson());
                Set<String> selected = new HashSet<>();
                if (stored == null) {
                    // Legacy GDI-JS indexes used root scanning.
                    selected.add("/");
                } else {
                    selected.addAll(stored);
                }

                mActivity.runOnUiThread(() -> {
                    indexLink = saved;
                    indexText.setText(saved.getLink());
                    renderFolders(folders, selected);
                    setBusy(false, selected.isEmpty()
                            ? "Select one or more folders."
                            : selected.size() + " folder" + (selected.size() == 1 ? "" : "s")
                            + " selected.");
                });
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = e.getClass().getSimpleName();
                }
                String finalMessage = "Could not load folders: " + message;
                mActivity.runOnUiThread(() -> showError(finalMessage));
            }
        }, "GdiJsFolderList").start();
    }

    private void renderFolders(List<GdiJsIndexClient.FolderOption> folders, Set<String> selected) {
        checkboxContainer.removeAllViews();
        checkBoxes.clear();
        bindingChecks = true;

        if (folders == null || folders.isEmpty()) {
            bindingChecks = false;
            showError("No folders were returned by this index.");
            return;
        }

        for (GdiJsIndexClient.FolderOption folder : folders) {
            if (folder == null || folder.path == null) continue;

            CheckBox checkBox = new CheckBox(mActivity);
            checkBox.setText(folder.displayPath);
            checkBox.setTextColor(Color.WHITE);
            checkBox.setButtonTintList(ColorStateList.valueOf(
                    getResources().getColor(R.color.download_button_bg_color)));
            checkBox.setPadding(8, 8, 8, 8);
            checkBox.setChecked(selected.contains(folder.path));
            checkBox.setTag(folder.path);

            checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (bindingChecks || !isChecked) {
                    updateSelectionStatus();
                    return;
                }

                String path = String.valueOf(buttonView.getTag());
                bindingChecks = true;
                if ("/".equals(path)) {
                    for (Map.Entry<String, CheckBox> entry : checkBoxes.entrySet()) {
                        if (!"/".equals(entry.getKey())) entry.getValue().setChecked(false);
                    }
                } else {
                    CheckBox root = checkBoxes.get("/");
                    if (root != null) root.setChecked(false);
                }
                bindingChecks = false;
                updateSelectionStatus();
            });

            checkBoxes.put(folder.path, checkBox);
            checkboxContainer.addView(checkBox);
        }

        bindingChecks = false;
        updateSelectionStatus();
    }

    private void updateSelectionStatus() {
        if (bindingChecks) return;
        int count = 0;
        for (CheckBox checkBox : checkBoxes.values()) {
            if (checkBox.isChecked()) count++;
        }
        if (progress.getVisibility() != View.VISIBLE) {
            statusText.setText(count == 0
                    ? "Select one or more folders."
                    : count + " folder" + (count == 1 ? "" : "s") + " selected.");
        }
        saveButton.setEnabled(count > 0 && indexLink != null);
    }

    private List<String> selectedPaths() {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, CheckBox> entry : checkBoxes.entrySet()) {
            if (entry.getValue().isChecked()) result.add(entry.getKey());
        }
        return result;
    }

    private void saveAndScan() {
        if (indexLink == null) return;
        List<String> selected = selectedPaths();
        if (selected.isEmpty()) {
            statusText.setText("Choose at least one folder.");
            return;
        }

        setBusy(true, "Saving folders and starting scan…");
        saveButton.setEnabled(false);
        cancelButton.setEnabled(false);

        IndexUtils.replaceSelectedFoldersAndRefresh(
                mActivity, indexLink, selected,
                scanProgress -> mActivity.runOnUiThread(() -> {
                    statusText.setText(scanProgress.message);
                    if (scanProgress.finished) {
                        progress.setVisibility(View.GONE);
                        cancelButton.setEnabled(true);
                        if (scanProgress.error) {
                            saveButton.setEnabled(true);
                            saveButton.setText("Save & Scan");
                        } else {
                            saveButton.setEnabled(true);
                            saveButton.setText("Scan Again");
                        }
                    }
                }));
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusText.setText(message);
        if (!busy) updateSelectionStatus();
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        statusText.setText(message);
        saveButton.setEnabled(false);
        cancelButton.setEnabled(true);
    }
}
