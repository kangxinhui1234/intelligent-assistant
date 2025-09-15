@echo off
chcp 65001
set JAVA_HOME=C:\Users\kxh\.jdks\graalvm-jdk-21.0.8
set PATH=%JAVA_HOME%\bin;%PATH%

echo 启动MCP服务器...
echo 使用Java版本:
java -version

echo.
echo 启动参数:
echo -Dspring.ai.mcp.server.stdio=true
echo -Dspring.main.web-application-type=none
echo -Dfile.encoding=UTF-8
echo -Dsun.jnu.encoding=UTF-8
echo -Dconsole.encoding=UTF-8
echo -Djava.awt.headless=true
echo.

java -Dspring.ai.mcp.server.stdio=true ^
     -Dspring.main.web-application-type=none ^
     -Dfile.encoding=UTF-8 ^
     -Dsun.jnu.encoding=UTF-8 ^
     -Dconsole.encoding=UTF-8 ^
     -Djava.awt.headless=true ^
     -jar target/kch_search_image_mcp_server-0.0.1-SNAPSHOT.jar

