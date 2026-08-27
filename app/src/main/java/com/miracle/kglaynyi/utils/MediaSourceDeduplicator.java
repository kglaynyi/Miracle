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
            String key = key(item.getFileName(), item.getSize(), item.getGd_id(), item.getUrlString());
            Episode existing = unique.get(key);
            if (existing == null || prefer(item, existing)) unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    public static List<Movie> deduplicateMovies(List<Movie> input) {
        Map<String, Movie> unique = new LinkedHashMap<>();
        if (input == null) return new ArrayList<>();
        for (Movie item : input) {
            if (item == null) continue;
            String key = key(item.getFileName(), item.getSize(), item.getGd_id(), item.getUrlString());
            Movie existing = unique.get(key);
            if (existing == null || prefer(item, existing)) unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    public static List<MyMedia> deduplicateMedia(List<MyMedia> input) {
        Map<String, MyMedia> unique = new LinkedHashMap<>();
        if (input == null) return new ArrayList<>();
        for (MyMedia item : input) {
            if (item == null) continue;

            String key;
            if (item instanceof Episode) {
                Episode e = (Episode) item;
                key = key(e.getFileName(), e.getSize(), e.getGd_id(), e.getUrlString());
            } else if (item instanceof Movie) {
                Movie m = (Movie) item;
                key = key(m.getFileName(), m.getSize(), m.getGd_id(), m.getUrlString());
            } else {
                continue;
            }

            MyMedia existing = unique.get(key);
            if (existing == null || prefer(item, existing)) unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    private static boolean prefer(MyMedia candidate, MyMedia existing) {
        if (candidate instanceof Episode && existing instanceof Episode) {
            return prefer((Episode) candidate, (Episode) existing);
        }
        if (candidate instanceof Movie && existing instanceof Movie) {
            return prefer((Movie) candidate, (Movie) existing);
        }
        return false;
    }

    private static boolean prefer(Episode candidate, Episode existing) {
        boolean candidateStable = hasStableId(candidate.getGd_id());
        boolean existingStable = hasStableId(existing.getGd_id());
        if (candidateStable != existingStable) return candidateStable;
        return candidate.getIdForDB() > existing.getIdForDB();
    }

    private static boolean prefer(Movie candidate, Movie existing) {
        boolean candidateStable = hasStableId(candidate.getGd_id());
        boolean existingStable = hasStableId(existing.getGd_id());
        if (candidateStable != existingStable) return candidateStable;
        return candidate.getFileidForDB() > existing.getFileidForDB();
    }

    private static boolean hasStableId(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String key(String fileName, String size, String stableId, String url) {
        String name = fileName == null ? "" : fileName.trim().toLowerCase(Locale.US);
        String bytes = size == null ? "" : size.trim();
        if (!name.isEmpty() && !bytes.isEmpty()) return "file|" + name + "|" + bytes;
        if (stableId != null && !stableId.trim().isEmpty()) return "id|" + stableId.trim();
        return "url|" + (url == null ? "" : url.trim());
    }
}
