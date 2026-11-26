package pe.sonosticker.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * Entrega el paquete a WhatsApp con el formato de su API de stickers de terceros
 * (https://github.com/WhatsApp/stickers).
 */
public class StickerContentProvider extends ContentProvider {
    private static final String METADATA = "metadata";
    private static final String STICKERS = "stickers";
    private static final String STICKERS_ASSET = "stickers_asset";

    private static final int PACKS = 1, PACK = 2, PACK_STICKERS = 3, ASSET = 4;

    private UriMatcher matcher;
    private String authority;

    @Override
    public boolean onCreate() {
        authority = getContext().getPackageName() + ".stickercontentprovider";
        matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(authority, METADATA, PACKS);
        matcher.addURI(authority, METADATA + "/*", PACK);
        matcher.addURI(authority, STICKERS + "/*", PACK_STICKERS);
        matcher.addURI(authority, STICKERS_ASSET + "/*/*", ASSET);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        switch (matcher.match(uri)) {
            case PACKS:
                return packCursor(uri);
            case PACK:
                if (!StickerStore.PACK_ID.equals(uri.getLastPathSegment())) return emptyPack(uri);
                return packCursor(uri);
            case PACK_STICKERS:
                return stickersCursor(uri);
            default:
                throw new IllegalArgumentException("URI desconocida: " + uri);
        }
    }

    private static final String[] PACK_COLUMNS = {
            "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher",
            "sticker_pack_icon", "android_play_store_link", "ios_app_download_link",
            "sticker_pack_publisher_email", "sticker_pack_publisher_website",
            "sticker_pack_privacy_policy_website", "sticker_pack_license_agreement_website",
            "image_data_version", "whatsapp_will_not_cache_stickers", "animated_sticker_pack"
    };

    private Cursor emptyPack(Uri uri) {
        MatrixCursor c = new MatrixCursor(PACK_COLUMNS);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    private Cursor packCursor(Uri uri) {
        MatrixCursor c = new MatrixCursor(PACK_COLUMNS);
        String site = "https://angelitho2073-ai.github.io/javi-web/sonosticker/";
        c.newRow()
                .add(StickerStore.PACK_ID)
                .add(StickerStore.PACK_NAME)
                .add(StickerStore.PUBLISHER)
                .add(StickerStore.TRAY)
                .add("https://play.google.com/store/apps/details?id=" + getContext().getPackageName())
                .add("")
                .add("")
                .add(site)
                .add(site + "privacidad.html")
                .add("")
                .add(StickerStore.version(getContext()))
                .add(0)
                .add(0);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    private Cursor stickersCursor(Uri uri) {
        MatrixCursor c = new MatrixCursor(new String[]{"sticker_file_name", "sticker_emoji", "sticker_accessibility_text"});
        if (StickerStore.PACK_ID.equals(uri.getLastPathSegment())) {
            List<File> list = StickerStore.stickers(getContext());
            for (File f : list) c.addRow(new Object[]{f.getName(), "😀", "Sticker de Sonosticker"});
        }
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public AssetFileDescriptor openAssetFile(Uri uri, String mode) throws FileNotFoundException {
        if (matcher.match(uri) != ASSET) throw new FileNotFoundException(uri.toString());
        List<String> seg = uri.getPathSegments();
        if (seg.size() != 3 || !StickerStore.PACK_ID.equals(seg.get(1))) throw new FileNotFoundException(uri.toString());
        String name = seg.get(2);
        if (name.contains("/") || name.contains("..")) throw new FileNotFoundException(name);
        File f = new File(StickerStore.packDir(getContext()), name);
        if (!f.exists()) throw new FileNotFoundException(name);
        return new AssetFileDescriptor(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY), 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    @Override
    public String getType(Uri uri) {
        switch (matcher.match(uri)) {
            case PACKS: return "vnd.android.cursor.dir/vnd." + authority + "." + METADATA;
            case PACK: return "vnd.android.cursor.item/vnd." + authority + "." + METADATA;
            case PACK_STICKERS: return "vnd.android.cursor.dir/vnd." + authority + "." + STICKERS;
            case ASSET:
                return uri.getLastPathSegment().endsWith(".png") ? "image/png" : "image/webp";
            default: return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
