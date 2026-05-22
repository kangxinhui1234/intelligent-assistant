@echo off
cd /d %~dp0
..\..\AppData\Local\Programs\Python\Python312\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8899
pause
