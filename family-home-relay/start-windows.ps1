$ErrorActionPreference = 'Stop'
$env:FAMILY_DATA = Join-Path $PSScriptRoot 'data'
$env:FAMILY_BIND = '0.0.0.0'
$env:FAMILY_PORT = '8787'
New-Item -ItemType Directory -Force -Path $env:FAMILY_DATA | Out-Null
& py -3 (Join-Path $PSScriptRoot 'server.py')
