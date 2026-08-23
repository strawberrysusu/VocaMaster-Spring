@echo off
REM VocaMaster local start - double-click to run your own Quizlet.
REM Closing this window stops the site. (IntelliJ NOT needed)
cd /d C:\Dev\VocaMaster-Spring

echo [1/3] Redis via Docker (skipped if Docker is off - site still works, fail-open)
docker compose up -d >nul 2>&1

echo [2/3] Building jar (Gradle builds the React bundle first; fast if nothing changed)...
REM Always run: gradle skips work when up-to-date. A stale-jar guard here once served
REM a 2-week-old build without Phase 4/5 - never trust "file exists" as "file is current".
REM Stop on failure: otherwise an OLD jar would start and look like the new code (Codex audit).
call gradlew.bat bootJar -x test
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed - NOT starting the old jar. Read the error above.
    pause
    exit /b 1
)

echo [3/3] Starting server. Browser opens now - if page fails, wait 15s and press F5.
echo.
echo   =============================================
echo    KEEP THIS WINDOW OPEN while studying.
echo    Close it to stop the site.
echo   =============================================
echo.
start "" http://localhost:8080/app/
for %%f in ("build\libs\*SNAPSHOT.jar") do set "JAR=%%f"
java -jar "%JAR%"
