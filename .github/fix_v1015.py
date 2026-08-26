from pathlib import Path
import re


def sub1(path, pattern, repl, flags=0):
    p = Path(path)
    s = p.read_text()
    out, count = re.subn(pattern, repl, s, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"Patch failed for {path}: {pattern[:120]} matched {count}")
    p.write_text(out)


def replace1(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"Patch token missing in {path}: {old[:120]!r}")
    p.write_text(s.replace(old, new, 1))


# Anime filename parsing.
Path("app/src/main/java/com/miracle/kglaynyi/utils/AnimeNameExtractor.java").write_text(r'''package com.miracle.kglaynyi.utils;

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
''')

# GDI-JS: route episodic content to TV metadata even in a Movies/mixed index.
gdi = "app/src/main/java/com/miracle/kglaynyi/utils/GdiJsIndexClient.java"
replace1(
    gdi,
    "if (!tvShows) saveDiscoveredPlaceholder(folderUrl, file, indexId);",
    "if (!tvShows && !looksLikeEpisode(folderUrl, file.getName())) saveDiscoveredPlaceholder(folderUrl, file, indexId);",
)
sub1(
    gdi,
    r"String id = file\.getId\(\);\s*if \(isAlreadyPresent\(id, file\.getModifiedTime\(\)\)\) return;\s*String streamUrl = resolveFileUrl\(rootUrl, folderUrl, file\);\s*if \(tvShows\) \{",
    '''String id = file.getId();
        boolean treatAsTv = tvShows || looksLikeEpisode(folderUrl, file.getName());

        // A previous scan may have incorrectly stored an episode as a Movie.
        if (treatAsTv && id != null && !id.trim().isEmpty()) {
            DatabaseClient.getInstance(context).getAppDatabase().movieDao().deleteByGdId(id);
        }
        if (isAlreadyPresent(id, file.getModifiedTime())) return;

        String streamUrl = resolveFileUrl(rootUrl, folderUrl, file);
        if (treatAsTv) {''',
    flags=re.S,
)
helper = r'''    private static boolean looksLikeEpisode(String folderUrl, String fileName) {
        if (fileName == null) return false;
        String clean = fileName.replace("Copy of ", "").trim();
        if (ShowUtils.parseShowName(clean) != null) return true;
        if (AnimeNameExtractor.getAnimeName(clean) != null) return true;

        String lowerPath = ((folderUrl == null ? "" : folderUrl) + "/" + clean).toLowerCase();
        boolean seriesFolder = lowerPath.contains("/tv shows/")
                || lowerPath.contains("/tvshows/")
                || lowerPath.contains("/series/")
                || lowerPath.contains("/shows/")
                || lowerPath.contains("/anime/");
        String base = clean.replaceFirst("\\.[^.]+$", "").trim();
        boolean numberOnlyEpisode = base.matches("(?i)^(?:e|ep|episode)?[ ._-]*\\d{1,3}(?:[ ._-].*)?$");
        boolean seasonFolder = lowerPath.matches(".*[/_-](?:season[ ._-]*|s)\\d{1,2}[/_-].*");
        return seriesFolder && (numberOnlyEpisode || seasonFolder);
    }

'''
sub1(gdi, r"(?=    private static boolean isAlreadyPresent\()", helper)

# TMDB movie upsert + anime/path TV parsing.
tmdb = "app/src/main/java/com/miracle/kglaynyi/utils/SendGetRequestTMDB.java"
replace1(
    tmdb,
    'getMovieById(Long.parseLong(movieIdFromPlexExtractor) , movie);\n        }',
    'getMovieById(Long.parseLong(movieIdFromPlexExtractor) , movie);\n            return;\n        }',
)
replace1(
    tmdb,
    '''            if (!titleExtracted.equals("") && !yearExtracted.equals("0")) {
                finalIndex = findIndexOfClosestMatch(titleExtracted + " " + yearExtracted , titlesAndYearsFromTMDB);
            }''',
    '''            if (!titleExtracted.equals("")) {
                String matchText = titleExtracted;
                if (!yearExtracted.equals("0")) matchText += " " + yearExtracted;
                finalIndex = findIndexOfClosestMatch(matchText, titlesAndYearsFromTMDB);
            }''',
)
replace1(
    tmdb,
    "DatabaseClient.getInstance(context).getAppDatabase().movieDao().delete(movie);",
    "// Replacement is keyed by gd_id below; deleting this newly-created entity is ineffective.",
)
helper2 = r'''    private static void upsertMovieByGdId(Movie movie) {
        if (movie == null) return;
        String gdId = movie.getGd_id();
        if (gdId != null && !gdId.trim().isEmpty()) {
            DatabaseClient.getInstance(context).getAppDatabase().movieDao().deleteByGdId(gdId);
        }
        DatabaseClient.getInstance(context).getAppDatabase().movieDao().insert(movie);
    }

    private static String[] parseEpisodeFallback(Episode episode) {
        if (episode == null) return null;
        String fileName = episode.getFileName() == null ? "" : episode.getFileName().replace("Copy of ", "").trim();
        String[] anime = AnimeNameExtractor.getAnimeName(fileName);
        if (anime != null) return anime;

        String episodeNumber = null;
        Matcher epMatcher = Pattern.compile(
                "(?i)(?:^|[ ._\\-])(?:e|ep|episode)?[ ._\\-]*(\\d{1,3})(?=[ ._\\-\\[]|$)")
                .matcher(fileName.replaceFirst("\\.[^.]+$", ""));
        while (epMatcher.find()) episodeNumber = epMatcher.group(1);
        if (episodeNumber == null) return null;

        String showName = null;
        String seasonNumber = "1";
        try {
            String decoded = java.net.URLDecoder.decode(episode.getUrlString() == null ? "" : episode.getUrlString(), "UTF-8");
            String[] parts = decoded.split("/");
            int seasonIndex = -1;
            for (int i = 0; i < parts.length; i++) {
                Matcher seasonMatcher = Pattern.compile("(?i)^(?:season[ ._-]*|s)(\\d{1,2})$").matcher(parts[i].trim());
                if (seasonMatcher.find()) {
                    seasonNumber = seasonMatcher.group(1);
                    seasonIndex = i;
                    break;
                }
            }
            if (seasonIndex > 0) showName = cleanPathTitle(parts[seasonIndex - 1]);

            if (showName == null || showName.isEmpty()) {
                for (int i = 0; i + 1 < parts.length; i++) {
                    String part = parts[i].trim().toLowerCase();
                    if (part.equals("anime") || part.equals("tv shows") || part.equals("tvshows")
                            || part.equals("shows") || part.equals("series")) {
                        showName = cleanPathTitle(parts[i + 1]);
                        break;
                    }
                }
            }
            if ((showName == null || showName.isEmpty()) && parts.length > 1) {
                String parent = parts[parts.length - 2];
                if (parent.matches("(?i)^(?:season[ ._-]*|s)\\d{1,2}$") && parts.length > 2) {
                    parent = parts[parts.length - 3];
                }
                showName = cleanPathTitle(parent);
            }
        } catch (Exception ignored) { }

        if (showName == null || showName.isEmpty()) return null;
        return new String[]{showName, seasonNumber, episodeNumber};
    }

    private static String cleanPathTitle(String value) {
        if (value == null) return "";
        String result = value.replace('+', ' ').replace('.', ' ').replace('_', ' ').trim();
        while (result.contains("  ")) result = result.replace("  ", " ");
        return result;
    }

'''
sub1(tmdb, r"(?=    private static String searchMovieOnTmdbByName\()", helper2)
replace1(
    tmdb,
    '''            ensureFallbackMovieTitle(movie);
            DatabaseClient.getInstance(context).getAppDatabase().movieDao().insert(movie);''',
    '''            ensureFallbackMovieTitle(movie);
            upsertMovieByGdId(movie);''',
)
sub1(
    tmdb,
    r"if \(DatabaseClient\.getInstance\(context\)\.getAppDatabase\(\)\.movieDao\(\)\.getByFileName\(movie\.getGd_id\(\)\) == null\) \{\s*DatabaseClient\.getInstance\(context\)\.getAppDatabase\(\)\.movieDao\(\)\.insert\(movie\);\s*\}",
    "upsertMovieByGdId(movie);",
    flags=re.S,
)
first_block = '''        if (result != null) {
            finalShowName = result.get(SHOW);
            finalSeasonNumber = result.get(SEASON);
            finalEpisodeNumber = result.get(EPNUM);
        }'''
first_new = '''        if (result != null) {
            finalShowName = result.get(SHOW);
            finalSeasonNumber = result.get(SEASON);
            finalEpisodeNumber = result.get(EPNUM);
        } else {
            String[] fallback = parseEpisodeFallback(episode);
            if (fallback != null) {
                finalShowName = fallback[0];
                finalSeasonNumber = fallback[1];
                finalEpisodeNumber = fallback[2];
            }
        }'''
replace1(tmdb, first_block, first_new)
second_block = '''            if (result != null) {
                finalShowName = result.get(SHOW);
                finalSeasonNumber = result.get(SEASON);
                finalEpisodeNumber = result.get(EPNUM);
            }'''
second_new = '''            if (result != null) {
                finalShowName = result.get(SHOW);
                finalSeasonNumber = result.get(SEASON);
                finalEpisodeNumber = result.get(EPNUM);
            } else {
                String[] fallback = parseEpisodeFallback(e);
                if (fallback != null) {
                    finalShowName = fallback[0];
                    finalSeasonNumber = fallback[1];
                    finalEpisodeNumber = fallback[2];
                }
            }'''
replace1(tmdb, second_block, second_new)

# Pick the best/latest enriched row for each movie title.
movie_dao = "app/src/main/java/com/miracle/kglaynyi/database/MovieDao.java"
replace1(
    movie_dao,
    '@Query("SELECT * FROM Movie WHERE title is not null and disabled=0 GROUP BY title ")',
    '@Query("SELECT * FROM Movie m WHERE m.title IS NOT NULL AND m.disabled=0 AND m.fileidForDB = (SELECT m2.fileidForDB FROM Movie m2 WHERE m2.title=m.title AND m2.disabled=0 ORDER BY (m2.poster_path IS NOT NULL) DESC, m2.fileidForDB DESC LIMIT 1) ORDER BY m.title COLLATE NOCASE")',
)

# Classification helper: Japanese Animation = Anime, plus explicit /anime/ movie paths.
Path("app/src/main/java/com/miracle/kglaynyi/utils/MediaClassificationUtils.java").write_text(r'''package com.miracle.kglaynyi.utils;

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
''')

# Clean card binding: no stale recycled poster/year; visible dummy poster on missing metadata.
Path("app/src/main/java/com/miracle/kglaynyi/adapter/MediaAdapter.java").write_text(r'''package com.miracle.kglaynyi.adapter;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.miracle.kglaynyi.Constants;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.model.TVShowInfo.TVShowSeasonDetails;

import java.util.List;

public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.MediaAdapterHolder> {
    private final Context context;
    private final List<MyMedia> mediaList;
    private final OnItemClickListener listener;

    public MediaAdapter(Context context, List<MyMedia> mediaList, OnItemClickListener listener) {
        this.context = context;
        this.mediaList = mediaList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MediaAdapterHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new MediaAdapterHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.media_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull MediaAdapterHolder holder, int position) {
        holder.name.setText("");
        holder.movieYear.setText("");
        holder.movieYear.setVisibility(View.GONE);
        Glide.with(context).clear(holder.poster);
        holder.poster.setImageResource(R.drawable.dummyposter);

        MyMedia media = mediaList.get(position);
        if (media instanceof Movie) {
            Movie movie = (Movie) media;
            String name = movie.getTitle();
            if (name == null || name.trim().isEmpty()) name = movie.getFileName();
            holder.name.setText(name == null ? "Unknown" : name);
            loadPoster(holder.poster, movie.getPoster_path());
            String year = movie.getRelease_date();
            if (year != null && !year.trim().isEmpty()) {
                holder.movieYear.setVisibility(View.VISIBLE);
                int dash = year.indexOf('-');
                holder.movieYear.setText(dash > 0 ? year.substring(0, dash) : year);
            }
        } else if (media instanceof TVShow) {
            TVShow show = (TVShow) media;
            holder.name.setText(show.getName() == null ? "Unknown Show" : show.getName());
            loadPoster(holder.poster, show.getPoster_path());
            String year = show.getFirst_air_date();
            if (year != null && !year.isEmpty()) {
                holder.movieYear.setVisibility(View.VISIBLE);
                int dash = year.indexOf('-');
                holder.movieYear.setText(dash > 0 ? year.substring(0, dash) : year);
            }
        } else if (media instanceof TVShowSeasonDetails) {
            TVShowSeasonDetails season = (TVShowSeasonDetails) media;
            holder.name.setText(season.getName() == null ? "Season" : season.getName());
            loadPoster(holder.poster, season.getPoster_path());
        }
        holder.itemView.startAnimation(AnimationUtils.loadAnimation(context, R.anim.pop_in));
    }

    private void loadPoster(ImageView view, String path) {
        if (path == null || path.trim().isEmpty()) return;
        Glide.with(context)
                .load(Constants.TMDB_IMAGE_BASE_URL + path)
                .placeholder(new ColorDrawable(Color.BLACK))
                .error(R.drawable.dummyposter)
                .apply(RequestOptions.bitmapTransform(new RoundedCorners(14)))
                .into(view);
    }

    @Override
    public int getItemCount() {
        return mediaList == null ? 0 : mediaList.size();
    }

    public class MediaAdapterHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView name;
        final ImageView poster;
        final TextView movieYear;

        MediaAdapterHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.nameInMediaItem);
            poster = itemView.findViewById(R.id.posterInMediaItem);
            movieYear = itemView.findViewById(R.id.yearInMediaItem);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            int position = getBindingAdapterPosition();
            if (listener != null && position != RecyclerView.NO_POSITION) listener.onClick(v, position);
        }
    }

    public interface OnItemClickListener {
        void onClick(View view, int position);
    }
}
''')

# Movies tab: remove anime and open fallback rows by filename when TMDB id is absent.
Path("app/src/main/java/com/miracle/kglaynyi/fragments/MovieLibraryFragment.java").write_text(r'''package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.MediaAdapter;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.utils.MediaClassificationUtils;

import java.util.ArrayList;
import java.util.List;

public class MovieLibraryFragment extends BaseFragment {
    private RecyclerView recyclerViewMovies;
    private List<Movie> movieList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_movie_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showLibraryMovies();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) showLibraryMovies();
    }

    private void showLibraryMovies() {
        new Thread(() -> {
            List<Movie> all = DatabaseClient.getInstance(mActivity).getAppDatabase().movieDao().getAll();
            List<Movie> filtered = new ArrayList<>();
            for (Movie movie : all) if (!MediaClassificationUtils.isAnime(movie)) filtered.add(movie);
            movieList = filtered;
            showRecyclerMovies(filtered);
        }).start();
    }

    private void showRecyclerMovies(List<Movie> list) {
        mActivity.runOnUiThread(() -> {
            DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
            int columns = Math.max(1, (int) ((metrics.widthPixels / metrics.density) / 120));
            recyclerViewMovies = mActivity.findViewById(R.id.recyclerLibraryMovies);
            if (recyclerViewMovies == null) return;
            recyclerViewMovies.setLayoutManager(new GridLayoutManager(mActivity, columns));
            recyclerViewMovies.setHasFixedSize(true);
            MediaAdapter.OnItemClickListener listener = (view, position) -> {
                if (position < 0 || position >= movieList.size()) return;
                Movie movie = movieList.get(position);
                MovieDetailsFragment details = movie.getId() != 0
                        ? new MovieDetailsFragment(movie.getId())
                        : new MovieDetailsFragment(movie.getFileName());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .add(R.id.container, details).addToBackStack(null).commit();
            };
            recyclerViewMovies.setAdapter(new MediaAdapter(mActivity, (List<MyMedia>)(List<?>) list, listener));
        });
    }
}
''')

# TV Shows tab: remove anime and refresh every visit.
Path("app/src/main/java/com/miracle/kglaynyi/fragments/TvShowsLibraryFragment.java").write_text(r'''package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.MediaAdapter;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.utils.MediaClassificationUtils;

import java.util.ArrayList;
import java.util.List;

public class TvShowsLibraryFragment extends BaseFragment {
    private RecyclerView recyclerViewTVShows;
    private List<TVShow> tvShowList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tv_shows_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showLibraryTVShows();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) showLibraryTVShows();
    }

    private void showLibraryTVShows() {
        new Thread(() -> {
            List<TVShow> all = DatabaseClient.getInstance(mActivity).getAppDatabase().tvShowDao().getAllByTitles();
            List<TVShow> filtered = new ArrayList<>();
            for (TVShow show : all) if (!MediaClassificationUtils.isAnime(show)) filtered.add(show);
            tvShowList = filtered;
            showRecycler(filtered);
        }).start();
    }

    private void showRecycler(List<TVShow> list) {
        mActivity.runOnUiThread(() -> {
            DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
            int columns = Math.max(1, (int) ((metrics.widthPixels / metrics.density) / 120));
            recyclerViewTVShows = mActivity.findViewById(R.id.recyclerLibraryTVShows);
            if (recyclerViewTVShows == null) return;
            recyclerViewTVShows.setLayoutManager(new GridLayoutManager(mActivity, columns));
            recyclerViewTVShows.setHasFixedSize(true);
            MediaAdapter.OnItemClickListener listener = (view, position) -> {
                if (position < 0 || position >= tvShowList.size()) return;
                TvShowDetailsFragment details = new TvShowDetailsFragment(tvShowList.get(position).getId());
                mActivity.getSupportFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                        .add(R.id.container, details).addToBackStack(null).commit();
            };
            recyclerViewTVShows.setAdapter(new MediaAdapter(mActivity, (List<MyMedia>)(List<?>) list, listener));
        });
    }
}
''')

# Dedicated Anime tab containing anime films and anime series.
Path("app/src/main/java/com/miracle/kglaynyi/fragments/AnimeLibraryFragment.java").write_text(r'''package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.MediaAdapter;
import com.miracle.kglaynyi.database.DatabaseClient;
import com.miracle.kglaynyi.model.Movie;
import com.miracle.kglaynyi.model.MyMedia;
import com.miracle.kglaynyi.model.TVShowInfo.TVShow;
import com.miracle.kglaynyi.utils.MediaClassificationUtils;

import java.util.ArrayList;
import java.util.List;

public class AnimeLibraryFragment extends BaseFragment {
    private final List<MyMedia> animeList = new ArrayList<>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_anime_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadAnime();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) loadAnime();
    }

    private void loadAnime() {
        new Thread(() -> {
            List<MyMedia> result = new ArrayList<>();
            for (Movie movie : DatabaseClient.getInstance(mActivity).getAppDatabase().movieDao().getAll()) {
                if (MediaClassificationUtils.isAnime(movie)) result.add(movie);
            }
            for (TVShow show : DatabaseClient.getInstance(mActivity).getAppDatabase().tvShowDao().getAllByTitles()) {
                if (MediaClassificationUtils.isAnime(show)) result.add(show);
            }
            synchronized (animeList) {
                animeList.clear();
                animeList.addAll(result);
            }
            showRecycler(new ArrayList<>(result));
        }).start();
    }

    private void showRecycler(List<MyMedia> list) {
        mActivity.runOnUiThread(() -> {
            RecyclerView recycler = mActivity.findViewById(R.id.recyclerLibraryAnime);
            if (recycler == null) return;
            DisplayMetrics metrics = mActivity.getResources().getDisplayMetrics();
            int columns = Math.max(1, (int) ((metrics.widthPixels / metrics.density) / 120));
            recycler.setLayoutManager(new GridLayoutManager(mActivity, columns));
            recycler.setHasFixedSize(true);
            MediaAdapter.OnItemClickListener listener = (view, position) -> {
                MyMedia media;
                synchronized (animeList) {
                    if (position < 0 || position >= animeList.size()) return;
                    media = animeList.get(position);
                }
                if (media instanceof Movie) {
                    Movie movie = (Movie) media;
                    MovieDetailsFragment details = movie.getId() != 0
                            ? new MovieDetailsFragment(movie.getId())
                            : new MovieDetailsFragment(movie.getFileName());
                    mActivity.getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                            .add(R.id.container, details).addToBackStack(null).commit();
                } else if (media instanceof TVShow) {
                    TvShowDetailsFragment details = new TvShowDetailsFragment(((TVShow) media).getId());
                    mActivity.getSupportFragmentManager().beginTransaction()
                            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
                            .add(R.id.container, details).addToBackStack(null).commit();
                }
            };
            recycler.setAdapter(new MediaAdapter(mActivity, list, listener));
        });
    }
}
''')

Path("app/src/main/res/layout/fragment_anime_library.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<androidx.recyclerview.widget.RecyclerView xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/recyclerLibraryAnime"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:clipToPadding="false"
    android:paddingStart="12dp"
    android:paddingEnd="12dp"
    android:paddingBottom="80dp" />
''')

Path("app/src/main/java/com/miracle/kglaynyi/adapter/FragmentViewPagerAdapter.java").write_text(r'''package com.miracle.kglaynyi.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.miracle.kglaynyi.fragments.AnimeLibraryFragment;
import com.miracle.kglaynyi.fragments.FilesLibraryFragment;
import com.miracle.kglaynyi.fragments.MovieLibraryFragment;
import com.miracle.kglaynyi.fragments.TvShowsLibraryFragment;

public class FragmentViewPagerAdapter extends FragmentStateAdapter {
    public FragmentViewPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) return new MovieLibraryFragment();
        if (position == 1) return new TvShowsLibraryFragment();
        if (position == 2) return new AnimeLibraryFragment();
        return new FilesLibraryFragment();
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}
''')

Path("app/src/main/java/com/miracle/kglaynyi/fragments/LibraryFragment.java").write_text(r'''package com.miracle.kglaynyi.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.miracle.kglaynyi.R;
import com.miracle.kglaynyi.adapter.FragmentViewPagerAdapter;

public class LibraryFragment extends BaseFragment {
    private TabLayout tabLayout;
    private ViewPager2 viewPagerLibrary;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tabLayout = view.findViewById(R.id.tabLayout);
        viewPagerLibrary = view.findViewById(R.id.viewPagerLibrary);
        viewPagerLibrary.setSaveEnabled(false);
        viewPagerLibrary.setAdapter(new FragmentViewPagerAdapter(this));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { viewPagerLibrary.setCurrentItem(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });
        viewPagerLibrary.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                TabLayout.Tab tab = tabLayout.getTabAt(position);
                if (tab != null) tabLayout.selectTab(tab);
            }
        });
    }
}
''')

Path("app/src/main/res/layout/fragment_library.xml").write_text(r'''<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/black"
    android:orientation="vertical">

    <TextView
        android:id="@+id/libraryTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginStart="20dp"
        android:layout_marginTop="40dp"
        android:fontFamily="@font/jost_medium"
        android:text="Library"
        android:textColor="@color/white"
        android:textSize="25sp" />

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabLayout"
        style="@style/RoundedTabLayoutStyle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginVertical="5dp"
        app:tabMode="scrollable"
        app:tabTextAppearance="@style/tabText">

        <com.google.android.material.tabs.TabItem
            android:id="@+id/movieTab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Movies" />

        <com.google.android.material.tabs.TabItem
            android:id="@+id/tvTab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="TV Shows" />

        <com.google.android.material.tabs.TabItem
            android:id="@+id/animeTab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Anime" />

        <com.google.android.material.tabs.TabItem
            android:id="@+id/filesTab"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Files" />
    </com.google.android.material.tabs.TabLayout>

    <androidx.viewpager2.widget.ViewPager2
        android:id="@+id/viewPagerLibrary"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />
</LinearLayout>
''')

# Detail title fallback.
replace1(
    "app/src/main/java/com/miracle/kglaynyi/fragments/MovieDetailsFragment.java",
    "titleText.setText(movieFileName);",
    '''String displayTitle = movieDetails.getTitle();
                                   if (displayTitle == null || displayTitle.trim().isEmpty()) {
                                       displayTitle = movieFileName != null ? movieFileName : movieDetails.getFileName();
                                   }
                                   titleText.setText(displayTitle);''',
)

# Version bump.
sub1(
    "app/build.gradle",
    r'versionCode\s+8\s*\n\s*versionName\s+"1\.0\.14"',
    'versionCode 9\n        versionName "1.0.15"',
)
