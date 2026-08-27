package com.miracle.kglaynyi.utils;

import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MediaSourceDeduplicator {
    private MediaSourceDeduplicator() {}

    public static List<Episode> deduplicateEpisodes(List<Episode> input) {
        Map<String, Episode> unique = new LinkedHashMap<>();
        if (input == null) return new ArrayList<>();
        for (Episode item : input) {
            if (item == null) continue;
            unique.putIfAbsent(key(item.getFileName(), item.getSize(), item.getGd_id(), item.getUrlString()), item);
        }
        return new ArrayList<>(unique.values());
    }

    public static List<Movie> deduplicateMovies(List<Movie> input) {
        Map<String, Movie> unique = new LinkedHashMap<>();
        if (input == null) return new ArrayList<>();
        for (Movie item : input) {
            if (item == null) continue;
            unique.putIfAbsent(key(item.getFileName(), item.getSize(), item.getGd_id(), item.getUrlString()), item);
        }
        return new ArrayList<>(unique.values());
    }

    public static List<MyMedia> deduplicateMedia(List<MyMedia> input) {
        Map<String, MyMedia> unique = new LinkedHashMap<>();
        if (input == null) return new ArrayList<>();
        for (MyMedia item : input) {
            if (item == null) continue;
            if (item instanceof Episode) {
                Episode e = (Episode) item;
                unique.putIfAbsent(key(e.getFileName(), e.getSize(), e.getGd_id(), e.getUrlString()), e);
            } else if (item instanceof Movie) {
                Movie m = (Movie) item;
                unique.putIfAbsent(key(m.getFileName(), m.getSize(), m.getGd_id(), m.getUrlString()), m);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static String key(String fileName, String size, String stableId, String url) {
        String name = fileName == null ? "" : fileName.trim().toLowerCase(Locale.US);
        String bytes = size == null ? "" : size.trim();
        if (!name.isEmpty() && !bytes.isEmpty()) return "file|" + name + "|" + bytes;
        if (stableId != null && !stableId.trim().isEmpty()) return "id|" + stableId.trim();
        return "url|" + (url == null ? "" : url.trim());
    }
}
