import Foundation
import Capacitor
import UIKit
import WebKit

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
        CAPPluginMethod(name: "cerrar", returnType: CAPPluginReturnPromise)
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
}
