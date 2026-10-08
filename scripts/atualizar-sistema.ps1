#Requires -RunAsAdministrator
<#
Atualiza o sistema em producao. Voce escolhe o que atualizar:
  - backend : baixa o jar mais recente do GitHub e reinicia o servico (atualizar-backend.ps1)
  - frontend: para, atualiza codigo, reinstala dependencias, builda e sobe de novo
  - ambos

Uso: clique com botao direito -> "Executar com PowerShell" (como Administrador) e escolha no
menu, ou passe direto no terminal PowerShell ja aberto como Administrador:
    .\atualizar-sistema.ps1                    (mostra o menu)
    .\atualizar-sistema.ps1 -Alvo backend
    .\atualizar-sistema.ps1 -Alvo frontend
    .\atualizar-sistema.ps1 -Alvo ambos
#>

param(
    [ValidateSet("", "backend", "frontend", "ambos")]
    [string]$Alvo = "",
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

# sem -Alvo: pergunta (duplo-clique no .bat cai aqui)
if (-not $Alvo) {
    Write-Host ""
    Write-Host "O que voce quer atualizar?" -ForegroundColor Cyan
    Write-Host "  1 - Backend"
    Write-Host "  2 - Frontend"
    Write-Host "  3 - Backend e Frontend"
    Write-Host "  0 - Cancelar"
    $escolha = Read-Host "Digite o numero"
    switch ($escolha) {
        "1" { $Alvo = "backend" }
        "2" { $Alvo = "frontend" }
        "3" { $Alvo = "ambos" }
        default {
            Write-Host "Nada foi atualizado." -ForegroundColor Yellow
            exit 0
        }
    }
}

$atualizarBackend = $Alvo -in @("backend", "ambos")
$atualizarFrontend = $Alvo -in @("frontend", "ambos")
$frontendParado = $false
$erro = $false

try {
    if ($atualizarBackend) {
        Invoke-Passo "Atualizando backend (baixa o jar mais recente e reinicia $BackendServiceName)" {
            $scriptBackend = Join-Path $PSScriptRoot "atualizar-backend.ps1"
            & $scriptBackend -ServiceName $BackendServiceName -NssmPath $NssmPath
        }
    }

    if ($atualizarFrontend) {
        Invoke-Passo "Parando frontend ($FrontendServiceName)" {
            & $NssmPath stop $FrontendServiceName
        }
        $frontendParado = $true

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
    }

    Write-Host ""
    Write-Host "Atualizacao concluida com sucesso ($Alvo)." -ForegroundColor Green
} catch {
    $erro = $true
    Write-Host ""
    Write-Host "ERRO durante a atualizacao: $($_.Exception.Message)" -ForegroundColor Red
    if ($frontendParado) {
        Write-Host "O frontend sera reiniciado com a ultima versao buildada (pode ser a antiga, se o build tiver falhado antes de terminar)." -ForegroundColor Yellow
    }
} finally {
    # so sobe o frontend de novo se este script foi quem parou
    if ($frontendParado) {
        Invoke-Passo "Subindo frontend ($FrontendServiceName)" {
            & $NssmPath start $FrontendServiceName
        }
    }
}

if ($erro) { exit 1 }
