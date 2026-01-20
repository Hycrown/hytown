@echo off
set "PROJECT=C:\Users\Crehop\Desktop\HyCrownDev\town\HyTown"
set "CLASSPATH=%PROJECT%\lib\HytaleServer.jar;%PROJECT%\lib\HyConomy-1.0.0.jar"

cd /d "%PROJECT%"

echo Compiling Java files...
javac -d "target\classes" -cp "%CLASSPATH%" @sources.txt

if errorlevel 1 (
    echo Compilation failed!
    pause
    exit /b 1
)

echo Copying resources...
xcopy /E /Y /I "src\main\resources\*" "target\classes\" >nul 2>&1

echo Creating JAR...
cd "target\classes"
jar -cvf "..\HyTown-1.0.1.jar" .

echo Deploying to server mods...
cd /d "%PROJECT%"
copy /Y "target\HyTown-1.0.1.jar" "C:\Users\Crehop\Desktop\HyCrownDev\mods\"

echo Done! HyTown-1.0.1.jar deployed to mods folder
