@echo off
title DIPROGRA v2.1

echo =========================================
echo   DIPROGRA v2.1 - Sistema de Inventario
echo   Base de Datos: PostgreSQL
echo =========================================
echo.

java -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java no esta instalado.
    echo Descarga Java 17 desde: https://adoptium.net
    pause
    exit /b 1
)

if not exist "diprogra-2.1.0.jar" (
    echo ERROR: No se encontro diprogra-2.1.0.jar
    echo Ejecuta primero COMPILAR.bat
    pause
    exit /b 1
)

set DB_URL=jdbc:postgresql://localhost:5432/diprogra
set DB_USER=postgres

:: FIX: Pide la contraseña en vez de tenerla hardcodeada
if "%DB_PASS%"=="" (
    set /p DB_PASS="Ingresa la contraseña de PostgreSQL: "
)

echo.
echo Iniciando servidor en http://localhost:8080
echo Base de datos: %DB_URL%
echo Presiona Ctrl+C para detener
echo.

start /b cmd /c "timeout /t 4 >nul && start http://localhost:8080"

java -Xmx256m -Xms64m ^
  -Dspring.datasource.url=%DB_URL% ^
  -Dspring.datasource.username=%DB_USER% ^
  -Dspring.datasource.password=%DB_PASS% ^
  -Dfile.encoding=UTF-8 ^
  -jar diprogra-2.1.0.jar

pause
