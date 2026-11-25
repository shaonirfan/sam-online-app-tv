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
   - What these screenshots show: `03_home_35s`, `07_title_page`, `16_search_friends`,
     `17_search_fri_suggestions`, `22_series_page`.
   - Expected in v1.0.2:
     - Titles have no "089." prefix.
     - The title page opens with the title visible, and sizes show in MB/GB (not KB).
     - Searching "friends" lists the *Friends* series first.
     - Typing "fri" shows a suggestions row (Friends, Friends with Benefits, ...).
     - Search results have All / Movies / TV Shows filter chips.
   - Any FAIL line, plus the matching lines from `app_errors.txt` / `crash.txt`.
5. Keep `results/<timestamp>.tar.gz` so it can be shared for debugging.

Don't modify the app code. This task is testing and reporting only.
