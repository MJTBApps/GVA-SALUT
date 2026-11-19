package es.mjtbapps.mibolsa;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Navegador real del sistema (WebView) que la app usa para la consulta pública de la GVA.
 *
 * Normalmente está oculto detrás de la app: la app lo maneja con abrir/ejecutar y enseña los
 * datos con su propio diseño. No se cambia el User-Agent ni nada del navegador: Cloudflare ve
 * el WebView tal cual. Si la verificación de Cloudflare necesita a la persona, la app llama a
 * mostrar() y el usuario la ve y la resuelve él mismo.
 */
@CapacitorPlugin(name = "Navegador")
public class NavegadorPlugin extends Plugin {

    private FrameLayout capa;      // barra + navegador
    private WebView web;
    private TextView titulo;
    private PluginCall pendienteAbrir;
    private boolean visible = false;

    @SuppressLint("SetJavaScriptEnabled")
    private void crear() {
        if (web != null) return;
        ViewGroup raiz = getActivity().findViewById(android.R.id.content);
        float dp = getActivity().getResources().getDisplayMetrics().density;

        capa = new FrameLayout(getActivity());
        capa.setBackgroundColor(Color.WHITE);

        LinearLayout columna = new LinearLayout(getActivity());
        columna.setOrientation(LinearLayout.VERTICAL);

        LinearLayout barra = new LinearLayout(getActivity());
        barra.setOrientation(LinearLayout.HORIZONTAL);
        barra.setGravity(Gravity.CENTER_VERTICAL);
        barra.setBackgroundColor(Color.parseColor("#0f172a"));
        int p = (int) (12 * dp);
        barra.setPadding(p, (int) (36 * dp), p, p);

        titulo = new TextView(getActivity());
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(16);
        titulo.setTypeface(Typeface.DEFAULT_BOLD);
        titulo.setText("Verificación de seguridad");
        barra.addView(titulo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button listo = new Button(getActivity());
        listo.setText("Listo");
        listo.setOnClickListener(v -> {
            ocultarVista();
            notifyListeners("cerrado", new JSObject());
        });
        barra.addView(listo);

        web = new WebView(getActivity());
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (pendienteAbrir != null) {
                    JSObject r = new JSObject();
                    r.put("url", url);
                    pendienteAbrir.resolve(r);
                    pendienteAbrir = null;
                }
            }
        });

        columna.addView(barra, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        columna.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        capa.addView(columna, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Detrás de la app: se carga y funciona como cualquier página, pero no se ve
        raiz.addView(capa, 0, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void ocultarVista() {
        if (capa == null || !visible) return;
        ViewGroup raiz = (ViewGroup) capa.getParent();
        raiz.removeView(capa);
        raiz.addView(capa, 0);
        visible = false;
    }

    @PluginMethod
    public void abrir(PluginCall call) {
        String url = call.getString("url");
        if (url == null || !url.startsWith("https://svsiscv.san.gva.es/")) {
            call.reject("Solo se abre la consulta pública de la GVA");
            return;
        }
        getActivity().runOnUiThread(() -> {
            crear();
            if (pendienteAbrir != null) pendienteAbrir.reject("Sustituida por otra carga");
            pendienteAbrir = call;
            web.loadUrl(url);
        });
    }

    @PluginMethod
    public void ejecutar(PluginCall call) {
        String js = call.getString("js", "null");
        getActivity().runOnUiThread(() -> {
            if (web == null) { call.reject("El navegador no está abierto"); return; }
            web.evaluateJavascript(js, valor -> {
                JSObject r = new JSObject();
                r.put("valor", valor); // JSON del resultado
                call.resolve(r);
            });
        });
    }

    @PluginMethod
    public void mostrar(PluginCall call) {
        String t = call.getString("titulo", "Verificación de seguridad");
        getActivity().runOnUiThread(() -> {
            crear();
            titulo.setText(t);
            capa.bringToFront();
            visible = true;
            call.resolve();
        });
    }

    @PluginMethod
    public void ocultar(PluginCall call) {
        getActivity().runOnUiThread(() -> { ocultarVista(); call.resolve(); });
    }

    @PluginMethod
    public void cerrar(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (capa != null) {
                ((ViewGroup) capa.getParent()).removeView(capa);
                web.destroy();
            }
            capa = null; web = null; visible = false;
            if (pendienteAbrir != null) { pendienteAbrir.reject("Cerrado"); pendienteAbrir = null; }
            call.resolve();
        });
    }

    /** Guarda el resumen para el widget de la pantalla de inicio y lo repinta. */
    @PluginMethod
    public void widget(PluginCall call) {
        Context ctx = getContext();
        ctx.getSharedPreferences(BolsaWidget.PREFS, Context.MODE_PRIVATE).edit()
            .putString("titulo", call.getString("titulo", "Mi Bolsa"))
            .putString("linea1", call.getString("linea1", ""))
            .putString("linea2", call.getString("linea2", ""))
            .apply();
        BolsaWidget.refrescar(ctx);
        call.resolve();
    }

    /** Comparte una imagen PNG (base64) generada por la app con tu propia situación. */
    @PluginMethod
    public void compartir(PluginCall call) {
        try {
            String b64 = call.getString("imagen", "");
            if (b64.contains(",")) b64 = b64.substring(b64.indexOf(',') + 1);
            byte[] png = Base64.decode(b64, Base64.DEFAULT);
            File dir = new File(getContext().getCacheDir(), "compartir");
            dir.mkdirs();
            File f = new File(dir, "mi-bolsa.png");
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(png); }
            Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/png");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_TEXT, call.getString("texto", ""));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent ch = Intent.createChooser(i, "Compartir");
            ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(ch);
            call.resolve();
        } catch (Exception e) {
            call.reject("No se pudo compartir: " + e.getMessage());
        }
    }
}
