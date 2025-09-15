@echo off
set JAVA_HOME=C:\Users\kxh\.jdks\graalvm-jdk-21.0.8
set PATH=%JAVA_HOME%\bin;%PATH%
echo Using Java version:
java -version
echo.
echo Building project...
mvn clean package -DskipTests

