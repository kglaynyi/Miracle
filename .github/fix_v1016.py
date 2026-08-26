from pathlib import Path
import re


def replace1(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"Patch token missing in {path}: {old[:160]!r}")
    p.write_text(s.replace(old, new, 1))


def sub1(path, pattern, repl, flags=0):
    p = Path(path)
    s = p.read_text()
    out, count = re.subn(pattern, lambda m: repl, s, count=1, flags=flags)
    if count != 1:
        raise SystemExit(f"Patch failed for {path}: {pattern[:160]} matched {count}")
    p.write_text(out)


# 1) Restore scan progress whenever an index row is rebound after changing tabs.
replace1(
    'app/src/main/java/com/miracle/kglaynyi/adapter/IndexAdapter.java',
    '''        int noOfMedia = getNoOfMedia(holder.itemView.getContext(), t);\n        holder.noOfMedia.setText(noOfMedia + " " + t.getFolderType());\n        holder.refreshIndex.setEnabled(true);\n''',
    '''        // A scan belongs to the index, not to this ViewHolder. Rebind the shared\n        // progress state whenever Settings/Manage Indexes is recreated.\n        bindProgress(holder, t);\n'''
)


# 2) A manual GDI-JS refresh is a real metadata rebuild. Clear stale/wrong rows from
#    this index before rediscovery, then prune TV metadata that no longer has files.
gdi = 'app/src/main/java/com/miracle/kglaynyi/utils/GdiJsIndexClient.java'
replace1(
    gdi,
    '''import com.miracle.kglaynyi.model.TVShowInfo.Episode;\n''',
    '''import com.miracle.kglaynyi.model.TVShowInfo.Episode;\nimport com.miracle.kglaynyi.model.TVShowInfo.TVShow;\nimport com.miracle.kglaynyi.model.TVShowInfo.TVShowSeasonDetails;\n'''
)
replace1(
    gdi,
    '''        if (!session.success) throw new IOException(session.message);\n\n        emit(listener, Progress.status("Login verified. Discovering videos…", -1,\n                0, 0, 0, 0, 0, 0));\n''',
    '''        if (!session.success) throw new IOException(session.message);\n\n        emit(listener, Progress.status("Preparing library for a clean metadata scan…", -1,\n                0, 0, 0, 0, 0, 0));\n        clearIndexMediaForRescan(indexId);\n\n        emit(listener, Progress.status("Login verified. Discovering videos…", -1,\n                0, 0, 0, 0, 0, 0));\n'''
)
replace1(
    gdi,
    '''    private static void saveDiscoveredPlaceholder(String folderUrl, File file, int indexId) {\n''',
    '''    private static void clearIndexMediaForRescan(int indexId) {\n        DatabaseClient db = DatabaseClient.getInstance(context);\n        db.getAppDatabase().movieDao().deleteAllFromthisIndex(indexId);\n        db.getAppDatabase().episodeDao().deleteAllFromThisIndex(indexId);\n\n        // Season/show rows are shared metadata, so only remove them when no episode\n        // from any enabled index still references them.\n        List<TVShowSeasonDetails> seasons = db.getAppDatabase().tvShowSeasonDetailsDao().getAll();\n        if (seasons != null) {\n            for (TVShowSeasonDetails season : seasons) {\n                List<Episode> episodes = db.getAppDatabase().episodeDao().getFromSeasonOnly(season.getId());\n                if (episodes == null || episodes.isEmpty()) {\n                    db.getAppDatabase().tvShowSeasonDetailsDao().deleteById(season.getId());\n                }\n            }\n        }\n\n        List<TVShow> shows = db.getAppDatabase().tvShowDao().getAll();\n        if (shows != null) {\n            for (TVShow show : shows) {\n                List<TVShowSeasonDetails> showSeasons = db.getAppDatabase()\n                        .tvShowSeasonDetailsDao().findByShowId(show.getId());\n                if (showSeasons == null || showSeasons.isEmpty()) {\n                    db.getAppDatabase().tvShowDao().deleteById(show.getId());\n                }\n            }\n        }\n    }\n\n    private static void saveDiscoveredPlaceholder(String folderUrl, File file, int indexId) {\n'''
)


# 3) Movie TMDB search: never send year=0, never accept a very weak first result.
tmdb = 'app/src/main/java/com/miracle/kglaynyi/utils/SendGetRequestTMDB.java'
replace1(
    tmdb,
    '''            int finalIndex = 0;\n            if (!titleExtracted.equals("")) {\n                String matchText = titleExtracted;\n                if (!yearExtracted.equals("0")) matchText += " " + yearExtracted;\n                finalIndex = findIndexOfClosestMatch(matchText, titlesAndYearsFromTMDB);\n            }\n            int movieId = moviesResponseFromTMDB.results.get(finalIndex).getId();\n            getMovieById(movieId , movie);\n''',
    '''            int finalIndex = -1;\n            if (!titleExtracted.equals("")) {\n                String matchText = titleExtracted;\n                if (!yearExtracted.equals("0")) matchText += " " + yearExtracted;\n                finalIndex = findIndexOfClosestMatch(matchText, titlesAndYearsFromTMDB);\n            }\n            if (finalIndex >= 0 && finalIndex < moviesResponseFromTMDB.results.size()) {\n                int movieId = moviesResponseFromTMDB.results.get(finalIndex).getId();\n                getMovieById(movieId , movie);\n            } else {\n                ensureFallbackMovieTitle(movie);\n                upsertMovieByGdId(movie);\n            }\n'''
)
sub1(
    tmdb,
    r'''    private static String searchMovieOnTmdbByName\(String titleExtracted , String yearExtracted\) \{.*?\n    \}\n\n    private static void getMovieById''',
    r'''    private static String searchMovieOnTmdbByName(String titleExtracted , String yearExtracted) {
        StringBuilder response = new StringBuilder();
        if (titleExtracted == null || titleExtracted.trim().isEmpty()) return "";
        try {
            StringBuilder finalUrl = new StringBuilder(TMDB_BASE_URL)
                    .append("search/movie?api_key=").append(TMDB_API_KEY)
                    .append("&language=en-US&page=1&include_adult=false&query=")
                    .append(URLEncoder.encode(titleExtracted.trim(), "UTF-8"));
            if (yearExtracted != null && yearExtracted.matches("\\d{4}")) {
                finalUrl.append("&year=").append(yearExtracted);
            }
            URL url = new URL(finalUrl.toString());
            System.out.println("TMDB GET REQUEST URL INSIDE searchMovieOnTmdbByName " + finalUrl);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            System.out.println("TMDB RESPONSE CODE" + responseCode);
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                while ((inputLine = in.readLine()) != null) response.append(inputLine);
                in.close();
            }
            con.disconnect();
        } catch (IOException | JsonSyntaxException e) {
            e.printStackTrace();
        }
        return response.toString();
    }

    private static void getMovieById''',
    flags=re.S
)


# 4) TV/anime search: match English or original title and reject low-confidence
#    matches instead of silently choosing TMDB result #1.
replace1(
    tmdb,
    '''                    if (tvShowsResponseFromTMDB.results.size() > 0) {\n                        ArrayList<String> tvTitlesFromTMDB = new ArrayList<>();\n\n                        for (int i = 0; i < tvShowsResponseFromTMDB.getResults().size(); i++) {\n                            Result tv = tvShowsResponseFromTMDB.getResults().get(i);\n                            tvTitlesFromTMDB.add(tv.getName());\n                        }\n                        ExtractedResult matchedTvTitle;\n                        int finalIndex = 0;\n                        matchedTvTitle = FuzzySearch.extractOne(finalShowName , tvTitlesFromTMDB);\n                        if (matchedTvTitle.getScore() == 100) {\n                            finalIndex = matchedTvTitle.getIndex();\n                        } else if (matchedTvTitle.getScore() > 70) {\n                            finalIndex = matchedTvTitle.getIndex();\n                        }\n                        tvShowId = tvShowsResponseFromTMDB.results.get(finalIndex).getId();\n\n                    }\n''',
    '''                    if (tvShowsResponseFromTMDB != null && tvShowsResponseFromTMDB.results != null\n                            && !tvShowsResponseFromTMDB.results.isEmpty()) {\n                        int finalIndex = findBestTvMatch(finalShowName, tvShowsResponseFromTMDB.results);\n                        if (finalIndex >= 0) {\n                            tvShowId = tvShowsResponseFromTMDB.results.get(finalIndex).getId();\n                        } else {\n                            Log.w("TMDB", "No confident TV match for " + finalShowName);\n                        }\n                    }\n'''
)


# 5) Each local TV/anime file must become exactly one Episode row. The old code
#    inserted a fallback row once for every non-matching TMDB episode.
sub1(
    tmdb,
    r'''                try \{\n                    System\.out\.println\("getTVSeasonById2 tvShowSeasonDetails " \+ tvShowSeasonDetails\.toString\(\)\);\n                    for \(Episode e: tvShowSeasonDetails\.getEpisodes\(\)\) \{.*?\n                \} catch \(NullPointerException e\) \{\n\n                    System\.out\.println\("caught exception in getTVSeasonById2\.2"\);\n                \}''',
    r'''                try {
                    if (tvShowSeasonDetails == null || tvShowSeasonDetails.getEpisodes() == null
                            || finalEpisodeNumber == null) return;

                    int wantedEpisode = Integer.parseInt(finalEpisodeNumber);
                    Episode matchedEpisode = null;
                    for (Episode candidate : tvShowSeasonDetails.getEpisodes()) {
                        if (candidate != null && candidate.getEpisode_number() == wantedEpisode) {
                            matchedEpisode = candidate;
                            break;
                        }
                    }

                    Episode row = matchedEpisode != null ? matchedEpisode : episode;
                    row.setFileName(episode.getFileName());
                    row.setMimeType(episode.getMimeType());
                    row.setModifiedTime(episode.getModifiedTime());
                    row.setSize(episode.getSize());
                    row.setUrlString(episode.getUrlString());
                    row.setGd_id(episode.getGd_id());
                    row.setIndex_id(episode.getIndex_id());
                    row.setSeason_id(tvShowSeasonDetails.getId());
                    row.setShow_id(tvShowId);

                    if (episode.getGd_id() != null && !episode.getGd_id().trim().isEmpty()) {
                        DatabaseClient.getInstance(context).getAppDatabase().episodeDao()
                                .deleteByGdId(episode.getGd_id());
                    }
                    DatabaseClient.getInstance(context).getAppDatabase().episodeDao().insert(row);
                } catch (Exception e) {
                    System.out.println("caught exception in getTVSeasonById2.2 " + e);
                }''',
    flags=re.S
)


# 6) Confidence helpers for both movie and TV metadata.
replace1(
    tmdb,
    '''    private static int findIndexOfClosestMatch(String s , ArrayList<String> titlesAndYearsFromTMDB) {\n        try {\n            ExtractedResult result = FuzzySearch.extractOne(s , titlesAndYearsFromTMDB);\n            System.out.println("findIndexOfClosestMatch FuzzySearch RESULT" + result.toString());\n            System.out.println("final Title chosen by findIndexOfClosestMatch" + result.getString());\n            return result.getIndex();\n        } catch (JsonSyntaxException | NoSuchElementException elementException) {\n            elementException.printStackTrace();\n        }\n        return 0;\n    }\n''',
    '''    private static String normalizeMediaTitle(String value) {\n        if (value == null) return "";\n        return value.toLowerCase(java.util.Locale.ROOT)\n                .replaceAll("[^\\p{L}\\p{N}]+", " ")\n                .replaceAll("\\s+", " ").trim();\n    }\n\n    private static int findBestTvMatch(String query, List<Result> results) {\n        if (query == null || results == null || results.isEmpty()) return -1;\n        String normalizedQuery = normalizeMediaTitle(query);\n        ArrayList<String> candidates = new ArrayList<>();\n        ArrayList<Integer> indexes = new ArrayList<>();\n\n        for (int i = 0; i < results.size(); i++) {\n            Result result = results.get(i);\n            if (result == null) continue;\n            String name = result.getName();\n            String original = result.getOriginal_name();\n            if (!normalizeMediaTitle(name).isEmpty()) {\n                if (normalizeMediaTitle(name).equals(normalizedQuery)) return i;\n                candidates.add(name);\n                indexes.add(i);\n            }\n            if (!normalizeMediaTitle(original).isEmpty()\n                    && !normalizeMediaTitle(original).equals(normalizeMediaTitle(name))) {\n                if (normalizeMediaTitle(original).equals(normalizedQuery)) return i;\n                candidates.add(original);\n                indexes.add(i);\n            }\n        }\n        if (candidates.isEmpty()) return -1;\n        try {\n            ExtractedResult match = FuzzySearch.extractOne(query, candidates);\n            return match != null && match.getScore() >= 72 ? indexes.get(match.getIndex()) : -1;\n        } catch (Exception e) {\n            return -1;\n        }\n    }\n\n    private static int findIndexOfClosestMatch(String s , ArrayList<String> titlesAndYearsFromTMDB) {\n        try {\n            ExtractedResult result = FuzzySearch.extractOne(s , titlesAndYearsFromTMDB);\n            System.out.println("findIndexOfClosestMatch FuzzySearch RESULT" + result.toString());\n            System.out.println("final Title chosen by findIndexOfClosestMatch" + result.getString());\n            return result.getScore() >= 70 ? result.getIndex() : -1;\n        } catch (JsonSyntaxException | NoSuchElementException elementException) {\n            elementException.printStackTrace();\n        }\n        return -1;\n    }\n'''
)


# 7) Version bump.
replace1('app/build.gradle', '        versionCode 9\n        versionName "1.0.15"',
         '        versionCode 10\n        versionName "1.0.16"')

print('v1.0.16 patch applied')
