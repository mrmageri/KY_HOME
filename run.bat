@echo off
rem Запуск инструмента ConfigToToml из Windows.
setlocal

set "ROOT=%~dp0"
cd /d "%ROOT%"

where javac >nul 2>nul
if errorlevel 1 (
    echo Не найден javac. Установите JDK 17+ и добавьте его в PATH.
    exit /b 1
)

where java >nul 2>nul
if errorlevel 1 (
    echo Не найден java. Установите JRE 17+ и добавьте её в PATH.
    exit /b 1
)

if not exist out mkdir out

rem Компилируем все java-файлы прямо из папки src
javac -encoding UTF-8 -d out src\*.java
if errorlevel 1 (
    echo Ошибка компиляции.
    exit /b 1
)

rem Запускаем Main (без указания пакета)
java -cp out Main %*
exit /b %ERRORLEVEL%