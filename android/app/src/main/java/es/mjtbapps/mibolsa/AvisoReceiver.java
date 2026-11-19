package es.mjtbapps.mibolsa;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Avisos programados en el móvil (días de publicación y plazos que apunta la persona).
 * La app manda la lista entera cada vez que se abre; se guarda para volver a programarla
 * al reiniciar el móvil. No hay servidor ni consultas: solo recordatorios.
 */
public class AvisoReceiver extends BroadcastReceiver {
    static final String PREFS = "mibolsa_avisos", CANAL = "avisos";
    static final int MAX = 40;

    static int programar(Context ctx, String json) throws Exception {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString("lista", json).apply();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < MAX; i++) am.cancel(pi(ctx, i, null, null));
        JSONArray a = new JSONArray(json);
        int n = 0;
        for (int i = 0; i < a.length() && n < MAX; i++) {
            JSONObject o = a.getJSONObject(i);
            long t = o.optLong("fecha");
            if (t <= System.currentTimeMillis()) continue;
            am.set(AlarmManager.RTC_WAKEUP, t, pi(ctx, n, o.optString("titulo", "Mi Bolsa Salut"), o.optString("texto", "")));
            n++;
        }
        return n;
    }

    private static PendingIntent pi(Context ctx, int n, String titulo, String texto) {
        Intent i = new Intent(ctx, AvisoReceiver.class).setAction("es.mjtbapps.mibolsa.AVISO");
        if (titulo != null) { i.putExtra("titulo", titulo); i.putExtra("texto", texto); i.putExtra("n", n); }
        return PendingIntent.getBroadcast(ctx, 7400 + n, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            try { programar(ctx, ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("lista", "[]")); } catch (Exception ignored) {}
            return;
        }
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CANAL) == null)
            nm.createNotificationChannel(new NotificationChannel(CANAL, "Avisos de Mi Bolsa Salut", NotificationManager.IMPORTANCE_DEFAULT));
        Intent abrir = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pa = PendingIntent.getActivity(ctx, 7399, abrir, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(intent.getStringExtra("titulo"))
            .setContentText(intent.getStringExtra("texto"))
            .setStyle(new NotificationCompat.BigTextStyle().bigText(intent.getStringExtra("texto")))
            .setContentIntent(pa).setAutoCancel(true);
        try { NotificationManagerCompat.from(ctx).notify(7500 + intent.getIntExtra("n", 0), b.build()); } catch (SecurityException ignored) {}
    }
}
