# Fossify Music Player — per-queue resume fork

Base: FossifyOrg/Music-Player, commit `a4cbf6f` (1.8.5, versionCode 18).
Fork version: **1.8.5-resume1** (versionCode 19), applicationId
**`org.fossify.musicplayer.resume`** — installs alongside the stock
Fossify Music Player; the launcher label stays "Music Player" (the
`app_name` string is unchanged). The Kotlin
package is unchanged (as in the Fossify Gallery Enhanced precedent); only
the installed applicationId changed, and the provider authority follows
`${applicationId}.provider`. The Android `namespace` is pinned to
`org.fossify.musicplayer` so the generated `R` class keeps resolving.

## The feature

**Each queue remembers exactly where you stopped.** Pause halfway through
an album, play other albums and playlists for days, come back, and pick up
at the exact track and millisecond position where you paused — per queue,
not just for the most recently played one.

Stock Fossify keeps a single queue in the `queue_items` table and rewrites
it whenever another album/playlist is played, so only the last-played queue
could ever be resumed, and only to whole-second precision. This fork adds
`queue_states` / `queue_state_items` tables keyed by the exact ordered
track list (SHA-256 of the track ids), storing the current track, its index,
and the position in **milliseconds**, plus shuffle/repeat and a label.

### How it behaves

- Saving: on pause and on track change (forced, so the exact stopping point
  is always recorded), plus every 10 seconds while playing, so killing the
  app mid-track does not lose the position. Writes are throttled to one
  meaningful change per 10 s per queue while playing. The newest 20 queues
  keep a saved position; older ones are pruned.
- Restoring when you tap a queue again: tapping the **first track** of a
  queue, or the **track playback stopped on**, resumes at the saved track
  and position and plays. Tapping any other track honours the tap and
  starts that track from the beginning.
- Restoring on a cold start: the last queue is prepared **paused** at its
  saved position (stock behaviour, now millisecond-exact). Press play to
  continue. Nothing auto-plays.
- A saved position under 5 seconds is ignored (fresh start). A position
  within 3 seconds of a track's end counts as finished: playback continues
  from the next track, or restarts the queue if it was the last track.
- Settings toggle: "Remember where each queue stopped" (on by default).
  Turning it off deletes all saved positions.
- **Every playlist remembers where it stopped** (DB v18 table
  `playlist_states`, keyed by playlist id): the song playback stopped on
  in each playlist, and the exact position in it. The playlist's row in
  the Playlists tab shows it ("12 Tracks • Artist — Title • resumes at
  M:SS"), opening the playlist highlights that song and scrolls straight
  to it, and tapping it continues from the saved position. Keying by
  playlist id means it survives re-sorting the playlist, adding or
  removing songs, and shuffle. A song played only briefly is still
  remembered (it resumes from its start). The playlist id is recorded
  when playback starts from the playlist, because the player's fast
  media items carry no metadata to read it back from. Deleting a playlist deletes its saved
  stopping point with it; turning the Settings toggle off forgets them
  all.
- **Named queues, like playlists** (main menu → "Queues", DB v17 tables
  `named_queues` / `named_queue_items`): a queue is a stored list of its
  own, with a name and its own stopping point. Create one from any track
  list (album / playlist / folder / genre menu → "Save as queue") or from
  the playing queue (queue menu → "Save playing queue"). Queue 1 can hold
  20 songs and Queue 2 can hold 30: play song 10 in Queue 1, then song 3
  in Queue 2, then open Queue 1 again and song 10 is highlighted, ready
  to continue. Each queue's position is kept in step with playback
  whenever the playing list matches that queue's track list, so switching
  queues never loses a place. Tap a queue to open its tracks; long-press
  to delete it; rename or delete it from its own menu. Deleting a queue
  never touches the music files. Queues whose files are all gone are hidden.
- **Highlight + no hunting:** in every track list (Songs tab, album /
  playlist / folder / genre lists, and inside a named queue), the track
  playback stopped on is shown in the accent colour with "resumes at
  M:SS" instead of its length, and the list opens scrolled straight to
  that track. Tap the highlighted track to continue from its saved
  position; tapping any other track starts that track from the beginning.
  The automatic per-list positions still work for lists that were played
  and paused without being saved as a named queue.
- The stock single-queue resume (`queue_items`) is kept untouched for
  external media-browser / Android Auto resume paths.
- **Performance (fix, 2026-10-27):** the database runs on a single
  thread and a write transaction blocks reads, so the first version
  rewrote the whole saved track list every 10 seconds and on every
  pause/track change, stalling list loads. Position saves are now a
  single-row UPDATE when the track list is unchanged; the full list is
  written only when the list itself changes, and the queue-key hash is
  cached for a repeated identical list.

### Files changed

- New: `models/QueueState.kt`, `models/QueueStateItem.kt`,
  `interfaces/QueueStatesDao.kt`, `helpers/QueueResumeHelper.kt`
  (keying, labelling, throttled save, resume lookup, pruning).
- `databases/SongsDatabase.kt`: version 15 → 16, `MIGRATION_15_16`
  creates both tables; existing installs migrate without losing data.
- Named queues: new `models/NamedQueue.kt`, `models/NamedQueueItem.kt`,
  `models/NamedQueueRow.kt`, `interfaces/NamedQueuesDao.kt`,
  `helpers/NamedQueuesHelper.kt`, `activities/QueuesActivity.kt`,
  `activities/QueueDetailActivity.kt`, `adapters/QueuesAdapter.kt`,
  `dialogs/NewQueueDialog.kt`; `SongsDatabase.kt` version 16 → 17,
  `MIGRATION_16_17` creates both tables. The Saved Queues screen
  (`SavedQueuesActivity`, `SavedQueuesAdapter`, `SavedQueue`) was replaced
  by the Queues screen; the automatic per-list positions in `queue_states`
  are unchanged and still back the highlights in plain track lists.
- Per-playlist resume: new `models/PlaylistState.kt`,
  `interfaces/PlaylistStatesDao.kt`, `helpers/PlaylistResumeHelper.kt`;
  `SongsDatabase.kt` version 17 → 18, `MIGRATION_17_18` creates
  `playlist_states`. Wired into `prepareAndPlay()` (playlist-id lookup),
  `TracksActivity` (playlist marker first, per-list marker as fallback),
  `playback/player/Player.kt` (sync from playback), `SettingsActivity`
  (toggle-off clears), `PlaylistsAdapter` (row text + delete hook) and
  `PlaylistsFragment` (row texts).
- `helpers/Config.kt`, `helpers/Constants.kt`: `rememberQueuePositions`
  preference (default on).
- `playback/player/Player.kt`: `updatePlaybackState()` also saves the
  per-queue state.
- `playback/PlaybackService.kt`: 10 s saver tick while playing.
- `activities/SimpleControllerActivity.kt`: `prepareAndPlay()` looks up
  the saved position off the main thread before preparing, and shows a
  "Resumed where you left off" toast on a real mid-track resume.
- `activities/SettingsActivity.kt` + `res/layout/activity_settings.xml` +
  `res/values/strings.xml`: the toggle and strings.
- `gradle.properties`: new applicationId / version. `res/values/donottranslate.xml`:
  launcher label.

## Build (GitHub Actions — the route that produced the delivered APK)

This fork was prepared in a sandbox where Gradle cannot run (see below).
The real APK is built by the manual-only workflow
`.github/workflows/build-resume-apk.yml` in the fork repo
(github.com/ismailghedamsi/Music-Player): Actions → "Build resume APK" →
Run workflow → main. It uses the runner's preinstalled Android SDK
(the `setup-android` action fails on the obsolete `tools` package) and
runs `./gradlew assembleFossRelease`, uploading the unsigned APK as an
artifact. Download it, zipalign-check it, sign it, and verify with
`apksigner verify`. Build history (2026-10-27): the first successful
run was 40593257878, after fixes for the pinned namespace (below), a
merged import line, the service-level `updatePlaybackState()` call, and
a `queueKey` JVM signature clash.

Two fork-specific build facts, learned the hard way:
- `namespace` stays `org.fossify.musicplayer` (pinned in
  `app/build.gradle.kts`). Only `APP_ID` changes the installed
  applicationId. Changing the namespace breaks every `R` / view-binding
  reference in the sources.
- The `gh` token needs the `workflow` scope to push any branch whose
  tree contains `.github/workflows/*`, even unchanged upstream files.

## Build (any machine with a working Gradle / Android SDK)

```bash
cd base
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleFossRelease --no-daemon
# sign with your own key, then zipalign; the release build is minified.
```

No new libraries were added. The Room migration and the resume lookup
deserve a first-run check on a real device: play an album, pause
mid-track, play another album, reopen the first, and confirm it resumes
at the saved position.

## Sandbox build note (honest)

This fork was prepared in a sandbox where Gradle cannot run: the sandbox
intercepts JVM loopback TCP, so the Gradle daemon never starts (the same
failure recorded for the Fossify Gallery mod). The Kotlin patch above was
first delivered as a separate Java build that implements the same
per-queue resume behaviour; that Java build is superseded. The APK
delivered since 2026-10-27 evening is compiled from this Kotlin source
by GitHub Actions as described above.
