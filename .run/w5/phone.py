import subprocess,sys,pathlib,xml.etree.ElementTree as E
adb=str(pathlib.Path.home()/'AppData/Local/Android/Sdk/platform-tools/adb.exe')
def run(*args): return subprocess.run([adb,'-s','RFGL70J9E2T',*args],capture_output=True).stdout
if sys.argv[1]=='snap':
 name=sys.argv[2]; run('shell','uiautomator','dump','/sdcard/w5.xml'); raw=run('shell','cat','/sdcard/w5.xml'); pathlib.Path('.run/w5/'+name+'.xml').write_bytes(raw); pathlib.Path('.run/w5/'+name+'.png').write_bytes(run('exec-out','screencap','-p'))
 for n in E.fromstring(raw).iter('node'):
  if n.get('text') or n.get('content-desc'): print(n.get('text'),n.get('content-desc'),n.get('bounds'),'enabled='+str(n.get('enabled')),'checked='+str(n.get('checked')))
else: print(run('shell',*sys.argv[1:]).decode(errors='replace'))
