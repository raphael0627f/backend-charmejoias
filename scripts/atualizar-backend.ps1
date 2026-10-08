#Requires -RunAsAdministrator
<#
Atualiza o backend em producao SEM codigo-fonte no servidor: baixa o .jar mais recente
(release "latest" do GitHub, gerada pelo GitHub Actions), troca o arquivo e reinicia o servico.
Se o servico nao subir com o jar novo, volta automaticamente para o jar anterior.

Requisitos no servidor:
  - variavel de ambiente de maquina ERP_GITHUB_TOKEN com um token do GitHub (fine-grained,
    so este repositorio, permissao "Contents: Read-only")
  - curl.exe (ja vem no Windows 10/11) e nssm

Uso (PowerShell como Administrador):
    .\atualizar-backend.ps1
#>

param(
    [string]$Repo = "raphael0627f/backend-charmejoias",
    [string]$JarPath = "C:\ERP\backend\erp-manager.jar",
    [string]$ServiceName = "erp-backend",
    [string]$NssmPath = "nssm",
    [int]$TimeoutSubidaSegundos = 90,
    [int]$BackupsManter = 5
)

$ErrorActionPreference = "Stop"

function Write-Passo($mensagem) {
    Write-Host ""
    Write-Host "==> $mensagem" -ForegroundColor Cyan
}

$token = [Environment]::GetEnvironmentVariable("ERP_GITHUB_TOKEN", "Machine")
if (-not $token) { $token = $env:ERP_GITHUB_TOKEN }
if (-not $token) {
    Write-Host "Defina a variavel de ambiente ERP_GITHUB_TOKEN (token do GitHub com leitura do repositorio)." -ForegroundColor Red
    exit 1
}

$pasta = Split-Path $JarPath -Parent
$temporario = Join-Path $pasta "erp-manager.novo.jar"
$cabecalhos = @("-H", "Authorization: Bearer $token", "-H", "X-GitHub-Api-Version: 2022-11-28")

try {
    Write-Passo "Consultando a release mais recente em $Repo"
    $json = & curl.exe -sS -f @cabecalhos -H "Accept: application/vnd.github+json" "https://api.github.com/repos/$Repo/releases/tags/latest"
    if ($LASTEXITCODE -ne 0) { throw "Nao foi possivel consultar a release (confira o token e o nome do repositorio)" }
    $release = $json | ConvertFrom-Json
    $asset = $release.assets | Where-Object { $_.name -eq "erp-manager.jar" } | Select-Object -First 1
    if (-not $asset) { throw "A release 'latest' nao tem o erp-manager.jar (o build do GitHub Actions terminou?)" }
    Write-Host "Versao: $($release.name) - $($release.body)"

    Write-Passo "Baixando o jar novo"
    & curl.exe -sS -f -L @cabecalhos -H "Accept: application/octet-stream" -o $temporario "https://api.github.com/repos/$Repo/releases/assets/$($asset.id)"
    if ($LASTEXITCODE -ne 0) { throw "Falha ao baixar o jar" }

    # sanidade: um jar e um zip ("PK") e tem dezenas de MB
    $bytes = [System.IO.File]::ReadAllBytes($temporario)
    if ($bytes.Length -lt 10MB -or $bytes[0] -ne 0x50 -or $bytes[1] -ne 0x4B) {
        throw "O arquivo baixado nao parece um jar valido ($($bytes.Length) bytes)"
    }

    Write-Passo "Parando o servico $ServiceName"
    & $NssmPath stop $ServiceName | Out-Null

    $backup = $null
    if (Test-Path $JarPath) {
        $backup = "$JarPath.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        Copy-Item $JarPath $backup
        Write-Host "Backup do jar anterior: $backup"
    }
    Move-Item $temporario $JarPath -Force

    Write-Passo "Iniciando o servico $ServiceName"
    & $NssmPath start $ServiceName | Out-Null

    # espera o servico ficar Running e estavel (nao cair logo em seguida)
    $ok = $false
    $limite = (Get-Date).AddSeconds($TimeoutSubidaSegundos)
    while ((Get-Date) -lt $limite) {
        Start-Sleep -Seconds 5
        if ((Get-Service $ServiceName).Status -eq "Running") { $ok = $true; break }
    }
    if ($ok) {
        Start-Sleep -Seconds 15
        $ok = (Get-Service $ServiceName).Status -eq "Running"
    }

    if (-not $ok) {
        Write-Host "O servico nao subiu com o jar novo. Voltando para a versao anterior..." -ForegroundColor Yellow
        & $NssmPath stop $ServiceName | Out-Null
        if ($backup) { Copy-Item $backup $JarPath -Force }
        & $NssmPath start $ServiceName | Out-Null
        throw "Atualizacao revertida: o jar novo nao iniciou. Veja o log do servico $ServiceName"
    }

    # guarda so os ultimos N jars de backup
    Get-ChildItem "$JarPath.bak-*" | Sort-Object LastWriteTime -Descending | Select-Object -Skip $BackupsManter | Remove-Item -Force

    Write-Host ""
    Write-Host "Backend atualizado com sucesso." -ForegroundColor Green
} catch {
    Write-Host ""
    Write-Host "ERRO: $($_.Exception.Message)" -ForegroundColor Red
    if (Test-Path $temporario) { Remove-Item $temporario -Force -ErrorAction SilentlyContinue }
    # garante que o servico nao fique parado por causa de uma falha no meio do caminho
    if ((Get-Service $ServiceName -ErrorAction SilentlyContinue).Status -ne "Running") {
        & $NssmPath start $ServiceName | Out-Null
    }
    exit 1
}
