"""Build a signed testing APK using the Android SDK and JDK; no Gradle required."""
from pathlib import Path
import os, subprocess, shutil, zipfile, hashlib

ROOT=Path(__file__).resolve().parent
SDK=Path(os.environ.get('ANDROID_HOME',str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk')))
TOOLS=SDK/'build-tools/37.0.0'
PLATFORM=SDK/'platforms/android-37.0/android.jar'
WORK=ROOT.parent/'build/android'
OUT=ROOT.parent/'dist/android'
java_info=subprocess.run(['java','-XshowSettings:properties','-version'],capture_output=True,text=True,check=True)
java_home=next(line.split('=',1)[1].strip() for line in java_info.stderr.splitlines() if line.strip().startswith('java.home ='))
JDK=Path(os.environ.get('JAVA_HOME',java_home))/'bin'
WORK.mkdir(parents=True,exist_ok=True)
OUT.mkdir(parents=True,exist_ok=True)
def run(*args):
    args=list(map(str,args))
    if args[0] in ('java','javac','keytool'):
        args[0]=str(JDK/(args[0]+'.exe'))
    subprocess.run(args,check=True,cwd=ROOT)
if not PLATFORM.exists():raise SystemExit('Install platforms;android-37.0 and build-tools;37.0.0 using sdkmanager first.')
run(TOOLS/'aapt2.exe','compile','--dir',ROOT/'res','-o',WORK/'resources.zip')
run(TOOLS/'aapt2.exe','link','-o',WORK/'base.apk','--manifest',ROOT/'AndroidManifest.xml','-I',PLATFORM,'--min-sdk-version','29','--target-sdk-version','37',WORK/'resources.zip')
classes=WORK/'classes';classes.mkdir(exist_ok=True)
run('javac','-encoding','UTF-8','--release','8','-classpath',PLATFORM,'-d',classes,*ROOT.glob('src/**/*.java'))
run('java','-cp',TOOLS/'lib/d8.jar','com.android.tools.r8.D8','--lib',PLATFORM,'--min-api','29','--output',WORK,*classes.rglob('*.class'))
unsigned=WORK/'unsigned.apk';shutil.copy2(WORK/'base.apk',unsigned)
with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as archive:archive.write(WORK/'classes.dex','classes.dex')
run(TOOLS/'zipalign.exe','-f','-P','16','4',unsigned,WORK/'aligned.apk')
key=ROOT.parent/'.git/android-testing.p12'
if not key.exists():
    run('keytool','-genkeypair','-keystore',key,'-storetype','PKCS12','-storepass','android','-keypass','android','-alias','panotess-testing','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=PanoTess Testing')
apk=OUT/'PanoTess-Android17-testing.apk'
run('java','-jar',TOOLS/'lib/apksigner.jar','sign','--ks',key,'--ks-pass','pass:android','--key-pass','pass:android','--out',apk,WORK/'aligned.apk')
run('java','-jar',TOOLS/'lib/apksigner.jar','verify','--verbose',apk)
run(TOOLS/'aapt2.exe','dump','badging',apk)
(OUT/'SHA256SUMS.txt').write_text(hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
print('Built:',apk)
