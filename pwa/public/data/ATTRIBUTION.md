# Exercise catalog attribution

`exercises.csv` is a reviewed, normalized subset of English exercise metadata from the
[wger public API](https://wger.de/api/v2/). Each row retains its source record,
author, and Creative Commons Attribution-ShareAlike license URL.

The generator fetches the upstream catalog, selects an explicit allowlist of
common cardio, strength, and bodyweight movements, and rejects any missing or
invalid allowlist entry. The generated catalog contains names and structured classification metadata
only. It deliberately excludes exercise descriptions and remote media. Source
data is provided by wger contributors under the per-row CC-BY-SA license shown
in the CSV.

`minimum_level` and the progression fields are local editorial metadata for
app filtering and substitutions. They are not medical classifications and do
not imply that the same movement is equally appropriate for every person.

`quest-templates.csv` and `quest-workouts.csv` are original, evidence-informed
app templates. They use the exercise source IDs from `exercises.csv` and cite
the CDC adult activity guidance and the Physical Activity Guidelines Advisory
Committee report. The specific quest is not represented as a clinically
validated treatment program. It is intended for generally healthy adults and
includes a safety note in the stored template.

Regenerate from the repository root with:

```sh
npm run data:exercises
```

Review the resulting diff before committing. The app reads the checked-in CSV
locally and never scrapes or calls wger at runtime.
