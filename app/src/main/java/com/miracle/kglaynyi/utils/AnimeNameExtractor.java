package com.miracle.kglaynyi.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnimeNameExtractor {

    private static final Pattern ANIME_EPISODE_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?(.+?)\\s+(?:S(\\d{1,2})\\s*)?-\\s*(\\d{1,3})(?:\\D.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern NAMED_EPISODE_PATTERN = Pattern.compile(
            "^(?:\\[[^\\]]+\\]\\s*)?(.+?)[ ._-]+(?:S(\\d{1,2})[ ._-]*)?(?:E|EP|Episode)[ ._-]*(\\d{1,3})(?:\\D.*)?$",
            Pattern.CASE_INSENSITIVE);

    private AnimeNameExtractor() { }

    public static String[] getAnimeName(String matchString) {
        if (matchString == null) return null;
        String input = matchString.replace("Copy of ", "").trim();
        Matcher matcher = ANIME_EPISODE_PATTERN.matcher(input);
        if (!matcher.matches()) matcher = NAMED_EPISODE_PATTERN.matcher(input);
        if (!matcher.matches()) return null;

        String animeName = cleanTitle(matcher.group(1));
        String seasonNumber = matcher.group(2);
        String episodeNumber = matcher.group(3);
        if (animeName.isEmpty() || episodeNumber == null) return null;
        if (seasonNumber == null || seasonNumber.trim().isEmpty()) seasonNumber = "1";
        return new String[]{animeName, seasonNumber, episodeNumber};
    }

    private static String cleanTitle(String value) {
        if (value == null) return "";
        String result = value.replace('.', ' ').replace('_', ' ').trim();
        while (result.contains("  ")) result = result.replace("  ", " ");
        return result;
    }
}
