# Adhan recordings

- `fajr/` — dawn adhan (tathweeb). Catalogue entries have `"fajr": true`.
- `regular/` — well-known reciters, plus `short_takbir.mp3` (opening takbir only).

Hosted URLs:

- `https://salati.sulfuro.xyz/audio/fajr/{id}.mp3`
- `https://salati.sulfuro.xyz/audio/regular/{id}.mp3`

## Changing a recording

An id is the filename on the user's device, and the app verifies the digest only while
downloading — after that a file is played on the strength of its name alone. So replacing
the audio behind an existing id can never reach anyone who already has it.

**Give changed audio a new id, and add the old one to `RETIRED_IDS`** in
`AdhanAudioStore.kt`. Retiring is also what withdraws a recording: it deletes the file and
clears the setting, so an id dropped from the catalogue without being retired keeps playing
and leaves the picker with nothing selected.

`AdhanManifestIntegrityTest` checks the manifest against these files — sizes, digests,
durations, urls, the size cap, and that neither side has entries the other lacks. Rebuild
the manifest rather than hand-editing it.
