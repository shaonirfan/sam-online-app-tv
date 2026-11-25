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
./run_test.sh 192.168.1.221 --cold           # clear app data first (fresh-install timing;
                                             # wipes My List / watch progress on the TV)
./run_test.sh 192.168.1.221 --push           # also publish the report + screenshots (see below)
```

Optional, for smaller screenshots when publishing: `sudo apt install -y python3-pil`. ImageMagick
or ffmpeg also work. Without any of them, the original PNGs are pushed.

Results go to `results/<timestamp>/` (also packed as `results/<timestamp>.tar.gz`):

- `summary.txt`: one PASS / WARN / FAIL / INFO line per check (details below).
- `testlog.txt`: the app's test markers (what each screen reported when it became ready).
- `NN_*.png`: TV screenshots of each step.
- `logcat.txt`, `crash.txt`, `app_errors.txt`, `01_device_info.txt`, `01_ping.txt`, `02_install.txt`.

### How the session works

The script doesn't press keys blindly. It opens each screen through the app's test entry point
(`am start ... --es open <target>`). It then waits until the app logs that the screen is ready (tag
`SamTvTest`), and checks the facts in that log line:

| Step | Entry point | Checks |
|---|---|---|
| Home (cold start) | app launch | time to first row (WARN if > 30 s), at least 7/9 rows loaded |
| Movie page | `title:imdb250:0` | no "089." rank prefix, file size in MB/GB |
| Series page | `title:series:0` | opens as a series, seasons and episodes listed |
| Categories | `category:series`, then OK | grid opens, first group opens |
| Search | `search:friends`, `search:fri` | *Friends* (TV) is the first result; "fri" has suggestions |
| Player | `play:imdb250:0`, then ▶▶▶ | player ready, position advancing, seek moves forward |

It finishes with crash, ANR and player-error checks. Other entry points: `home`, `category:<id>`,
`title:<id>:<n>`, `play:<id>:<n>`, `search:<text>`. Category ids: english, english1080, imdb250,
hindi, south, series, anime, korean, animation.

Exit code: `0` = all checks passed, `1` = a check failed, `2` = couldn't connect, download or install.

Note: player screenshots are often black even while video plays. Many TV chips (Amlogic, for example)
draw video on a hardware layer that `screencap` can't capture. Playback is checked from the app's
position markers instead.

## Publishing reports (`--push`)

With `--push`, the script publishes each run to the **`test-reports`** branch, not `main`. Pushing to
`main` would trigger a new build and release every time. Each run adds `reports/<timestamp>/` with:

- `REPORT.md`: version, device, the PASS/FAIL summary and every screenshot inline. It renders on GitHub.
- The screenshots as 960×540 JPEGs (~640 KB per run in total).
- `summary.txt`, `logcat.txt.gz`, `crash.txt`, `app_errors.txt`, `01_device_info.txt`, `02_install.txt`.

The branch's `README.md` lists every run, newest first, with version and result:
https://github.com/shaonirfan/sam-online-app-tv/tree/test-reports

The script uses a separate git worktree (`.test-reports/`, ignored by git), so your checkout of `main` is
never touched. The machine needs push access to the repo (an SSH key or deploy key, or an HTTPS token
in git's credential helper). If git has no user name/email set, it commits as "SAM TV test bot".

## Driving the TV by hand

```bash
adb connect 192.168.1.221:5555
adb -s 192.168.1.221:5555 shell input keyevent KEYCODE_DPAD_DOWN     # UP/LEFT/RIGHT/CENTER/BACK
adb -s 192.168.1.221:5555 shell input text "friends"
adb -s 192.168.1.221:5555 exec-out screencap -p > screen.png
adb -s 192.168.1.221:5555 logcat -v time | grep -i samonline
```
