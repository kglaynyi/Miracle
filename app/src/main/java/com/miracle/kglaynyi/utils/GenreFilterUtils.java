package com.miracle.kglaynyi.utils;

import com.miracle.kglaynyi.model.Genre;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class GenreFilterUtils {

    public static final String ALL_GENRES = "All Genres";

    private GenreFilterUtils() {}

    public static List<String> collectGenres(List<? extends MyMedia> media) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (media != null) {
            for (MyMedia item : media) {
                List<Genre> genres = genresOf(item);
                if (genres == null) continue;
                for (Genre genre : genres) {
                    if (genre == null || genre.getName() == null) continue;
                    String name = genre.getName().trim();
                    if (!name.isEmpty()) names.add(name);
                }
            }
        }
        List<String> result = new ArrayList<>();
        result.add(ALL_GENRES);
        result.addAll(names);
        return result;
    }

    public static <T extends MyMedia> List<T> filter(List<T> media, String genreName) {
        if (media == null) return new ArrayList<>();
        if (genreName == null || ALL_GENRES.equalsIgnoreCase(genreName)) {
            return new ArrayList<>(media);
        }

        List<T> result = new ArrayList<>();
        for (T item : media) {
            List<Genre> genres = genresOf(item);
            if (genres == null) continue;
            for (Genre genre : genres) {
                if (genre != null && genre.getName() != null
                        && genreName.equalsIgnoreCase(genre.getName().trim())) {
                    result.add(item);
                    break;
                }
            }
        }
        return result;
    }

    private static List<Genre> genresOf(MyMedia media) {
        if (media instanceof Movie) return ((Movie) media).getGenres();
        if (media instanceof TVShow) return ((TVShow) media).getGenres();
        return null;
    }
}
