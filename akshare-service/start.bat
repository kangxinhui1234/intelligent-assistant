@echo off
cd /d %~dp0
C:\Users\a\AppData\Local\Programs\Python\Python312\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8899
pause
