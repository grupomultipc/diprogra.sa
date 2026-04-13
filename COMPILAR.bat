@echo off
title DIPROGRA - Compilar

echo =========================================
echo   DIPROGRA - Compilar proyecto Java
echo =========================================
echo.

mvn -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven no esta instalado.
    echo Descarga desde: https://maven.apache.org/download.cgi
    echo O instala con: winget install Apache.Maven
    pause
    exit /b 1
)

echo Compilando... puede tardar 1-2 minutos la primera vez.
echo.

mvn clean package -DskipTests

if errorlevel 1 (
    echo.
    echo ERROR: La compilacion fallo. Revisa los mensajes de error.
    pause
    exit /b 1
)

copy target\diprogra-2.1.0.jar diprogra-2.1.0.jar >nul 2>&1

echo.
echo =========================================
echo   Compilacion exitosa!
echo   Archivo: diprogra-2.1.0.jar
echo   Ahora ejecuta: INICIAR.bat
echo =========================================
pause
