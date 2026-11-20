@echo off
setlocal
cd /d "%~dp0"

echo ========================================
echo   Building modern plugin (2022.3+ / Java 17)
echo ========================================
call gradlew.bat buildPlugin
if errorlevel 1 goto :error

echo.
echo ========================================
echo   Building legacy plugin (2021.2-2022.2 / Java 11)
echo ========================================
call gradlew.bat -p legacy buildPlugin
if errorlevel 1 goto :error

echo.
echo ========================================
echo   Build succeeded. Artifacts:
echo ========================================
dir /b build\distributions\*.zip
dir /b legacy\build\distributions\*.zip
exit /b 0

:error
echo.
echo Build failed. Please check the error output above.
exit /b 1
