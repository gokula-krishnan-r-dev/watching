#Requires -RunAsAdministrator
param(
  [string]$InstallDir = "$env:ProgramFiles\MeritScreen",
  [string]$ParentPin = ""
)

$ErrorActionPreference = "Stop"
$guardian = Join-Path $InstallDir "meritscreen-guardian.exe"
if (-not (Test-Path $guardian)) {
  Write-Error "Guardian binary not found at $guardian"
}

if ([string]::IsNullOrWhiteSpace($ParentPin)) {
  Write-Host "Parent PIN required. Usage: .\uninstall-service.ps1 -ParentPin <pin>"
  Write-Host "Recording uninstallAttempt and aborting (honest model — no silent block)."
  & $guardian --mark-uninstall-attempt
  exit 1
}

& $guardian --uninstall-with-pin $ParentPin
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Stop-Service -Name "MeritScreenGuardian" -ErrorAction SilentlyContinue
Write-Host "MeritScreen service removed after Parent PIN verification."
