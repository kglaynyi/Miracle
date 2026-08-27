package com.miracle.kglaynyi.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.IndexLink;
import com.miracle.kglaynyi.utils.GdiJsIndexClient;
import com.miracle.kglaynyi.utils.GoogleDriveFolderClient;

public class AddNewIndexFragment extends BaseFragment {

    private static final int REQUEST_DRIVE_FOLDER = 4021;

    private EditText indexLinkView;
    private EditText userNameView;
    private EditText passWordView;
    private Button save;
    private Button drivePickerButton;
    private ProgressBar progressCircular;
    private TextView statusText;
    private AutoCompleteTextView indexTypeView;
    private AutoCompleteTextView folderTypeView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_index, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        indexLinkView = view.findViewById(R.id.indexlink);
        userNameView = view.findViewById(R.id.username);
        passWordView = view.findViewById(R.id.password);
        save = view.findViewById(R.id.save);
        drivePickerButton = view.findViewById(R.id.drive_picker_button);
        progressCircular = view.findViewById(R.id.progress_circular);
        statusText = view.findViewById(R.id.suggestRefresh);
        indexTypeView = view.findViewById(R.id.actv);
        folderTypeView = view.findViewById(R.id.actv2);

        String[] indexTypes = mActivity.getResources().getStringArray(R.array.index_types);
        indexTypeView.setAdapter(new ArrayAdapter<>(mActivity, R.layout.item_index_type, indexTypes));
        indexTypeView.setText("GDI-JS", false);
        indexTypeView.setEnabled(false);

        String[] folderTypes = mActivity.getResources().getStringArray(R.array.folder_types);
        folderTypeView.setAdapter(new ArrayAdapter<>(mActivity, R.layout.item_folder_type, folderTypes));
        if (folderTypes.length > 0) {
            folderTypeView.setText(folderTypes[0], false);
        }

        save.setOnClickListener(v -> addGdiJsIndex());
        drivePickerButton.setOnClickListener(v -> chooseGoogleDriveFolder());
    }

    private void chooseGoogleDriveFolder() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_DRIVE_FOLDER);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_DRIVE_FOLDER || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }

        Uri treeUri = data.getData();
        try {
            mActivity.getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            setBusy(false, "Could not keep Google Drive folder permission. Please select it again.");
            return;
        }

        addGoogleDriveSource(treeUri);
    }

    private void addGoogleDriveSource(Uri treeUri) {
        final String tree = treeUri.toString();
        final String folderName = GoogleDriveFolderClient.getFolderDisplayName(mActivity, treeUri);
        setBusy(true, "Google Drive connected • scanning " + folderName + "…");

        new Thread(() -> {
            try {
                IndexLink existing = DatabaseClient.getInstance(mActivity)
                        .getAppDatabase().indexLinksDao().find(tree);
                if (existing != null) {
                    mActivity.runOnUiThread(() -> setBusy(false,
                            "This Google Drive folder is already added. Refresh it in Manage Sources."));
                    return;
                }

                IndexLink source = new IndexLink();
                source.setLink(tree);
                source.setUsername(folderName);
                source.setPassword("");
                source.setIndexType("Google Drive");
                source.setFolderType("Movies + TV Shows");

                DatabaseClient.getInstance(mActivity).getAppDatabase().indexLinksDao().insert(source);
                IndexLink saved = DatabaseClient.getInstance(mActivity)
                        .getAppDatabase().indexLinksDao().find(tree);
                if (saved == null) {
                    mActivity.runOnUiThread(() -> setBusy(false, "Could not save Google Drive source"));
                    return;
                }

                int found = GoogleDriveFolderClient.scan(
                        mActivity, tree, saved.getId(),
                        progress -> mActivity.runOnUiThread(() -> setBusy(true, progress.message)));

                String result = found > 0
                        ? "Google Drive scan complete • " + found + " video" + (found == 1 ? "" : "s")
                        : "Google Drive connected, but no supported videos were found in this folder.";
                mActivity.runOnUiThread(() -> {
                    setBusy(false, result);
                    drivePickerButton.setText("Google Drive • Select Another Folder");
                });
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
                String finalMessage = "Google Drive scan failed: " + message;
                mActivity.runOnUiThread(() -> setBusy(false, finalMessage));
            }
        }, "GoogleDriveFolderScan").start();
    }

    private void addGdiJsIndex() {
        String link = indexLinkView.getText().toString().trim();
        String user = userNameView.getText().toString();
        String pass = passWordView.getText().toString();
        String folderType = folderTypeView.getText().toString().trim();

        if (link.isEmpty()) {
            indexLinkView.setError("Enter GDI-JS index link");
            return;
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            indexLinkView.setError("URL must start with http:// or https://");
            return;
        }
        if (folderType.isEmpty()) {
            folderTypeView.setError("Select Movies + TV Shows, Movies, or TVShows");
            return;
        }

        setBusy(true, "Checking GDI-JS URL and login…");

        new Thread(() -> {
            GdiJsIndexClient.Result validation = GdiJsIndexClient.validate(link, user, pass);
            if (!validation.success) {
                mActivity.runOnUiThread(() -> setBusy(false, validation.message));
                return;
            }

            try {
                if (DatabaseClient.getInstance(mActivity).getAppDatabase()
                        .indexLinksDao().find(link) != null) {
                    mActivity.runOnUiThread(() -> setBusy(false,
                            "This GDI-JS index is already added. Refresh it in Manage Sources."));
                    return;
                }

                IndexLink indexLink = new IndexLink();
                indexLink.setLink(link);
                indexLink.setUsername(user);
                indexLink.setPassword(pass);
                indexLink.setIndexType("GDI-JS");
                indexLink.setFolderType(folderType);

                DatabaseClient.getInstance(mActivity).getAppDatabase().indexLinksDao().insert(indexLink);
                IndexLink saved = DatabaseClient.getInstance(mActivity)
                        .getAppDatabase().indexLinksDao().find(link);
                if (saved == null) {
                    mActivity.runOnUiThread(() -> setBusy(false, "Could not save GDI-JS index"));
                    return;
                }

                int found = GdiJsIndexClient.scan(
                        link, user, pass, "TVShows".equals(folderType), saved.getId(),
                        progress -> mActivity.runOnUiThread(() -> setBusy(true, progress.message)));

                String result = found > 0
                        ? "GDI-JS scan complete • " + found + " video" + (found == 1 ? "" : "s")
                        : "GDI-JS connected, but no supported videos were found.";
                mActivity.runOnUiThread(() -> {
                    setBusy(false, result);
                    save.setText("Done");
                });
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
                String finalMessage = "GDI-JS scan failed: " + message;
                mActivity.runOnUiThread(() -> setBusy(false, finalMessage));
            }
        }, "GdiJsAddScan").start();
    }

    private void setBusy(boolean busy, String message) {
        save.setEnabled(!busy);
        drivePickerButton.setEnabled(!busy);
        if (busy) {
            save.setText("Working…");
        } else if (!"Done".contentEquals(save.getText())) {
            save.setText("Save GDI-JS");
        }
        progressCircular.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message);
    }
}
