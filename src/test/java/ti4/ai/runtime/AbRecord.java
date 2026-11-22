package ti4.ai.runtime;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import ti4.ai.AiExperiments;
import ti4.ai.AiExperiments.Experiment;
import tools.jackson.databind.ObjectMapper;

// The A/B run catalog. Every mixed-brain A/B batch (-Dai.selfplay.ab=...), every snapshot continuation A/B
// (ContinuationAbExperimentTest) and every benchmark batch (-Dai.selfplay.record=true without A/B) writes one folder
// under docs/ai/experiments/runs/, and a row in docs/ai/experiments/INDEX.md, so the AI's results can be compared
// across commits. See docs/ai/experiments/README.md.
//
// A folder holds:
//   run.json          the kind of run, the code state (commit, branch, uncommitted files), the harness and every ai.*
//                     property, the experiments under test and those off by default, the machine, the timings and the
//                     estimates
//   games.csv         whole-game runs: one row per seat per game (seed, pair, ending, round, faction, role, points,
// won)
//   pairs.csv         snapshot continuations: one row per continuation pair (seed, round, continuation, both arms)
//   uncommitted.patch the diff against the commit when the tree was dirty, so the exact code can be rebuilt
//   console.log.gz    everything the batch printed (console-full.log.gz, ignored by git, when it is too big to keep)
//   README.md         question, setup and results; the question and verdict are filled in by hand after the run
//
// Switches: -Dai.selfplay.record=false turns recording off for an A/B batch; -Dai.selfplay.record.label names the
// folder (default: the experiments under test, or "benchmark"); -Dai.selfplay.record.question sets the question;
// -Dai.selfplay.record.dir moves the catalog (default docs/ai/experiments under the working directory).
final class AbRecord {

    static final String PROPERTY = "ai.selfplay.record";
    static final long KEPT_CONSOLE_BYTES = 2_000_000L;
    static final String SNAPSHOT_CONTINUATION = "snapshot-continuation";
    private static final String CATALOG = "docs/ai/experiments";
    private static final String PENDING = "pending";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT).withZone(ZoneId.systemDefault());

    // What a harness tells the catalog about its run. kind is "ab", "aa", "benchmark", or SNAPSHOT_CONTINUATION
    // (with "-aa" when no experiment is under test); details are harness settings worth keeping beyond the ai.*
    // properties.
    record Spec(
            String kind,
            String harness,
            Optional<Set<Experiment>> experiments,
            long baseSeed,
            boolean seedFixed,
            int games,
            int threads,
            Map<String, Object> details) {}

    // What the finished batch hands back: estimates for run.json, its data file, result lines for the README and the
    // cells of the INDEX row.
    private record Results(
            @Nullable Map<String, Object> estimate,
            Map<String, Object> batch,
            String dataFile,
            String data,
            List<String> lines,
            String pairs,
            String points,
            String share,
            String endState) {}

    private final Path catalog;
    private final Path folder;
    private final Spec spec;
    private final Instant started;
    private final Map<String, Object> code;
    private final Map<String, Object> setup;
    private final Path console;
    private final PrintStream originalOut;
    private final OutputStream consoleFile;

    private AbRecord(Path catalog, Spec spec) throws IOException {
        this.catalog = catalog;
        this.spec = spec;
        this.started = Instant.now();
        this.folder = uniqueFolder(catalog.resolve("runs"), ID_TIME.format(started) + "-" + slug(label(spec)));
        Files.createDirectories(folder);
        this.code = codeState(folder);
        this.setup = setup(spec);
        this.console = folder.resolve("console.log.gz");
        this.originalOut = System.out;
        this.consoleFile = new GZIPOutputStream(Files.newOutputStream(console));
        System.setOut(new PrintStream(new Tee(originalOut, consoleFile), true, StandardCharsets.UTF_8));
        writeJson("run.json", runJson(null));
    }

    // Whole-game batches: records an A/B batch (unless -Dai.selfplay.record=false) or a benchmark
    // (-Dai.selfplay.record=true). Recording problems never fail the batch.
    static Optional<AbRecord> start(String harness, int games, int threads) {
        String flag = System.getProperty(PROPERTY, "");
        boolean wanted = SelfPlayGame.AB.isPresent() ? !"false".equalsIgnoreCase(flag) : "true".equalsIgnoreCase(flag);
        if (!wanted) return Optional.empty();
        String kind = SelfPlayGame.AB.map(set -> set.isEmpty() ? "aa" : "ab").orElse("benchmark");
        return open(new Spec(
                kind,
                harness,
                SelfPlayGame.AB,
                SelfPlayGame.BASE_SEED,
                System.getProperty("ai.selfplay.seed") != null,
                games,
                threads,
                Map.of()));
    }

    // Snapshot continuation A/B runs are always recorded, unless -Dai.selfplay.record=false.
    static Optional<AbRecord> startContinuation(
            String harness, Set<Experiment> flags, long baseSeed, int games, int threads, Map<String, Object> details) {
        if ("false".equalsIgnoreCase(System.getProperty(PROPERTY, ""))) return Optional.empty();
        return open(new Spec(
                flags.isEmpty() ? SNAPSHOT_CONTINUATION + "-aa" : SNAPSHOT_CONTINUATION,
                harness,
                Optional.of(flags),
                baseSeed,
                true,
                games,
                threads,
                details));
    }

    private static Optional<AbRecord> open(Spec spec) {
        try {
            Path catalog =
                    Path.of(System.getProperty(PROPERTY + ".dir", CATALOG)).toAbsolutePath();
            return Optional.of(new AbRecord(catalog, spec));
        } catch (IOException | RuntimeException e) {
            System.out.println("AB RECORD could not start: " + e);
            return Optional.empty();
        }
    }

    Path folder() {
        return folder;
    }

    void finish(List<SelfPlayGame.Outcome> outcomes, Predicate<SelfPlayGame.Ending> complete, long batchSeconds) {
        complete(() -> {
            AbSummary.Estimate estimate = AbSummary.estimate(outcomes, complete);
            Map<String, Object> batch = batch(outcomes, batchSeconds);
            boolean ab = spec.experiments().isPresent();
            List<String> lines = new ArrayList<>(List.of(
                    "Endings: " + batch.get("endings"),
                    "Average final round " + batch.get("averageFinalRound") + ", leader points "
                            + batch.get("averageLeaderPoints") + ", table points " + batch.get("averageTablePoints"),
                    "Batch wall time " + batch.get("batchWallSeconds") + " s, " + batch.get("wallMillisPerTick")
                            + " ms per tick"));
            if (ab) {
                lines.add("Treatment minus control points: " + AbSummary.meanAndError(estimate.pointDifference(), 0)
                        + " over " + estimate.pairs() + " complete pairs");
                lines.add("Treatment win share: " + AbSummary.meanAndError(estimate.winShare(), 0.5) + " (null 0.500)");
                lines.add("Unfinished games (their pairs excluded): " + estimate.unfinishedGames());
            }
            return new Results(
                    ab ? estimateJson(estimate) : null,
                    batch,
                    "games.csv",
                    gamesCsv(outcomes, complete),
                    lines,
                    ab ? String.valueOf(estimate.pairs()) : String.valueOf(batch.get("games")),
                    ab ? AbIndex.stat(estimate.pointDifference()) : "",
                    ab ? AbIndex.share(estimate.winShare()) : "",
                    batch.get("averageFinalRound") + " / " + batch.get("averageLeaderPoints") + " / "
                            + batch.get("averageTablePoints"));
        });
    }

    // continuationEnds holds the averages over all arms of where the continuations stopped: round, leader and table
    // points; armEndings counts how each arm ended.
    void finishContinuation(
            List<ContinuationAb.Pair> pairs,
            int failedGames,
            Map<String, Integer> armEndings,
            double endRound,
            double endLeader,
            double endTable,
            long averageGameWallSeconds) {
        complete(() -> {
            ContinuationAb.Estimate estimate = ContinuationAb.estimate(pairs);
            Map<String, Object> batch = new LinkedHashMap<>();
            batch.put("pairs", estimate.pairs());
            batch.put("snapshots", estimate.snapshots());
            batch.put("games", estimate.games());
            batch.put("failedGames", failedGames);
            batch.put("armEndings", new TreeMap<>(armEndings));
            batch.put("averageEndRound", round(endRound));
            batch.put("averageEndLeaderPoints", round(endLeader));
            batch.put("averageEndTablePoints", round(endTable));
            batch.put("averageGameWallSeconds", averageGameWallSeconds);
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("pairs", estimate.pairs());
            json.put("untouchedPairs", estimate.untouched());
            json.put("gainDifferenceOverSnapshots", statJson(estimate.overSnapshots(), 0));
            json.put("gainDifferenceOverGames", statJson(estimate.overGames(), 0));
            List<String> lines = List.of(
                    "Snapshot continuation: " + estimate.pairs() + " pairs from " + estimate.snapshots()
                            + " snapshots of " + estimate.games() + " games (" + failedGames + " games failed); "
                            + estimate.untouched() + " pairs untouched",
                    "Treatment minus control VP gain over games (the safer error): "
                            + AbSummary.meanAndError(estimate.overGames(), 0),
                    "Treatment minus control VP gain over snapshots: "
                            + AbSummary.meanAndError(estimate.overSnapshots(), 0),
                    "Arm endings: " + new TreeMap<>(armEndings),
                    "Continuations end on average in round " + round(endRound) + ", leader " + round(endLeader)
                            + " VP, table " + round(endTable) + " VP; " + averageGameWallSeconds + " s per game");
            return new Results(
                    json,
                    batch,
                    "pairs.csv",
                    pairsCsv(pairs),
                    lines,
                    estimate.pairs() + " pairs, " + estimate.games() + " games",
                    AbIndex.stat(estimate.overGames()),
                    "",
                    round(endRound) + " / " + round(endLeader) + " / " + round(endTable) + " (at horizon)");
        });
    }

    private void complete(java.util.function.Supplier<Results> collect) {
        try {
            Results results = collect.get();
            System.out.println("AB RECORD " + folder);
            restoreConsole();
            writeJson("run.json", runJson(results));
            Files.writeString(folder.resolve(results.dataFile()), results.data(), StandardCharsets.UTF_8);
            Files.writeString(folder.resolve("README.md"), readme(results), StandardCharsets.UTF_8);
            appendIndex(results);
        } catch (IOException | RuntimeException e) {
            restoreConsole();
            System.out.println("AB RECORD could not finish " + folder + ": " + e);
        }
    }

    private void restoreConsole() {
        if (System.out == originalOut) return;
        System.out.flush();
        System.setOut(originalOut);
        try {
            consoleFile.close();
            if (Files.size(console) > KEPT_CONSOLE_BYTES) Files.move(console, folder.resolve("console-full.log.gz"));
        } catch (IOException e) {
            System.out.println("AB RECORD console log: " + e);
        }
    }

    private static String label(Spec spec) {
        String label = System.getProperty(PROPERTY + ".label", "");
        if (!label.isBlank()) return label;
        String tested = spec.experiments()
                .map(set -> set.isEmpty()
                        ? "aa"
                        : set.size() == Experiment.values().length
                                ? "all"
                                : set.stream().map(Experiment::name).collect(Collectors.joining("+")))
                .orElse("benchmark");
        return spec.kind().startsWith(SNAPSHOT_CONTINUATION) ? "continuation-" + tested : tested;
    }

    static String slug(String label) {
        String slug =
                label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+]+", "-").replaceAll("(^-|-$)", "");
        return slug.length() > 60 ? slug.substring(0, 60) : slug;
    }

    private static Path uniqueFolder(Path runs, String id) {
        Path folder = runs.resolve(id);
        for (int n = 2; Files.exists(folder); n++) folder = runs.resolve(id + "-" + n);
        return folder;
    }

    // The code the batch ran: the commit and the uncommitted changes, with the catalog itself left out so earlier
    // records never make a frozen worktree look dirty.
    private static Map<String, Object> codeState(Path folder) throws IOException {
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("commit", git("rev-parse", "HEAD"));
        code.put("commitSubject", git("log", "-1", "--format=%s"));
        code.put("commitDate", git("log", "-1", "--format=%cI"));
        code.put("branch", git("rev-parse", "--abbrev-ref", "HEAD"));
        code.put("worktree", Path.of("").toAbsolutePath().toString());
        String status = git("status", "--porcelain", "--", ".", ":(exclude)" + CATALOG);
        List<String> dirty = status.isBlank() ? List.of() : Arrays.asList(status.split("\\R"));
        code.put("dirty", !dirty.isEmpty());
        code.put("uncommittedFiles", dirty);
        if (!dirty.isEmpty()) {
            String patch = git("diff", "HEAD", "--", ".", ":(exclude)" + CATALOG);
            Files.writeString(folder.resolve("uncommitted.patch"), patch, StandardCharsets.UTF_8);
            code.put("uncommittedPatch", "uncommitted.patch");
        }
        return code;
    }

    private static String git(String... args) {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        try {
            Process process =
                    new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return output.strip();
        } catch (IOException e) {
            return "unknown (" + e.getMessage() + ")";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "unknown (interrupted)";
        }
    }

    private static Map<String, Object> setup(Spec spec) {
        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("harness", spec.harness());
        setup.put("games", spec.games());
        setup.put("threads", spec.threads());
        setup.put("baseSeed", spec.baseSeed());
        setup.put("seedFixed", spec.seedFixed());
        setup.put(
                "experimentsUnderTest",
                spec.experiments()
                        .map(set -> set.stream().map(Experiment::name).toList())
                        .orElse(null));
        setup.put(
                "experimentsKnown",
                Arrays.stream(Experiment.values()).map(Experiment::name).toList());
        setup.put(
                "experimentsOffByDefault",
                AiExperiments.offByDefault().stream().map(Experiment::name).toList());
        setup.put("experimentsOffEverywhere", System.getProperty(AiExperiments.OFF_PROPERTY, ""));
        if (!spec.details().isEmpty()) setup.put("details", spec.details());
        Map<String, String> properties = new TreeMap<>();
        System.getProperties().forEach((key, value) -> {
            if (key.toString().startsWith("ai.")) properties.put(key.toString(), value.toString());
        });
        setup.put("properties", properties);
        Map<String, Object> machine = new LinkedHashMap<>();
        machine.put("processors", Runtime.getRuntime().availableProcessors());
        machine.put("maxHeapMb", Runtime.getRuntime().maxMemory() / (1024 * 1024));
        machine.put("java", System.getProperty("java.version"));
        machine.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        machine.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
        setup.put("machine", machine);
        return setup;
    }

    private static Map<String, Object> batch(List<SelfPlayGame.Outcome> outcomes, long batchSeconds) {
        Map<String, Object> batch = new LinkedHashMap<>();
        int games = Math.max(1, outcomes.size());
        batch.put("games", outcomes.size());
        batch.put("batchWallSeconds", batchSeconds);
        batch.put(
                "endings",
                outcomes.stream()
                        .collect(Collectors.groupingBy(
                                outcome -> outcome.ending().name(), TreeMap::new, Collectors.counting())));
        batch.put(
                "averageFinalRound",
                round(outcomes.stream().mapToInt(SelfPlayGame.Outcome::round).sum() / (double) games));
        batch.put(
                "averageLeaderPoints",
                round(outcomes.stream()
                                .mapToInt(SelfPlayGame.Outcome::leaderVictoryPoints)
                                .sum()
                        / (double) games));
        batch.put(
                "averageTablePoints",
                round(outcomes.stream()
                                .mapToInt(SelfPlayGame.Outcome::totalVictoryPoints)
                                .sum()
                        / (double) games));
        long ticks = outcomes.stream().mapToLong(SelfPlayGame.Outcome::ticks).sum();
        long wall =
                outcomes.stream().mapToLong(SelfPlayGame.Outcome::wallSeconds).sum();
        batch.put("averageGameWallSeconds", round(wall / (double) games));
        batch.put("wallMillisPerTick", ticks == 0 ? null : round(wall * 1000.0 / ticks));
        return batch;
    }

    private Map<String, Object> runJson(@Nullable Results results) {
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("id", folder.getFileName().toString());
        run.put("kind", spec.kind());
        run.put("question", System.getProperty(PROPERTY + ".question", PENDING));
        run.put("started", started.toString());
        run.put("finished", results == null ? null : Instant.now().toString());
        run.put("status", results == null ? "running" : "finished");
        run.put("code", code);
        run.put("setup", setup);
        if (results != null) {
            run.put("batch", results.batch());
            if (results.estimate() != null) run.put("estimate", results.estimate());
            run.put("data", results.dataFile());
        }
        run.put("verdict", PENDING);
        return run;
    }

    private static Map<String, Object> estimateJson(AbSummary.Estimate estimate) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("finishedGames", estimate.finishedGames());
        json.put("completePairs", estimate.pairs());
        json.put("unfinishedGames", estimate.unfinishedGames());
        json.put("pointDifference", statJson(estimate.pointDifference(), 0));
        json.put("treatmentWinShare", statJson(estimate.winShare(), 0.5));
        json.put("treatmentAveragePoints", round(estimate.treatmentPoints()));
        json.put("controlAveragePoints", round(estimate.controlPoints()));
        return json;
    }

    private static Map<String, Object> statJson(AbSummary.Stat stat, double nullValue) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("mean", finite(stat.mean()));
        json.put("standardError", finite(stat.error()));
        json.put("t", stat.error() > 0 ? finite((stat.mean() - nullValue) / stat.error()) : null);
        json.put("n", stat.n());
        json.put("null", nullValue);
        return json;
    }

    static String gamesCsv(List<SelfPlayGame.Outcome> outcomes, Predicate<SelfPlayGame.Ending> complete) {
        StringBuilder csv =
                new StringBuilder("slot,seed,pair,ending,complete,round,ticks,wall_seconds,faction,role,points,won\n");
        for (SelfPlayGame.Outcome outcome : outcomes) {
            int best = outcome.victoryPoints().values().stream()
                    .mapToInt(Integer::intValue)
                    .max()
                    .orElse(0);
            Set<String> control = outcome.controlSeats() == null ? Set.of() : outcome.controlSeats();
            for (Map.Entry<String, Integer> seat : outcome.victoryPoints().entrySet()) {
                String role =
                        SelfPlayGame.AB.isEmpty() ? "none" : control.contains(seat.getKey()) ? "control" : "treatment";
                csv.append(String.join(
                                ",",
                                String.valueOf(outcome.slot()),
                                String.valueOf(SelfPlayGame.seedFor(outcome.slot())),
                                String.valueOf(SelfPlayGame.pairSlot(outcome.slot())),
                                outcome.ending().name(),
                                String.valueOf(complete.test(outcome.ending())),
                                String.valueOf(outcome.round()),
                                String.valueOf(outcome.ticks()),
                                String.valueOf(outcome.wallSeconds()),
                                seat.getKey(),
                                role,
                                String.valueOf(seat.getValue()),
                                String.valueOf(seat.getValue() == best)))
                        .append('\n');
            }
        }
        return csv.toString();
    }

    // Arm A treats treated_a; arm B treats the other seats. gains_a and gains_b are faction:VP gain over the horizon.
    static String pairsCsv(List<ContinuationAb.Pair> pairs) {
        StringBuilder csv = new StringBuilder(
                "seed,round,continuation,difference_a,difference_b,value,untouched,treated_a,gains_a,gains_b\n");
        for (ContinuationAb.Pair pair : pairs) {
            csv.append(String.join(
                            ",",
                            String.valueOf(pair.seed()),
                            String.valueOf(pair.round()),
                            String.valueOf(pair.continuation()),
                            String.format(Locale.ROOT, "%.3f", pair.a().difference()),
                            String.format(Locale.ROOT, "%.3f", pair.b().difference()),
                            String.format(Locale.ROOT, "%.3f", pair.value()),
                            String.valueOf(pair.untouched()),
                            String.join(" ", pair.a().treated()),
                            gains(pair.a().gains()),
                            gains(pair.b().gains())))
                    .append('\n');
        }
        return csv.toString();
    }

    private static String gains(Map<String, Integer> gains) {
        return gains.entrySet().stream()
                .map(seat -> seat.getKey() + ":" + seat.getValue())
                .collect(Collectors.joining(" "));
    }

    private String tested() {
        String flags = spec.experiments()
                .map(set -> set.isEmpty() ? "A/A" : set.size() == Experiment.values().length ? "all" : set.toString())
                .orElse("benchmark");
        return spec.kind().startsWith(SNAPSHOT_CONTINUATION) ? "snapshot continuation: " + flags : flags;
    }

    private String readme(Results results) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(folder.getFileName()).append("\n\n");
        md.append("**Kind:** ").append(spec.kind()).append("\n\n");
        md.append("**Question:** ")
                .append(System.getProperty(PROPERTY + ".question", "_fill in: what was tested and why_"))
                .append("\n\n");
        md.append("**Verdict:** _fill in after reading the results: kept, reverted, inconclusive, follow-ups_\n\n");
        md.append("## Code\n\n");
        md.append("- Commit `")
                .append(code.get("commit"))
                .append("` on `")
                .append(code.get("branch"))
                .append("`: ");
        md.append(code.get("commitSubject")).append('\n');
        md.append("- Uncommitted changes: ")
                .append(Boolean.TRUE.equals(code.get("dirty")) ? "yes, see `uncommitted.patch`" : "none")
                .append("\n\n");
        md.append("## Setup\n\n");
        md.append("- Harness: `").append(spec.harness()).append("`\n");
        md.append("- Games: ")
                .append(spec.games())
                .append(" on ")
                .append(spec.threads())
                .append(" threads, base seed ")
                .append(spec.baseSeed())
                .append('\n');
        md.append("- Tested: ").append(tested()).append('\n');
        if (!spec.details().isEmpty())
            md.append("- Details: `").append(spec.details()).append("`\n");
        md.append("- Properties: `").append(setup.get("properties")).append("`\n\n");
        md.append("## Results\n\n");
        for (String line : results.lines()) md.append("- ").append(line).append('\n');
        md.append("\nThe rows are in `")
                .append(results.dataFile())
                .append("`; the console output is in `console.log.gz`.\n");
        return md.toString();
    }

    private void appendIndex(Results results) throws IOException {
        Path index = catalog.resolve("INDEX.md");
        if (!Files.exists(index)) Files.writeString(index, AbIndex.HEADER, StandardCharsets.UTF_8);
        String commit = String.valueOf(code.get("commit"));
        String row = AbIndex.row(
                "[" + folder.getFileName() + "](runs/" + folder.getFileName() + "/README.md)",
                started.toString().substring(0, 10),
                (commit.length() > 10 ? commit.substring(0, 10) : commit)
                        + (Boolean.TRUE.equals(code.get("dirty")) ? "+dirty" : ""),
                tested(),
                results.pairs(),
                results.points(),
                results.share(),
                results.endState(),
                PENDING);
        Files.writeString(index, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    }

    private void writeJson(String name, Object value) {
        Path path = folder.resolve(name);
        try {
            Files.writeString(
                    path, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }

    @Nullable
    private static Double finite(double value) {
        return Double.isFinite(value) ? round(value) : null;
    }

    // Writes to the real console and the run's log at once.
    private static final class Tee extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        Tee(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
            first.write(bytes, offset, length);
            second.write(bytes, offset, length);
        }

        @Override
        public synchronized void flush() throws IOException {
            first.flush();
            second.flush();
        }
    }
}
