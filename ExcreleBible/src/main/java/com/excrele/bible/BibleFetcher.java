package com.excrele.bible;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

/**
 * Cool part! This class fetches verses from a free Catholic Bible API (NABRE version).
 * Builds web URLs like https://query.bibleget.io/v3/?query=John3:16&version=NABRE
 * Reads JSON, pulls "text". Errors? Returns null.
 * Loads Bible structure (books/chaps/verses) once for /random. Then picks random ref + fetches text.
 * FIXED: "indexes" is an object like {"NABRE": [books array]} – grab inner array to avoid cast crash. More null checks for API weirdness.
 * Async-friendly. Uses full book names like "John".
 * Author: excrele
 */
public class BibleFetcher {

    private static final String API_BASE = "https://query.bibleget.io/v3/";
    private static final String METADATA_BASE = "https://query.bibleget.io/metadata.php";  // For book/chapter/verse counts
    private static final String VERSION = "NABRE";  // Catholic edition
    private static final String APP_ID = "excrelebible";  // Our plugin ID for API

    // Storage for random – maps book to max chapters, and chapters to max verses
    private static final Map<String, Integer> maxChaptersPerBook = new HashMap<>();
    private static final Map<String, Map<Integer, Integer>> maxVersesPerChapter = new HashMap<>();
    private static boolean isLoaded = false;  // Track if metadata ready

    // Simple holder for verse ref + text (like a tiny backpack)
    public static class Verse {
        public String reference;  // e.g., "John 3:16"
        public String text;       // The actual words

        public Verse(String ref, String txt) {
            this.reference = ref;
            this.text = txt;
        }
    }

    /**
     * Load Bible structure from API (run once on startup).
     * Fetches chapter/verse counts for every book in NABRE.
     * Async-safe – call from scheduler.
     * FIXED: Grab "indexes" as object, then NABRE's array inside (API structure quirk). Null guards everywhere!
     */
    public static void loadMetadata() {
        try {
            // Build URL: e.g., https://query.bibleget.io/metadata.php?query=versionindex&versions=NABRE&return=json&appid=excrelebible
            URL url = new URL(METADATA_BASE + "?query=versionindex&versions=" + VERSION + "&return=json&appid=" + APP_ID);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);  // 10 sec – metadata bigger
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return;  // API said no – quiet fail
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse JSON
            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();

            // Safety first – check for errors array (API might complain)
            if (json.has("errors") && !json.get("errors").isJsonNull() && json.getAsJsonArray("errors").size() > 0) {
                // Quiet log – don't spam, but note for debug
                System.err.println("BibleGet errors: " + json.getAsJsonArray("errors").toString());
                return;
            }

            // FIXED: "indexes" is a JsonObject like {"NABRE": [books array]} – drill down!
            JsonObject indexesObj = json.getAsJsonObject("indexes");
            if (indexesObj == null || !indexesObj.has(VERSION)) {
                // No NABRE data? Bail – random skips
                return;
            }

            JsonArray indexesArray = indexesObj.getAsJsonArray(VERSION);
            if (indexesArray == null || indexesArray.size() == 0) {
                return;  // Empty list? No go
            }

            for (int i = 0; i < indexesArray.size(); i++) {
                JsonObject bookObj = indexesArray.get(i).getAsJsonObject();
                if (bookObj == null) continue;  // Skip junk

                String bookName = bookObj.get("name").getAsString();  // Full name like "John"
                if (bookName == null || bookName.isEmpty()) continue;

                // Max chapters = number of chapter objects
                JsonArray chaptersArray = bookObj.getAsJsonArray("chapters");
                if (chaptersArray == null || chaptersArray.size() == 0) continue;  // Skip bad book
                int maxCh = chaptersArray.size();
                maxChaptersPerBook.put(bookName, maxCh);

                // Verses per chapter – loop safe
                Map<Integer, Integer> chVerses = new HashMap<>();
                for (int j = 0; j < chaptersArray.size(); j++) {
                    JsonObject chObj = chaptersArray.get(j).getAsJsonObject();
                    if (chObj == null) continue;
                    Integer chNum = chObj.get("number") != null ? chObj.get("number").getAsInt() : null;
                    Integer verses = chObj.get("verses") != null ? chObj.get("verses").getAsInt() : 0;
                    if (chNum != null && verses > 0) {
                        chVerses.put(chNum, verses);
                    }
                }
                if (!chVerses.isEmpty()) {
                    maxVersesPerChapter.put(bookName, chVerses);
                }
            }

            isLoaded = true;  // Ready! (Expect ~73 books)

        } catch (IOException | IllegalStateException e) {  // Catch cast/parse fails too
            e.printStackTrace();  // Debug only – shows in console if net/parse bombs
        }
    }

    /**
     * Get a totally random verse! First check if loaded, pick book/ch/v, then fetch text.
     * @return Verse object or null if not ready
     */
    public static Verse getRandomVerse() {
        if (!isLoaded || maxVersesPerChapter.isEmpty()) {
            return null;  // Not loaded yet
        }

        Random rand = new Random();
        List<String> bookList = new ArrayList<>(maxVersesPerChapter.keySet());
        String book = bookList.get(rand.nextInt(bookList.size()));  // Random book

        Map<Integer, Integer> chMap = maxVersesPerChapter.get(book);
        if (chMap == null || chMap.isEmpty()) return null;
        List<Integer> chList = new ArrayList<>(chMap.keySet());
        int chapter = chList.get(rand.nextInt(chList.size()));  // Random chapter

        Integer maxVerseObj = chMap.get(chapter);
        if (maxVerseObj == null || maxVerseObj <= 0) return null;
        int maxVerse = maxVerseObj;
        int verse = rand.nextInt(1, maxVerse + 1);  // Random verse 1 to max

        // Now fetch the actual text
        return getVerseFull(book, chapter, verse);
    }

    /**
     * Get verse text only (for backward compat – but we use full now).
     */
    public static String getVerse(String book, int chapter, int verse) {
        Verse full = getVerseFull(book, chapter, verse);
        return full != null ? full.text : null;
    }

    /**
     * Main fetcher – gets full Verse (ref + text) for any book/ch/v.
     * Builds query like "John3:16", hits API, parses JSON.
     */
    public static Verse getVerseFull(String book, int chapter, int verse) {
        try {
            // Build query: e.g., "John3:16" (full book name works!)
            String queryStr = book + chapter + ":" + verse;

            // Full URL
            URL url = new URL(API_BASE + "?query=" + queryStr + "&version=" + VERSION + "&return=json&appid=" + APP_ID);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                return null;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // Parse JSON
            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            if (json.has("results") && !json.get("results").isJsonNull() && json.getAsJsonArray("results").size() > 0) {
                JsonObject firstResult = json.getAsJsonArray("results").get(0).getAsJsonObject();
                if (firstResult.has("text")) {
                    String text = firstResult.get("text").getAsString();
                    String ref = book + " " + chapter + ":" + verse;  // Build ref
                    return new Verse(ref, text.trim());
                }
            }
            return null;

        } catch (IOException | IllegalStateException e) {
            e.printStackTrace();
            return null;
        }
    }
}