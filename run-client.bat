@echo off
chcp 65001 > nul
echo Starting Java TCP Chat Client GUI...
cd /d "%~dp0"

if exist "target\chat-client.jar" (
    start javaw -jar target\chat-client.jar
) else (
    start javaw -cp "target\classes;lib\*" com.chatapp.client.ClientApplication
)
