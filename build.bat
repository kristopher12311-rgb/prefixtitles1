@echo off
setlocal
mvn clean package
if errorlevel 1 (
  echo Build failed. Check the Maven output above.
  exit /b 1
)
echo.
echo Plugin built: target\PrefixTitles-Paper-1.2.0.jar
endlocal
