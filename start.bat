@echo off
echo Building project...
call mvn clean package

echo Starting servers...
start "Bot Server" java -cp target\websocket-bot-server-1.0-SNAPSHOT-jar-with-dependencies.jar com.example.websocket.MainServer

timeout /t 5

echo Starting Nginx...
start "Nginx" /D "C:\nginx" nginx -c "%CD%\config\nginx.conf"

echo All services started!
echo Press any key to stop...
pause > nul

echo Stopping Nginx...
taskkill /F /FI "WINDOWTITLE eq Nginx*"
"C:\nginx\nginx.exe" -s stop

echo Stopping Bot Server...
taskkill /F /FI "WINDOWTITLE eq Bot Server*"

echo All stopped.
