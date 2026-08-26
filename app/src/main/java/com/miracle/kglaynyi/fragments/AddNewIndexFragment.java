package com.miracle.kglaynyi.fragments;

import static com.miracle.kglaynyi.utils.SendPostRequest.getScannedVideoCount;
import static com.miracle.kglaynyi.utils.SendPostRequest.postRequestGDIndex;
import static com.miracle.kglaynyi.utils.SendPostRequest.postRequestGoIndex;
import static com.miracle.kglaynyi.utils.SendPostRequest.postRequestMapleIndex;
import static com.miracle.kglaynyi.utils.SendPostRequest.postRequestSimpleProgramIndex;
import static com.miracle.kglaynyi.utils.SendPostRequest.resetPagingState;

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
import com.miracle.kglaynyi.utils.IndexConnectionValidator;

public class AddNewIndexFragment extends BaseFragment {

    private EditText indexLinkView;
    private EditText userNameView;
    private EditText passWordView;
    private Button save;
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
        progressCircular = view.findViewById(R.id.progress_circular);
        statusText = view.findViewById(R.id.suggestRefresh);
        indexTypeView = view.findViewById(R.id.actv);
        folderTypeView = view.findViewById(R.id.actv2);

        String[] indexTypes = mActivity.getResources().getStringArray(R.array.index_types);
        indexTypeView.setAdapter(new ArrayAdapter<>(mActivity, R.layout.item_index_type, indexTypes));

        String[] folderTypes = mActivity.getResources().getStringArray(R.array.folder_types);
        folderTypeView.setAdapter(new ArrayAdapter<>(mActivity, R.layout.item_folder_type, folderTypes));

        save.setOnClickListener(v -> addIndex());
    }

    private void addIndex() {
        String link = indexLinkView.getText().toString().trim();
        String user = userNameView.getText().toString();
        String pass = passWordView.getText().toString();
        String indexType = indexTypeView.getText().toString().trim();
        String folderType = folderTypeView.getText().toString().trim();

        if (link.isEmpty()) {
            indexLinkView.setError("Enter index link");
            return;
        }
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            indexLinkView.setError("URL must start with http:// or https://");
            return;
        }
        if (indexType.isEmpty()) {
            indexTypeView.setError("Select index type");
            return;
        }
        if (folderType.isEmpty()) {
            folderTypeView.setError("Select Movies or TVShows");
            return;
        }

        setBusy(true, "Checking URL and credentials…");

        new Thread(() -> {
            IndexConnectionValidator.ValidationResult validation =
                    IndexConnectionValidator.validate(link, user, pass, indexType);

            if (!validation.success) {
                mActivity.runOnUiThread(() -> setBusy(false, validation.message));
                return;
            }

            try {
                if (DatabaseClient.getInstance(mActivity).getAppDatabase().indexLinksDao().find(link) != null) {
                    mActivity.runOnUiThread(() -> setBusy(false,
                            "This index is already added. Use Manage Indexes to refresh it."));
                    return;
                }

                IndexLink indexLink = new IndexLink();
                indexLink.setLink(link);
                indexLink.setUsername(user);
                indexLink.setPassword(pass);
                indexLink.setIndexType(indexType);
                indexLink.setFolderType(folderType);

                DatabaseClient.getInstance(mActivity).getAppDatabase().indexLinksDao().insert(indexLink);
                IndexLink saved = DatabaseClient.getInstance(mActivity).getAppDatabase().indexLinksDao().find(link);
                if (saved == null) {
                    mActivity.runOnUiThread(() -> setBusy(false, "Could not save index"));
                    return;
                }

                mActivity.runOnUiThread(() -> setBusy(true, "Index verified. Scanning videos…"));

                resetPagingState();
                boolean tvShows = "TVShows".equals(folderType);
                int indexId = saved.getId();

                if ("GDIndex".equals(indexType)) {
                    postRequestGDIndex(link, user, pass, tvShows, indexId);
                } else if ("GoIndex".equals(indexType)) {
                    postRequestGoIndex(link, user, pass, tvShows, indexId);
                } else if ("MapleIndex".equals(indexType) || "Maple".equals(indexType)) {
                    postRequestMapleIndex(link, user, pass, tvShows, indexId);
                } else if ("SimpleProgram".equals(indexType)) {
                    postRequestSimpleProgramIndex(link, user, pass, tvShows, indexId);
                }

                int found = getScannedVideoCount();
                String result = found > 0
                        ? "Done. Found " + found + " video" + (found == 1 ? "." : "s.")
                        : "Connected successfully, but no supported video files were found.";
                mActivity.runOnUiThread(() -> {
                    setBusy(false, result);
                    save.setText("Done");
                });
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
                String finalMessage = "Index scan failed: " + message;
                mActivity.runOnUiThread(() -> setBusy(false, finalMessage));
            }
        }).start();
    }

    private void setBusy(boolean busy, String message) {
        save.setEnabled(!busy);
        if (busy) save.setText("Working…");
        else if (!"Done".contentEquals(save.getText())) save.setText("Save");
        progressCircular.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusText.setVisibility(View.VISIBLE);
        statusText.setText(message);
    }
}
