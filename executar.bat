@echo off
REM Configura o console para UTF-8 (acentos corretos) e roda o programa.
chcp 65001 >nul
cd /d "%~dp0"
echo Compilando SistemaVendas.java ...
javac -encoding UTF-8 SistemaVendas.java
if errorlevel 1 (
  echo.
  echo ERRO ao compilar. Verifique se o JDK esta instalado e no PATH.
  echo.
  pause
  exit /b
)
echo.
java SistemaVendas
echo.
pause
