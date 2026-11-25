#!/usr/bin/env bash
# SAM Online TV - automated install + smoke test on an Android TV over network adb.
# Works on Raspberry Pi OS / Debian / Ubuntu / macOS. Needs: adb, curl, (optional) python3 for image checks.
#
# Usage:
#   ./run_test.sh 192.168.1.221                 # install latest GitHub release, run the full test
#   ./run_test.sh 192.168.1.221 --apk app.apk   # install a local APK instead
#   ./run_test.sh 192.168.1.221 --no-install    # test what is already installed
#   ./run_test.sh 192.168.1.221 --cold          # clear app data first (measures a fresh start;
#                                               # wipes My List / watch progress on the TV)
#   ./run_test.sh 192.168.1.221 --push          # also publish report + screenshots to the
#                                               # 'test-reports' branch (needs git push access)
#   ./run_test.sh 192.168.1.221 --os-user NAME --os-pass PASS   # store the OpenSubtitles login on the TV
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
COLD=0
OS_USER=""
OS_PASS=""
while [ $# -gt 0 ]; do
  case "$1" in
    --apk) APK="$2"; shift 2 ;;
    --no-install) INSTALL=0; shift ;;
    --push) PUSH=1; shift ;;
    --cold) COLD=1; shift ;;
    --os-user) OS_USER="$2"; shift 2 ;;
    --os-pass) OS_PASS="$2"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) TV_IP="$1"; shift ;;
  esac
done
if [ -z "$TV_IP" ]; then
  echo "Usage: $0 <tv-ip> [--apk file.apk] [--no-install] [--cold] [--push] [--os-user U --os-pass P]"; exit 2
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
shot() { sleep "${2:-0}"; A exec-out screencap -p > "$OUT/$1.png"; log "  screenshot $1"; }
alive() { [ -n "$(A shell pidof $PKG | tr -d '\r')" ]; }
warn() { echo "WARN  $*" | tee -a "$SUMMARY"; }

# The app logs one-line "ready" markers under the SamTvTest tag (see app/.../TestHooks.kt).
marks() { A logcat -d -s SamTvTest:I -v raw 2>/dev/null | tr -d '\r' | grep -v '^-----'; }
MARK_BASE=0
# open_target <target>: open a screen through the app's test entry point (--es open ...).
open_target() {
  MARK_BASE=$(marks | wc -l)
  A shell am start -n "$ACTIVITY" --es open "$1" > /dev/null 2>&1
  log "open $1"
}
# wait_mark <regex> <timeout-s>: wait for a NEW marker line matching regex; prints it.
wait_mark() {
  local re="$1" to="$2" line i
  for i in $(seq 1 "$to"); do
    line="$(marks | tail -n +$((MARK_BASE + 1)) | grep -E "$re" | tail -1)"
    if [ -n "$line" ]; then echo "$line"; return 0; fi
    sleep 1
  done
  return 1
}
# field <line> <name>: value of name=... in a marker line
field() { echo "$1" | grep -oE "(^| )$2=[^ ]*" | head -1 | cut -d= -f2-; }

# ---------------------------------------------------------------- session
A logcat -G 16M >/dev/null 2>&1
A shell am force-stop "$PKG"
if [ "$COLD" = 1 ]; then log "--cold: clearing app data (My List / progress / caches)"; A shell pm clear "$PKG" >/dev/null; fi
A logcat -c
MARK_BASE=0
A shell am start -W -n "$ACTIVITY" > "$OUT/03_launch.txt" 2>&1
log "App launched - running the test session (~5-8 min). Don't touch the remote."
if [ -n "$OS_USER" ]; then
  A shell am start -n "$ACTIVITY" --es os_user "$OS_USER" --es os_pass "$OS_PASS" > /dev/null 2>&1
  log "OpenSubtitles login stored on the TV for $OS_USER"
fi

# 1. Home: time to first row and to all rows
if m=$(wait_mark '^home_row ' 120); then
  t=$(( $(field "$m" t) / 1000 ))
  if [ "$t" -le 30 ]; then pass "home: first row after ${t}s"; else warn "home: first row only after ${t}s"; fi
  shot 01_home_first_row 2
else fail "home: no row loaded within 120s"; shot 01_home_first_row; fi
if m=$(wait_mark '^home_ready ' 240); then
  t=$(( $(field "$m" t) / 1000 )); rows=$(field "$m" rows)
  if [ "${rows:-0}" -ge 7 ]; then pass "home: $rows/9 rows loaded after ${t}s"; else fail "home: only $rows/9 rows loaded (${t}s)"; fi
else fail "home: rows still loading after 240s"; fi
shot 02_home_ready 3

# 2. Movie title page (first IMDb Top 250 title)
open_target title:imdb250:0
if m=$(wait_mark '^details ' 40); then
  title=$(field "$m" title); size=$(field "$m" size)
  info "title page: $title, size $size"
  if echo "$title" | grep -qE '^[0-9]{1,3}[._)-]'; then fail "title still has a rank prefix: $title"; else pass "title has no rank prefix ($title)"; fi
  case "$size" in *MB|*GB) pass "file size shown in MB/GB ($size)";; *KB) fail "file size still in KB ($size)";; *) info "no file size on this server listing";; esac
  shot 03_title_page 3
else fail "title page did not open"; shot 03_title_page; fi

# 3. Series page (first title of the TV & Web Series row) + its episodes
open_target title:series:0
if m=$(wait_mark '^details ' 40); then
  title=$(field "$m" title); seasons=$(field "$m" seasons); files=$(field "$m" files)
  if [ "$(field "$m" series)" = "true" ]; then pass "series page: $title ($seasons seasons, $files files)"; else fail "series row title opened as a movie: $title"; fi
  if [ "${seasons:-0}" -gt 0 ]; then
    if e=$(wait_mark '^episodes ' 30); then
      n=$(field "$e" count); es=$(field "$e" size)
      if [ "${n:-0}" -gt 0 ]; then pass "episodes listed: $n in $(field "$e" season) (first: $(field "$e" first), $es)"; else fail "season has no episodes"; fi
    else fail "episodes did not load"; fi
  fi
  shot 04_series_page 3
  key DPAD_DOWN; key DPAD_DOWN; key DPAD_DOWN; shot 05_series_episodes 2
else fail "series page did not open"; shot 04_series_page; fi

# 4. Category grid -> first letter group
open_target category:series
if m=$(wait_mark '^grid ' 40); then
  pass "category grid: $(field "$m" items) folders"
  shot 06_category_grid 3
  MARK_BASE=$(marks | wc -l); key DPAD_CENTER
  if m=$(wait_mark '^(grid|details) ' 40); then pass "opened $(field "$m" title) ($(echo "$m" | cut -d' ' -f1), $(field "$m" items)$(field "$m" seasons) entries)"; else fail "first group did not open"; fi
  shot 07_group 3
else fail "category grid did not open"; shot 06_category_grid; fi

# 5. Search "friends" (first run builds the index: results improve as it grows)
open_target search:friends
if m=$(wait_mark '^search t=[0-9]+ q=friends .*first=Friends/tv' 300) || m=$(wait_mark '^search t=[0-9]+ q=friends ' 5); then
  first=$(field "$m" first); info "search friends: $(field "$m" count) results (movies $(field "$m" movies), tv $(field "$m" series)), $(field "$m" indexed) titles indexed"
  case "$first" in Friends/tv*) pass "\"friends\" lists the Friends series first";; *) fail "\"friends\" first results: $first";; esac
else fail "search produced no results for friends"; fi
shot 08_search_friends 3

open_target search:fri
if m=$(wait_mark '^search t=[0-9]+ q=fri ' 60); then
  sug=$(field "$m" suggestions)
  if [ -n "$sug" ]; then pass "suggestions for \"fri\": $sug"; else fail "no suggestions for \"fri\""; fi
else fail "search for fri did not run"; fi
shot 09_search_fri 3

# 6. Playback: start, keep playing, seek
open_target play:imdb250:0
if m=$(wait_mark '^player_ready ' 60); then
  pass "player ready: $(field "$m" title), $(( $(field "$m" duration_s) / 60 )) min, starting at $(field "$m" pos_s)s"
  shot 10_player 3
  p1=$(field "$(wait_mark '^player_tick .*playing=true' 30)" pos_s)
  MARK_BASE=$(marks | wc -l)
  key DPAD_RIGHT; key DPAD_RIGHT; key DPAD_RIGHT
  shot 11_player_seek 1
  sleep 6
  p2=$(field "$(wait_mark '^player_tick ' 20)" pos_s)
  if [ -n "$p1" ] && [ -n "$p2" ] && [ "$p2" -ge $((p1 + 20)) ]; then pass "playing and seeking (${p1}s -> ${p2}s after 3x forward)"
  elif [ -n "$p2" ] && [ -n "$p1" ] && [ "$p2" -gt "$p1" ]; then warn "playing, but seek moved less than expected (${p1}s -> ${p2}s)"
  else fail "playback not progressing (${p1:-?}s -> ${p2:-?}s)"; fi
  key DPAD_UP; shot 12_player_controls 1
  key BACK; key BACK; shot 13_after_player 3
else
  m2=$(marks | grep '^player_error' | tail -1)
  fail "player did not start ${m2:+($m2)}"; shot 10_player
fi

# 7. Online subtitles: search only (doesn't use any of the daily downloads)
open_target subs:imdb250:0
if m=$(wait_mark '^online_search(_error)? ' 90); then
  case "$m" in
    online_search_error*) msg=$(field "$m" message)
      case "$msg" in *"not set up"*|*"no API key"*) warn "online subtitles disabled in this build (no OPENSUBTITLES_API_KEY secret)";;
                     *) fail "online subtitle search failed: $msg";; esac ;;
    *) n=$(field "$m" count); hm=$(field "$m" hash_matches)
      if [ "${n:-0}" -gt 0 ]; then pass "online subtitles: $n English results ($hm exact file matches), top: $(field "$m" top)"
      else warn "online subtitle search found nothing for this title"; fi ;;
  esac
else fail "online subtitle search did not run (player not ready?)"; fi
shot 14_online_subtitles 2
key BACK; key BACK; key BACK

# ---------------------------------------------------------------- logs + checks
marks > "$OUT/testlog.txt"
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
{
  echo
  echo "Note: player screenshots are often black - this TV draws video on a layer screencap can't see."
  echo "Playback is verified from the app's position markers instead (testlog.txt)."
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
  cp "$SUMMARY" "$OUT/01_device_info.txt" "$OUT/02_install.txt" "$OUT/testlog.txt" "$dest/" 2>/dev/null
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
    echo "Files: [summary.txt](summary.txt) · [testlog.txt](testlog.txt) · [logcat.txt.gz](logcat.txt.gz) · [crash.txt](crash.txt) · [app_errors.txt](app_errors.txt)"
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

  # Machines without a git identity (e.g. a fresh Pi) commit as a bot.
  local ident=()
  [ -n "$(git -C "$wt" config user.email)" ] || ident=(-c "user.name=SAM TV test bot" -c "user.email=tv-test-bot@users.noreply.github.com")
  (cd "$wt" && git add -A && \
    git "${ident[@]}" commit -q -m "TV test $STAMP: v${VERSION:-?} - $([ "$FAILS" -eq 0 ] && echo "all passed" || echo "$FAILS failed")" && \
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
