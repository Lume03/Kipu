param([string]$Name)
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
& $adb -s RFGL70J9E2T shell uiautomator dump /sdcard/w5c.xml | Out-Null
& $adb -s RFGL70J9E2T pull /sdcard/w5c.xml ".run/w5c/$Name.xml" 2>$null
& $adb -s RFGL70J9E2T shell screencap -p /sdcard/w5c.png
& $adb -s RFGL70J9E2T pull /sdcard/w5c.png ".run/w5c/$Name.png" 2>$null
[xml]$ui = Get-Content ".run/w5c/$Name.xml"
$ui.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' } | ForEach-Object { "$($_.text) | $($_.'content-desc') | $($_.bounds) | enabled=$($_.enabled)" }
