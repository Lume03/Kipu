import subprocess,sys,xml.etree.ElementTree as E
from pathlib import Path
a=[str(Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe'),'-s','RFGL70J9E2T']
def adb(*args): return subprocess.run(a+list(args),capture_output=True)
if sys.argv[1]=='snap':
 n=sys.argv[2]; Path('.run/w5b/'+n+'.png').write_bytes(adb('exec-out','screencap','-p').stdout)
 adb('shell','uiautomator','dump','/sdcard/window.xml'); b=adb('exec-out','cat','/sdcard/window.xml').stdout;Path('.run/w5b/'+n+'.xml').write_bytes(b)
 for e in E.fromstring(b).iter('node'):
  t=e.get('text') or e.get('content-desc')
  if t: print(t,e.get('bounds'))
else:
 print(adb('shell',*sys.argv[1:]).stdout.decode(errors='replace'))
