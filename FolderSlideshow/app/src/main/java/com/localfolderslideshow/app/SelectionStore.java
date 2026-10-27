package com.localfolderslideshow.app;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SelectionStore {
    private SelectionStore() { }

    public static class Item {
        public final Uri uri;
        public final String name;
        public final String relativePath;
        public final long modified;

        public Item(Uri uri, String name, String relativePath, long modified) {
            this.uri = uri;
            this.name = name == null ? "Image" : name;
            this.relativePath = relativePath == null ? this.name : relativePath;
            this.modified = modified;
        }
    }

    public static String write(Context context, List<Item> items) throws Exception {
        JSONArray array = new JSONArray();
        for (Item item : items) {
            JSONObject object = new JSONObject();
            object.put("uri", item.uri.toString());
            object.put("name", item.name);
            object.put("relativePath", item.relativePath);
            object.put("modified", item.modified);
            array.put(object);
        }

        File file = new File(context.getCacheDir(), "slideshow_selection_" + System.nanoTime() + ".json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(array.toString().getBytes(StandardCharsets.UTF_8));
        }
        return file.getAbsolutePath();
    }

    public static ArrayList<Item> read(String path) throws Exception {
        File file = new File(path);
        byte[] bytes;
        try (FileInputStream in = new FileInputStream(file)) {
            bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int read = in.read(bytes, offset, bytes.length - offset);
                if (read < 0) break;
                offset += read;
            }
        }

        JSONArray array = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
        ArrayList<Item> items = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            items.add(new Item(
                    Uri.parse(object.getString("uri")),
                    object.optString("name", "Image"),
                    object.optString("relativePath", object.optString("name", "Image")),
                    object.optLong("modified", 0L)
            ));
        }
        return items;
    }
}
