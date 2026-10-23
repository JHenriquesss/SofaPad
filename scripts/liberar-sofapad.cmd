@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0configurar-firewall.ps1" -Apply
if errorlevel 1 echo A configuracao nao foi concluida. Confira a mensagem acima.
pause
