# AI experiment catalog

Every A/B and benchmark run of the AI gets a folder under `runs/` and a row in [INDEX.md](INDEX.md). The aim is
to see, over months, whether the AI is getting stronger or weaker, and which changes did it.

## What each kind of run can tell us

| Kind | How it runs | What it measures | What it cannot measure |
| --- | --- | --- | --- |
| **A/B** (`-Dai.selfplay.ab=X,...`) | Half the seats play with experiment X off, half with everything on. The paired game swaps the halves on the same seed. | The marginal value of X, at that commit, against the rest of the AI at that commit. | Growth over time: the control seats are "today's AI minus X", not an older AI. |
| **Snapshot continuation** (`ContinuationAbExperimentTest`, kind `snapshot-continuation`) | Games are snapshotted at chosen rounds. From each snapshot, two arms play a short horizon on the same seed, each treating a different half of the seats. | The same marginal value of X as an A/B, but on VP gained over the horizon. It is cheaper, and pairs the experiments never touched score exactly 0. | Whole-game effects past the horizon. Its rows are not comparable with whole-game A/B rows. |
| **A/A** (`-Dai.selfplay.ab=none`) | Same pairing, no difference between the halves. | The noise of the paired estimate: how big an effect has to be before an A/B can see it. | Anything about strength. |
| **Benchmark** (`-Dai.selfplay.record=true`, no A/B) | All seats run the same code on a fixed protocol. | Health over time: games that finish, stalls, final round, table points, speed. | Strength. When every seat plays the same AI, a better AI does not score more; it only changes the tempo of the table. |
| **Ladder** (not built yet) | Some seats run the current build, some a frozen older build. | Real growth or decline: the current AI head to head against an anchor. | — |

So:

- A/B rows tell us **which changes help**. Read them per row and never add them up: experiments interact, flags are
  removed once a change is settled, and a lot of work lands without a flag.
- Benchmark rows tell us **whether the AI still plays whole games well and fast**. Compare a row only with rows on
  the same protocol (the same harness, seed, game count and factions).
- Neither one is a strength curve. That needs the ladder: the current AI against a frozen anchor build in the same
  game. It is the planned next step; see [Open work](#open-work).

## What a run folder holds

`AbRecord` (`src/test/java/ti4/ai/runtime/AbRecord.java`) writes it at the end of every A/B batch of
`AiSelfPlayTest#sixAiSeatsPlayFullGames` or `RoundTwoSelfPlayTest#playTwoRounds`, of every snapshot continuation
run of `ContinuationAbExperimentTest`, and of every batch run with `-Dai.selfplay.record=true`. In INDEX.md a snapshot
continuation shows as "snapshot continuation: …". Its points column is the VP gain difference with the error over
games, and its last column is where the continuations stopped.

| File | Contents |
| --- | --- |
| `run.json` | Code state: commit, branch, worktree, uncommitted files. Setup: harness, games, threads, base seed, experiments under test, experiments known and off by default, every `ai.*` property, machine and JVM. Batch: endings, average final round, leader and table points, wall time and ms per tick. A/B estimate: point difference and win share with standard error, t and n, plus unfinished games. Also the question and verdict. |
| `pairs.csv` | Snapshot continuations only, one row per pair: seed, snapshot round, continuation, each arm's difference, the pair value, whether the pair was untouched, the seats arm A treated, and each arm's VP gains. |
| `games.csv` | Whole-game runs only, one row per seat per game: slot, seed, pair, ending, whether it counts, round, ticks, wall seconds, faction, role (control, treatment or none), points and won. |
| `uncommitted.patch` | Only when the tree was dirty: the diff against the commit, so the exact code can be rebuilt. The catalog itself is left out. |
| `console.log.gz` | Everything the batch printed, including the agenda A/B lines and every tracker summary. Above 2 MB it is saved as `console-full.log.gz`, which git ignores, so it stays only on the machine that ran it. |
| `README.md` | Question, code, setup and results. The question and verdict are filled in by hand. |

Switches:
- `-Dai.selfplay.record.label=...` names the folder. The default is the experiments under test, or `benchmark`.
- `-Dai.selfplay.record.question="..."` writes the question for you.
- `-Dai.selfplay.record=false` skips recording.
- `-Dai.selfplay.record.dir=...` moves the catalog.

## Running an A/B so the record means something

1. **Freeze the code.**
   - Run from a worktree nothing else builds in, and never recompile under a running batch.
   - Commit first. A `+dirty` commit in the index means the patch is the only way to rebuild that code.
2. **Ask first.** Get the user's yes for the run, as for every long self-play run.
3. **Decide the bar in advance.** Write it in the question, e.g. "keep if points ≥ 0 and no harm in win share".
   - A/A noise is about ±0.26 VP at 12 pairs, so 12 pairs only see big effects. About 35 pairs are needed for 0.3 VP.
4. **Fix the seed.** Pass `-Dai.selfplay.seed`, so a later run can repeat the same games. Confirm a surprising result
   on a second seed.
5. **After the run:**
   - Fill in the question and verdict in the run's README.md and its INDEX.md row.
   - Commit the folder with the code it measured.
   - Name the run in the commit that keeps, changes or removes the experiment.

## Benchmark protocol

Run it after each milestone, with the same command every time, so the rows line up:

```
mvn -o test -Dtest=AiSelfPlayTest#sixAiSeatsPlayFullGames -Dai.selfplay.full=true -Dai.selfplay.games=24 -Dai.selfplay.threads=10 -Dai.selfplay.seed=9000 -Dai.selfplay.record=true -Dai.selfplay.record.label=benchmark-full-24
```

Watch:
- unfinished games (stalls, loops, limits), which should be 0;
- the average final round, leader points and table points, which should be stable;
- ms per tick, where lower is better.

A jump in these is a regression to explain, not a strength result.

## Open work

- **Ladder.** Run the current AI against a frozen anchor build in the same games.
  - Each anchor is an archived build of `ti4.ai`, loaded through its own class loader for its seats.
  - Paired seeds work as in A/B, and each new anchor is rated against the previous one.
  - This is the only design here that gives a strength curve.
- **Games against humans.** Record AI results in human games as the outside check on the curve.
- **Trend view.** Plot the benchmark rows and the A/B rows by date, read from each `run.json`, once there are enough
  rows.
