package com.miracle.kglaynyi.utils;

import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.Episode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class MediaSourceDeduplicator {
    private MediaSourceDeduplicator() {}

    public static List<Episode> deduplicateEpisodes(List<Episode> input) {
        if (input == null) return new ArrayList<>();
        Set<String> stableBases = collectStableBasesEpisodes(input);
        Map<String, Episode> unique = new LinkedHashMap<>();
        for (Episode item : input) {
            if (item == null) continue;
            String key = sourceKey(item.getFileName(), item.getSize(), item.getGd_id(),
                    item.getUrlString(), item.getIndex_id(), stableBases);
            if (key == null) continue;
            Episode existing = unique.get(key);
            if (existing == null || prefer(item, existing)) unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    public static List<Movie> deduplicateMovies(List<Movie> input) {
        if (input == null) return new ArrayList<>();
        Set<String> stableBases = collectStableBasesMovies(input);
        Map<String, Movie> unique = new LinkedHashMap<>();
        for (Movie item : input) {
            if (item == null) continue;
            String key = sourceKey(item.getFileName(), item.getSize(), item.getGd_id(),
                    item.getUrlString(), item.getIndex_id(), stableBases);
            if (key == null) continue;
            Movie existing = unique.get(key);
            if (existing == null || prefer(item, existing)) unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    public static List<MyMedia> deduplicateMedia(List<MyMedia> input) {
        if (input == null) return new ArrayList<>();

        Set<String> stableBases = new HashSet<>();
        for (MyMedia item : input) {
            if (item instanceof Episode) {
                Episode e = (Episode) item;
                addStableBase(stableBases, e.getFileName(), e.getSize(), e.getGd_id());
            } else if (item instanceof Movie) {
                Movie m = (Movie) item;
                addStableBase(stableBases, m.getFileName(), m.getSize(), m.getGd_id());
            }
        }

        Map<String, MyMedia> unique = new LinkedHashMap<>();
        for (MyMedia item : input) {
            if (item == null) continue;

            String key;
            if (item instanceof Episode) {
                Episode e = (Episode) item;
                key = sourceKey(e.getFileName(), e.getSize(), e.getGd_id(),
                        e.getUrlString(), e.getIndex_id(), stableBases);
            } else if (item instanceof Movie) {
                Movie m = (Movie) item;
                key = sourceKey(m.getFileName(), m.getSize(), m.getGd_id(),
                        m.getUrlString(), m.getIndex_id(), stableBases);
            } else {
                continue;
            }

            if (key == null) continue;
            MyMedia existing = unique.get(key);
            if (existing == null || prefer(item, existing)) unique.put(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    private static Set<String> collectStableBasesEpisodes(List<Episode> input) {
        Set<String> result = new HashSet<>();
        for (Episode item : input) {
            if (item != null) addStableBase(result, item.getFileName(), item.getSize(), item.getGd_id());
        }
        return result;
    }

    private static Set<String> collectStableBasesMovies(List<Movie> input) {
        Set<String> result = new HashSet<>();
        for (Movie item : input) {
            if (item != null) addStableBase(result, item.getFileName(), item.getSize(), item.getGd_id());
        }
        return result;
    }

    private static void addStableBase(Set<String> target, String fileName, String size, String stableId) {
        if (!hasStableId(stableId)) return;
        String base = baseKey(fileName, size);
        if (base != null) target.add(base);
    }

    private static String sourceKey(String fileName, String size, String stableId,
                                    String url, int indexId, Set<String> stableBases) {
        String base = baseKey(fileName, size);
        boolean stable = hasStableId(stableId);

        if (base != null) {
            // Legacy duplicate rows had no stable source id. When a stable row for the
            // same physical file exists, hide the legacy row entirely.
            if (!stable && stableBases.contains(base)) return null;

            // Preserve the same file when it intentionally exists in two different
            // configured sources (for example GDI-JS + Google Drive), while collapsing
            // duplicates produced inside one source.
            return stable
                    ? base + "|source:" + indexId
                    : base + "|legacy";
        }

        if (stable) return "id|" + stableId.trim();
        return "url|" + (url == null ? "" : url.trim());
    }

    private static String baseKey(String fileName, String size) {
        String name = fileName == null ? "" : fileName.trim().toLowerCase(Locale.US);
        String bytes = size == null ? "" : size.trim();
        if (name.isEmpty() || bytes.isEmpty()) return null;
        return "file|" + name + "|" + bytes;
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
}
