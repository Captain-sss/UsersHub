param([string]$Serial = '77986a02', [string]$Screenshot)
$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
Start-Sleep -Milliseconds 900
$dump = (& $adb -s $Serial shell uiautomator dump /data/local/tmp/identity-ui.xml 2>$null) -join "`n"
if ($LASTEXITCODE -ne 0 -or $dump -notmatch 'dumped to:') { throw "No fresh UI tree: $dump" }
if ($Screenshot) {
    & $adb -s $Serial shell screencap -p /data/local/tmp/usershub-validation.png
    & $adb -s $Serial pull /data/local/tmp/usershub-validation.png $Screenshot
}
[xml]$tree = (& $adb -s $Serial shell cat /data/local/tmp/identity-ui.xml) -join "`n"
$tree.SelectNodes('//node') | Where-Object { $_.text -or $_.'content-desc' -or $_.checkable -eq 'true' } |
    ForEach-Object { "$($_.bounds) | checked=$($_.checked) | $($_.text) $($_.'content-desc')" }
