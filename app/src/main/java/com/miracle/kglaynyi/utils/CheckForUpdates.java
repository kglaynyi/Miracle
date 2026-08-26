package com.miracle.kglaynyi.utils;

import android.util.Log;

import com.google.gson.Gson;
import com.miracle.kglaynyi.BuildConfig;
import com.miracle.kglaynyi.fragments.BaseFragment;
import com.miracle.kglaynyi.model.GitHubResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class CheckForUpdates extends BaseFragment {
    private static final String TAG = "CheckForUpdates";
    private static final String RELEASES_URL =
            "https://api.github.com/repos/kglaynyi/Miracle/releases?per_page=1";

    public GitHubResponse[] checkForUpdates() throws IOException {
        HttpURLConnection con = null;
        try {
            URL obj = new URL(RELEASES_URL);
            con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(10000);
            con.setReadTimeout(10000);
            con.setRequestProperty("Accept", "application/vnd.github+json");

            int responseCode = con.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub releases request returned HTTP " + responseCode);
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
            }

            GitHubResponse[] releases = new Gson().fromJson(response.toString(), GitHubResponse[].class);
            if (releases == null || releases.length == 0 || releases[0] == null || releases[0].tag_name == null) {
                return null;
            }

            String tagName = releases[0].tag_name.trim();
            String githubVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String currentVersion = BuildConfig.VERSION_NAME;
            Log.i(TAG, "latest=" + githubVersion + ", current=" + currentVersion);

            return versionCompare(githubVersion, currentVersion) == 1 ? releases : null;
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to parse update information", e);
            return null;
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
    }

    static int versionCompare(String v1, String v2) {
        int vnum1 = 0, vnum2 = 0;

        for (int i = 0, j = 0; (i < v1.length() || j < v2.length());) {
            while (i < v1.length() && v1.charAt(i) != '.') {
                char c = v1.charAt(i);
                if (!Character.isDigit(c)) return 0;
                vnum1 = vnum1 * 10 + (c - '0');
                i++;
            }
            while (j < v2.length() && v2.charAt(j) != '.') {
                char c = v2.charAt(j);
                if (!Character.isDigit(c)) return 0;
                vnum2 = vnum2 * 10 + (c - '0');
                j++;
            }

            if (vnum1 > vnum2) return 1;
            if (vnum2 > vnum1) return -1;

            vnum1 = vnum2 = 0;
            i++;
            j++;
        }
        return 0;
    }
}
