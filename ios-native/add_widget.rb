# Añade al proyecto que genera Capacitor (ios/App) el widget de la pantalla de inicio y el
# grupo de apps compartido. Lo usa .github/workflows/ios.yml (gema xcodeproj).
require 'xcodeproj'
require 'fileutils'

raiz = ARGV[0] || 'ios/App'
aqui = File.dirname(File.expand_path(__FILE__))
FileUtils.cp_r(File.join(aqui, 'BolsaWidget'), raiz)
FileUtils.cp(File.join(aqui, 'App.entitlements'), File.join(raiz, 'App', 'App.entitlements'))

proj = Xcodeproj::Project.open(File.join(raiz, 'App.xcodeproj'))
app = proj.targets.find { |t| t.name == 'App' } or abort 'No encuentro el target App'
abort 'El widget ya está' if proj.targets.any? { |t| t.name == 'BolsaWidget' }

w = proj.new_target(:app_extension, 'BolsaWidget', :ios, '16.0', nil, :swift)
grupo = proj.main_group.new_group('BolsaWidget', 'BolsaWidget')
w.add_file_references([grupo.new_file('BolsaWidget.swift')])
grupo.new_file('Info.plist')
grupo.new_file('BolsaWidget.entitlements')

w.build_configurations.each do |c|
  s = c.build_settings
  s['PRODUCT_BUNDLE_IDENTIFIER'] = 'es.mjtbapps.mibolsa.BolsaWidget'
  s['PRODUCT_NAME'] = '$(TARGET_NAME)'
  s['INFOPLIST_FILE'] = 'BolsaWidget/Info.plist'
  s['GENERATE_INFOPLIST_FILE'] = 'NO'
  s['CODE_SIGN_ENTITLEMENTS'] = 'BolsaWidget/BolsaWidget.entitlements'
  s['CODE_SIGN_STYLE'] = 'Automatic'
  s['SWIFT_VERSION'] = '5.0'
  s['IPHONEOS_DEPLOYMENT_TARGET'] = '16.0'
  s['TARGETED_DEVICE_FAMILY'] = '1,2'
  s['SKIP_INSTALL'] = 'YES'
  s['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks', '@executable_path/../../Frameworks']
end

fase = app.new_copy_files_build_phase('Embed Foundation Extensions')
fase.dst_subfolder_spec = '13' # PlugIns
fase.add_file_reference(w.product_reference, true).settings = { 'ATTRIBUTES' => ['RemoveHeadersOnCopy'] }
app.add_dependency(w)
app.build_configurations.each { |c| c.build_settings['CODE_SIGN_ENTITLEMENTS'] = 'App/App.entitlements' }

proj.save
puts 'Widget añadido: ' + proj.targets.map(&:name).join(', ')
