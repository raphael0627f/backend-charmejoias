@echo off
REM Duplo-clique nesse arquivo: se nao estiver rodando como Administrador, ele mesmo
REM se relança elevado (pede a janela do UAC) e depois chama o atualizar-sistema.ps1.

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Pedindo elevacao de Administrador...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0atualizar-sistema.ps1"

echo.
pause
