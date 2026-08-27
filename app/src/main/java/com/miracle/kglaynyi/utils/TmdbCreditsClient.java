package com.miracle.kglaynyi.utils;

import static com.miracle.kglaynyi.Constants.TMDB_API_KEY;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miracle.kglaynyi.model.CreditPerson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TmdbCreditsClient {
    private TmdbCreditsClient() {}

    public static List<CreditPerson> fetch(boolean tv, int tmdbId) {
        List<CreditPerson> result = new ArrayList<>();
        if (tmdbId <= 0 || TMDB_API_KEY == null || TMDB_API_KEY.trim().isEmpty()) return result;
        HttpURLConnection connection = null;
        try {
            String type = tv ? "tv" : "movie";
            String endpoint = "https://api.themoviedb.org/3/" + type + "/" + tmdbId
                    + "/credits?api_key=" + URLEncoder.encode(TMDB_API_KEY, "UTF-8")
                    + "&language=en-US";
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                return result;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();

            JsonObject root = JsonParser.parseString(body.toString()).getAsJsonObject();
            JsonArray cast = root.has("cast") && root.get("cast").isJsonArray()
                    ? root.getAsJsonArray("cast") : new JsonArray();

            int limit = Math.min(12, cast.size());
            for (int i = 0; i < limit; i++) {
                JsonObject person = cast.get(i).getAsJsonObject();
                String name = getString(person, "name");
                String role = getString(person, "character");
                String image = getString(person, "profile_path");
                if (!name.isEmpty()) result.add(new CreditPerson(name, role, image));
            }

            JsonArray crew = root.has("crew") && root.get("crew").isJsonArray()
                    ? root.getAsJsonArray("crew") : new JsonArray();
            int addedCrew = 0;
            for (JsonElement element : crew) {
                if (addedCrew >= 4) break;
                if (!element.isJsonObject()) continue;
                JsonObject person = element.getAsJsonObject();
                String job = getString(person, "job");
                if (!("Director".equalsIgnoreCase(job)
                        || "Creator".equalsIgnoreCase(job)
                        || "Executive Producer".equalsIgnoreCase(job))) continue;
                String name = getString(person, "name");
                if (name.isEmpty()) continue;
                result.add(new CreditPerson(name, job, getString(person, "profile_path")));
                addedCrew++;
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
        return result;
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return "";
        try { return object.get(key).getAsString(); } catch (Exception ignored) { return ""; }
    }
}
