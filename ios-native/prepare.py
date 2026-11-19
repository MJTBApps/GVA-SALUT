"""Añade al proyecto de iPhone que genera Capacitor (ios/App) lo propio de Mi Bolsa:
el complemento Navegador (BolsaNative). Lo usa .github/workflows/ios.yml."""
import json, os, re, shutil, sys

ROOT = os.path.dirname(os.path.abspath(__file__))
APP = sys.argv[1] if len(sys.argv) > 1 else 'ios/App'

# 1. Paquete BolsaNative con la misma versión de Capacitor que el proyecto
spm = os.path.join(APP, 'CapApp-SPM', 'Package.swift')
s = open(spm).read()
m = re.search(r'\.package\(url:\s*"https://github\.com/ionic-team/capacitor-swift-pm\.git"[^)]*\)', s)
assert m, 'No encuentro capacitor-swift-pm en ' + spm
dst = os.path.join(APP, 'BolsaNative')
shutil.rmtree(dst, ignore_errors=True)
shutil.copytree(os.path.join(ROOT, 'BolsaNative'), dst)
pk = os.path.join(dst, 'Package.swift')
txt = open(pk).read().replace('CAPACITOR_DEP', m.group(0))
open(pk, 'w').write(txt)  # leer antes de abrir para escribir (si no, queda vacío)

# 2. CapApp-SPM depende de BolsaNative
if 'BolsaNative' not in s:
    s = s.replace(m.group(0), m.group(0) + ',\n        .package(name: "BolsaNative", path: "../BolsaNative")', 1)
    prod = re.search(r'\.product\(name:\s*"Capacitor",\s*package:\s*"capacitor-swift-pm"\)', s)
    assert prod, 'No encuentro el producto Capacitor en ' + spm
    s = s[:prod.end()] + ',\n                .product(name: "BolsaNative", package: "BolsaNative")' + s[prod.end():]
    open(spm, 'w').write(s)
print(open(spm).read())

# 3. Que Capacitor cargue el complemento
cc = os.path.join(APP, 'App', 'capacitor.config.json')
cfg = json.load(open(cc))
lst = cfg.get('packageClassList', [])
if 'NavegadorPlugin' not in lst: lst.append('NavegadorPlugin')
cfg['packageClassList'] = lst
json.dump(cfg, open(cc, 'w'), indent=2)
print('packageClassList:', lst)

# 4. AppDelegate de Capacitor: importar BolsaNative y nombrar la clase, para que el
#    enlazador no la descarte y Capacitor la encuentre por su nombre
ad = os.path.join(APP, 'App', 'AppDelegate.swift')
t = open(ad).read()
if 'import BolsaNative' not in t:
    t = t.replace('import Capacitor', 'import Capacitor\nimport BolsaNative', 1)
    t, n = re.subn(r'(didFinishLaunchingWithOptions[^{]*\{)', r'\1\n        _ = NavegadorPlugin.self', t, count=1)
    assert n == 1, 'No encuentro didFinishLaunchingWithOptions en ' + ad
    open(ad, 'w').write(t)
print(open(ad).read()[:600])
