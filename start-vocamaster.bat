@echo off
REM VocaMaster local start - double-click to run your own Quizlet.
REM Closing this window stops the site. (IntelliJ NOT needed)
cd /d C:\Dev\VocaMaster-Spring

echo [1/3] Redis via Docker (skipped if Docker is off - site still works, fail-open)
docker compose up -d >nul 2>&1

echo [2/3] Checking jar...
if not exist "build\libs\*SNAPSHOT.jar" (
    echo     First run: building jar, takes 1-2 min...
    call gradlew.bat bootJar -x test
)

echo [3/3] Starting server. Browser opens now - if page fails, wait 10s and press F5.
echo.
echo   =============================================
echo    KEEP THIS WINDOW OPEN while studying.
echo    Close it to stop the site.
echo   =============================================
echo.
start "" http://localhost:8080/pages/login
for %%f in ("build\libs\*SNAPSHOT.jar") do set "JAR=%%f"
java -jar "%JAR%"
