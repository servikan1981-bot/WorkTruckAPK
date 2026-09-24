package com.sergey.duochat;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilderFactory;

public final class PositiveNewsFetcher {
    private static final String KEY_ITEMS = "positive_news_v56_ru";
    private static final String KEY_LAST_CHECK = "positive_news_last_check_v56_ru";
    private static final long TWELVE_HOURS = 12L * 60L * 60L * 1000L;
    private static final int MAX_ITEMS = 20;

    private static final Feed[] FEEDS = new Feed[] {
            new Feed("Позитивные новости", "https://wildcar.org/news/rss.xml", 10)
    };

    private PositiveNewsFetcher() {}

    public static String load(Context context) {
        return SecureStore.prefs(context).getString(KEY_ITEMS, "[]");
    }

    public static void checkAndStore(Context context) {
        SharedPreferences p = SecureStore.prefs(context);
        long now = System.currentTimeMillis();
        long last = p.getLong(KEY_LAST_CHECK, 0L);
        if (last > 0 && now - last < TWELVE_HOURS) return;

        try {
            List<Candidate> all = new ArrayList<>();
            for (Feed feed : FEEDS) {
                try { all.addAll(readFeed(feed)); } catch (Exception ignored) {}
            }
            if (all.isEmpty()) return;

            JSONArray existing;
            try { existing = new JSONArray(p.getString(KEY_ITEMS, "[]")); }
            catch (Exception e) { existing = new JSONArray(); }

            Candidate best = null;
            for (Candidate c : all) {
                if (contains(existing, c.id)) continue;
                c.score += positivityScore(c.title + " " + c.summary);
                if (best == null || c.score > best.score) best = c;
            }
            if (best == null) {
                p.edit().putLong(KEY_LAST_CHECK, now).apply();
                return;
            }

            if (best.image.isEmpty() || best.summary.length() < 70) enrichFromArticle(best);

            JSONObject item = new JSONObject();
            item.put("id", best.id);
            item.put("title", best.title);
            item.put("summary", trim(best.summary, 520));
            item.put("image", best.image);
            item.put("source", best.source);
            item.put("publishedAt", best.publishedAt);
            item.put("addedAt", now);

            JSONArray out = new JSONArray();
            out.put(item);
            for (int i = 0; i < existing.length() && out.length() < MAX_ITEMS; i++) {
                JSONObject old = existing.optJSONObject(i);
                if (old != null) out.put(old);
            }

            p.edit()
                    .putString(KEY_ITEMS, out.toString())
                    .putLong(KEY_LAST_CHECK, now)
                    .apply();
        } catch (Exception ignored) {}
    }

    private static boolean contains(JSONArray a, String id) {
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return true;
        }
        return false;
    }

    private static List<Candidate> readFeed(Feed feed) throws Exception {
        List<Candidate> out = new ArrayList<>();
        HttpURLConnection c = open(feed.url);
        try (InputStream in = c.getInputStream()) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
            try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
            try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
            f.setNamespaceAware(false);
            Document doc = f.newDocumentBuilder().parse(in);
            NodeList items = doc.getElementsByTagName("item");
            int limit = Math.min(items.getLength(), 8);
            for (int i = 0; i < limit; i++) {
                Element e = (Element) items.item(i);
                String title = clean(text(e, "title"));
                String link = clean(text(e, "link"));
                String desc = clean(text(e, "description"));
                String date = clean(text(e, "pubDate"));
                if (title.isEmpty() || link.isEmpty()) continue;
                if (!looksRussian(title + " " + desc)) continue;

                Candidate x = new Candidate();
                x.title = title;
                x.link = link;
                x.summary = desc;
                x.source = feed.name;
                x.publishedAt = date;
                x.id = sha256(link);
                x.score = feed.bonus + Math.max(0, 8 - i);
                x.image = findImage(e);
                out.add(x);
            }
        } finally {
            c.disconnect();
        }
        return out;
    }

    private static String findImage(Element item) {
        NodeList all = item.getElementsByTagName("*");
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (!(n instanceof Element)) continue;
            Element e = (Element) n;
            String name = e.getNodeName().toLowerCase(Locale.US);
            if (name.contains("content") || name.contains("thumbnail") || name.equals("enclosure")) {
                String url = e.getAttribute("url");
                String type = e.getAttribute("type");
                if (!url.isEmpty() && (type.isEmpty() || type.startsWith("image/") || name.contains("thumbnail"))) return url;
            }
        }
        return "";
    }

    private static void enrichFromArticle(Candidate c) {
        try {
            String html = fetchText(c.link, 650_000);
            String image = meta(html, "property", "og:image");
            if (!image.isEmpty()) c.image = image;
            String d = meta(html, "property", "og:description");
            if (d.isEmpty()) d = meta(html, "name", "description");
            d = clean(d);
            if (d.length() > c.summary.length()) c.summary = d;
        } catch (Exception ignored) {}
    }

    private static String meta(String html, String attr, String value) {
        if (html == null) return "";
        String lower = html.toLowerCase(Locale.US);
        String needle = attr.toLowerCase(Locale.US) + "=\"" + value.toLowerCase(Locale.US) + "\"";
        int pos = lower.indexOf(needle);
        if (pos < 0) {
            needle = attr.toLowerCase(Locale.US) + "='" + value.toLowerCase(Locale.US) + "'";
            pos = lower.indexOf(needle);
        }
        if (pos < 0) return "";
        int start = Math.max(0, lower.lastIndexOf("<meta", pos));
        int end = lower.indexOf(">", pos);
        if (end < 0) return "";
        String tag = html.substring(start, Math.min(html.length(), end + 1));
        String tl = tag.toLowerCase(Locale.US);
        int cp = tl.indexOf("content=");
        if (cp < 0) return "";
        int q = cp + 8;
        if (q >= tag.length()) return "";
        char quote = tag.charAt(q);
        if (quote == '"' || quote == '\'') {
            int qe = tag.indexOf(quote, q + 1);
            if (qe > q) return tag.substring(q + 1, qe);
        }
        int sp = tag.indexOf(' ', q);
        return tag.substring(q, sp > q ? sp : tag.length()).replace("/>", "").replace(">", "");
    }

    private static String text(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        if (n.getLength() == 0) return "";
        return n.item(0).getTextContent();
    }

    private static String clean(String s) {
        if (s == null) return "";
        String out = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString();
        return out.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean looksRussian(String s) {
        if (s == null || s.isEmpty()) return false;
        int cyr = 0, letters = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = Character.toLowerCase(s.charAt(i));
            if (Character.isLetter(ch)) letters++;
            if ((ch >= 'а' && ch <= 'я') || ch == 'ё') cyr++;
        }
        return cyr >= 20 && (letters == 0 || ((double) cyr / (double) letters) >= 0.35);
    }

    private static int positivityScore(String s) {
        String x = s.toLowerCase(Locale.ROOT);
        String[] plus = {
                "спас", "добро", "помог", "успех", "рекорд", "выздоров", "изобр", "откры", "воссоед",
                "побед", "улучш", "снизил", "вернул", "бесплат", "благотвор", "усынов", "награ",
                "save", "rescue", "kind", "help", "record", "recover", "breakthrough", "reunite",
                "win", "improve", "restore", "charity", "adopt", "award", "celebrate", "hope"
        };
        String[] minus = {"убит", "войн", "погиб", "смерт", "катастроф", "crime", "killed", "war", "dead", "death"};
        int score = 0;
        for (String p : plus) if (x.contains(p)) score += 3;
        for (String m : minus) if (x.contains(m)) score -= 6;
        return score;
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "OurFamily/5.6 Android");
        c.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, text/html, */*");
        return c;
    }

    private static String fetchText(String url, int maxBytes) throws Exception {
        HttpURLConnection c = open(url);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder out = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = br.read(buf)) > 0 && out.length() < maxBytes) out.append(buf, 0, n);
            return out.toString();
        } finally {
            c.disconnect();
        }
    }

    private static String sha256(String s) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] b = d.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte x : b) out.append(String.format(Locale.US, "%02x", x & 0xff));
            return out.toString().substring(0, 24);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max).trim() + "…";
    }

    private static final class Feed {
        final String name, url;
        final int bonus;
        Feed(String name, String url, int bonus) { this.name = name; this.url = url; this.bonus = bonus; }
    }

    private static final class Candidate {
        String id = "", title = "", link = "", summary = "", image = "", source = "", publishedAt = "";
        int score = 0;
    }
}
