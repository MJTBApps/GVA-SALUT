import Foundation
import Capacitor
import UIKit
import WebKit
import WidgetKit
import UserNotifications

/// Mismo puente que NavegadorPlugin.java: navegador real del sistema (WKWebView), oculto
/// detrás de la app, para la consulta pública de la GVA. No se toca el User-Agent.
/// Si Cloudflare necesita a la persona, la app llama a mostrar() y el usuario lo resuelve.
@objc(NavegadorPlugin)
public class NavegadorPlugin: CAPPlugin, CAPBridgedPlugin, WKNavigationDelegate {
    public let identifier = "NavegadorPlugin"
    public let jsName = "Navegador"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "abrir", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "ejecutar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "mostrar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "ocultar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "cerrar", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "widget", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "compartir", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "avisos", returnType: CAPPluginReturnPromise)
    ]

    private var capa: UIView?
    private var web: WKWebView?
    private var titulo: UILabel?
    private var pendienteAbrir: CAPPluginCall?

    private func crear() {
        if web != nil { return }
        guard let host = bridge?.viewController?.view else { return }

        let c = UIView(frame: host.bounds)
        c.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        c.backgroundColor = .white

        let barra = UIView()
        barra.translatesAutoresizingMaskIntoConstraints = false
        barra.backgroundColor = UIColor(red: 0x0f/255, green: 0x17/255, blue: 0x2a/255, alpha: 1)
        let t = UILabel()
        t.translatesAutoresizingMaskIntoConstraints = false
        t.textColor = .white
        t.font = .boldSystemFont(ofSize: 16)
        t.text = "Verificación de seguridad"
        let listo = UIButton(type: .system)
        listo.translatesAutoresizingMaskIntoConstraints = false
        listo.setTitle("Listo", for: .normal)
        listo.setTitleColor(.white, for: .normal)
        listo.addTarget(self, action: #selector(pulsarListo), for: .touchUpInside)
        barra.addSubview(t); barra.addSubview(listo)

        let cfg = WKWebViewConfiguration()
        cfg.websiteDataStore = .default()
        let w = WKWebView(frame: .zero, configuration: cfg)
        w.translatesAutoresizingMaskIntoConstraints = false
        w.navigationDelegate = self

        c.addSubview(barra); c.addSubview(w)
        NSLayoutConstraint.activate([
            barra.topAnchor.constraint(equalTo: c.topAnchor),
            barra.leadingAnchor.constraint(equalTo: c.leadingAnchor),
            barra.trailingAnchor.constraint(equalTo: c.trailingAnchor),
            t.leadingAnchor.constraint(equalTo: barra.leadingAnchor, constant: 16),
            t.bottomAnchor.constraint(equalTo: barra.bottomAnchor, constant: -12),
            t.topAnchor.constraint(equalTo: c.safeAreaLayoutGuide.topAnchor, constant: 12),
            listo.trailingAnchor.constraint(equalTo: barra.trailingAnchor, constant: -16),
            listo.centerYAnchor.constraint(equalTo: t.centerYAnchor),
            w.topAnchor.constraint(equalTo: barra.bottomAnchor),
            w.leadingAnchor.constraint(equalTo: c.leadingAnchor),
            w.trailingAnchor.constraint(equalTo: c.trailingAnchor),
            w.bottomAnchor.constraint(equalTo: c.bottomAnchor)
        ])

        // Detrás de la app: funciona como cualquier página, pero no se ve
        host.insertSubview(c, at: 0)
        capa = c; web = w; titulo = t
    }

    @objc private func pulsarListo() {
        ocultarVista()
        notifyListeners("cerrado", data: [:])
    }

    private func ocultarVista() {
        guard let c = capa, let host = c.superview else { return }
        host.sendSubviewToBack(c)
    }

    public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        pendienteAbrir?.resolve(["url": webView.url?.absoluteString ?? ""])
        pendienteAbrir = nil
    }

    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        pendienteAbrir?.reject(error.localizedDescription); pendienteAbrir = nil
    }

    public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        pendienteAbrir?.reject(error.localizedDescription); pendienteAbrir = nil
    }

    @objc func abrir(_ call: CAPPluginCall) {
        guard let s = call.getString("url"), s.hasPrefix("https://svsiscv.san.gva.es/"), let url = URL(string: s) else {
            call.reject("Solo se abre la consulta pública de la GVA"); return
        }
        DispatchQueue.main.async {
            self.crear()
            self.pendienteAbrir?.reject("Sustituida por otra carga")
            self.pendienteAbrir = call
            self.web?.load(URLRequest(url: url))
        }
    }

    @objc func ejecutar(_ call: CAPPluginCall) {
        let js = call.getString("js") ?? "null"
        DispatchQueue.main.async {
            guard let w = self.web else { call.reject("El navegador no está abierto"); return }
            // Igual que Android: se devuelve el resultado como texto JSON
            w.evaluateJavaScript("JSON.stringify((function(){ return (\(js)); })())") { valor, error in
                if let e = error { call.reject(e.localizedDescription); return }
                call.resolve(["valor": (valor as? String) ?? "null"])
            }
        }
    }

    @objc func mostrar(_ call: CAPPluginCall) {
        let t = call.getString("titulo") ?? "Verificación de seguridad"
        DispatchQueue.main.async {
            self.crear()
            self.titulo?.text = t
            if let c = self.capa { c.superview?.bringSubviewToFront(c) }
            call.resolve()
        }
    }

    @objc func ocultar(_ call: CAPPluginCall) {
        DispatchQueue.main.async { self.ocultarVista(); call.resolve() }
    }

    @objc func cerrar(_ call: CAPPluginCall) {
        DispatchQueue.main.async {
            self.capa?.removeFromSuperview()
            self.capa = nil; self.web = nil; self.titulo = nil
            self.pendienteAbrir?.reject("Cerrado"); self.pendienteAbrir = nil
            call.resolve()
        }
    }

    /// Guarda el resumen para el widget (grupo de apps compartido) y lo repinta.
    @objc func widget(_ call: CAPPluginCall) {
        let d = UserDefaults(suiteName: "group.es.mjtbapps.mibolsa")
        d?.set(call.getString("titulo") ?? "Mi Bolsa", forKey: "titulo")
        d?.set(call.getString("linea1") ?? "", forKey: "linea1")
        d?.set(call.getString("linea2") ?? "", forKey: "linea2")
        if #available(iOS 14.0, *) { WidgetCenter.shared.reloadAllTimelines() }
        call.resolve()
    }

    /// Comparte la imagen de «Mi situación» con el menú de compartir de iOS.
    @objc func compartir(_ call: CAPPluginCall) {
        var b64 = call.getString("imagen") ?? ""
        if let coma = b64.firstIndex(of: ",") { b64 = String(b64[b64.index(after: coma)...]) }
        guard let datos = Data(base64Encoded: b64), let img = UIImage(data: datos) else {
            call.reject("Imagen no válida"); return
        }
        let texto = call.getString("texto") ?? ""
        DispatchQueue.main.async {
            guard let vc = self.bridge?.viewController else { call.reject("Sin pantalla"); return }
            let av = UIActivityViewController(activityItems: [img, texto], applicationActivities: nil)
            if let pop = av.popoverPresentationController {
                pop.sourceView = vc.view
                pop.sourceRect = CGRect(x: vc.view.bounds.midX, y: vc.view.bounds.maxY - 40, width: 1, height: 1)
            }
            vc.present(av, animated: true) { call.resolve() }
        }
    }

    /// Avisos programados en el propio móvil (sin servidor): borra los anteriores y programa
    /// la lista que manda la app [{id, fecha (ms), titulo, texto}]. Pide permiso la primera vez.
    @objc func avisos(_ call: CAPPluginCall) {
        let lista = call.getArray("avisos", JSObject.self) ?? []
        let c = UNUserNotificationCenter.current()
        c.requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            c.getPendingNotificationRequests { pend in
                c.removePendingNotificationRequests(withIdentifiers: pend.map { $0.identifier }.filter { $0.hasPrefix("mb-") })
                guard ok else { call.resolve(["permiso": false]); return }
                var n = 0
                for a in lista {
                    guard let id = a["id"] as? String, let ms = a["fecha"] as? Double else { continue }
                    let fecha = Date(timeIntervalSince1970: ms / 1000)
                    if fecha <= Date() { continue }
                    let ct = UNMutableNotificationContent()
                    ct.title = (a["titulo"] as? String) ?? "Mi Bolsa"
                    ct.body = (a["texto"] as? String) ?? ""
                    ct.sound = .default
                    let comp = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fecha)
                    c.add(UNNotificationRequest(identifier: "mb-" + id, content: ct,
                        trigger: UNCalendarNotificationTrigger(dateMatching: comp, repeats: false)))
                    n += 1
                }
                call.resolve(["permiso": true, "programados": n])
            }
        }
    }
}
