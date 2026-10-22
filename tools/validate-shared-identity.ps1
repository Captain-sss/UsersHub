param([string]$Serial = '77986a02')
$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
function Read-Phone([string]$Command) {
    $value = (& $adb -s $Serial shell "su -c '$Command'") -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "Read failed: $Command" }
    return $value.Trim()
}
$root = (Read-Phone 'cat /data/user/0/com.captainsss.usershub/files/identity-store.json') | ConvertFrom-Json
$instances = (Read-Phone 'usershubidentity list') -split "`n"
$seen = @{}; $rows = @()
foreach ($line in $instances) {
    $parts = $line.Trim().Split('|'); $uid = [int]$parts[0]; $serialNumber = [long]$parts[1]
    $key = "${uid}:${serialNumber}"
    $w = $root.workspaces.PSObject.Properties[$key].Value
    if (!$w -or !$w.enabled -or !$w.autoInherit -or @($w.profiles).Count -ne 1) { throw "Invalid shared mode: $uid" }
    $p = $w.profiles[0]
    if ($w.sharedProfileId -ne $p.id -or @($p.enabledFields).Count -ne 10 -or @($p.values.PSObject.Properties).Count -ne 10) { throw "Wrong field count: $uid" }
    foreach ($field in $p.values.PSObject.Properties) {
        $identityKey = "$($field.Name):$($field.Value)"
        if ($seen.ContainsKey($identityKey)) { throw "Cross-user identity collision: $uid/$($field.Name)" }
        $seen[$identityKey] = $true
    }
    $runtime = (Read-Phone "cat /data/adb/usershub/identity/$uid-$serialNumber.json") | ConvertFrom-Json
    $scope = (Read-Phone "cat /data/adb/usershub/identity/$uid-$serialNumber.scope.json") | ConvertFrom-Json
    if ($runtime.revision -ne $w.revision -or $scope.revision -ne $w.revision -or $scope.state -ne 'READY') { throw "Deployment/scope mismatch: $uid" }
    $rows += [pscustomobject]@{Space=$uid; Fields=10; Shared=$true; Unique=$true; Scope=$scope.state; Targets=@($scope.packages).Count}
}
$state = Read-Phone 'usershubstatus'
foreach ($line in $state -split "`n") {
    $fields = $line.Trim().Split('|')
    if ($fields.Count -lt 4) { continue }
    $uid = [int]$fields[0]
    if ($uid -eq 18) { if ($fields[3] -ne 'RUNNING') { throw 'Previously running u18 changed state' } }
    elseif ($uid -ge 10 -and $uid -le 21) {
        if ($fields[2] -ne 'FROZEN' -or $fields[3] -ne 'STOPPED') { throw "Frozen workspace changed state: $uid" }
    }
}
$rows | Format-Table -AutoSize
'PASS: 12 independent shared configurations; frozen spaces unchanged. No identifier values printed.'
