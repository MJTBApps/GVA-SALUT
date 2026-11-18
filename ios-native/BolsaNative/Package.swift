// swift-tools-version: 5.9
// Complemento propio de Mi Bolsa en iPhone. La compilación (ios.yml) cambia CAPACITOR_DEP por la
// misma línea de capacitor-swift-pm que usa el proyecto generado por Capacitor.
import PackageDescription

let package = Package(
    name: "BolsaNative",
    platforms: [.iOS(.v15)],
    products: [.library(name: "BolsaNative", targets: ["BolsaNative"])],
    dependencies: [
        CAPACITOR_DEP
    ],
    targets: [
        .target(name: "BolsaNative", dependencies: [
            .product(name: "Capacitor", package: "capacitor-swift-pm")
        ])
    ]
)
