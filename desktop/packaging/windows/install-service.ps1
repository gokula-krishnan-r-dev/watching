#Requires -RunAsAdministrator
<#
.SYNOPSIS
  Install MeritScreen Guardian as a Windows Service (AutoStart) and copy binaries.

.DESCRIPTION
  Phase d3 skeleton installer. Prefer signed MSI (MeritScreen.wxs) for production.
  This script is for lab VMs and CI smoke installs.

.PARAMETER PayloadDir
  Directory containing meritscreen-guardian.exe, meritscreen-agent.exe, meritscreen-ui.exe
#>
param(
  [Parameter(Mandatory = $true)]
  [string]$PayloadDir,

  [string]$InstallDir = "$env:ProgramFiles\MeritScreen"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $PayloadDir)) {
  throw "PayloadDir not found: $PayloadDir"
}

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item -Force (Join-Path $PayloadDir "meritscreen-guardian.exe") $InstallDir
Copy-Item -Force (Join-Path $PayloadDir "meritscreen-agent.exe") $InstallDir
if (Test-Path (Join-Path $PayloadDir "meritscreen-ui.exe")) {
  Copy-Item -Force (Join-Path $PayloadDir "meritscreen-ui.exe") $InstallDir
}

$guardian = Join-Path $InstallDir "meritscreen-guardian.exe"
& $guardian --uninstall-service 2>$null
& $guardian --install-service
Start-Service -Name "MeritScreenGuardian"
Get-Service MeritScreenGuardian | Format-List

Write-Host "Installed. After reboot, Guardian should be Running and spawn the Session Agent."
Write-Host "SmartScreen: use EV code signing for reputation (see packaging/windows/README.md)."
