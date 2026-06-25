@echo off
set DIR=%~dp0
set MVN_CMD=%DIR%.maven\apache-maven-3.9.16\bin\mvn.cmd
if exist "%MVN_CMD%" (
    "%MVN_CMD%" %*
) else (
    echo Maven not found at %MVN_CMD%
    exit /b 1
)
