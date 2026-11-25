@echo off
setlocal
cd /d "%~dp0"
title SAM Online TV - adb test
set "OUT=%~dp0results"
if not exist "%OUT%" mkdir "%OUT%"
del /q "%OUT%\*" >nul 2>&1

echo ================================================
echo  SAM Online TV - automatic adb test
echo ================================================
echo.

rem ---------- find or download adb ----------
set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB if exist "%~dp0platform-tools\adb.exe" set "ADB=%~dp0platform-tools\adb.exe"
if defined ADB goto haveadb
echo Downloading Android platform-tools (adb)...
curl -L -s -o platform-tools.zip https://dl.google.com/android/repository/platform-tools-latest-windows.zip
tar -xf platform-tools.zip
set "ADB=%~dp0platform-tools\adb.exe"
:haveadb
echo adb: %ADB%
"%ADB%" version > "%OUT%\00_adb_version.txt" 2>&1

rem ---------- TV IP ----------
set "TVIP="
if exist tv_ip.txt set /p TVIP=<tv_ip.txt
if not defined TVIP set /p TVIP=Enter your TV's IP address (Settings - Network), e.g. 192.168.0.105: 
echo %TVIP%> tv_ip.txt
set "DEV=%TVIP%:5555"
echo %DEV%> "%OUT%\00_target.txt"

echo.
echo Connecting to %DEV% ...
echo ** If the TV shows "Allow USB debugging?", choose "Always allow" + OK on the remote. **
set /a tries=0
:waitdev
"%ADB%" connect %DEV% >> "%OUT%\00_connect.txt" 2>&1
"%ADB%" -s %DEV% get-state 2>nul | find "device" >nul && goto connected
set /a tries+=1
if %tries% GEQ 40 goto fail
timeout /t 3 /nobreak >nul
goto waitdev

:fail
"%ADB%" devices -l >> "%OUT%\00_connect.txt" 2>&1
echo FAILED to connect >> "%OUT%\00_connect.txt"
echo.
echo Could not connect. Check: TV and PC on the same network, USB/Network debugging ON, correct IP.
echo (To change the IP, delete tv_ip.txt and run again.)
echo FAILED> "%OUT%\done.txt"
pause
exit /b 1

:connected
echo Connected.
"%ADB%" devices -l >> "%OUT%\00_connect.txt" 2>&1
set "A="%ADB%" -s %DEV%"

rem ---------- device info + BDIX reachability ----------
(
 echo model:   & %A% shell getprop ro.product.model
 echo brand:   & %A% shell getprop ro.product.brand
 echo android: & %A% shell getprop ro.build.version.release
 echo sdk:     & %A% shell getprop ro.build.version.sdk
 echo abi:     & %A% shell getprop ro.product.cpu.abi
 echo screen:  & %A% shell wm size
 echo density: & %A% shell wm density
 echo --- ping DHAKA-FLIX servers ---
 %A% shell ping -c 2 -W 2 172.16.50.7
 %A% shell ping -c 2 -W 2 172.16.50.14
 %A% shell ping -c 2 -W 2 172.16.50.12
 %A% shell ping -c 2 -W 2 172.16.50.10
) > "%OUT%\01_device_info.txt" 2>&1

rem ---------- install ----------
echo Downloading latest APK...
curl -L -s -o SAM-Online-TV.apk https://github.com/shaonirfan/sam-online-app-tv/releases/latest/download/SAM-Online-TV.apk
if exist SAM-Online-TV-local.apk copy /y SAM-Online-TV-local.apk SAM-Online-TV.apk >nul
echo Installing on TV...
%A% install -r SAM-Online-TV.apk > "%OUT%\02_install.txt" 2>&1
find "Success" "%OUT%\02_install.txt" >nul || (
  echo Signature changed - reinstalling clean...
  %A% uninstall com.shaonirfan.samonline.tv >> "%OUT%\02_install.txt" 2>&1
  %A% install SAM-Online-TV.apk >> "%OUT%\02_install.txt" 2>&1
)
type "%OUT%\02_install.txt"
%A% shell dumpsys package com.shaonirfan.samonline.tv | find "versionName" >> "%OUT%\02_install.txt"

rem ---------- launch + scripted remote presses ----------
%A% logcat -G 16M >nul 2>&1
%A% logcat -c
%A% shell am force-stop com.shaonirfan.samonline.tv
%A% shell am start -W -n com.shaonirfan.samonline.tv/.MainActivity > "%OUT%\03_launch.txt" 2>&1
echo App launched. Running the test sequence (about 3 minutes) - please don't touch the remote.

rem cold start: clear cached rows so first-load speed is measured
%A% shell run-as com.shaonirfan.samonline.tv rm -f files/sam_data/home_rows.json >nul 2>&1
%A% shell am force-stop com.shaonirfan.samonline.tv
%A% shell am start -W -n com.shaonirfan.samonline.tv/.MainActivity >> "%OUT%\03_launch.txt" 2>&1
call :wait 10 & call :shot 01a_home_10s
call :wait 10 & call :shot 01b_home_20s
call :wait 10 & call :shot 01c_home_30s
call :wait 15 & call :shot 02_home_loaded
call :key DPAD_DOWN & call :wait 2 & call :shot 03_row1
call :key DPAD_DOWN & call :wait 2 & call :shot 04_row2
call :key DPAD_RIGHT & call :key DPAD_RIGHT & call :wait 2 & call :shot 05_row2_right
call :key DPAD_CENTER & call :wait 10 & call :shot 06_title_page
call :key DPAD_CENTER & call :wait 15 & call :shot 07_player
call :wait 7 & call :shot 08_player_controls_hidden
call :key DPAD_RIGHT & call :wait 1 & call :shot 09_seek_right
call :wait 4 & call :key DPAD_UP & call :wait 1 & call :shot 10_controls
call :key BACK & call :wait 2 & call :key BACK & call :wait 4 & call :shot 11_back_on_title
call :key BACK & call :wait 4 & call :shot 12_back_home_focus
call :key DPAD_LEFT & call :key DPAD_LEFT & call :key DPAD_LEFT & call :wait 2 & call :shot 13_rail
call :key DPAD_UP & call :wait 1 & call :key DPAD_CENTER & call :wait 4 & call :shot 14_search
%A% shell input text "friends"
call :wait 8 & call :shot 15_search_friends
call :key DEL & call :key DEL & call :key DEL & call :key DEL & call :wait 5 & call :shot 15b_search_fri_suggestions
call :key BACK & call :wait 3 & call :key DPAD_LEFT & call :key DPAD_LEFT & call :wait 1
call :key DPAD_DOWN & call :key DPAD_DOWN & call :wait 1 & call :key DPAD_CENTER & call :wait 4 & call :shot 16_categories
call :key DPAD_DOWN & call :key DPAD_RIGHT & call :key DPAD_RIGHT & call :wait 1 & call :shot 17_categories_focus
call :key DPAD_CENTER & call :wait 12 & call :shot 18_category_grid
call :key DPAD_CENTER & call :wait 10 & call :shot 19_next_level
call :key DPAD_CENTER & call :wait 10 & call :shot 20_next_level2

rem ---------- logs ----------
%A% logcat -d -v threadtime > "%OUT%\logcat.txt" 2>&1
%A% logcat -b crash -d -v threadtime > "%OUT%\crash.txt" 2>&1
%A% shell pidof com.shaonirfan.samonline.tv > "%OUT%\pid.txt" 2>&1
%A% shell dumpsys activity top > "%OUT%\activity_top.txt" 2>&1
echo DONE> "%OUT%\done.txt"
echo.
echo ================================================
echo  Finished. Results are in %OUT%
echo  Go back to Claude and say "done".
echo ================================================
pause
exit /b 0

:wait
timeout /t %1 /nobreak >nul
exit /b 0
:key
%A% shell input keyevent KEYCODE_%1
exit /b 0
:shot
%A% exec-out screencap -p > "%OUT%\%1.png"
echo   captured %1
exit /b 0
