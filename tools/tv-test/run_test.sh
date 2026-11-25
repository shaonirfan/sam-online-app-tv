#!/usr/bin/env bash
# SAM Online TV - automated install + smoke test on an Android TV over network adb.
# Works on Raspberry Pi OS / Debian / Ubuntu / macOS. Needs: adb, curl, (optional) python3 for image checks.
#
# Usage:
#   ./run_test.sh 192.168.1.221                 # install latest GitHub release, run the full test
#   ./run_test.sh 192.168.1.221 --apk app.apk   # install a local APK instead
#   ./run_test.sh 192.168.1.221 --no-install    # test what is already installed
#   ./run_test.sh 192.168.1.221 --push          # also publish report + screenshots to the
#                                               # 'test-reports' branch (needs git push access)
#   TV_IP=192.168.1.221 ./run_test.sh
#
# Output: results/<timestamp>/ with screenshots (NN_name.png), logcat.txt, crash.txt,
#         summary.txt (PASS/FAIL per check) and results/<timestamp>.tar.gz
# Exit code: 0 = all checks passed, 1 = some check failed, 2 = could not connect / install.

set -u
PKG="com.shaonirfan.samonline.tv"
ACTIVITY="$PKG/.MainActivity"
APK_URL="https://github.com/shaonirfan/sam-online-app-tv/releases/latest/download/SAM-Online-TV.apk"
SERVERS="172.16.50.7 172.16.50.14 172.16.50.12 172.16.50.10"

TV_IP="${TV_IP:-}"
APK=""
INSTALL=1
PUSH=0
while [ $# -gt 0 ]; do
  case "$1" in
    --apk) APK="$2"; shift 2 ;;
    --no-install) INSTALL=0; shift ;;
    --push) PUSH=1; shift ;;
    -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
    *) TV_IP="$1"; shift ;;
  esac
done
if [ -z "$TV_IP" ]; then
  echo "Usage: $0 <tv-ip> [--apk file.apk] [--no-install] [--push]"; exit 2
fi
DEV="${TV_IP}:5555"

cd "$(dirname "$0")"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="results/$STAMP"
mkdir -p "$OUT"
SUMMARY="$OUT/summary.txt"
FAILS=0

log()  { echo "[$(date +%H:%M:%S)] $*" | tee -a "$OUT/run.log"; }
pass() { echo "PASS  $*" | tee -a "$SUMMARY"; }
fail() { echo "FAIL  $*" | tee -a "$SUMMARY"; FAILS=$((FAILS + 1)); }
info() { echo "INFO  $*" | tee -a "$SUMMARY"; }

# ---------------------------------------------------------------- adb
if ! command -v adb >/dev/null 2>&1; then
  log "adb not found - trying: sudo apt-get install -y adb"
  if command -v apt-get >/dev/null 2>&1 && sudo -n true 2>/dev/null; then
    sudo apt-get update -qq && sudo apt-get install -y -qq adb
  fi
  command -v adb >/dev/null 2>&1 || { echo "Install adb first (Raspberry Pi: sudo apt install adb)"; exit 2; }
fi
A() { adb -s "$DEV" "$@"; }
adb version > "$OUT/00_adb_version.txt" 2>&1

# ---------------------------------------------------------------- connect
log "Connecting to $DEV (accept 'Allow USB debugging?' on the TV the first time, tick 'Always allow')"
connected=0
for i in $(seq 1 40); do
  adb connect "$DEV" >> "$OUT/00_connect.txt" 2>&1
  if [ "$(A get-state 2>/dev/null)" = "device" ]; then connected=1; break; fi
  sleep 3
done
adb devices -l >> "$OUT/00_connect.txt" 2>&1
if [ "$connected" != 1 ]; then
  fail "connect to $DEV (check: same network, USB/Network debugging on, IP correct, debugging prompt accepted)"
  exit 2
fi
pass "connected to $DEV"

{
  for p in ro.product.brand ro.product.model ro.build.version.release ro.build.version.sdk ro.product.cpu.abi; do
    echo "$p: $(A shell getprop $p | tr -d '\r')"
  done
  echo "screen: $(A shell wm size | tr -d '\r')"
  echo "density: $(A shell wm density | tr -d '\r')"
} > "$OUT/01_device_info.txt" 2>&1
info "device: $(grep -h 'brand\|model\|release' "$OUT/01_device_info.txt" | cut -d' ' -f2- | paste -sd' ')"

for s in $SERVERS; do
  if A shell ping -c 2 -W 2 "$s" >> "$OUT/01_ping.txt" 2>&1; then pass "TV reaches DHAKA-FLIX $s"; else fail "TV cannot reach DHAKA-FLIX $s (BDIX)"; fi
done

# ---------------------------------------------------------------- install
if [ "$INSTALL" = 1 ]; then
  if [ -z "$APK" ]; then
    APK="$OUT/SAM-Online-TV.apk"
    log "Downloading latest release APK"
    curl -fsSL -o "$APK" "$APK_URL" || { fail "download $APK_URL"; exit 2; }
  fi
  log "Installing $APK"
  A install -r "$APK" > "$OUT/02_install.txt" 2>&1
  if ! grep -q Success "$OUT/02_install.txt"; then
    log "Update install failed (signature change?) - uninstalling and installing clean"
    A uninstall "$PKG" >> "$OUT/02_install.txt" 2>&1
    A install "$APK" >> "$OUT/02_install.txt" 2>&1
  fi
  if grep -q Success "$OUT/02_install.txt"; then pass "install"; else fail "install: $(tail -1 "$OUT/02_install.txt")"; exit 2; fi
fi
VERSION="$(A shell dumpsys package $PKG | grep -m1 versionName | tr -d '\r ' | cut -d= -f2)"
info "installed version: ${VERSION:-unknown}"

# ---------------------------------------------------------------- helpers
key()  { A shell input keyevent "KEYCODE_$1"; sleep 0.4; }
wait_s() { sleep "$1"; }
shot() { A exec-out screencap -p > "$OUT/$1.png"; log "  screenshot $1 ($(wc -c < "$OUT/$1.png") bytes)"; }
alive() { [ -n "$(A shell pidof $PKG | tr -d '\r')" ]; }

# ---------------------------------------------------------------- scripted session
A logcat -G 16M >/dev/null 2>&1
A logcat -c
A shell am force-stop "$PKG"
A shell am start -W -n "$ACTIVITY" > "$OUT/03_launch.txt" 2>&1
log "App launched - running the remote-control sequence (~4 min). Don't touch the remote."

wait_s 10; shot 01_home_10s
wait_s 10; shot 02_home_20s
wait_s 15; shot 03_home_35s
key DPAD_DOWN; wait_s 2; shot 04_row1
key DPAD_DOWN; wait_s 2; shot 05_row2
key DPAD_RIGHT; key DPAD_RIGHT; wait_s 2; shot 06_row2_right
key DPAD_CENTER; wait_s 10; shot 07_title_page
key DPAD_CENTER; wait_s 15; shot 08_player
wait_s 7; shot 09_player_controls_hidden
key DPAD_RIGHT; wait_s 1; shot 10_seek_right
wait_s 4; key DPAD_UP; wait_s 1; shot 11_controls
key BACK; wait_s 2; key BACK; wait_s 4; shot 12_back_on_title
key BACK; wait_s 4; shot 13_back_home_focus
key DPAD_LEFT; key DPAD_LEFT; key DPAD_LEFT; wait_s 2; shot 14_rail
key DPAD_UP; wait_s 1; key DPAD_CENTER; wait_s 4; shot 15_search
A shell input text "friends"; wait_s 8; shot 16_search_friends
key DEL; key DEL; key DEL; key DEL; wait_s 5; shot 17_search_fri_suggestions
key BACK; wait_s 3; key DPAD_LEFT; key DPAD_LEFT; wait_s 1
key DPAD_DOWN; key DPAD_DOWN; wait_s 1; key DPAD_CENTER; wait_s 4; shot 18_categories
key DPAD_DOWN; key DPAD_RIGHT; key DPAD_RIGHT; wait_s 1; shot 19_categories_focus
key DPAD_CENTER; wait_s 12; shot 20_category_grid
key DPAD_CENTER; wait_s 10; shot 21_group_grid
key DPAD_CENTER; wait_s 10; shot 22_series_page

# ---------------------------------------------------------------- logs + checks
A logcat -d -v threadtime > "$OUT/logcat.txt" 2>&1
A logcat -b crash -d -v threadtime > "$OUT/crash.txt" 2>&1
A shell dumpsys activity top > "$OUT/activity_top.txt" 2>&1
PID="$(A shell pidof $PKG | tr -d '\r')"

if alive; then pass "app still running at the end (pid $PID)"; else fail "app not running at the end (crashed or exited)"; fi
if grep -q "$PKG" "$OUT/crash.txt"; then fail "crash buffer has entries for $PKG (see crash.txt)"; else pass "no crashes"; fi
anr="$(grep -c "ANR in $PKG" "$OUT/logcat.txt" || true)"
if [ "${anr:-0}" -gt 0 ]; then fail "$anr ANR(s) (app not responding)"; else pass "no ANRs"; fi
if [ -n "$PID" ]; then
  grep " $PID " "$OUT/logcat.txt" | grep -E " E (AndroidRuntime|ExoPlayerImplInternal|MediaCodecRenderer)" > "$OUT/app_errors.txt" || true
  n="$(wc -l < "$OUT/app_errors.txt")"; if [ "$n" -gt 0 ]; then fail "$n player/runtime error lines (app_errors.txt)"; else pass "no player/runtime errors"; fi
  grep " $PID " "$OUT/logcat.txt" | grep -m1 -oE "MediaCodec: \[[^]]+\]" | head -1 | sed 's/^/INFO  video decoder: /' >> "$SUMMARY" || true
fi

# Screens that should show real content (posters) are big PNGs; near-empty ones are tiny.
# (The player screens are excluded: many TV chips draw video on a layer screencap can't see.)
for s in 03_home_35s 07_title_page 16_search_friends 20_category_grid 22_series_page; do
  f="$OUT/$s.png"
  if [ -f "$f" ] && [ "$(wc -c < "$f")" -gt 150000 ]; then pass "screen $s has content"; else fail "screen $s looks empty ($(wc -c < "$f" 2>/dev/null || echo 0) bytes)"; fi
done

{
  echo
  echo "Screens to eyeball: 03 home, 07 title page (title visible, sizes in MB/GB), 16 'friends' (Friends series first),"
  echo "17 'fri' suggestions row, 22 series page (seasons + episodes)."
} >> "$SUMMARY"

tar -czf "results/$STAMP.tar.gz" -C results "$STAMP"

# ---------------------------------------------------------------- publish (--push)
# Screenshots are shrunk to 960x540 JPEGs (~60 KB) so the branch stays small.
to_jpg() { # $1 = png, $2 = jpg
  if python3 -c "import PIL" 2>/dev/null; then
    python3 - "$1" "$2" <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert("RGB"); im.thumbnail((960, 540)); im.save(sys.argv[2], quality=80)
PY
  elif command -v convert >/dev/null 2>&1; then convert "$1" -resize 960x540 -quality 80 "$2"
  elif command -v ffmpeg >/dev/null 2>&1; then ffmpeg -loglevel error -y -i "$1" -vf scale=960:-1 -q:v 4 "$2"
  else cp "$1" "${2%.jpg}.png"; fi
}

publish() {
  local root wt dest img
  root="$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "--push: not inside a git clone"; return 1; }
  wt="$root/.test-reports"
  git -C "$root" fetch -q origin test-reports 2>/dev/null || true
  if [ ! -d "$wt/.git" ] && [ ! -f "$wt/.git" ]; then
    if git -C "$root" show-ref -q refs/remotes/origin/test-reports; then
      git -C "$root" worktree add -q -B test-reports "$wt" origin/test-reports || return 1
    else
      git -C "$root" worktree add -q --detach "$wt" || return 1
      (cd "$wt" && git checkout -q --orphan test-reports && git rm -rfq . >/dev/null 2>&1; true)
    fi
  else
    (cd "$wt" && git checkout -q test-reports 2>/dev/null; git pull -q --rebase origin test-reports 2>/dev/null; true)
  fi

  dest="$wt/reports/$STAMP"
  mkdir -p "$dest"
  for f in "$OUT"/*.png; do to_jpg "$f" "$dest/$(basename "${f%.png}").jpg"; done
  cp "$SUMMARY" "$OUT/01_device_info.txt" "$OUT/02_install.txt" "$dest/" 2>/dev/null
  cp "$OUT/crash.txt" "$OUT/app_errors.txt" "$dest/" 2>/dev/null
  gzip -c "$OUT/logcat.txt" > "$dest/logcat.txt.gz"

  {
    echo "# TV test $STAMP"
    echo
    echo "- App version: **${VERSION:-?}**"
    echo "- Device: $(grep -h 'brand\|model\|release' "$OUT/01_device_info.txt" | cut -d' ' -f2- | paste -sd' ')"
    echo "- Result: **$([ "$FAILS" -eq 0 ] && echo "ALL PASSED" || echo "$FAILS FAILED")**"
    echo
    echo '```'
    cat "$SUMMARY"
    echo '```'
    echo
    echo "Files: [summary.txt](summary.txt) · [logcat.txt.gz](logcat.txt.gz) · [crash.txt](crash.txt) · [app_errors.txt](app_errors.txt)"
    echo
    echo "## Screenshots"
    for img in $(cd "$dest" && ls *.jpg *.png 2>/dev/null | sort); do
      echo
      echo "**${img%.*}**"
      echo
      echo "![${img%.*}]($img)"
    done
  } > "$dest/REPORT.md"

  {
    echo "# SAM Online TV - on-device test reports"
    echo
    echo "Pushed by \`tools/tv-test/run_test.sh --push\`. Newest first."
    echo
    for d in $(cd "$wt/reports" && ls -1 | sort -r); do
      res="$(grep -m1 -o 'ALL PASSED\|[0-9]* FAILED' "$wt/reports/$d/REPORT.md" 2>/dev/null)"
      ver="$(grep -m1 -o 'App version: \*\*[^*]*' "$wt/reports/$d/REPORT.md" | sed 's/.*\*\*//')"
      echo "- [$d](reports/$d/REPORT.md) - v${ver:-?} - ${res:-?}"
    done
  } > "$wt/README.md"

  (cd "$wt" && git add -A && \
    git commit -q -m "TV test $STAMP: v${VERSION:-?} - $([ "$FAILS" -eq 0 ] && echo "all passed" || echo "$FAILS failed")" && \
    git push -q -u origin test-reports) || return 1
  local url
  url="$(git -C "$root" remote get-url origin | sed -e 's#git@github.com:#https://github.com/#' -e 's#\.git$##')"
  echo "Published: $url/blob/test-reports/reports/$STAMP/REPORT.md"
}

if [ "$PUSH" = 1 ]; then
  publish || { echo "WARNING: --push failed (see messages above); results are still in $OUT"; }
fi
echo
echo "================ SUMMARY ($STAMP, version ${VERSION:-?}) ================"
cat "$SUMMARY"
echo "Results: $OUT  (archive: results/$STAMP.tar.gz)"
[ "$FAILS" -eq 0 ] && exit 0 || exit 1
