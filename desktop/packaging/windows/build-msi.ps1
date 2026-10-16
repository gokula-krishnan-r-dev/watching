#Requires -Version 5.1
<#
.SYNOPSIS
  Build and package MeritScreen Windows MSI installer using WiX Toolset.

.DESCRIPTION
  This script builds (or stages) meritscreen-guardian.exe, meritscreen-agent.exe,
  and meritscreen-ui.exe, then uses WiX v4/v5 (or WiX v3 candle/light) to build
  a production-ready MSI installer. Optionally signs executables and MSI using signtool.

.PARAMETER Configuration
  Build configuration: 'release' (default) or 'debug'.

.PARAMETER TargetDir
  Directory containing compiled binaries. Default: ../../target/$Configuration

.PARAMETER OutputDir
  Directory to output the MSI. Default: ../../target/installer

.PARAMETER Sign
  Switch to trigger code signing using signtool.exe.

.PARAMETER CertThumbprint
  SHA-1 thumbprint of the code signing certificate in the Windows Certificate Store.

.PARAMETER CertFile
  Path to .pfx certificate file (if not using certificate store).

.PARAMETER CertPassword
  Password for .pfx certificate file.

.PARAMETER TimestampServer
  Timestamp server URL. Default: http://timestamp.digicert.com
#>
param(
  [ValidateSet("release", "debug")]
  [string]$Configuration = "release",

  [string]$TargetDir = "",

  [string]$OutputDir = "",

  [switch]$Sign,

  [string]$CertThumbprint = "",

  [string]$CertFile = "",

  [string]$CertPassword = "",

  [string]$TimestampServer = "http://timestamp.digicert.com"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$WorkspaceRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

if ([string]::IsNullOrWhiteSpace($TargetDir)) {
  $TargetDir = Join-Path $WorkspaceRoot "target\$Configuration"
}
if ([string]::IsNullOrWhiteSpace($OutputDir)) {
  $OutputDir = Join-Path $WorkspaceRoot "target\installer"
}

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host " MeritScreen Windows MSI Package Builder     " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Configuration : $Configuration"
Write-Host "TargetDir     : $TargetDir"
Write-Host "OutputDir     : $OutputDir"

# 1. Verify binaries exist or build them
$guardianBin = Join-Path $TargetDir "meritscreen-guardian.exe"
$agentBin    = Join-Path $TargetDir "meritscreen-agent.exe"
$uiBin       = Join-Path $TargetDir "meritscreen-ui.exe"

$missing = @()
if (-not (Test-Path $guardianBin)) { $missing += "meritscreen-guardian.exe" }
if (-not (Test-Path $agentBin))    { $missing += "meritscreen-agent.exe" }
if (-not (Test-Path $uiBin))       { $missing += "meritscreen-ui.exe" }

if ($missing.Count -gt 0) {
  Write-Host "Missing binaries in $TargetDir : $($missing -join ', ')" -ForegroundColor Yellow
  Write-Host "Building workspace in $Configuration mode..." -ForegroundColor Yellow
  Push-Location $WorkspaceRoot
  try {
    if ($Configuration -eq "release") {
      cargo build --release -p meritscreen-guardian -p meritscreen-agent -p meritscreen-ui
    } else {
      cargo build -p meritscreen-guardian -p meritscreen-agent -p meritscreen-ui
    }
  } finally {
    Pop-Location
  }
}

if (-not (Test-Path $guardianBin) -or -not (Test-Path $agentBin)) {
  throw "Required binaries could not be found in $TargetDir"
}

# 2. Stage files in a dedicated temporary staging directory
$StageDir = Join-Path $OutputDir "staging"
if (Test-Path $StageDir) { Remove-Item -Recurse -Force $StageDir }
New-Item -ItemType Directory -Force -Path $StageDir | Out-Null
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Copy-Item -Force $guardianBin $StageDir
Copy-Item -Force $agentBin $StageDir
if (Test-Path $uiBin) {
  Copy-Item -Force $uiBin $StageDir
} else {
  # If meritscreen-ui is not yet built as exe, create a placeholder stub for packaging
  New-Item -ItemType File -Force -Path (Join-Path $StageDir "meritscreen-ui.exe") | Out-Null
}

$WxsSource = Join-Path $ScriptDir "MeritScreen.wxs"
$WxsDest   = Join-Path $StageDir "MeritScreen.wxs"
Copy-Item -Force $WxsSource $WxsDest

# 3. Optional signing of binaries before packaging
if ($Sign) {
  Write-Host "Signing stage binaries..." -ForegroundColor Green
  $stagedBins = @(
    Join-Path $StageDir "meritscreen-guardian.exe",
    Join-Path $StageDir "meritscreen-agent.exe",
    Join-Path $StageDir "meritscreen-ui.exe"
  )
  foreach ($bin in $stagedBins) {
    if (Test-Path $bin) {
      Invoke-SignTool -FilePath $bin -CertThumbprint $CertThumbprint -CertFile $CertFile -CertPassword $CertPassword -TimestampServer $TimestampServer
    }
  }
}

# 4. Check WiX availability (WiX v4/v5 or WiX v3)
$MsiOutput = Join-Path $OutputDir "MeritScreen.msi"
$hasWix4 = $false
try {
  $wixCheck = & wix --version 2>&1
  if ($LASTEXITCODE -eq 0) { $hasWix4 = $true }
} catch {
  $hasWix4 = $false
}

if ($hasWix4) {
  Write-Host "Building MSI using WiX v4/v5 CLI..." -ForegroundColor Green
  Push-Location $StageDir
  try {
    & wix build MeritScreen.wxs -o $MsiOutput
    if ($LASTEXITCODE -ne 0) { throw "WiX build failed with code $LASTEXITCODE" }
  } finally {
    Pop-Location
  }
} else {
  # Check WiX v3 candle/light fallback
  $hasCandle = (Get-Command candle.exe -ErrorAction SilentlyContinue) -ne $null
  $hasLight  = (Get-Command light.exe  -ErrorAction SilentlyContinue) -ne $null
  if ($hasCandle -and $hasLight) {
    Write-Host "Building MSI using WiX v3 (candle + light)..." -ForegroundColor Green
    Push-Location $StageDir
    try {
      & candle.exe MeritScreen.wxs -out MeritScreen.wixobj
      if ($LASTEXITCODE -ne 0) { throw "candle.exe failed with code $LASTEXITCODE" }
      & light.exe MeritScreen.wixobj -out $MsiOutput -ext WixUIExtension
      if ($LASTEXITCODE -ne 0) { throw "light.exe failed with code $LASTEXITCODE" }
    } finally {
      Pop-Location
    }
  } else {
    Write-Warning "WiX Toolset (wix.exe or candle.exe/light.exe) not found on PATH."
    Write-Warning "To install WiX v4: dotnet tool install --global wix"
    Write-Warning "Staged installer files are ready at: $StageDir"
    return
  }
}

# 5. Optional signing of the generated MSI
if ($Sign -and (Test-Path $MsiOutput)) {
  Write-Host "Signing MSI package..." -ForegroundColor Green
  Invoke-SignTool -FilePath $MsiOutput -CertThumbprint $CertThumbprint -CertFile $CertFile -CertPassword $CertPassword -TimestampServer $TimestampServer
}

if (Test-Path $MsiOutput) {
  $msiItem = Get-Item $MsiOutput
  Write-Host "SUCCESS: Generated $($msiItem.FullName) ($([math]::Round($msiItem.Length / 1MB, 2)) MB)" -ForegroundColor Green
}

function Invoke-SignTool {
  param(
    [string]$FilePath,
    [string]$CertThumbprint,
    [string]$CertFile,
    [string]$CertPassword,
    [string]$TimestampServer
  )
  $signtool = (Get-Command signtool.exe -ErrorAction SilentlyContinue)
  if (-not $signtool) {
    Write-Warning "signtool.exe not found on PATH. Skipping code signature on $FilePath."
    return
  }
  $args = @("sign", "/fd", "SHA256", "/tr", $TimestampServer, "/td", "SHA256")
  if (-not [string]::IsNullOrWhiteSpace($CertThumbprint)) {
    $args += @("/sha1", $CertThumbprint)
  } elseif (-not [string]::IsNullOrWhiteSpace($CertFile)) {
    $args += @("/f", $CertFile)
    if (-not [string]::IsNullOrWhiteSpace($CertPassword)) {
      $args += @("/p", $CertPassword)
    }
  } else {
    $args += @("/a") # Auto-select best cert
  }
  $args += $FilePath
  Write-Host "Running: signtool $($args -join ' ')"
  & signtool.exe @args
  if ($LASTEXITCODE -ne 0) {
    Write-Warning "Signing failed for $FilePath with code $LASTEXITCODE"
  }
}
