@echo off
cd /d "%~dp0"
set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%
echo 启动 Minecraft 1.21.1 - 强化红石模组...
call .\gradlew runClient
pause
