from pathlib import Path


def replace1(path, old, new):
    p = Path(path)
    s = p.read_text()
    if old not in s:
        raise SystemExit(f"Patch token missing in {path}: {old[:180]!r}")
    p.write_text(s.replace(old, new, 1))


def add_live_refresh(path, load_method, list_field_marker):
    replace1(path,
        'import android.os.Bundle;\n',
        'import android.os.Bundle;\nimport android.os.Handler;\nimport android.os.Looper;\n')
    replace1(path,
        'import com.miracle.kglaynyi.utils.MediaClassificationUtils;\n',
        'import com.miracle.kglaynyi.utils.MediaClassificationUtils;\nimport com.miracle.kglaynyi.utils.IndexUtils;\n')
    replace1(path,
        list_field_marker,
        list_field_marker + '''\n    private final Handler libraryRefreshHandler = new Handler(Looper.getMainLooper());\n    private final Runnable libraryRefreshRunnable = new Runnable() {\n        @Override public void run() {\n            if (!isAdded() || getView() == null) return;\n            ''' + load_method + '''();\n            if (IndexUtils.isAnyScanRunning()) {\n                libraryRefreshHandler.postDelayed(this, 1200);\n            }\n        }\n    };\n''')
    old_resume = '''    @Override\n    public void onResume() {\n        super.onResume();\n        if (getView() != null) ''' + load_method + '''();\n    }\n'''
    new_resume = '''    @Override\n    public void onResume() {\n        super.onResume();\n        libraryRefreshHandler.removeCallbacks(libraryRefreshRunnable);\n        libraryRefreshHandler.post(libraryRefreshRunnable);\n    }\n\n    @Override\n    public void onPause() {\n        libraryRefreshHandler.removeCallbacks(libraryRefreshRunnable);\n        super.onPause();\n    }\n'''
    replace1(path, old_resume, new_resume)


gdi = 'app/src/main/java/com/miracle/kglaynyi/utils/GdiJsIndexClient.java'
replace1(gdi,
'''        emit(listener, Progress.status("Preparing library for a clean metadata scan…", -1,\n                0, 0, 0, 0, 0, 0));\n        clearIndexMediaForRescan(indexId);\n\n        emit(listener, Progress.status("Login verified. Discovering videos…", -1,\n                0, 0, 0, 0, 0, 0));\n''',
'''        emit(listener, Progress.status("Login verified. Discovering videos…", -1,\n                0, 0, 0, 0, 0, 0));\n''')
replace1(gdi,
'''        int total = videoEntries.size();\n        if (total == 0) {\n            emit(listener, Progress.done("Scan complete • 0 videos found", 0, stats[0], stats[1]));\n            return 0;\n        }\n\n        emit(listener, Progress.status("Found " + total + " videos • processing 0/" + total,\n                0, 0, total, total, stats[0], stats[1], total));\n''',
'''        int total = videoEntries.size();\n        if (total == 0) {\n            clearIndexMediaForRescan(indexId);\n            emit(listener, Progress.done("Scan complete • 0 videos found", 0, stats[0], stats[1]));\n            return 0;\n        }\n\n        emit(listener, Progress.status("Discovery complete • rebuilding library…", -1,\n                0, total, total, stats[0], stats[1], total));\n        clearIndexMediaForRescan(indexId);\n\n        emit(listener, Progress.status("Found " + total + " videos • processing 0/" + total,\n                0, 0, total, total, stats[0], stats[1], total));\n''')

idx = 'app/src/main/java/com/miracle/kglaynyi/utils/IndexUtils.java'
replace1(idx,
'''    public static GdiJsIndexClient.Progress getScanProgress(int indexId) {\n        return SCAN_PROGRESS.get(indexId);\n    }\n''',
'''    public static GdiJsIndexClient.Progress getScanProgress(int indexId) {\n        return SCAN_PROGRESS.get(indexId);\n    }\n\n    public static boolean isAnyScanRunning() {\n        for (GdiJsIndexClient.Progress progress : SCAN_PROGRESS.values()) {\n            if (progress != null && !progress.finished) return true;\n        }\n        return false;\n    }\n''')

add_live_refresh(
    'app/src/main/java/com/miracle/kglaynyi/fragments/MovieLibraryFragment.java',
    'showLibraryMovies',
    '    private List<Movie> movieList = new ArrayList<>();\n')
add_live_refresh(
    'app/src/main/java/com/miracle/kglaynyi/fragments/TvShowsLibraryFragment.java',
    'showLibraryTVShows',
    '    private List<TVShow> tvShowList = new ArrayList<>();\n')
add_live_refresh(
    'app/src/main/java/com/miracle/kglaynyi/fragments/AnimeLibraryFragment.java',
    'loadAnime',
    '    private final List<MyMedia> animeList = new ArrayList<>();\n')

replace1('app/build.gradle',
         '        versionCode 10\n        versionName "1.0.16"',
         '        versionCode 11\n        versionName "1.0.17"')

replace1('.github/workflows/release-apk.yml',
         '          NOTES="Keeps scan progress across tabs and rebuilds stale movie, TV, anime, poster, and TMDB metadata on refresh."',
         '          NOTES="Keeps the library visible during discovery and refreshes Movies, TV Shows, and Anime automatically while scan metadata is rebuilt."')

print('v1.0.17 patch applied')
