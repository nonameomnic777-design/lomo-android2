package app.lomo.reader;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Base64;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carpeta de datos de Lomo en Android: el selector de carpetas del sistema (Storage Access Framework).
 * Solo ofrece operaciones básicas sobre una carpeta elegida; la organización de archivos vive en la parte web.
 * Las rutas son relativas a la carpeta elegida, separadas por "/".
 */
@CapacitorPlugin(name = "LomoSaf")
public class LomoSafPlugin extends Plugin {

    private static final String DIR = DocumentsContract.Document.MIME_TYPE_DIR;
    private final Map<String, String> cache = new HashMap<>();

    private static class Child {
        String id;
        String name;
        boolean dir;
        long size;
    }

    private ContentResolver cr() {
        return getContext().getContentResolver();
    }

    private Uri docUri(Uri tree, String docId) {
        return DocumentsContract.buildDocumentUriUsingTree(tree, docId);
    }

    private String displayName(Uri tree, String docId) {
        Cursor c = null;
        try {
            c = cr().query(docUri(tree, docId), new String[] { DocumentsContract.Document.COLUMN_DISPLAY_NAME }, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception e) {
            // sin nombre: se usará uno genérico
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private List<Child> children(Uri tree, String parentId) {
        List<Child> out = new ArrayList<>();
        Uri u = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        String[] cols = new String[] {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        };
        Cursor c = null;
        try {
            c = cr().query(u, cols, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    Child k = new Child();
                    k.id = c.getString(0);
                    k.name = c.getString(1);
                    k.dir = DIR.equals(c.getString(2));
                    k.size = c.isNull(3) ? 0L : c.getLong(3);
                    out.add(k);
                }
            }
        } catch (Exception e) {
            // carpeta inexistente o proveedor no disponible: se devuelve lo que haya
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    /** Id del documento de la carpeta "path", o null si no existe. Con create=true crea las carpetas que falten. */
    private String resolveDir(Uri tree, String path, boolean create) throws Exception {
        String id = DocumentsContract.getTreeDocumentId(tree);
        if (path == null || path.isEmpty()) return id;
        String acc = "";
        for (String part : path.split("/")) {
            if (part.isEmpty()) continue;
            acc = acc.isEmpty() ? part : acc + "/" + part;
            String key = tree.toString() + "|" + acc;
            String hit = cache.get(key);
            if (hit != null) {
                id = hit;
                continue;
            }
            String found = null;
            for (Child k : children(tree, id)) {
                if (k.dir && part.equals(k.name)) {
                    found = k.id;
                    break;
                }
            }
            if (found == null) {
                if (!create) return null;
                Uri made = DocumentsContract.createDocument(cr(), docUri(tree, id), DIR, part);
                if (made == null) throw new Exception("No se pudo crear la carpeta " + part);
                found = DocumentsContract.getDocumentId(made);
            }
            cache.put(key, found);
            id = found;
        }
        return id;
    }

    private static String dirOf(String path) {
        int cut = path.lastIndexOf('/');
        return cut < 0 ? "" : path.substring(0, cut);
    }

    private static String nameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String mimeFor(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".epub")) return "application/epub+zip";
        if (n.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private Child findFile(Uri tree, String path) throws Exception {
        String dirId = resolveDir(tree, dirOf(path), false);
        if (dirId == null) return null;
        String name = nameOf(path);
        for (Child k : children(tree, dirId)) {
            if (!k.dir && name.equals(k.name)) return k;
        }
        return null;
    }

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        );
        startActivityForResult(call, i, "pickResult");
    }

    @ActivityCallback
    private void pickResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject ret = new JSObject();
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
            Uri tree = result.getData().getData();
            try {
                cr().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (Exception e) {
                // algunos proveedores no permiten conservar el permiso
            }
            ret.put("uri", tree.toString());
            String name = displayName(tree, DocumentsContract.getTreeDocumentId(tree));
            if (name != null) ret.put("name", name);
        }
        call.resolve(ret);
    }

    /** ¿Sigue disponible la carpeta (permiso vigente y proveedor accesible)? */
    @PluginMethod
    public void check(PluginCall call) {
        JSObject ret = new JSObject();
        boolean ok = false;
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            boolean perm = false;
            for (android.content.UriPermission p : cr().getPersistedUriPermissions()) {
                if (p.getUri().equals(tree) && p.isReadPermission() && p.isWritePermission()) {
                    perm = true;
                    break;
                }
            }
            if (perm) {
                Cursor c = null;
                try {
                    c = cr().query(docUri(tree, DocumentsContract.getTreeDocumentId(tree)), new String[] { DocumentsContract.Document.COLUMN_DOCUMENT_ID }, null, null, null);
                    ok = c != null && c.moveToFirst();
                } finally {
                    if (c != null) c.close();
                }
            }
        } catch (Exception e) {
            ok = false;
        }
        ret.put("ok", ok);
        call.resolve(ret);
    }

    @PluginMethod
    public void readdir(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            String path = call.getString("path", "");
            JSArray arr = new JSArray();
            String dirId = resolveDir(tree, path, false);
            if (dirId != null) {
                for (Child k : children(tree, dirId)) {
                    JSObject o = new JSObject();
                    o.put("name", k.name);
                    o.put("dir", k.dir);
                    o.put("size", k.size);
                    arr.put(o);
                }
            }
            JSObject ret = new JSObject();
            ret.put("entries", arr);
            call.resolve(ret);
        } catch (Exception e) {
            cache.clear();
            call.reject(String.valueOf(e.getMessage()), e);
        }
    }

    @PluginMethod
    public void read(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            Child f = findFile(tree, call.getString("path"));
            JSObject ret = new JSObject();
            if (f == null) {
                ret.put("exists", false);
                call.resolve(ret);
                return;
            }
            InputStream in = cr().openInputStream(docUri(tree, f.id));
            if (in == null) throw new Exception("No se pudo abrir " + f.name);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) != -1) bo.write(buf, 0, n);
            } finally {
                in.close();
            }
            ret.put("exists", true);
            ret.put("data", Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP));
            call.resolve(ret);
        } catch (Exception e) {
            cache.clear();
            call.reject(String.valueOf(e.getMessage()), e);
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            String path = call.getString("path");
            byte[] data = Base64.decode(call.getString("data"), Base64.DEFAULT);
            String name = nameOf(path);
            String dirId = resolveDir(tree, dirOf(path), true);
            Uri target = null;
            for (Child k : children(tree, dirId)) {
                if (!k.dir && name.equals(k.name)) {
                    target = docUri(tree, k.id);
                    break;
                }
            }
            if (target == null) {
                target = DocumentsContract.createDocument(cr(), docUri(tree, dirId), mimeFor(name), name);
                if (target == null) throw new Exception("No se pudo crear " + name);
            }
            OutputStream out = cr().openOutputStream(target, "wt");
            if (out == null) throw new Exception("No se pudo escribir " + name);
            try {
                out.write(data);
                out.flush();
            } finally {
                out.close();
            }
            call.resolve();
        } catch (Exception e) {
            cache.clear();
            call.reject(String.valueOf(e.getMessage()), e);
        }
    }

    @PluginMethod
    public void remove(PluginCall call) {
        try {
            Uri tree = Uri.parse(call.getString("tree"));
            Child f = findFile(tree, call.getString("path"));
            if (f != null) DocumentsContract.deleteDocument(cr(), docUri(tree, f.id));
            call.resolve();
        } catch (Exception e) {
            cache.clear();
            call.reject(String.valueOf(e.getMessage()), e);
        }
    }
}
