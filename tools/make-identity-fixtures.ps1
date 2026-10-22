param([int]$UserA, [long]$SerialA, [int]$UserB, [long]$SerialB,
      [string]$OutputDirectory = "$PSScriptRoot/../build/device/identity-170-validation")
$ErrorActionPreference = 'Stop'
if ($UserA -lt 10 -or $UserB -lt 10 -or $UserA -eq $UserB) { throw 'Two distinct temporary users are required' }
[void][IO.Directory]::CreateDirectory($OutputDirectory)
function Hex([int]$length) {
    $bytes = [byte[]]::new($length / 2)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    [Convert]::ToHexString($bytes).ToLowerInvariant()
}
function Luhn([string]$prefix) {
    $sum = 0; $double = $true
    for ($i = $prefix.Length - 1; $i -ge 0; $i--) {
        $n = [int]::Parse($prefix.Substring($i, 1))
        if ($double) { $n *= 2; if ($n -gt 9) { $n -= 9 } }
        $sum += $n; $double = !$double
    }
    $prefix + ((10 - $sum % 10) % 10)
}
$instances = @(@($UserA, $SerialA), @($UserB, $SerialB))
foreach ($instance in $instances) {
    $user = $instance[0]; $serial = $instance[1]
    $suffix = '{0:D6}' -f $user
    $values = [ordered]@{
        android_id = (Hex 16); imei = (Luhn "35847631$suffix"); meid = (Hex 14).ToUpperInvariant()
        imsi = "310260000$suffix"; iccid = (Luhn "890126000000$suffix"); phone_number = "+12020$suffix"
        gaid = [guid]::NewGuid().ToString(); gsf_id = (Hex 16); media_drm_id = (Hex 64); app_set_id = [guid]::NewGuid().ToString()
        brand = 'UsersHubTest'; manufacturer = 'UsersHubTest'; model = "Test $user"; device = "test$user"
        product = "test$user"; board = "test$user"; hardware = "test$user"
        fingerprint = "UsersHubTest/test$user/test${user}:12/TEST/1:user/release-keys"
        build_id = 'TEST'; build_incremental = '1'; build_type = 'user'; build_tags = 'release-keys'; serial = "TEST$user"
    }
    $profileId = [guid]::NewGuid().ToString()
    $workspace = [ordered]@{
        version = 1; userId = $user; serial = $serial; enabled = $true; revision = [guid]::NewGuid().ToString()
        profiles = @([ordered]@{id = $profileId; name = '1'; values = $values; enabledFields = @($values.Keys)})
        assignments = @{'com.captainsss.usershub.probe' = $profileId}
    }
    $file = Join-Path $OutputDirectory "fixture-$user.json"
    [IO.File]::WriteAllText($file, ($workspace | ConvertTo-Json -Depth 20 -Compress), [Text.UTF8Encoding]::new($false))
    Write-Output "Fixture user=$user serial=$serial revision=$($workspace.revision)"
}
