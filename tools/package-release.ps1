param(
    [string]$Version = '1.9.0',
    [string]$CoreVersion = '1.9.0'
)
$ErrorActionPreference = 'Stop'
if ($Version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid version' }
if ($CoreVersion -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid core version' }
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskDist = Join-Path $taskRoot 'dist'
New-Item -ItemType Directory -Path $taskDist -Force | Out-Null
$taskApk = Join-Path $taskRoot 'app\build\outputs\apk\release\app-release.apk'
$taskZipPath = Join-Path $taskDist "UsersHub-Core-Magisk-v$CoreVersion.zip"
$taskModule = Join-Path $taskRoot 'magisk'
Add-Type -AssemblyName System.IO.Compression
$taskStream = [IO.File]::Open($taskZipPath, [IO.FileMode]::Create)
try {
    $taskZip = [IO.Compression.ZipArchive]::new($taskStream, [IO.Compression.ZipArchiveMode]::Create, $false)
    try {
        foreach ($taskFile in Get-ChildItem -LiteralPath $taskModule -Recurse -File) {
            $taskRelative = $taskFile.FullName.Substring($taskModule.Length + 1).Replace('\', '/')
            $taskEntry = $taskZip.CreateEntry($taskRelative, [IO.Compression.CompressionLevel]::Optimal)
            $taskEntryStream = $taskEntry.Open()
            try {
                if ($taskRelative -match '\.sh$|\.prop$|^system/bin/') {
                    $taskBytes = [Text.UTF8Encoding]::new($false).GetBytes([IO.File]::ReadAllText($taskFile.FullName).Replace("`r`n", "`n"))
                } else { $taskBytes = [IO.File]::ReadAllBytes($taskFile.FullName) }
                $taskEntryStream.Write($taskBytes, 0, $taskBytes.Length)
            } finally { $taskEntryStream.Dispose() }
        }
    } finally { $taskZip.Dispose() }
} finally { $taskStream.Dispose() }
Copy-Item -LiteralPath $taskApk -Destination (Join-Path $taskDist "UsersHub-v$Version.apk") -Force
$taskHashes = foreach ($taskName in @("UsersHub-v$Version.apk", "UsersHub-Core-Magisk-v$CoreVersion.zip")) {
    $taskHash = Get-FileHash -LiteralPath (Join-Path $taskDist $taskName) -Algorithm SHA256
    "$($taskHash.Hash.ToLowerInvariant())  $taskName"
}
[IO.File]::WriteAllLines((Join-Path $taskDist "SHA256SUMS-v$Version.txt"), $taskHashes, [Text.UTF8Encoding]::new($false))
$taskHashes
