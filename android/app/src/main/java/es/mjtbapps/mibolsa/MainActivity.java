package es.mjtbapps.mibolsa;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Complemento propio: navegador real (oculto) para la consulta pública de la GVA
        registerPlugin(NavegadorPlugin.class);
        // Anuncios (AdMob)
        registerPlugin(com.getcapacitor.community.admob.AdMob.class);
        super.onCreate(savedInstanceState);
    }
}
