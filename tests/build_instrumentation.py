#!/usr/bin/env python3
"""Build a test-only instrumentation package with the release APK's local certificate."""
import json
import os
from pathlib import Path
import subprocess
import zipfile
root = Path(__file__).resolve().parent.parent
sdk = Path(os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk')))
tools = Path(os.environ.get('ANDROID_BUILD_TOOLS', sdk / 'build-tools' / '35.0.0'))
android = Path(os.environ.get('ANDROID_JAR', sdk / 'platforms' / 'android-30' / 'android.jar'))
dest = root / 'build' / 'instrumentation'
dest.mkdir(exist_ok=True)
(dest / 'classes').mkdir(exist_ok=True)
(dest / 'dex').mkdir(exist_ok=True)
def run(*args): subprocess.run([str(x) for x in args], check=True)
run(tools / 'aapt2', 'link', '-I', android, '--manifest', root / 'tests' / 'AndroidManifest.xml', '-o', dest / 'tests.apk')
run('javac', '--release', '8', '-encoding', 'UTF-8', '-classpath', str(android) + os.pathsep + str(root / 'build' / 'classes'), '-d', dest / 'classes', root / 'tests' / 'SmokeTests.java')
with zipfile.ZipFile(dest / 'tests.jar', 'w') as z:
    for p in (dest / 'classes').rglob('*.class'): z.write(p, p.relative_to(dest / 'classes'))
run('java', '-cp', tools / 'lib' / 'd8.jar', 'com.android.tools.r8.D8', '--min-api', '30', '--lib', android, '--classpath', root / 'build' / 'classes.jar', '--output', dest / 'dex', dest / 'tests.jar')
with zipfile.ZipFile(dest / 'tests.apk', 'a') as z: z.write(dest / 'dex' / 'classes.dex', 'classes.dex')
run(tools / 'zipalign', '-f', '4', dest / 'tests.apk', dest / 'aligned.apk')
os.environ['FIXED_LOCATION_KEY_PASSWORD'] = json.loads((root / '.local' / '.fixed-location-signing.json').read_text())['password']
run('java', '-jar', tools / 'lib' / 'apksigner.jar', 'sign', '--ks', root / '.local' / '.fixed-location-signing.p12', '--ks-pass', 'env:FIXED_LOCATION_KEY_PASSWORD', '--ks-key-alias', 'fixed-location', '--out', dest / 'SmokeTests.apk', dest / 'aligned.apk')
print(dest / 'SmokeTests.apk')
