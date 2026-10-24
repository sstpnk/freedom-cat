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
