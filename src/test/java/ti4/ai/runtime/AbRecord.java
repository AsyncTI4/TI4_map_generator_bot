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

// The A/B run catalog. Every mixed-brain A/B batch (-Dai.selfplay.ab=...) and every benchmark batch
// (-Dai.selfplay.record=true without A/B) writes one folder under docs/ai/experiments/runs/, and a row in
// docs/ai/experiments/INDEX.md, so the AI's results can be compared across commits. See docs/ai/experiments/README.md.
//
// A folder holds:
//   run.json          the code state (commit, branch, uncommitted files), the harness and every ai.* property, the
//                     experiments under test and those off by default, the machine, the timings and the estimates
//   games.csv         one row per seat per game: seed, pair, ending, round, faction, control or treatment, points, won
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
    private static final String CATALOG = "docs/ai/experiments";
    private static final String PENDING = "pending";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private final Path catalog;
    private final Path folder;
    private final String id;
    private final String harness;
    private final Instant started;
    private final Map<String, Object> code;
    private final Map<String, Object> setup;
    private final Optional<Set<Experiment>> experiments;
    private final Path console;
    private final PrintStream originalOut;
    private final OutputStream consoleFile;

    private AbRecord(Path catalog, String harness, int games, int threads) throws IOException {
        this.catalog = catalog;
        this.harness = harness;
        this.started = Instant.now();
        this.experiments = SelfPlayGame.AB;
        this.id = ID_TIME.format(started) + "-" + slug(label());
        this.folder = uniqueFolder(catalog.resolve("runs"), id);
        Files.createDirectories(folder);
        this.code = codeState(folder);
        this.setup = setup(games, threads);
        this.console = folder.resolve("console.log.gz");
        this.originalOut = System.out;
        this.consoleFile = new GZIPOutputStream(Files.newOutputStream(console));
        System.setOut(new PrintStream(new Tee(originalOut, consoleFile), true, StandardCharsets.UTF_8));
        writeJson("run.json", runJson(null, null, 0));
    }

    // Starts recording when the batch is an A/B batch (unless -Dai.selfplay.record=false) or a benchmark
    // (-Dai.selfplay.record=true). Recording problems never fail the batch.
    static Optional<AbRecord> start(String harness, int games, int threads) {
        String flag = System.getProperty(PROPERTY, "");
        boolean wanted = SelfPlayGame.AB.isPresent() ? !"false".equalsIgnoreCase(flag) : "true".equalsIgnoreCase(flag);
        if (!wanted) return Optional.empty();
        try {
            Path catalog =
                    Path.of(System.getProperty(PROPERTY + ".dir", CATALOG)).toAbsolutePath();
            return Optional.of(new AbRecord(catalog, harness, games, threads));
        } catch (IOException | RuntimeException e) {
            System.out.println("AB RECORD could not start: " + e);
            return Optional.empty();
        }
    }

    Path folder() {
        return folder;
    }

    void finish(List<SelfPlayGame.Outcome> outcomes, Predicate<SelfPlayGame.Ending> complete, long batchSeconds) {
        try {
            AbSummary.Estimate estimate = AbSummary.estimate(outcomes, complete);
            Map<String, Object> batch = batch(outcomes, batchSeconds);
            System.out.println("AB RECORD " + folder);
            restoreConsole();
            writeJson("run.json", runJson(estimate, batch, outcomes.size()));
            Files.writeString(folder.resolve("games.csv"), gamesCsv(outcomes, complete), StandardCharsets.UTF_8);
            Files.writeString(folder.resolve("README.md"), readme(estimate, batch), StandardCharsets.UTF_8);
            appendIndex(estimate, batch);
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

    private static String label() {
        String label = System.getProperty(PROPERTY + ".label", "");
        if (!label.isBlank()) return label;
        return SelfPlayGame.AB
                .map(set -> set.isEmpty()
                        ? "aa"
                        : set.size() == Experiment.values().length
                                ? "all"
                                : set.stream().map(Experiment::name).collect(Collectors.joining("+")))
                .orElse("benchmark");
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

    private Map<String, Object> setup(int games, int threads) {
        Map<String, Object> setup = new LinkedHashMap<>();
        setup.put("harness", harness);
        setup.put("games", games);
        setup.put("threads", threads);
        setup.put("baseSeed", SelfPlayGame.BASE_SEED);
        setup.put("seedFixed", System.getProperty("ai.selfplay.seed") != null);
        setup.put(
                "experimentsUnderTest",
                experiments
                        .map(set -> set.stream().map(Experiment::name).toList())
                        .orElse(null));
        setup.put(
                "experimentsKnown",
                Arrays.stream(Experiment.values()).map(Experiment::name).toList());
        setup.put(
                "experimentsOffByDefault",
                AiExperiments.offByDefault().stream().map(Experiment::name).toList());
        setup.put("experimentsOffEverywhere", System.getProperty(AiExperiments.OFF_PROPERTY, ""));
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

    private Map<String, Object> runJson(
            @Nullable AbSummary.Estimate estimate, @Nullable Map<String, Object> batch, int games) {
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("id", folder.getFileName().toString());
        run.put("kind", experiments.isPresent() ? (experiments.get().isEmpty() ? "aa" : "ab") : "benchmark");
        run.put("question", System.getProperty(PROPERTY + ".question", PENDING));
        run.put("started", started.toString());
        run.put("finished", estimate == null ? null : Instant.now().toString());
        run.put("status", estimate == null ? "running" : "finished");
        run.put("code", code);
        run.put("setup", setup);
        if (batch != null) run.put("batch", batch);
        if (estimate != null && experiments.isPresent()) run.put("estimate", estimateJson(estimate));
        run.put("gamesRecorded", games);
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

    private String readme(AbSummary.Estimate estimate, Map<String, Object> batch) {
        StringBuilder md = new StringBuilder();
        md.append("# ").append(folder.getFileName()).append("\n\n");
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
        md.append("- Harness: `").append(harness).append("`\n");
        md.append("- Games: ")
                .append(setup.get("games"))
                .append(" on ")
                .append(setup.get("threads"))
                .append(" threads, base seed ")
                .append(setup.get("baseSeed"))
                .append('\n');
        md.append("- Experiments under test: ")
                .append(experiments
                        .map(set -> set.isEmpty() ? "none (A/A)" : set.toString())
                        .orElse("none (benchmark)"))
                .append('\n');
        md.append("- Properties: `").append(setup.get("properties")).append("`\n\n");
        md.append("## Results\n\n");
        md.append("- Endings: ").append(batch.get("endings")).append('\n');
        md.append("- Average final round ")
                .append(batch.get("averageFinalRound"))
                .append(", leader points ")
                .append(batch.get("averageLeaderPoints"))
                .append(", table points ")
                .append(batch.get("averageTablePoints"))
                .append('\n');
        md.append("- Batch wall time ")
                .append(batch.get("batchWallSeconds"))
                .append(" s, ")
                .append(batch.get("wallMillisPerTick"))
                .append(" ms per tick\n");
        if (experiments.isPresent()) {
            md.append("- Treatment minus control points: ")
                    .append(AbSummary.meanAndError(estimate.pointDifference(), 0))
                    .append(" over ")
                    .append(estimate.pairs())
                    .append(" complete pairs\n");
            md.append("- Treatment win share: ")
                    .append(AbSummary.meanAndError(estimate.winShare(), 0.5))
                    .append(" (null 0.500)\n");
            md.append("- Unfinished games (their pairs excluded): ")
                    .append(estimate.unfinishedGames())
                    .append('\n');
        }
        md.append("\nPer-seat rows are in `games.csv`; the console output is in `console.log.gz`.\n");
        return md.toString();
    }

    private void appendIndex(AbSummary.Estimate estimate, Map<String, Object> batch) throws IOException {
        Path index = catalog.resolve("INDEX.md");
        if (!Files.exists(index)) Files.writeString(index, AbIndex.HEADER, StandardCharsets.UTF_8);
        String commit = String.valueOf(code.get("commit"));
        String row = AbIndex.row(
                "[" + folder.getFileName() + "](runs/" + folder.getFileName() + "/README.md)",
                started.toString().substring(0, 10),
                (commit.length() > 10 ? commit.substring(0, 10) : commit)
                        + (Boolean.TRUE.equals(code.get("dirty")) ? "+dirty" : ""),
                experiments
                        .map(set -> set.isEmpty()
                                ? "A/A"
                                : set.size() == Experiment.values().length ? "all" : set.toString())
                        .orElse("benchmark"),
                experiments.isPresent() ? String.valueOf(estimate.pairs()) : String.valueOf(batch.get("games")),
                experiments.isPresent() ? AbIndex.stat(estimate.pointDifference()) : "",
                experiments.isPresent() ? AbIndex.share(estimate.winShare()) : "",
                batch.get("averageFinalRound") + " / " + batch.get("averageLeaderPoints") + " / "
                        + batch.get("averageTablePoints"),
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
