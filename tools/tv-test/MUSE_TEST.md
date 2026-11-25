# Task for Muse: test SAM Online TV on the living-room TV from the Raspberry Pi

1. On the Pi, install the tools if they're missing: `sudo apt install -y adb curl git python3-pil`
2. Get the latest code:
   `git clone https://github.com/shaonirfan/sam-online-app-tv` (or `git pull` in an existing clone),
   then `cd sam-online-app-tv/tools/tv-test`.
3. Run `./run_test.sh 192.168.1.221 --push`. It takes about 4 minutes. `--push` publishes the report and
   all screenshots to the `test-reports` branch, which needs git push access from the Pi. On the very first connection, someone
   must accept "Allow USB debugging?" on the TV (tick "Always allow").
   - Exit 2 with "connect" in `summary.txt` means the Pi can't reach the TV, or debugging is off or
     not yet accepted.
4. Report back to Farhan:
   - The link the script prints after "Published:". That `REPORT.md` shows the summary and every
     screenshot.
   - The full contents of `results/<timestamp>/summary.txt` and the installed version.
   - What these screenshots show: `02_home_ready`, `03_title_page`, `04_series_page`,
     `05_series_episodes`, `08_search_friends`, `09_search_fri`, `12_player_controls`.
     Check that the title page shows the title at the top (not scrolled away).
   - v1.0.4 adds online subtitles. The script's `subs:` step searches OpenSubtitles (search only,
     no download). Screenshot `14_online_subtitles` should show a list of English subtitles.
   - The script now checks these itself (v1.0.3+): no rank prefix, MB/GB sizes, the series page with
     episodes, *Friends* first, "fri" suggestions, and playback + seek. Report any WARN/FAIL lines.
   - Updates should now install over the previous version. This is the last time a clean reinstall
     (signature change) is expected.
   - Any FAIL line, plus the matching lines from `app_errors.txt` / `crash.txt`.
5. Keep `results/<timestamp>.tar.gz` so it can be shared for debugging.

Don't modify the app code. This task is testing and reporting only.
