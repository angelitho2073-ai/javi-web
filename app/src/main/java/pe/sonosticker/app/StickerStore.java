package pe.sonosticker.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Guarda el paquete de stickers del usuario dentro de la app. */
final class StickerStore {
    static final String PACK_ID = "mis_stickers";
    static final String PACK_NAME = "Mis Sonostickers";
    static final String PUBLISHER = "Sonosticker";
    static final String TRAY = "tray.png";
    static final int MAX = 30;
    static final int MIN = 3;

    private StickerStore() {}

    static File packDir(Context c) {
        File d = new File(c.getFilesDir(), "stickers/" + PACK_ID);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Stickers ordenados del más antiguo al más nuevo. */
    static List<File> stickers(Context c) {
        File[] files = packDir(c).listFiles((dir, name) -> name.endsWith(".webp"));
        List<File> list = new ArrayList<>();
        if (files == null) return list;
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName()));
        list.addAll(Arrays.asList(files));
        return list;
    }

    static int add(Context c, byte[] webp, byte[] tray) throws IOException {
        File dir = packDir(c);
        List<File> now = stickers(c);
        while (now.size() >= MAX) { now.get(0).delete(); now.remove(0); }
        write(new File(dir, String.format("s%013d.webp", System.currentTimeMillis())), webp);
        if (tray != null && tray.length > 0) write(new File(dir, TRAY), tray);
        bumpVersion(c);
        return stickers(c).size();
    }

    static void clear(Context c) {
        File[] files = packDir(c).listFiles();
        if (files != null) for (File f : files) f.delete();
        bumpVersion(c);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("pack", Context.MODE_PRIVATE);
    }

    /** WhatsApp vuelve a leer el paquete cuando cambia este número. */
    static String version(Context c) {
        return String.valueOf(prefs(c).getInt("version", 1));
    }

    private static void bumpVersion(Context c) {
        SharedPreferences p = prefs(c);
        p.edit().putInt("version", p.getInt("version", 1) + 1).apply();
    }

    private static void write(File f, byte[] data) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(data); }
    }
}
