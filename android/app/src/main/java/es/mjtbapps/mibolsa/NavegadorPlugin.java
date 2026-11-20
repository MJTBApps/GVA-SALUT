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

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.Collections;
import java.util.List;
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

    /** Avisos programados en el propio móvil (sin servidor): ver AvisoReceiver. */
    @PluginMethod
    public void avisos(PluginCall call) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(getContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(getActivity(), new String[]{ android.Manifest.permission.POST_NOTIFICATIONS }, 7301);
            }
            int n = AvisoReceiver.programar(getContext(), call.getArray("avisos").toString());
            JSObject r = new JSObject(); r.put("programados", n); call.resolve(r);
        } catch (Exception e) { call.reject("No se pudieron programar los avisos: " + e.getMessage()); }
    }

    // ── Premium (pago único que quita los anuncios) con Google Play Billing ──
    static final String PREMIUM = "mibolsa_premium";
    private BillingClient pago;
    private PluginCall compraPendiente;

    private interface Listo { void ok(); void error(String e); }

    private void conectar(Listo l) {
        if (pago != null && pago.isReady()) { l.ok(); return; }
        if (pago == null) {
            pago = BillingClient.newBuilder(getContext())
                .setListener((res, compras) -> {
                    PluginCall c = compraPendiente; compraPendiente = null;
                    if (c == null) { if (compras != null) for (Purchase p : compras) reconocer(p); return; }
                    JSObject r = new JSObject();
                    if (res.getResponseCode() == BillingClient.BillingResponseCode.OK && compras != null) {
                        boolean ok = false;
                        for (Purchase p : compras) if (p.getProducts().contains(PREMIUM)) {
                            if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) { ok = true; reconocer(p); }
                            else r.put("pendiente", true);
                        }
                        r.put("premium", ok); c.resolve(r);
                    } else if (res.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                        r.put("premium", false); r.put("cancelado", true); c.resolve(r);
                    } else if (res.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                        r.put("premium", true); c.resolve(r);
                    } else c.reject("Compra no completada (" + res.getResponseCode() + "): " + res.getDebugMessage());
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        }
        pago.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) l.ok(); else l.error("Google Play no disponible (" + r.getResponseCode() + ")");
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    private void reconocer(Purchase p) {
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED || p.isAcknowledged()) return;
        pago.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), r -> { });
    }

    private void producto(Listo l, ProductDetails[] out) {
        QueryProductDetailsParams q = QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(
            QueryProductDetailsParams.Product.newBuilder().setProductId(PREMIUM).setProductType(BillingClient.ProductType.INAPP).build())).build();
        pago.queryProductDetailsAsync(q, (r, lista) -> {
            if (lista != null && !lista.isEmpty()) { out[0] = lista.get(0); l.ok(); } else l.error("Producto no encontrado (" + r.getResponseCode() + ")");
        });
    }

    /** {premium, precio}: si ya está comprado (también sirve de «restaurar») y el precio de la tienda. */
    @PluginMethod
    public void premiumEstado(PluginCall call) {
        conectar(new Listo() {
            public void ok() {
                pago.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(), (r, compras) -> {
                    boolean prem = false;
                    if (compras != null) for (Purchase p : compras) if (p.getProducts().contains(PREMIUM) && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) { prem = true; reconocer(p); }
                    final boolean premium = prem;
                    final boolean fiable = r.getResponseCode() == BillingClient.BillingResponseCode.OK;
                    ProductDetails[] pd = new ProductDetails[1];
                    producto(new Listo() {
                        public void ok() { JSObject o = new JSObject(); o.put("premium", premium); o.put("fiable", fiable); ProductDetails.OneTimePurchaseOfferDetails d = pd[0].getOneTimePurchaseOfferDetails(); o.put("precio", d != null ? d.getFormattedPrice() : ""); call.resolve(o); }
                        public void error(String e) { JSObject o = new JSObject(); o.put("premium", premium); o.put("fiable", fiable); o.put("precio", ""); call.resolve(o); }
                    }, pd);
                });
            }
            public void error(String e) { call.reject(e); }
        });
    }

    @PluginMethod
    public void premiumComprar(PluginCall call) {
        conectar(new Listo() {
            public void ok() {
                ProductDetails[] pd = new ProductDetails[1];
                producto(new Listo() {
                    public void ok() {
                        getActivity().runOnUiThread(() -> {
                            compraPendiente = call;
                            BillingFlowParams f = BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(
                                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(pd[0]).build())).build();
                            BillingResult r = pago.launchBillingFlow(getActivity(), f);
                            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK && compraPendiente == call) {
                                compraPendiente = null;
                                if (r.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) { JSObject o = new JSObject(); o.put("premium", true); call.resolve(o); }
                                else call.reject("No se pudo abrir la compra (" + r.getResponseCode() + ")");
                            }
                        });
                    }
                    public void error(String e) { call.reject(e); }
                }, pd);
            }
            public void error(String e) { call.reject(e); }
        });
    }

    @PluginMethod
    public void premiumRestaurar(PluginCall call) { premiumEstado(call); }
}
