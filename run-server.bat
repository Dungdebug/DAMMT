@echo off
chcp 65001 > nul
echo Starting Java TCP Chat Server on port 5000...
cd /d "%~dp0"

if exist "target\chat-server.jar" (
    java -jar target\chat-server.jar
) else (
    java -cp "target\classes;lib\*" com.chatapp.server.ServerApplication
)

if errorlevel 1 pause
