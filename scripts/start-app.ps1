Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot

# Start the backend in a separate PowerShell window.
Start-Process powershell.exe `
    -WorkingDirectory $projectRoot `
    -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-File", ".\scripts\start-backend.ps1"
    )

# Give the backend process time to begin starting.
Start-Sleep -Seconds 3

# Start the frontend in a separate PowerShell window.
Start-Process powershell.exe `
    -WorkingDirectory $projectRoot `
    -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", "npm --prefix frontend run dev"
    )

# Wait until the frontend is accepting connections.
$frontendPort = 3000
$maxAttempts = 60

for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    $connection = Test-NetConnection `
        -ComputerName "localhost" `
        -Port $frontendPort `
        -WarningAction SilentlyContinue

    if ($connection.TcpTestSucceeded) {
        Write-Host "Frontend is ready. Opening browser..."
        Start-Process "http://localhost:3000/"
        exit 0
    }

    Start-Sleep -Seconds 1
}

Write-Error "Frontend did not become ready on port $frontendPort."
exit 1
