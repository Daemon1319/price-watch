# Stops a background price-watch jar started with:
#   .\run-local-cloud.ps1 -Hidden
#
# Safe to commit (no secrets).

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$pidFile = Join-Path $PSScriptRoot "logs\price-watch.pid"

function Stop-JavaJarByName {
  Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*price_watch-0.0.1-SNAPSHOT.jar*" } |
    ForEach-Object {
      Write-Host "Stopping java PID $($_.ProcessId) ..."
      Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

if (Test-Path $pidFile) {
  $procId = (Get-Content $pidFile -ErrorAction SilentlyContinue | Select-Object -First 1)
  if ($procId) {
    $p = Get-Process -Id $procId -ErrorAction SilentlyContinue
    if ($p) {
      Write-Host "Stopping PID $procId ($($p.ProcessName)) ..."
      Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
      # cmd.exe wrapper may exit while java keeps running — also match by jar name
      Start-Sleep -Milliseconds 300
    } else {
      Write-Host "PID file present but process $procId is not running."
    }
  }
  Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

Stop-JavaJarByName
Write-Host "Done."
