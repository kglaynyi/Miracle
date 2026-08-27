package com.miracle.kglaynyi.utils;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;

public final class MediaDownloadUtils {

    private MediaDownloadUtils() {}

    public static long enqueue(Context context, String url, String fileName) {
        if (context == null || url == null || url.trim().isEmpty()) return -1L;

        String safeName = sanitizeFileName(fileName);
        if (safeName.isEmpty()) safeName = "Miracle-video";

        DownloadManager manager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            Toast.makeText(context, "Download service is unavailable", Toast.LENGTH_LONG).show();
            return -1L;
        }

        Uri uri = Uri.parse(url);
        DownloadManager.Request request = new DownloadManager.Request(uri)
                .setTitle(safeName)
                .setDescription("Downloading with Miracle")
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true);

        String extension = MimeTypeMap.getFileExtensionFromUrl(safeName);
        if (extension != null && !extension.isEmpty()) {
            String mime = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.toLowerCase());
            if (mime != null) request.setMimeType(mime);
        }

        try {
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "Miracle" + File.separator + safeName);
        } catch (Exception ignored) {
            // DownloadManager will use its default destination.
        }

        long id = manager.enqueue(request);
        Toast.makeText(context, "Download started", Toast.LENGTH_SHORT).show();
        return id;
    }

    public static String sanitizeFileName(String input) {
        if (input == null) return "";
        String value = input.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        value = value.replaceAll("\\s+", " ");
        return value;
    }
}
