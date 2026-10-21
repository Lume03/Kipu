"""Copy optional private QA images into the isolated lab app, never into APK assets.

Run from the repository root after installLab. Base64 avoids Windows ADB binary
stdin truncation; run-as avoids granting broad photo/storage permissions.
"""
import os
import base64
import hashlib
import pathlib
import subprocess

adb = str(pathlib.Path(os.environ['LOCALAPPDATA']) / 'Android/Sdk/platform-tools/adb.exe')
package = 'com.kipu.app.lab'
subprocess.run([adb, 'shell', 'run-as', package, 'mkdir', '-p', 'files/ocr-yape'], check=True)
sources = sorted(pathlib.Path('docs/referencias/OCR-YAPE').glob('*.png'))
assert len(sources) == 8
for index, source in enumerate(sources):
    command = f"run-as {package} sh -c 'base64 -d > files/ocr-yape/sample-{index}.png'"
    subprocess.run([adb, 'shell', '-T', command], input=base64.b64encode(source.read_bytes()) + b'\n', capture_output=True, check=True)
    digest = subprocess.run([adb, 'shell', f'run-as {package} sha256sum files/ocr-yape/sample-{index}.png'], capture_output=True, text=True, check=True).stdout.split()[0]
    expected = hashlib.sha256(source.read_bytes()).hexdigest()
    assert digest == expected, f'Fixture {index} hash verification failed ({digest!r}, {expected!r})'
print('Eight private fixtures copied to isolated app-private storage.')
