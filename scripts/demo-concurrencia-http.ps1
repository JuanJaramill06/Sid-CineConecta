# =============================================================================
# CINECONECTA - Demo HTTP de transacciones y concurrencia (RF18-RF23, RNF10, RNF11)
#
# Requiere el backend corriendo (.\mvnw.cmd spring-boot:run) con Oracle cargado.
# Crea una cuenta NUEVA en cada corrida (no toca la cuenta 4 de la demo) y prueba:
#   1. Pago rechazado: 402 y no queda nada registrado.
#   2. (con -IncludeFailure) Falla entre los inserts y el commit: 0 suscripciones, 0 pagos.
#      El backend debe arrancar con DEMO_SIMULATE_FAILURES=true.
#   3. Dos contrataciones SIMULTANEAS de la misma cuenta: una 201 y otra 409.
#   4. Dos perfiles nuevos SIMULTANEOS con un solo cupo libre: uno 201 y otro 422.
#
# Uso (desde la raiz del repo):
#   powershell -ExecutionPolicy Bypass -File scripts\demo-concurrencia-http.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\demo-concurrencia-http.ps1 -IncludeFailure
#
# Compatible con Windows PowerShell 5.1. Las peticiones simultaneas salen a la vez con
# HttpClient.SendAsync; Oracle serializa la segunda con el FOR UPDATE sobre la cuenta.
# =============================================================================
param(
    [string]$BaseUrl = 'http://localhost:8080',
    [switch]$IncludeFailure
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromSeconds(60)
$script:failed = $false

function New-ApiRequest([string]$Method, [string]$Path, $Body, [string]$Token) {
    $request = New-Object System.Net.Http.HttpRequestMessage ([System.Net.Http.HttpMethod]::new($Method)), "$BaseUrl$Path"
    if ($Token) {
        $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $Token)
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Compress
        $request.Content = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, 'application/json')
    }
    return $request
}

# Sends every request at the same time and waits for all of them.
function Send-Together($Requests) {
    $tasks = @($Requests | ForEach-Object { $client.SendAsync($_) })
    [System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$tasks)
    foreach ($task in $tasks) {
        $response = $task.Result
        $text = $response.Content.ReadAsStringAsync().Result
        $data = $null
        if ($text) { try { $data = $text | ConvertFrom-Json } catch { } }
        [pscustomobject]@{ Status = [int]$response.StatusCode; Body = $data; Raw = $text }
    }
}

function Send-One([string]$Method, [string]$Path, $Body, [string]$Token) {
    return Send-Together @(New-ApiRequest $Method $Path $Body $Token) | Select-Object -First 1
}

function Check([bool]$Ok, [string]$What) {
    if ($Ok) { Write-Host "  PASS  $What" -ForegroundColor Green }
    else     { Write-Host "  FAIL  $What" -ForegroundColor Red; $script:failed = $true }
}

function Count-Items($Response) { return @($Response.Body | Where-Object { $_ }).Count }

# ---- 0. Cuenta nueva -------------------------------------------------------------
$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$email = "demo.concurrencia.$stamp@example.com"
Write-Host "`n== 0. Registro de $email"
$register = Send-One 'POST' '/api/auth/register' @{ fullName = 'Demo Concurrencia'; email = $email; password = 'Demo12345' } $null
Check ($register.Status -eq 201) "Cuenta creada (201)"
if ($register.Status -ne 201) { throw "No se pudo registrar la cuenta: $($register.Status) $($register.Raw)" }
$token = $register.Body.token

# ---- 1. Pago rechazado -------------------------------------------------------------
Write-Host "`n== 1. Pago simulado rechazado (RF18-RF20)"
$rejected = Send-One 'POST' '/api/subscriptions' @{ planId = 2; approvePayment = $false } $token
Check ($rejected.Status -eq 402) "Respuesta 402 PAYMENT_REJECTED (recibido $($rejected.Status))"
Check ((Count-Items (Send-One 'GET' '/api/subscriptions' $null $token)) -eq 0) "No quedo ninguna suscripcion"

# ---- 2. Falla antes del commit -------------------------------------------------------
if ($IncludeFailure) {
    Write-Host "`n== 2. Falla despues de insertar pago y suscripcion, antes del commit (RF19, RNF11)"
    $failure = Send-One 'POST' '/api/subscriptions' @{ planId = 2; approvePayment = $true; simulateFailure = $true } $token
    if ($failure.Status -eq 422) {
        Write-Host "  El backend tiene la simulacion apagada: arranquelo con DEMO_SIMULATE_FAILURES=true" -ForegroundColor Yellow
    }
    Check ($failure.Status -eq 500 -and $failure.Body.code -eq 'SIMULATED_FAILURE') "Respuesta 500 SIMULATED_FAILURE (recibido $($failure.Status))"
    Write-Host "  Mensaje: $($failure.Body.message)"
    Check ((Count-Items (Send-One 'GET' '/api/subscriptions' $null $token)) -eq 0) "Sin registros parciales: 0 suscripciones"
    Check ((Count-Items (Send-One 'GET' '/api/payments' $null $token)) -eq 0) "Sin registros parciales: 0 pagos"
}

# ---- 3. Dos contrataciones simultaneas -----------------------------------------------
Write-Host "`n== 3. Dos contrataciones SIMULTANEAS para la misma cuenta (RF16, RF22, RNF10)"
$pair = Send-Together @(
    (New-ApiRequest 'POST' '/api/subscriptions' @{ planId = 2; approvePayment = $true } $token),
    (New-ApiRequest 'POST' '/api/subscriptions' @{ planId = 2; approvePayment = $true } $token))
$codes = ($pair | ForEach-Object { $_.Status } | Sort-Object) -join ','
$pair | ForEach-Object { Write-Host "  $($_.Status) $($_.Body.code) $($_.Body.message)" }
Check ($codes -eq '201,409') "Solo una se confirma: 201 y 409 (recibido $codes)"
Check ((Count-Items (Send-One 'GET' '/api/subscriptions' $null $token)) -eq 1) "Exactamente 1 suscripcion"
Check ((Count-Items (Send-One 'GET' '/api/payments' $null $token)) -eq 1) "Exactamente 1 pago"

# ---- 4. Dos perfiles simultaneos con un solo cupo --------------------------------------
Write-Host "`n== 4. Dos perfiles SIMULTANEOS con un solo cupo libre (plan Estandar: 2) (RF10)"
$first = Send-One 'POST' '/api/profiles' @{ name = 'Perfil 1'; maxAgeRating = 18 } $token
Check ($first.Status -eq 201) "Primer perfil creado (201)"
$profiles = Send-Together @(
    (New-ApiRequest 'POST' '/api/profiles' @{ name = 'Perfil A'; maxAgeRating = 12 } $token),
    (New-ApiRequest 'POST' '/api/profiles' @{ name = 'Perfil B'; maxAgeRating = 7 } $token))
$codes = ($profiles | ForEach-Object { $_.Status } | Sort-Object) -join ','
$profiles | ForEach-Object { Write-Host "  $($_.Status) $($_.Body.code) $($_.Body.message)" }
Check ($codes -eq '201,422') "Solo uno entra: 201 y 422 (recibido $codes)"
$active = @((Send-One 'GET' '/api/profiles' $null $token).Body | Where-Object { $_.active }).Count
Check ($active -eq 2) "Perfiles activos = 2 = maximo del plan (recibido $active)"

Write-Host ""
if ($script:failed) { Write-Host "RESULTADO: hubo fallas" -ForegroundColor Red; exit 1 }
Write-Host "RESULTADO: todo correcto. Cuenta usada: $email" -ForegroundColor Green
