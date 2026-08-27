package com.miracle.kglaynyi.adapter;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.fragments.ChangeTMDBFragment;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;
import com.miracle.kglaynyi.player.PlayerActivity;
import com.miracle.kglaynyi.utils.MediaDownloadUtils;
import com.miracle.kglaynyi.utils.MediaSourceDeduplicator;
import com.miracle.kglaynyi.utils.MovieQualityExtractor;
import com.miracle.kglaynyi.utils.sizetoReadablesize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.FileItemAdapterHolder> {

    private final Context context;
    private final List<MyMedia> mediaList;

    public FileItemAdapter(Context context, List<MyMedia> mediaList) {
        this.context = context;
        this.mediaList = MediaSourceDeduplicator.deduplicateMedia(mediaList);
    }

    @NonNull
    @Override
    public FileItemAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_item, parent, false);
        return new FileItemAdapterHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileItemAdapterHolder holder, int position) {
        MyMedia media = mediaList.get(position);
        String fileName = fileName(media);
        String url = url(media);

        holder.fileName.setText(fileName == null ? "Video source" : fileName);
        holder.link.setVisibility(View.GONE);
        holder.link.setText("");

        String size = size(media);
        try {
            holder.size.setText(size == null || size.trim().isEmpty()
                    ? ""
                    : sizetoReadablesize.humanReadableByteCountBin(Long.parseLong(size)));
        } catch (Exception ignored) {
            holder.size.setText("");
        }

        String quality = MovieQualityExtractor.extractQualtiy(fileName);
        holder.quality.setText(quality == null || quality.trim().isEmpty() ? "Video" : quality);

        boolean movie = media instanceof Movie;
        holder.changeTMDB.setVisibility(movie ? View.VISIBLE : View.GONE);
        holder.changeTMDB.setOnClickListener(movie ? v -> holder.changeTMDBFragment(media) : null);

        holder.play.setOnClickListener(v -> {
            markPlayed(media);
            holder.playMedia(media);
        });
        holder.download.setOnClickListener(v ->
                MediaDownloadUtils.enqueue(context, url, fileName));
    }

    @Override
    public int getItemCount() {
        return mediaList == null ? 0 : mediaList.size();
    }

    private void markPlayed(MyMedia media) {
        new Thread(() -> {
            if (media instanceof Movie) {
                DatabaseClient.getInstance(context).getAppDatabase()
                        .movieDao().updatePlayed(((Movie) media).getId());
            }
        }, "MiracleMarkPlayed").start();
    }

    private String url(MyMedia media) {
        if (media instanceof Movie) return ((Movie) media).getUrlString();
        if (media instanceof Episode) return ((Episode) media).getUrlString();
        return null;
    }

    private String fileName(MyMedia media) {
        if (media instanceof Movie) return ((Movie) media).getFileName();
        if (media instanceof Episode) return ((Episode) media).getFileName();
        return null;
    }

    private String size(MyMedia media) {
        if (media instanceof Movie) return ((Movie) media).getSize();
        if (media instanceof Episode) return ((Episode) media).getSize();
        return null;
    }

    private String resumeKey(MyMedia media) {
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            if (movie.getId() > 0) return "movie:" + movie.getId();
            if (movie.getGd_id() != null && !movie.getGd_id().trim().isEmpty()) {
                return "gdi:" + movie.getGd_id();
            }
        } else if (media instanceof Episode) {
            Episode episode = (Episode) media;
            if (episode.getId() > 0) return "episode:" + episode.getId();
            if (episode.getGd_id() != null && !episode.getGd_id().trim().isEmpty()) {
                return "gdi:" + episode.getGd_id();
            }
        }
        return "source:" + String.valueOf(fileName(media)).hashCode();
    }

    private String groupKey(MyMedia media) {
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            return movie.getId() > 0 ? "movie:" + movie.getId() : resumeKey(media);
        }
        if (media instanceof Episode) {
            Episode episode = (Episode) media;
            return episode.getShow_id() > 0
                    ? "show:" + episode.getShow_id()
                    : resumeKey(media);
        }
        return resumeKey(media);
    }

    public class FileItemAdapterHolder extends RecyclerView.ViewHolder {
        final TextView fileName;
        final TextView link;
        final TextView size;
        final TextView quality;
        final Button play;
        final Button download;
        final Button changeTMDB;

        FileItemAdapterHolder(@NonNull View itemView) {
            super(itemView);
            fileName = itemView.findViewById(R.id.fileNameInFileItem);
            link = itemView.findViewById(R.id.fileLinkInFileItem);
            size = itemView.findViewById(R.id.sizeTextInFileItem);
            quality = itemView.findViewById(R.id.videoQualityTextInFileItem);
            play = itemView.findViewById(R.id.playInFileItem);
            download = itemView.findViewById(R.id.downloadInFileItem);
            changeTMDB = itemView.findViewById(R.id.changeTMDBIdInFileItem);
        }

        private void playMedia(MyMedia media) {
            String sourceUrl = url(media);
            if (sourceUrl == null || sourceUrl.trim().isEmpty()) {
                Toast.makeText(context, "Video source is unavailable", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences prefs =
                    context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
            boolean external = prefs.getBoolean("EXTERNAL_SETTING", false);
            Uri uri = Uri.parse(sourceUrl);

            if (external) {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.setDataAndType(uri, "video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                context.startActivity(intent);
                return;
            }

            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("url", sourceUrl);
            intent.putExtra(PlayerActivity.EXTRA_RESUME_KEY, resumeKey(media));
            intent.putExtra(PlayerActivity.EXTRA_MEDIA_GROUP_KEY, groupKey(media));
            attachQualitySources(intent);
            context.startActivity(intent);
        }

        private void attachQualitySources(Intent intent) {
            if (mediaList == null || mediaList.size() < 2) return;
            ArrayList<String> urls = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();

            for (MyMedia item : mediaList) {
                String itemUrl = url(item);
                if (itemUrl == null || itemUrl.trim().isEmpty()) continue;
                urls.add(itemUrl);
                labels.add(buildQualityLabel(fileName(item), urls.size()));
            }

            if (urls.size() < 2) return;
            intent.putExtra(PlayerActivity.EXTRA_QUALITY_URLS, urls.toArray(new String[0]));
            intent.putExtra(PlayerActivity.EXTRA_QUALITY_LABELS, labels.toArray(new String[0]));
        }

        private String buildQualityLabel(String name, int sourceNumber) {
            String quality = name == null ? null : MovieQualityExtractor.extractQualtiy(name);
            String lower = name == null ? "" : name.toLowerCase(Locale.US);
            String codec = "";
            if (lower.contains("x265") || lower.contains("h265")
                    || lower.contains("h.265") || lower.contains("hevc")) {
                codec = " • HEVC";
            } else if (lower.contains("x264") || lower.contains("h264")
                    || lower.contains("h.264") || lower.contains("avc")) {
                codec = " • H.264";
            }
            if (quality != null && !quality.trim().isEmpty()) {
                return quality + codec + " • GDI-JS";
            }
            return "Source " + sourceNumber + codec + " • GDI-JS";
        }

        private void changeTMDBFragment(MyMedia media) {
            if (!(itemView.getContext() instanceof AppCompatActivity)) return;
            AppCompatActivity activity = (AppCompatActivity) itemView.getContext();
            ChangeTMDBFragment fragment = new ChangeTMDBFragment(media);
            activity.getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.fade_in, R.anim.fade_out,
                            R.anim.fade_in, R.anim.fade_out)
                    .add(R.id.container, fragment)
                    .addToBackStack(null)
                    .commit();
        }
    }

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }
}
