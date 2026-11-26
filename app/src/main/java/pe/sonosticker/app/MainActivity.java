package pe.sonosticker.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final String HOME = "https://appassets.androidplatform.net/assets/www/index.html";
    private static final int REQ_FILE = 10, REQ_MIC = 11, REQ_PACK = 12, REQ_STORAGE = 13;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingMic;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        web = new WebView(this);
        setContentView(web);
        // Android 15+ dibuja de borde a borde: dejamos espacio para las barras del sistema.
        web.setOnApplyWindowInsetsListener((v, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets i = insets.getInsets(android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                v.setPadding(i.left, i.top, i.right, i.bottom);
            } else {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        web.setBackgroundColor(0xFFE9ECF8);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (ActivityNotFoundException ignored) {}
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                String[] types = params.getAcceptTypes();
                String joined = types == null ? "" : String.join(",", types);
                if (joined.contains("audio")) {
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
                } else {
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                }
                try {
                    startActivityForResult(Intent.createChooser(i, "Elige un archivo"), REQ_FILE);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean wantsMic = false;
                    for (String r : request.getResources())
                        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
                    if (!wantsMic) { request.deny(); return; }
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                    } else {
                        pendingMic = request;
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                    }
                });
            }
        });

        web.addJavascriptInterface(new Bridge(), "SonoAndroid");
        if (saved != null) web.restoreState(saved); else web.loadUrl(HOME);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == REQ_FILE && fileCallback != null) {
            Uri[] picked = null;
            if (result == RESULT_OK && data != null) {
                if (data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                    picked = new Uri[]{data.getClipData().getItemAt(0).getUri()};
                } else if (data.getData() != null) {
                    picked = new Uri[]{data.getData()};
                }
            }
            fileCallback.onReceiveValue(picked);
            fileCallback = null;
        } else if (code == REQ_PACK) {
            if (result == RESULT_CANCELED && data != null) {
                String err = data.getStringExtra("validation_error");
                if (err != null) toast("WhatsApp no aceptó el paquete: " + err);
            } else if (result == RESULT_OK) {
                toast("Paquete añadido a WhatsApp.");
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_MIC && pendingMic != null) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED)
                pendingMic.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pendingMic.deny();
            pendingMic = null;
        }
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
    }

    private boolean installed(String pkg) {
        try { getPackageManager().getPackageInfo(pkg, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    /** Funciones que la página llama como window.SonoAndroid.*. */
    private class Bridge {
        @JavascriptInterface
        public String saveFile(String name, String mime, String b64) {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT < 29) {
                if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
                    return "Permite guardar archivos y vuelve a tocar Descargar.";
                }
                try {
                    String dir = mime.startsWith("video/") ? Environment.DIRECTORY_MOVIES
                            : mime.startsWith("image/") ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS;
                    File folder = new File(Environment.getExternalStoragePublicDirectory(dir), "Sonosticker");
                    folder.mkdirs();
                    File f = new File(folder, unique(name));
                    try (OutputStream out = new FileOutputStream(f)) { out.write(data); }
                    sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f)));
                    return "Guardado en " + dir + "/Sonosticker";
                } catch (Exception e) {
                    return "No se pudo guardar el archivo.";
                }
            }
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, unique(name));
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            Uri coll;
            String place;
            if (mime.startsWith("video/")) {
                coll = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                place = Environment.DIRECTORY_MOVIES + "/Sonosticker";
            } else if (mime.startsWith("image/")) {
                coll = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                place = Environment.DIRECTORY_PICTURES + "/Sonosticker";
            } else {
                coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                place = Environment.DIRECTORY_DOWNLOADS + "/Sonosticker";
            }
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, place);
            try {
                Uri item = cr.insert(coll, v);
                if (item == null) return "No se pudo guardar el archivo.";
                try (OutputStream out = cr.openOutputStream(item)) { out.write(data); }
                return mime.startsWith("video/") || mime.startsWith("image/")
                        ? "Guardado en tu galería, carpeta Sonosticker."
                        : "Guardado en Descargas, carpeta Sonosticker.";
            } catch (Exception e) {
                return "No se pudo guardar el archivo.";
            }
        }

        @JavascriptInterface
        public void shareFile(String name, String mime, String b64) {
            try {
                File dir = new File(getCacheDir(), "share");
                dir.mkdirs();
                File f = new File(dir, name.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (OutputStream out = new FileOutputStream(f)) { out.write(Base64.decode(b64, Base64.DEFAULT)); }
                Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".files", f);
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType(mime);
                send.putExtra(Intent.EXTRA_STREAM, uri);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (installed("com.whatsapp")) send.setPackage("com.whatsapp");
                else if (installed("com.whatsapp.w4b")) send.setPackage("com.whatsapp.w4b");
                final Intent go = send.getPackage() != null ? send : Intent.createChooser(send, "Enviar con");
                runOnUiThread(() -> {
                    try { startActivity(go); } catch (ActivityNotFoundException e) { toast("No se encontró WhatsApp."); }
                });
            } catch (Exception e) {
                toast("No se pudo compartir el archivo.");
            }
        }

        @JavascriptInterface
        public int stickerCount() {
            return StickerStore.stickers(MainActivity.this).size();
        }

        @JavascriptInterface
        public String addSticker(String webpB64, String trayB64) {
            try {
                int n = StickerStore.add(MainActivity.this,
                        Base64.decode(webpB64, Base64.DEFAULT), Base64.decode(trayB64, Base64.DEFAULT));
                return n < StickerStore.MIN
                        ? "Guardado. Llevas " + n + " de " + StickerStore.MIN + " para añadir el paquete."
                        : "Guardado. Tu paquete tiene " + n + " stickers. Toca «Añadir a WhatsApp».";
            } catch (Exception e) {
                return "No se pudo guardar el sticker.";
            }
        }

        @JavascriptInterface
        public void clearPack() {
            StickerStore.clear(MainActivity.this);
        }

        @JavascriptInterface
        public void addToWhatsApp() {
            runOnUiThread(() -> {
                if (StickerStore.stickers(MainActivity.this).size() < StickerStore.MIN) {
                    toast("Necesitas al menos " + StickerStore.MIN + " stickers en tu paquete.");
                    return;
                }
                Intent i = new Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK");
                i.putExtra("sticker_pack_id", StickerStore.PACK_ID);
                i.putExtra("sticker_pack_authority", getPackageName() + ".stickercontentprovider");
                i.putExtra("sticker_pack_name", StickerStore.PACK_NAME);
                if (installed("com.whatsapp")) i.setPackage("com.whatsapp");
                else if (installed("com.whatsapp.w4b")) i.setPackage("com.whatsapp.w4b");
                else { toast("Instala WhatsApp para añadir tu paquete."); return; }
                try { startActivityForResult(i, REQ_PACK); }
                catch (ActivityNotFoundException e) { toast("Tu WhatsApp no permite añadir paquetes. Actualízalo."); }
            });
        }
    }

    private static String unique(String name) {
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        return base + "-" + (System.currentTimeMillis() / 1000) + ext;
    }
}
