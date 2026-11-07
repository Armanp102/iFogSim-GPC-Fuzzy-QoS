@echo off
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
dir /s /b src\*.java > build\sources.txt
javac -encoding ISO-8859-1 -source 8 -target 8 -cp "jars/*;jars/commons-math3-3.5/*" -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
java -cp "build\classes;jars/*;jars/commons-math3-3.5/*" org.fog.test.perfeval.GpcFuzzyQosExperiment
endlocal
