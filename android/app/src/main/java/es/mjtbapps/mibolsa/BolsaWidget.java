package es.mjtbapps.mibolsa;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

/** Widget de la pantalla de inicio: enseña lo último que guardó la app (no consulta nada por sí mismo). */
public class BolsaWidget extends AppWidgetProvider {

    static final String PREFS = "mibolsa_widget";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) pintar(ctx, mgr, id);
    }

    static void refrescar(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, BolsaWidget.class));
        for (int id : ids) pintar(ctx, mgr, id);
    }

    static void pintar(Context ctx, AppWidgetManager mgr, int id) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_bolsa);
        v.setTextViewText(R.id.w_titulo, p.getString("titulo", "Mi Bolsa"));
        v.setTextViewText(R.id.w_l1, p.getString("linea1", "Abre la app"));
        v.setTextViewText(R.id.w_l2, p.getString("linea2", "para ver tu puesto"));
        Intent abrir = new Intent(ctx, MainActivity.class);
        abrir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.w_raiz, pi);
        mgr.updateAppWidget(id, v);
    }
}
