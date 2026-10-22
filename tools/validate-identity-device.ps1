param([string]$Serial = '77986a02', [string]$ArtifactDirectory = "$PSScriptRoot/../build/device/identity-170-validation")
$ErrorActionPreference = 'Stop'
$adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
$probe = 'com.captainsss.usershub.probe'
function Root([string]$command) {
    $out = (& $adb -s $Serial shell "su -c '$command'") -join "`n"
    if ($LASTEXITCODE -ne 0) { throw $out }
    $out
}
$instances = Root 'usershubidentity list'
if ($instances -notmatch '(?m)^22\|63\r?$' -or $instances -notmatch '(?m)^23\|64\r?$') { throw 'Temporary user instances have changed; refuse to run' }
$users = Root 'pm list users'
if ($users -notmatch 'UserInfo\{22:UsersHub-验证A:' -or $users -notmatch 'UserInfo\{23:UsersHub-验证B:') { throw 'Temporary user names do not match' }
$summary = [ordered]@{}
function Stage([int]$user, [string]$mode) {
    $fixture = Get-Content (Join-Path $ArtifactDirectory "fixture-$user.json") -Raw | ConvertFrom-Json
    $fixture.revision = [guid]::NewGuid().ToString()
    if ($mode -eq 'disabled') { $fixture.enabled = $false }
    if ($mode -eq 'unassigned') { $fixture.assignments = @{} }
    $file = Join-Path $ArtifactDirectory "variant-$user.json"
    [IO.File]::WriteAllText($file, ($fixture | ConvertTo-Json -Depth 20 -Compress), [Text.UTF8Encoding]::new($false))
    & $adb -s $Serial push $file "/data/local/tmp/identity-variant-$user.json" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Push failed' }
    Root "usershubidentity stage $user $($fixture.serial) $($fixture.revision) $([int]$fixture.enabled) < /data/local/tmp/identity-variant-$user.json"
}
function Probe([int]$user, [string]$name) {
    Root "am force-stop --user $user $probe; am start --user $user -n $probe/.ProbeActivity" | Out-Null
    Start-Sleep -Seconds 2
    $json = Root "cat /data/user/$user/$probe/files/result.json"
    $result = $json | ConvertFrom-Json
    [IO.File]::WriteAllText((Join-Path $ArtifactDirectory "$name-$user.json"), $json, [Text.UTF8Encoding]::new($false))
    $result
}
function AssertActive($result) {
    if ($result.providerState -ne 'ASSIGNED' -or !$result.crossPackageRejected -or !$result.sdkUnchanged) { throw 'Provider/security/SDK test failed' }
    if (@($result.checks.PSObject.Properties).Count -ne 23 -or @($result.checks.PSObject.Properties | Where-Object { $_.Value -ne $true }).Count -gt 0) { throw 'Field comparison failed' }
}
function AssertBaseline($result, [int]$user) {
    $baseline = Get-Content (Join-Path $ArtifactDirectory "baseline-$user.json") -Raw | ConvertFrom-Json
    foreach ($field in $baseline.hashes.PSObject.Properties.Name) {
        if ($result.hashes.$field -ne $baseline.hashes.$field) { throw "Baseline mismatch: $user/$field" }
    }
}
$a = Get-Content (Join-Path $ArtifactDirectory 'active-22.json') -Raw | ConvertFrom-Json
$b = Get-Content (Join-Path $ArtifactDirectory 'active-23.json') -Raw | ConvertFrom-Json
AssertActive $a; AssertActive $b
$idFields = 'android_id','imei','meid','imsi','iccid','phone_number','gaid','gsf_id','media_drm_id','app_set_id'
foreach ($field in $idFields) { if ($a.hashes.$field -eq $b.hashes.$field) { throw "Cross-user collision: $field" } }
$summary.crossUserDistinct10 = $true
$restart = Probe 22 'restart'; AssertActive $restart
foreach ($field in $a.hashes.PSObject.Properties.Name) { if ($a.hashes.$field -ne $restart.hashes.$field) { throw "Restart changed: $field" } }
$summary.restartPreservesFullProfile = $true
Stage 22 disabled; AssertBaseline (Probe 22 'disabled') 22; $summary.disabledRestoresBaseline = $true
Stage 22 unassigned; AssertBaseline (Probe 22 'unassigned') 22; $summary.unassignedRestoresBaseline = $true
$stale = (& $adb -s $Serial shell "su -c 'usershubidentity stage 22 999999 12345678-1234-1234-1234-123456789abc 1 < /data/local/tmp/fixture-22.json'") -join "`n"
if ($LASTEXITCODE -eq 0 -or $stale -notmatch '空间已删除') { throw 'Stale instance was not rejected' }
$summary.staleInstanceRejected = $true
Root 'usershubctl freeze 23'
$pending = Stage 23 active
if ($pending -notmatch 'PENDING\|23') { throw 'Frozen user did not queue deployment' }
$state = Root 'am get-started-user-state 23 2>&1 || true'
if ($state -match 'RUNNING') { throw 'Configuration unexpectedly started user' }
$summary.frozenStageDoesNotStartUser = $true
Root 'usershubctl thaw 23'
AssertActive (Probe 23 'thaw'); $summary.thawDeploys = $true
Stage 22 active
Root 'usershubidentity emergency-off'
AssertBaseline (Probe 22 'emergency') 22
AssertBaseline (Probe 23 'emergency') 23
$summary.emergencyRestoresBoth = $true
$summary.fixtureNotice = 'GAID and AppSet use local test SDK-shaped classes, not live Google services.'
$summary | ConvertTo-Json
[IO.File]::WriteAllText((Join-Path $ArtifactDirectory 'summary.json'), ($summary | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
