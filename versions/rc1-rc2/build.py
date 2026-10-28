#!/usr/bin/env python3
"""Build with official Android SDK tools; no Gradle or third-party dependencies."""
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent
SDK = Path(os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', Path.home() / 'Library/Android/sdk')))
TOOLS = Path(os.environ.get('ANDROID_BUILD_TOOLS', SDK / 'build-tools' / '35.0.0'))
ANDROID = Path(os.environ.get('ANDROID_JAR', SDK / 'platforms' / 'android-30' / 'android.jar'))
BUILD = ROOT / 'build'
DEST = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else BUILD / 'FixedLocation-RC1-RC2-test.apk'

def run(*args, env=None):
    subprocess.run([str(x) for x in args], check=True, env=env)

def main():
    if not ANDROID.is_file():
        raise SystemExit('Set ANDROID_JAR to Android 11/API 30 android.jar')
    for name in ['aapt2', 'zipalign']:
        (TOOLS / name).chmod(0o755)
    BUILD.mkdir(exist_ok=True)
    for name in ['generated', 'classes', 'dex']:
        shutil.rmtree(BUILD / name, ignore_errors=True)
        (BUILD / name).mkdir()
    run(TOOLS / 'aapt2', 'compile', '--dir', ROOT / 'res', '-o', BUILD / 'resources.zip')
    run(TOOLS / 'aapt2', 'link', '-o', BUILD / 'resources.apk', '-I', ANDROID,
        '--manifest', ROOT / 'AndroidManifest.xml', '--java', BUILD / 'generated', '-A', ROOT / 'assets',
        '--min-sdk-version', '29', '--target-sdk-version', '30', BUILD / 'resources.zip')
    sources = sorted((ROOT / 'src').rglob('*.java')) + sorted((BUILD / 'generated').rglob('*.java'))
    run('javac', '--release', '8', '-encoding', 'UTF-8', '-classpath', ANDROID,
        '-d', BUILD / 'classes', *sources)
    with zipfile.ZipFile(BUILD / 'classes.jar', 'w', zipfile.ZIP_DEFLATED) as archive:
        for path in sorted((BUILD / 'classes').rglob('*.class')):
            archive.write(path, path.relative_to(BUILD / 'classes'))
    run('java', '-cp', TOOLS / 'lib' / 'd8.jar', 'com.android.tools.r8.D8',
        '--release', '--min-api', '29', '--lib', ANDROID,
        '--output', BUILD / 'dex', BUILD / 'classes.jar')
    shutil.copyfile(BUILD / 'resources.apk', BUILD / 'unsigned.apk')
    with zipfile.ZipFile(BUILD / 'unsigned.apk', 'a', zipfile.ZIP_DEFLATED) as archive:
        for dex in sorted((BUILD / 'dex').glob('*.dex')):
            archive.write(dex, dex.name)
    run(TOOLS / 'zipalign', '-f', '-p', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk')
    # Local signing material persists for updates and is never included in source exports.
    local = ROOT / '.local'
    local.mkdir(mode=0o700, exist_ok=True)
    secret_path = local / '.fixed-location-signing.json'
    key_path = local / '.fixed-location-signing.p12'
    if secret_path.exists():
        password = json.loads(secret_path.read_text())['password']
    else:
        password = secrets.token_urlsafe(24)
        fd = os.open(secret_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as stream:
            json.dump({'password': password}, stream)
    env = dict(os.environ, FIXED_LOCATION_KEY_PASSWORD=password)
    if not key_path.exists():
        run('keytool', '-genkeypair', '-keystore', key_path, '-storetype', 'PKCS12',
            '-storepass:env', 'FIXED_LOCATION_KEY_PASSWORD', '-keypass:env', 'FIXED_LOCATION_KEY_PASSWORD',
            '-alias', 'fixed-location', '-keyalg', 'RSA', '-keysize', '3072',
            '-validity', '3650', '-dname', 'CN=Fixed Location Local Build', env=env)
        key_path.chmod(0o600)
    DEST.parent.mkdir(parents=True, exist_ok=True)
    signer = TOOLS / 'lib' / 'apksigner.jar'
    run('java', '-jar', signer, 'sign', '--ks', key_path,
        '--ks-pass', 'env:FIXED_LOCATION_KEY_PASSWORD', '--ks-key-alias', 'fixed-location',
        '--out', DEST, BUILD / 'aligned.apk', env=env)
    run('java', '-jar', signer, 'verify', '--verbose', '--print-certs', DEST)
    run(TOOLS / 'zipalign', '-c', '-v', '4', DEST)
    print('APK:', DEST)
    print('SHA-256:', hashlib.sha256(DEST.read_bytes()).hexdigest())

if __name__ == '__main__':
    main()
