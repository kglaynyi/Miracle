package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

public class AddNewIndexFragment extends BaseFragment {

    private EditText indexLinkView;
    private EditText userNameView;
    private EditText passWordView;
    private Button save;
    private ProgressBar progressCircular;
    private TextView statusText;

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
        progressCircular = view.findViewById(R.id.progress_circular);
        statusText = view.findViewById(R.id.suggestRefresh);

        save.setText("Add GDI-JS Index");
        save.setOnClickListener(v -> addGdiJsIndex());
    }

    private void addGdiJsIndex() {
        String link = indexLinkView.getText().toString().trim();
        String user = userNameView.getText().toString();
        String pass = passWordView.getText().toString();

        if (link.isEmpty()) {
            indexLinkView.setError("Enter GDI-JS index link");
            return;
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            indexLinkView.setError("URL must start with http:// or https://");
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
                IndexLink existing = DatabaseClient.getInstance(mActivity)
                        .getAppDatabase().indexLinksDao().find(link);
                if (existing != null) {
                    mActivity.runOnUiThread(() -> {
                        setBusy(false, "This GDI-JS index is already added.");
                        openFolderSelector(existing);
                    });
                    return;
                }

                IndexLink indexLink = new IndexLink();
                indexLink.setLink(link);
                indexLink.setUsername(user);
                indexLink.setPassword(pass);
                indexLink.setIndexType("GDI-JS");
                indexLink.setFolderType("Movies + TV Shows");
                // New indexes do not scan the root automatically. The user selects
                // one or more folders on the next screen.
                indexLink.setSelectedFoldersJson("[]");

                DatabaseClient.getInstance(mActivity).getAppDatabase()
                        .indexLinksDao().insert(indexLink);

                IndexLink saved = DatabaseClient.getInstance(mActivity)
                        .getAppDatabase().indexLinksDao().find(link);
                if (saved == null) {
                    mActivity.runOnUiThread(() ->
                            setBusy(false, "Could not save GDI-JS index"));
                    return;
                }

                mActivity.runOnUiThread(() -> {
                    setBusy(false, "Index added. Select folders to scan.");
                    openFolderSelector(saved);
                });
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = e.getClass().getSimpleName();
                }
                String finalMessage = "Could not add GDI-JS index: " + message;
                mActivity.runOnUiThread(() -> setBusy(false, finalMessage));
            }
        }, "GdiJsAddIndex").start();
    }

    private void openFolderSelector(IndexLink indexLink) {
        SelectIndexFoldersFragment next = new SelectIndexFoldersFragment(indexLink);
        mActivity.getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.containersettings, next)
                .addToBackStack(null)
                .commit();
    }

    private void setBusy(boolean busy, String message) {
        save.setEnabled(!busy);
        save.setText(busy ? "Working…" : "Add GDI-JS Index");
        progressCircular.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message);
    }
}
