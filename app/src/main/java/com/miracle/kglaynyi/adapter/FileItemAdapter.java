package com.miracle.kglaynyi.adapter;

import static android.content.Context.DOWNLOAD_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
import com.miracle.kglaynyi.utils.MovieQualityExtractor;
import com.miracle.kglaynyi.utils.MediaSourceDeduplicator;
import com.miracle.kglaynyi.utils.sizetoReadablesize;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import eightbitlab.com.blurview.BlurView;
import eightbitlab.com.blurview.RenderScriptBlur;


public class FileItemAdapter extends RecyclerView.Adapter<FileItemAdapter.FileItemAdapterHolder> {

    Context context;
    List<MyMedia> mediaList;
//    private FileItemAdapter.OnItemClickListener listener;

    public FileItemAdapter(Context context, List<MyMedia> mediaList) {
        this.context = context;
        this.mediaList = MediaSourceDeduplicator.deduplicateMedia(mediaList);
//        this.listener= listener;
    }

    @NonNull
    @Override
    public FileItemAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_item, parent, false);
        return new FileItemAdapterHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileItemAdapterHolder holder, @SuppressLint("RecyclerView") int position) {

        if (mediaList.get(position) instanceof Movie) {
            if (((Movie)mediaList.get(position)).getUrlString() != null) {
                String link = ((Movie) mediaList.get(position)).getUrlString();
                if(!link.contains("proxy")){
                    holder.link.setText(link);
                }

                holder.fileName.setText(((Movie)mediaList.get(position)).getFileName());
                holder.size.setText(sizetoReadablesize.humanReadableByteCountBin(Long.parseLong(((Movie)mediaList.get(position)).getSize())));
            }
            String qualityStr = MovieQualityExtractor.extractQualtiy(((Movie)mediaList.get(position)).getFileName());
            if(qualityStr!=null){
                holder.quality.setVisibility(View.VISIBLE);
                holder.quality.setText(qualityStr);
            }


            holder.play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    holder.playMedia(((Movie)mediaList.get(position)).getUrlString());
                    addToLastPlayed();

                }
                private void addToLastPlayed() {
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            DatabaseClient.getInstance(context).getAppDatabase().movieDao().updatePlayed(((Movie)mediaList.get(position)).getId());
                        }
                    });
                    thread.start();
                }
            });


            holder.download.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                  holder.downloadMedia(((Movie)mediaList.get(position)).getUrlString());
                }
            });

            holder.changeTMDB.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    holder.changeTMDBFragmet(mediaList.get(position));

                }
            });
        }

        if (mediaList.get(position) instanceof Episode) {
            if (((Episode)mediaList.get(position)).getUrlString() != null) {
                holder.link.setText(((Episode)mediaList.get(position)).getUrlString());
                holder.fileName.setText(((Episode)mediaList.get(position)).getFileName());
                holder.size.setText(sizetoReadablesize.humanReadableByteCountBin(Long.parseLong(((Episode)mediaList.get(position)).getSize())));
            }
            String qualityStr = MovieQualityExtractor.extractQualtiy(((Episode)mediaList.get(position)).getFileName());
            if(qualityStr!=null){
                holder.quality.setVisibility(View.VISIBLE);
                holder.quality.setText(qualityStr);
            }


            holder.play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    holder.playMedia(((Episode)mediaList.get(position)).getUrlString());
                    addToLastPlayed();
                }
                private void addToLastPlayed() {
                    Thread thread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            DatabaseClient.getInstance(context).getAppDatabase().episodeDao().updatePlayed(((Episode)mediaList.get(position)).getId());
                        }
                    });
                    thread.start();
                }
            });
            holder.download.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    holder.downloadMedia(((Episode)mediaList.get(position)).getUrlString());
                }
            });

            holder.changeTMDB.setVisibility(View.GONE);
//            holder.changeTMDB.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    holder.changeTMDBFragmet(mediaList.get(position));
//
//                }
//            });
        }




        setAnimation(holder.itemView , position);

        }



    @Override
        public int getItemCount () {
            return mediaList.size();
        }


        public class FileItemAdapterHolder extends RecyclerView.ViewHolder{

            BlurView blurView;
            ViewGroup rootView;
            View decorView;

            TextView fileName;
            TextView link;
            TextView size;
            TextView quality;
            Button play;
            Button download;
            Button changeTMDB;
            SharedPreferences sharedPreferences = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
            boolean savedEXT = sharedPreferences.getBoolean("EXTERNAL_SETTING", false);


            public FileItemAdapterHolder(@NonNull View itemView) {
                super(itemView);
                blurView = itemView.findViewById(R.id.blurView2);
                decorView =  ((Activity) context).getWindow().getDecorView();
                rootView = decorView.findViewById(android.R.id.content);

                fileName = itemView.findViewById(R.id.fileNameInFileItem);
                link = itemView.findViewById(R.id.fileLinkInFileItem);
                size = itemView.findViewById(R.id.sizeTextInFileItem);
                quality = itemView.findViewById(R.id.videoQualityTextInFileItem);
                play = itemView.findViewById(R.id.playInFileItem);
                download = itemView.findViewById(R.id.downloadInFileItem);
                changeTMDB = itemView.findViewById(R.id.changeTMDBIdInFileItem);

                blurBottom();
//                itemView.setOnClickListener(this);
            }


            private void playMedia(String url) {
                if (savedEXT) {
                    //External Player
                    Intent intent = new Intent(Intent.ACTION_VIEW , Uri.parse(url));
                    intent.setDataAndType(Uri.parse(url) , "video/*");
                    context.startActivity(intent);
                } else {
                    //Play video
                    Intent in = new Intent(context , PlayerActivity.class);
                    in.putExtra("url" , (url));
                    attachQualitySources(in);
                    context.startActivity(in);
                    Toast.makeText(context , "Play" , Toast.LENGTH_LONG).show();
                }
            }
            private void attachQualitySources(Intent intent) {
                if (intent == null || mediaList == null || mediaList.size() < 2) return;

                ArrayList<String> urls = new ArrayList<>();
                ArrayList<String> labels = new ArrayList<>();
                for (MyMedia item : mediaList) {
                    String itemUrl = null;
                    String fileName = null;
                    if (item instanceof Movie) {
                        itemUrl = ((Movie) item).getUrlString();
                        fileName = ((Movie) item).getFileName();
                    } else if (item instanceof Episode) {
                        itemUrl = ((Episode) item).getUrlString();
                        fileName = ((Episode) item).getFileName();
                    }
                    if (itemUrl == null || itemUrl.trim().isEmpty()) continue;
                    urls.add(itemUrl);
                    labels.add(buildQualityLabel(fileName, urls.size()));
                }

                if (urls.size() < 2) return;
                intent.putExtra(PlayerActivity.EXTRA_QUALITY_URLS, urls.toArray(new String[0]));
                intent.putExtra(PlayerActivity.EXTRA_QUALITY_LABELS, labels.toArray(new String[0]));
            }

            private String buildQualityLabel(String fileName, int sourceNumber) {
                String quality = fileName == null ? null : MovieQualityExtractor.extractQualtiy(fileName);
                String lower = fileName == null ? "" : fileName.toLowerCase(Locale.US);
                String codec = "";
                if (lower.contains("x265") || lower.contains("h265") || lower.contains("h.265") || lower.contains("hevc")) {
                    codec = " • HEVC";
                } else if (lower.contains("x264") || lower.contains("h264") || lower.contains("h.264") || lower.contains("avc")) {
                    codec = " • H.264";
                }
                if (quality != null && !quality.trim().isEmpty()) return quality + codec;
                return "Source " + sourceNumber + codec;
            }

            private void downloadMedia(String url) {
                DownloadManager manager = (DownloadManager) context.getSystemService(DOWNLOAD_SERVICE);
                Uri uri = Uri.parse(url);
                DownloadManager.Request request = new DownloadManager.Request(uri);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                        .setDescription("Downloading");
                long reference = manager.enqueue(request);
                Toast.makeText(context,"Download Started",Toast.LENGTH_LONG).show();
            }

            private void blurBottom(){

                ((Activity) context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                ((Activity) context).getWindow().setStatusBarColor(Color.TRANSPARENT);
                final float radius = 5f;
                final Drawable windowBackground = ((Activity) context).getWindow().getDecorView().getBackground();

                blurView.setupWith(rootView, new RenderScriptBlur(context))
                        .setFrameClearDrawable(windowBackground)
                        .setBlurRadius(radius);
                blurView.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
                blurView.setClipToOutline(true);
            }

            public void changeTMDBFragmet(MyMedia myMedia) {
                AppCompatActivity activity = (AppCompatActivity) itemView.getContext();
                ChangeTMDBFragment changeTMDBFragment = new ChangeTMDBFragment(myMedia);

                activity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in,R.anim.fade_out,R.anim.fade_in,R.anim.fade_out)
                        .add(R.id.container,changeTMDBFragment).addToBackStack(null).commit();

            }
        }
        public interface OnItemClickListener {
            public void onClick(View view , int position);
        }


        private void setAnimation (View itemView ,int position){
            Animation popIn = AnimationUtils.loadAnimation(context , R.anim.pop_in);
            itemView.startAnimation(popIn);
        }
}

