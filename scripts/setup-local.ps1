$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$environmentPath = Join-Path $repositoryRoot '.env'
if (Test-Path -LiteralPath $environmentPath) {
    Write-Output 'O arquivo .env já existe e foi preservado.'
    exit 0
}
$randomBytes = New-Object byte[] 32
$generator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $generator.GetBytes($randomBytes)
} finally {
    $generator.Dispose()
}
$databasePassword = [BitConverter]::ToString($randomBytes).Replace('-', '').ToLowerInvariant()
$template = Get-Content -LiteralPath (Join-Path $repositoryRoot '.env.example') -Raw
$environmentContent = $template.Replace('POSTGRES_PASSWORD=', "POSTGRES_PASSWORD=$databasePassword")
[System.IO.File]::WriteAllText($environmentPath, $environmentContent, [System.Text.UTF8Encoding]::new($false))
Write-Output 'Arquivo .env criado com senha local aleatória. Execute docker compose up --build -d --wait.'
