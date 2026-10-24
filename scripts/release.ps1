<#
.SYNOPSIS
    Builds every release flavor locally and publishes a GitHub release.

.DESCRIPTION
    Reads the version from freedomcat.properties, builds the phone (oss) and TV (ossTv)
    release flavors for all ABIs, copies the APKs into dist/vX.Y.Z, writes
    SHA256SUMS, creates the git tag and uploads the files with gh.

.EXAMPLE
    powershell -File scripts/release.ps1 -SkipBuild
    powershell -File scripts/release.ps1 -SkipUpload
#>
param(
    [switch]$SkipBuild,
    [switch]$SkipUpload,
    [string]$Notes
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$props = Get-Content 'freedomcat.properties' -Raw
if ($props -notmatch 'VERSION_NAME=(?<v>[^\r\n]+)') { throw 'VERSION_NAME not found in freedomcat.properties' }
$version = $Matches.v.Trim()
$tag = "v$version"
$dist = Join-Path $root "dist\$tag"

if (-not $SkipBuild) {
    & .\gradlew.bat --console=plain :app:assembleOssRelease :app:assembleOssTvRelease
    if ($LASTEXITCODE -ne 0) { throw "gradle failed with exit code $LASTEXITCODE" }
}

New-Item -ItemType Directory -Force -Path $dist | Out-Null
Get-ChildItem -Path 'app\build\outputs\apk' -Recurse -File -Filter *.apk |
    Where-Object { $_.FullName -match '\\release\\' } |
    ForEach-Object { Copy-Item $_.FullName -Destination $dist -Force }

$apks = @(Get-ChildItem $dist -Filter *.apk)
if ($apks.Count -eq 0) { throw "no release APKs found for $tag" }

# A rebrand can leave the manifest pointing at a class that no longer exists;
# the app then dies on start with ClassNotFoundException. Refuse to ship that.
function Assert-ManifestClasses([string]$apkPath) {
    $aapt2 = Join-Path $env:ANDROID_HOME 'build-tools\35.0.1\aapt2.exe'
    if (-not (Test-Path $aapt2)) {
        Write-Warning 'aapt2 not found, skipping manifest class check'
        return
    }
    $dump = & $aapt2 dump xmltree --file AndroidManifest.xml $apkPath 2>$null

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($apkPath)
    $stream = New-Object System.IO.MemoryStream
    foreach ($entry in $zip.Entries) {
        if ($entry.Name.EndsWith('.dex')) {
            $src = $entry.Open()
            $src.CopyTo($stream)
            $src.Dispose()
        }
    }
    $zip.Dispose()
    $dex = [System.Text.Encoding]::GetEncoding(28591).GetString($stream.ToArray())

    $missing = @()
    foreach ($line in $dump) {
        $m = [regex]::Match($line, 'android:(?:name|parentActivityName)\(0x[0-9a-f]+\)="([^"]+)"')
        if (-not $m.Success) { continue }
        $name = $m.Groups[1].Value
        # Only our own classes: third-party manifest entries and generated
        # actions/permissions are not what a rebrand breaks.
        if (-not $name.StartsWith('tech.stpnk.freedomcat.')) { continue }
        $parts = $name.Split('.')
        $last = $parts[-1]
        if ($last.Length -eq 0 -or -not [char]::IsUpper($last[0]) -or $last -cmatch '^[A-Z0-9_]+$') { continue }
        $descriptor = 'L' + ($name -replace '\.', '/') + ';'
        if (-not $dex.Contains($descriptor)) { $missing += $name }
    }
    if ($missing.Count -gt 0) {
        throw "APK $apkPath references classes missing from dex: $($missing -join ', ')"
    }
}

foreach ($apk in $apks) { Assert-ManifestClasses $apk.FullName }

# The Java bindings and libgojni.so must come from the same libcore.aar. When a
# stale copy sits in app/src/main/jniLibs (or a transform cache), the APK mixes
# new Java classes with an old native core and dies before the UI appears.
function Assert-NativeMatchesAar([string]$apkPath, [string]$aarPath) {
    if (-not (Test-Path $aarPath)) {
        Write-Warning "no $aarPath, skipping native library check"
        return
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $apk = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $apkPath))
    $aar = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path $aarPath))
    try {
        $mismatched = @()
        foreach ($entry in $apk.Entries) {
            if ($entry.FullName -notmatch '^lib/([^/]+)/libgojni\.so$') { continue }
            $abi = $Matches[1]
            $aarEntry = $aar.Entries | Where-Object { $_.FullName -eq "jni/$abi/libgojni.so" }
            if (-not $aarEntry) { continue }

            $apkStream = $entry.Open()
            $apkHash = [BitConverter]::ToString($sha.ComputeHash($apkStream)).Replace('-', '').ToLower()
            $apkStream.Dispose()

            $aarStream = $aarEntry.Open()
            $aarHash = [BitConverter]::ToString($sha.ComputeHash($aarStream)).Replace('-', '').ToLower()
            $aarStream.Dispose()

            if ($apkHash -ne $aarHash) { $mismatched += $abi }
        }
    } finally {
        $apk.Dispose()
        $aar.Dispose()
        $sha.Dispose()
    }
    if ($mismatched.Count -gt 0) {
        throw "APK $apkPath ships a libgojni.so that differs from app/libs/libcore.aar ($($mismatched -join ', ')): stale app/src/main/jniLibs or a cached transform"
    }
}

foreach ($apk in $apks) { Assert-NativeMatchesAar $apk.FullName 'app/libs/libcore.aar' }

$sums = @(foreach ($f in $apks) {
    '{0}  {1}' -f (Get-FileHash $f.FullName -Algorithm SHA256).Hash.ToLower(), $f.Name
})
[System.IO.File]::WriteAllLines(
    (Join-Path $dist 'SHA256SUMS'),
    $sums,
    (New-Object System.Text.UTF8Encoding($false))
)
Get-ChildItem $dist | Select-Object Name, Length | Format-Table -AutoSize

if ($SkipUpload) {
    Write-Host "built only: $dist"
    return
}

git rev-parse -q --verify "refs/tags/$tag" *> $null
if ($LASTEXITCODE -ne 0) {
    git tag -a $tag -m "FreedomCat $version"
    git push origin $tag
    if ($LASTEXITCODE -ne 0) { throw "failed to push tag $tag" }
}

$files = @(Get-ChildItem $dist -File | Select-Object -ExpandProperty FullName)
& gh release view $tag *> $null
if ($LASTEXITCODE -eq 0) {
    & gh release upload $tag $files --clobber
} elseif ($Notes) {
    & gh release create $tag $files --title "FreedomCat $version" --notes $Notes
} else {
    & gh release create $tag $files --title "FreedomCat $version" --generate-notes
}
if ($LASTEXITCODE -ne 0) { throw "gh release failed with exit code $LASTEXITCODE" }

Write-Host "released $tag"
