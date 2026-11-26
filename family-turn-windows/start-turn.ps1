$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$configPath = Join-Path $PSScriptRoot 'turn-config.json'
if (-not (Test-Path $configPath)) {
    $ip = Read-Host 'Введите публичный IPv4 вашего домашнего подключения (например, 5.227.60.178)'
    $random = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($random) } finally { $rng.Dispose() }
    $password = [Convert]::ToBase64String($random).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $settings = [ordered]@{ publicIp=$ip; username='familyvideo'; password=$password; port=3478; minPort=49160; maxPort=49259 }
    $json = $settings | ConvertTo-Json
    [IO.File]::WriteAllText($configPath, $json, [Text.UTF8Encoding]::new($false))
    Write-Host 'Создан turn-config.json. Храните пароль только у себя и членов семьи.'
}
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
Write-Host ('TURN URL: turn:{0}:{1}?transport=udp' -f $config.publicIp, $config.port)
Write-Host ('Пользователь: {0}' -f $config.username)
Write-Host ('Пароль: {0}' -f $config.password)
Write-Host 'Откройте UDP 3478 и UDP 49160-49259 на роутере и направьте их на компьютер с сервером.'
& (Join-Path $PSScriptRoot 'family-turn.exe') -config $configPath
if ($LASTEXITCODE -ne 0) { throw "TURN завершился с ошибкой $LASTEXITCODE" }
