param(
    [string]$ProjectName = "userservice-e2e-$PID",
    [switch]$KeepOnFailure
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$failed = $false
$env:COMPOSE_PROJECT_NAME = $ProjectName
$env:INTEGRATION_BASE_URL = "http://localhost:8080"
$env:INTEGRATION_JDBC_URL = "jdbc:h2:tcp://localhost:9092/./userservice"
$env:INTEGRATION_JDBC_USER = "sa"
$env:INTEGRATION_JDBC_PASSWORD = ""

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker compose -p $ProjectName @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

function Wait-ForApplication {
    param([int]$TimeoutSeconds = 180)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $previousPreference = $ErrorActionPreference
        $ErrorActionPreference = "SilentlyContinue"
        & curl.exe --fail --silent --max-time 5 `
            "$($env:INTEGRATION_BASE_URL)/api/v1/users/1" *> $null
        $curlExitCode = $LASTEXITCODE
        $ErrorActionPreference = $previousPreference
        if ($curlExitCode -eq 0) {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "user-service did not become ready within $TimeoutSeconds seconds"
}

try {
    Set-Location $root
    Invoke-Compose down --volumes --remove-orphans
    Invoke-Compose up --build --detach --wait
    Wait-ForApplication

    $env:INTEGRATION_PHASE = "main"
    & .\gradlew.bat test integrationTest
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle unit/integration test phase failed with exit code $LASTEXITCODE"
    }

    Invoke-Compose restart
    Wait-ForApplication

    $env:INTEGRATION_PHASE = "verify-persistence"
    & .\gradlew.bat integrationTest --tests "com.userservice.integration.PersistentVolumeIntegrationTest"
    if ($LASTEXITCODE -ne 0) {
        throw "Persistent-volume verification failed with exit code $LASTEXITCODE"
    }
} catch {
    $failed = $true
    Write-Host "Integration/E2E failure: $($_.Exception.Message)" -ForegroundColor Red
    & docker compose -p $ProjectName ps
    & docker compose -p $ProjectName logs --no-color
    throw
} finally {
    if (-not ($failed -and $KeepOnFailure)) {
        & docker compose -p $ProjectName down --volumes --remove-orphans
    } else {
        Write-Warning "Compose project '$ProjectName' retained for diagnostics."
    }
    Remove-Item Env:\INTEGRATION_PHASE -ErrorAction SilentlyContinue
    Remove-Item Env:\COMPOSE_PROJECT_NAME -ErrorAction SilentlyContinue
}
