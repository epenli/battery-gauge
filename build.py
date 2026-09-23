from pathlib import Path
import subprocess, zipfile, secrets

root = Path(__file__).resolve().parent
tools = root.parent / 'battery-app-tools'
jdk = next((tools / 'jdk').glob('*/bin'))
bt = next((tools / 'build-tools').glob('*/aapt2.exe')).parent
android = next((tools / 'platform').glob('*/android.jar'))
build = root / 'build'
classes = build / 'classes'
dex = build / 'dex'
for p in [build, classes, dex]: p.mkdir(exist_ok=True, parents=True)

def run(args):
    subprocess.run([str(a) for a in args], check=True)

run([jdk/'javac.exe', '-encoding', 'UTF-8', '--release', '8',
     '-classpath', android, '-d', classes, *root.glob('src/**/*.java')])
run([jdk/'java.exe', '-cp', bt/'lib/d8.jar', 'com.android.tools.r8.D8',
     '--lib', android, '--min-api', '28', '--output', dex, *classes.rglob('*.class')])
run([bt/'aapt2.exe', 'link', '-I', android, '--manifest', root/'AndroidManifest.xml', '-o', build/'base.apk'])
with zipfile.ZipFile(build/'base.apk', 'a', zipfile.ZIP_DEFLATED) as z:
    for p in dex.glob('*.dex'): z.write(p, p.name)
run([bt/'zipalign.exe', '-f', '4', build/'base.apk', build/'aligned.apk'])
key = root/'local-signing.p12'
password_file = root/'local-signing.password'
if not key.exists():
    password_file.write_text(secrets.token_urlsafe(32), encoding='utf-8')
    run([jdk/'keytool.exe', '-genkeypair', '-keystore', key, '-storepass:file', password_file,
         '-alias', 'battery-local', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '3650',
         '-dname', 'CN=Battery Gauge Local Build', '-storetype', 'PKCS12'])
run([jdk/'java.exe', '-jar', bt/'lib/apksigner.jar', 'sign', '--ks', key,
     '--ks-pass', 'file:'+str(password_file), '--out', build/'battery-gauge.apk', build/'aligned.apk'])
run([jdk/'java.exe', '-jar', bt/'lib/apksigner.jar', 'verify', '--verbose', build/'battery-gauge.apk'])
print(build/'battery-gauge.apk')
