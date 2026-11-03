@echo off
setlocal
cd /d "%~dp0"

echo Starting Parser. The browser will open when the server is ready...

start "" /b powershell -NoProfile -WindowStyle Hidden -Command "for($i=0;$i -lt 90;$i++){try{Invoke-WebRequest -UseBasicParsing 'http://localhost:8080' -TimeoutSec 1 | Out-Null; Start-Process 'http://localhost:8080'; break}catch{Start-Sleep -Seconds 1}}"

call mvnw.cmd spring-boot:run

endlocal
