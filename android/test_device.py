"""Run native integration tests on the dedicated PanoTess API 37 emulator."""
import runpy,subprocess,zipfile,shutil
from pathlib import Path
config=runpy.run_path(str(Path(__file__).with_name('build.py')))
ROOT,WORK,TOOLS,PLATFORM,JDK=[config[k] for k in ('ROOT','WORK','TOOLS','PLATFORM','JDK')]
def run(*args):subprocess.run(list(map(str,args)),check=True)
classes=WORK/'instrumentation';classes.mkdir(exist_ok=True)
run(JDK/'javac.exe','-encoding','UTF-8','--release','8','-classpath',str(PLATFORM)+';'+str(WORK/'classes'),'-d',classes,ROOT/'tests/TestDocuments.java',ROOT/'tests/NativeSmoke.java')
testdex=WORK/'testdex';testdex.mkdir(exist_ok=True)
run(JDK/'java.exe','-cp',TOOLS/'lib/d8.jar','com.android.tools.r8.D8','--lib',PLATFORM,'--classpath',WORK/'classes','--min-api','29','--output',testdex,*classes.rglob('*.class'))
base=WORK/'tests-unsigned.apk'
run(TOOLS/'aapt2.exe','link','--manifest',ROOT/'tests/AndroidManifest.xml','-I',PLATFORM,'-o',base)
with zipfile.ZipFile(base,'a',zipfile.ZIP_DEFLATED)as z:z.write(testdex/'classes.dex','classes.dex')
aligned=WORK/'tests-aligned.apk';apk=WORK/'tests.apk'
run(TOOLS/'zipalign.exe','-f','-P','16','4',base,aligned)
run(JDK/'java.exe','-jar',TOOLS/'lib/apksigner.jar','sign','--ks',ROOT.parent/'.git/android-testing.p12','--ks-pass','pass:android','--out',apk,aligned)
adb=['adb','-s','emulator-5580']
run(*adb,'install','-r',ROOT.parent/'dist/android/PanoTess-Android17-testing.apk')
run(*adb,'install','-r',apk)
result=subprocess.run(adb+['shell','am','instrument','-w','com.jirawat2105.panotess.tests/com.jirawat2105.panotess.NativeSmoke'],capture_output=True,text=True,timeout=90)
print(result.stdout,result.stderr)
if 'PASS:' not in result.stdout:raise SystemExit('Android integration test failed')
