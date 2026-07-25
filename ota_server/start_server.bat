@echo off
echo Starting local OTA server on port 8000...
echo Make sure your phone is on the same WiFi network as your PC (IP: 10.130.202.148)
python -m http.server 8000
pause
