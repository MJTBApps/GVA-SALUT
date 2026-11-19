import WidgetKit
import SwiftUI

// Widget de Mi Bolsa: enseña lo último que guardó la app (no consulta nada por sí mismo).
private let grupo = "group.es.mjtbapps.mibolsa"

struct Entrada: TimelineEntry {
    let date: Date
    let titulo: String
    let linea1: String
    let linea2: String
}

struct Proveedor: TimelineProvider {
    func leer() -> Entrada {
        let d = UserDefaults(suiteName: grupo)
        return Entrada(date: Date(),
                       titulo: d?.string(forKey: "titulo") ?? "Mi Bolsa",
                       linea1: d?.string(forKey: "linea1") ?? "Abre la app",
                       linea2: d?.string(forKey: "linea2") ?? "para ver tu puesto")
    }
    func placeholder(in context: Context) -> Entrada {
        Entrada(date: Date(), titulo: "Mi Bolsa · SES Alicante", linea1: "#237 · Disponible", linea2: "6 disponibles delante")
    }
    func getSnapshot(in context: Context, completion: @escaping (Entrada) -> Void) { completion(leer()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entrada>) -> Void) {
        completion(Timeline(entries: [leer()], policy: .never))
    }
}

private let degradado = LinearGradient(colors: [Color(red: 0.06, green: 0.46, blue: 0.43), Color(red: 0.12, green: 0.23, blue: 0.54)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing)

struct Fondo: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            content.containerBackground(for: .widget) { degradado }
        } else {
            content.padding().background(degradado)
        }
    }
}

struct VistaBolsa: View {
    var e: Entrada
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(e.titulo).font(.caption).foregroundColor(Color(red: 0.6, green: 0.96, blue: 0.89)).lineLimit(1)
            Spacer(minLength: 0)
            Text(e.linea1).font(.system(size: 22, weight: .bold, design: .rounded)).foregroundColor(.white).lineLimit(1).minimumScaleFactor(0.6)
            Text(e.linea2).font(.footnote).foregroundColor(Color.white.opacity(0.85)).lineLimit(2)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        .modifier(Fondo())
    }
}

@main
struct BolsaWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "BolsaWidget", provider: Proveedor()) { e in VistaBolsa(e: e) }
            .configurationDisplayName("Mi Bolsa")
            .description("Tu puesto y la gente disponible delante.")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}
