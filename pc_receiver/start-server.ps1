# DroidPad — inicia o servidor (equivale ao "abrir servidor.bat")
# Uso: Right-click > Run with PowerShell, ou: .\start-server.ps1
param(
    [switch]$PrintOnly
)

$ErrorActionPreference = "Stop"
# Vai para a raiz do projeto (pasta pai deste script), funcione de onde for chamado
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)

if ($PrintOnly) {
    python pc_receiver\main.py --print-only
} else {
    python pc_receiver\main.py
}
