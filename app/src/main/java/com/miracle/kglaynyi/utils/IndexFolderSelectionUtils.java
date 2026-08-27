package com.miracle.kglaynyi.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class IndexFolderSelectionUtils {
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<String>>() {}.getType();

    private IndexFolderSelectionUtils() {}

    public static List<String> parse(String json) {
        if (json == null) return null;
        try {
            List<String> values = GSON.fromJson(json, LIST_TYPE);
            if (values == null) return new ArrayList<>();
            Set<String> unique = new LinkedHashSet<>();
            for (String value : values) {
                if (value == null) continue;
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) unique.add(trimmed);
            }
            return new ArrayList<>(unique);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    public static String encode(List<String> folders) {
        List<String> safe = folders == null ? new ArrayList<>() : folders;
        return GSON.toJson(safe, LIST_TYPE);
    }

    public static int count(String json) {
        List<String> values = parse(json);
        return values == null ? 0 : values.size();
    }
}
