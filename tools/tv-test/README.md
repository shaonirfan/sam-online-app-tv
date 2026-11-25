# TV install & smoke test

These scripts install SAM Online TV on an Android TV over network adb, then drive the app with remote
key presses. They save a screenshot after every step, plus logcat and a PASS/FAIL summary.

| Script | Runs on |
|---|---|
| `run_test.sh` | Raspberry Pi / any Linux / macOS |
| `run_test.bat` | Windows. Double-click it. It downloads adb itself and asks for the TV IP once. |

## TV setup (once)

1. Turn on Developer options: Settings → Device Preferences → About → click **Build** 7 times.
2. In Developer options, turn on **USB debugging**, plus **Network debugging** if the TV has it.
3. On the first connection, accept **"Allow USB debugging?"** with **Always allow** ticked.

The TV and the test machine must be on the same network. The TV also needs the SAM Online / BDIX
connection to reach the 172.16.50.x servers.

## Raspberry Pi

```bash
sudo apt install -y adb curl
git clone https://github.com/shaonirfan/sam-online-app-tv && cd sam-online-app-tv/tools/tv-test
./run_test.sh 192.168.1.221                  # latest GitHub release, full test (~4 min)
./run_test.sh 192.168.1.221 --apk my.apk     # a local build instead
./run_test.sh 192.168.1.221 --no-install     # test what's already installed
```

Results go to `results/<timestamp>/` (also packed as `results/<timestamp>.tar.gz`):

- `summary.txt`: one PASS / FAIL / INFO line per check. It covers connection, BDIX reachability,
  install, the installed version, whether the app survived the run, crashes, ANRs, player errors,
  the video decoder used, and whether key screens show content.
- `NN_*.png`: TV screenshots in order. These cover Home, title page, player, back navigation, search
  "friends" and the "fri" suggestions, Categories → TV & Web Series → a letter group → a series page.
- `logcat.txt`, `crash.txt`, `app_errors.txt`, `01_device_info.txt`, `01_ping.txt`, `02_install.txt`.

Exit code: `0` = all checks passed, `1` = a check failed, `2` = couldn't connect, download or install.

Note: player screenshots are often black even while video plays. Many TV chips (Amlogic, for example)
draw video on a hardware layer that `screencap` can't capture. Use the decoder line in `summary.txt`
and the absence of player errors instead.

## Driving the TV by hand

```bash
adb connect 192.168.1.221:5555
adb -s 192.168.1.221:5555 shell input keyevent KEYCODE_DPAD_DOWN     # UP/LEFT/RIGHT/CENTER/BACK
adb -s 192.168.1.221:5555 shell input text "friends"
adb -s 192.168.1.221:5555 exec-out screencap -p > screen.png
adb -s 192.168.1.221:5555 logcat -v time | grep -i samonline
```
