package com.miracle.kglaynyi.utils;

import com.miracle.kglaynyi.model.Genre;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;

import java.util.List;

public final class MediaClassificationUtils {
    private MediaClassificationUtils() { }

    public static boolean isAnime(Movie movie) {
        if (movie == null) return false;
        String source = ((movie.getUrlString() == null ? "" : movie.getUrlString()) + " "
                + (movie.getFileName() == null ? "" : movie.getFileName())).toLowerCase();
        if (source.contains("/anime/") || source.contains("%2fanime%2f")) return true;
        return isJapaneseAnimation(movie.getOriginal_language(), movie.getGenres());
    }

    public static boolean isAnime(TVShow show) {
        if (show == null) return false;
        return isJapaneseAnimation(show.getOriginal_language(), show.getGenres());
    }

    private static boolean isJapaneseAnimation(String language, List<Genre> genres) {
        boolean japanese = language != null && (language.equalsIgnoreCase("ja") || language.equalsIgnoreCase("jpn"));
        if (!japanese || genres == null) return false;
        for (Genre genre : genres) {
            if (genre != null && (genre.getId() == 16
                    || (genre.getName() != null && genre.getName().equalsIgnoreCase("Animation")))) {
                return true;
            }
        }
        return false;
    }
}
