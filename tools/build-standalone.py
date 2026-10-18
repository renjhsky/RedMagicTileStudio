#!/usr/bin/env python3
"""Build the resource-free app with official SDK tools and Eclipse ECJ, without Gradle.
Supply an existing android.jar, build-tools directory and ECJ jar; downloads are not performed.
"""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

RELEASE_VARIABLES = ('RMT_RELEASE_KEYSTORE','RMT_RELEASE_STORE_PASSWORD','RMT_RELEASE_KEY_ALIAS','RMT_RELEASE_KEY_PASSWORD')

def load_version(root):
    values = dict(line.strip().split('=',1) for line in (root / 'version.properties').read_text().splitlines()
                  if line.strip() and not line.lstrip().startswith('#'))
    name, code = values['versionName'], int(values['versionCode'])
    if not re.fullmatch(r'[0-9A-Za-z._-]+',name) or code < 1:
        raise ValueError('Invalid version.properties')
    return name, code

def load_repository(root, required=False):
    values = dict(line.strip().split('=',1) for line in (root / 'repository.properties').read_text().splitlines()
                  if line.strip() and not line.lstrip().startswith('#'))
    repository = values.get('githubRepository','').strip()
    if (required and not repository) or (repository and not re.fullmatch(r'[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})/[A-Za-z0-9._-]+',repository)):
        raise ValueError('Set githubRepository to the actual GitHub owner/repository before publishing.')
    return repository

def signing_options(root, release):
    if not release:
        configured = os.environ.get('RMT_KEYSTORE','').strip()
        legacy = root / '.build-sign/prebuilt.jks'
        key = Path(configured).expanduser().resolve() if configured else legacy if legacy.is_file() else root / '.local-sign/debug.jks'
        if configured and not key.is_file():
            raise ValueError('RMT_KEYSTORE must point to an existing local keystore.')
        password = 'env:RMT_SIGN_PASS' if os.environ.get('RMT_SIGN_PASS') else 'pass:android'
        return key, os.environ.get('RMT_KEY_ALIAS','tile-studio'), password, password
    missing = [name for name in RELEASE_VARIABLES if not os.environ.get(name,'').strip()]
    if missing:
        raise ValueError('Release signing requires: '+', '.join(missing))
    key = Path(os.environ['RMT_RELEASE_KEYSTORE']).expanduser().resolve()
    if not key.is_file() or root == key or root in key.parents:
        raise ValueError('Release keystore must be a regular file outside the source project.')
    return key, os.environ['RMT_RELEASE_KEY_ALIAS'], 'env:RMT_RELEASE_STORE_PASSWORD', 'env:RMT_RELEASE_KEY_PASSWORD'

def ensure_debug_key(root, key, alias):
    if key.is_file():
        return
    if key != root / '.local-sign/debug.jks':
        raise ValueError('Refusing to generate or overwrite a configured signing key.')
    key.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    subprocess.run(['keytool','-genkeypair','-keystore',str(key),'-alias',alias,
                    '-storepass','android','-keypass','android','-keyalg','RSA','-keysize','3072',
                    '-validity','10000','-dname','CN=RedMagic Tile Studio Local Debug,O=Local Build,C=CN',
                    '-storetype','JKS','-noprompt'],check=True)
    key.chmod(0o600)

def validate_release_key(verification_output, root):
    public_key = re.search(r'Signer #1 public key SHA-256 digest: ([0-9a-fA-F]+)', verification_output)
    test_key = (root / '.build-sign/test-public-key.sha256').read_text().strip().lower()
    if public_key is None or public_key.group(1).lower() == test_key:
        raise ValueError('Release signing cannot use the public test key.')

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--android-jar', required=True, type=Path)
    parser.add_argument('--build-tools', required=True, type=Path)
    parser.add_argument('--ecj', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--release', action='store_true', help='Require an external private signing key; never use the public test key.')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    version_name, version_code = load_version(root)
    repository = load_repository(root,args.release)
    key, alias, store_password, key_password = signing_options(root,args.release)
    if not args.release:
        ensure_debug_key(root,key,alias)
    android = args.android_jar.resolve()
    bt = args.build_tools.resolve()
    ecj = args.ecj.resolve()
    output = args.output.resolve()
    work = root / '.standalone-build'
    if work.exists():
        shutil.rmtree(work)
    (work / 'classes').mkdir(parents=True)
    (work / 'dex').mkdir()
    generated = work / 'generated/io/github/renjhtools/redmagictiles/BuildConfig.java'
    generated.parent.mkdir(parents=True)
    generated.write_text('package io.github.renjhtools.redmagictiles;\npublic final class BuildConfig {\n'
                         f' public static final String VERSION_NAME = "{version_name}";\n'
                         f' public static final int VERSION_CODE = {version_code};\n'
                         f' public static final String GITHUB_REPOSITORY = "{repository}";\n}}\n')

    def run(command):
        subprocess.run([str(x) for x in command], check=True)

    dependencies = []
    for name in ['api', 'interface']:
        aar = root / '.tools/maven/io/github/libxposed' / name / '102.0.0' / f'{name}-102.0.0.aar'
        jar = work / f'{name}.jar'
        with zipfile.ZipFile(aar) as archive:
            jar.write_bytes(archive.read('classes.jar'))
        dependencies.append(jar)
    classpath = os.pathsep.join(str(x) for x in dependencies + [bt / 'core-lambda-stubs.jar'])
    # Android's bootclasspath and core-lambda stubs avoid mixing java.base with Android stubs.
    run(['java', '-jar', ecj, '-8', '-bootclasspath', android, '-encoding', 'UTF-8', '-proc:none',
         '-warn:none', '-classpath', classpath, '-d', work / 'classes',
         *sorted((root / 'app/src/main/java').rglob('*.java')), generated])
    with zipfile.ZipFile(work / 'classes.jar', 'w') as archive:
        for file in sorted((work / 'classes').rglob('*.class')):
            archive.write(file, file.relative_to(work / 'classes').as_posix())
    run(['java', '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--release', '--min-api', '35',
         '--lib', android, '--classpath', dependencies[0], '--classpath', dependencies[1],
         '--output', work / 'dex', work / 'classes.jar'])

    ns = 'http://schemas.android.com/apk/res/android'
    ET.register_namespace('android', ns)
    manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml')
    document = manifest.getroot()
    document.set('package', 'io.github.renjhtools.redmagictiles')
    document.set(f'{{{ns}}}versionCode', str(version_code))
    document.set(f'{{{ns}}}versionName', version_name)
    sdk = ET.Element('uses-sdk')
    sdk.set(f'{{{ns}}}minSdkVersion', '35')
    sdk.set(f'{{{ns}}}targetSdkVersion', '35')
    document.insert(0, sdk)
    manifest.write(work / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    run([bt / 'aapt2', 'link', '-I', android, '--manifest', work / 'AndroidManifest.xml', '-o', work / 'unsigned.apk'])
    with zipfile.ZipFile(work / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as archive:
        archive.write(work / 'dex/classes.dex', 'classes.dex')
        resources = root / 'app/src/main/resources'
        for file in sorted(resources.rglob('*')):
            if file.is_file():
                archive.write(file, file.relative_to(resources).as_posix())
    run([bt / 'zipalign', '-f', '4', work / 'unsigned.apk', work / 'aligned.apk'])
    signed = work / 'signed.apk'
    run(['java', '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', key,
         '--ks-key-alias', alias, '--ks-pass', store_password, '--key-pass', key_password,
         '--min-sdk-version', '35', '--v4-signing-enabled', 'false', '--out', signed, work / 'aligned.apk'])
    verification = subprocess.run(['java','-jar',str(bt / 'lib/apksigner.jar'),'verify','--verbose','--print-certs',str(signed)],
                                  text=True,capture_output=True,check=True)
    if args.release:
        validate_release_key(verification.stdout,root)
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(signed,output)
    print(verification.stdout)
    print(output)

if __name__ == '__main__':
    main()
