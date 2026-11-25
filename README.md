# SAM Online TV

An Android TV / Google TV client for the SAM Online **DHAKA-FLIX** BDIX file servers, built with
Jetpack Compose for TV. It is a 10-foot, D-pad-only rebuild of [BeeGear](https://github.com/SelfishBeeOP/BeeGear)'s functionality.

> There is no backend or API. Every section is a plain directory-listing page on a private
> `172.16.50.x` server, so the app only works on a SAM Online / BDIX connection.

## Features

- **Home**: auto-rotating hero carousel (Play / More Info), **Continue Watching** with progress bars,
  **My List**, and a row of the newest titles for each of the 9 sections. Rows from the last session
  show instantly and refresh in the background; the backdrop follows the focused poster.
- **Categories**: all 9 DHAKA-FLIX sections. Any folder opens as a poster grid, a movie page, or a
  series page, depending on what it contains.
- **Details**: blurred backdrop, poster, Play / Resume, My List, Download, season chips → episode list
  with per-episode progress, and "More Like This".
- **Search**: on-screen D-pad keyboard, voice search, and hardware-keyboard input. The first search
  crawls the libraries into a local index (searchable while it builds); after that every search is
  instant. The index refreshes every 24 h.
- **Player** (Media3 ExoPlayer + FFmpeg audio decoder for AC3/EAC3/DTS MKVs) with a custom TV overlay:
  ◀/▶ seek 10 s (hold to scrub 30 s → 60 s steps), seek bar, subtitle / audio / speed dialogs,
  `.srt` / `.vtt` / `.ass` subtitles auto-loaded from the video's folder (or a `Subs` sub-folder),
  Next Episode + 10 s up-next countdown, resume per URL, and media-key support.
- **Downloads**: BeeGear's single-active queue with HTTP Range resume and auto-resume after
  connection drops. Files are saved to `Movies/SAM Online TV`.
- **Saved on the device**: watch positions, Continue Watching, My List, the search index, folder→poster
  map, home rows, settings and the download queue. Posters use Coil's memory + 512 MB disk cache.

## TV UX

- Every element is focusable, with a 1.08× scale, a white border and a red glow on focus.
- Focus is remembered per screen and restored on Back. Rows return focus to the last poster you were on.
- Rows keep the focused item near the top of the screen (a pivot `BringIntoViewSpec`).
- 48 dp / 27 dp overscan-safe margins, TV type scale, and a dark cinematic theme.
- Back from deep in Home scrolls to the top first. Back from any other top-level screen goes Home.

### A note on TvLazyRow / ImmersiveList

`androidx.tv:tv-foundation` 1.0.0 **removed** `TvLazyRow` / `TvLazyColumn`. Google now recommends the
standard Compose `LazyRow` / `LazyColumn`, which handle TV focus scrolling since Compose 1.7.
`ImmersiveList` was also dropped from `tv-material` before 1.0. This app uses the current stable APIs:
tv-material `Carousel`, `Card`, `Surface`, `ListItem`, `FilterChip` and `ModalNavigationDrawer`, plus
foundation lazy layouts with `focusRestorer()` and a pivot `BringIntoViewSpec`. The backdrop that
follows focus does the job ImmersiveList used to do.

## Project structure

```
app/src/main/java/com/shaonirfan/samonline/tv/
├── SamOnlineApp.kt / AppContainer.kt / MainActivity.kt
├── data/
│   ├── model/        Models, NameCleaner (readable titles, years, episode labels)
│   ├── remote/       Servers (the 9 sections), DirectoryScraper (Jsoup), SubtitleFinder
│   ├── repository/   CatalogRepository (rows, folder → grid/movie/series, posters),
│   │                 SearchRepository (local index), PlaybackResolver (Play/Resume queues)
│   ├── local/        LibraryStore (My List + progress), PosterStore, SettingsStore, JsonFileStore
│   └── download/     DownloadManager
├── player/           PlayerController (ExoPlayer logic), PlayerScreen (TV overlay), TrackDialog
└── ui/
    ├── theme/        Colors, TV typography, dimensions
    ├── components/   PosterCard, ContentRow, Backdrop, FocusMemory, pivot scrolling, buttons, states
    ├── navigation/   Routes, NavHost, left navigation rail
    └── screens/      home, browse (categories), details (folder: grid / details), search,
                      mylist, downloads, settings
```

## Building

Requirements: JDK 17 and the Android SDK (platform 35).

```bash
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease      # debug-signed unless a release keystore is configured
```

Install on a TV over ADB: `adb connect <tv-ip> && adb install -r app-debug.apk`.

### CI and releases

`.github/workflows/android.yml` runs the unit tests and builds both APKs on every push to `main`.
It uploads them as workflow artifacts and creates a GitHub Release with the release APK.

To sign releases with your own key, run **Generate Release Keystore** once. Follow the steps at the
top of `generate-key.yml`, then add the secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`
(`samonline`) and `KEY_PASSWORD`. Until then, release APKs are signed with the debug key.

### Testing on a real TV

`tools/tv-test/` has `run_test.sh` (Raspberry Pi / Linux / macOS) and `run_test.bat` (Windows). Each
installs the latest release on an Android TV over network adb, drives the app with remote key presses,
and saves screenshots, logcat and a PASS/FAIL summary. See [tools/tv-test/README.md](tools/tv-test/README.md).

### Testing the scraper without the BDIX network

`tools/make_fakeflix.py` builds a fake DHAKA-FLIX tree. It includes an h5ai-style page, year folders,
letter groups with seasons, a `Subs` folder and flat anime episodes.

```bash
python3 tools/make_fakeflix.py /tmp/fakeflix && (cd /tmp/fakeflix && python3 -m http.server 8765 &)
FAKEFLIX=http://127.0.0.1:8765 ./gradlew :app:testDebugUnitTest
```

Without `FAKEFLIX` set (as in CI), these tests are skipped.

## Credits

Server list, scraping approach, poster discovery, subtitle auto-loading and the download queue design
come from [BeeGear](https://github.com/SelfishBeeOP/BeeGear).
