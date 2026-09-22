#Requires -RunAsAdministrator
<#
Atualiza o backend (reinicia o servico) e o frontend (para, atualiza codigo,
reinstala dependencias, builda e sobe de novo) em producao.

Uso: clique com botao direito -> "Executar com PowerShell" (como Administrador),
ou rode num terminal PowerShell ja aberto como Administrador:
    .\atualizar-sistema.ps1
#>

param(
    [string]$NssmPath = "nssm",
    [string]$FrontendDir = "C:\ERP\frontend",
    [string]$BackendServiceName = "erp-backend",
    [string]$FrontendServiceName = "erp-frontend"
)

$ErrorActionPreference = "Stop"

function Write-Passo($mensagem) {
    Write-Host ""
    Write-Host "==> $mensagem" -ForegroundColor Cyan
}

function Invoke-Passo($mensagem, [scriptblock]$acao) {
    Write-Passo $mensagem
    & $acao
    if ($LASTEXITCODE -ne $null -and $LASTEXITCODE -ne 0) {
        throw "Falhou: $mensagem (exit code $LASTEXITCODE)"
    }
}

# valida que esta rodando elevado (o #Requires acima ja bloqueia, isso e so uma segunda checagem clara)
$identidadeAtual = [Security.Principal.WindowsIdentity]::GetCurrent()
$principal = New-Object Security.Principal.WindowsPrincipal($identidadeAtual)
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Host "Este script precisa ser executado como Administrador." -ForegroundColor Red
    exit 1
}

try {
    Invoke-Passo "Reiniciando backend ($BackendServiceName)" {
        & $NssmPath restart $BackendServiceName
    }

    Invoke-Passo "Parando frontend ($FrontendServiceName)" {
        & $NssmPath stop $FrontendServiceName
    }

    Invoke-Passo "Atualizando codigo do frontend (git pull em $FrontendDir)" {
        Push-Location $FrontendDir
        try {
            git pull
        } finally {
            Pop-Location
        }
    }

    Invoke-Passo "Instalando dependencias (npm install)" {
        Push-Location $FrontendDir
        try {
            npm install
        } finally {
            Pop-Location
        }
    }

    Invoke-Passo "Buildando frontend (npm run build)" {
        Push-Location $FrontendDir
        try {
            npm run build
        } finally {
            Pop-Location
        }
    }

    Write-Host ""
    Write-Host "Atualizacao concluida com sucesso." -ForegroundColor Green
} catch {
    Write-Host ""
    Write-Host "ERRO durante a atualizacao: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "O frontend sera reiniciado com a ultima versao buildada (pode ser a antiga, se o build tiver falhado antes de terminar)." -ForegroundColor Yellow
} finally {
    Invoke-Passo "Subindo frontend ($FrontendServiceName)" {
        & $NssmPath start $FrontendServiceName
    }
}
