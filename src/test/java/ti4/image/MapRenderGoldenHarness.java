package ti4.image;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.RenderRandom;
import ti4.helpers.Storage;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.user.AFKService;
import ti4.testUtils.BaseTi4Test;
import ti4.website.model.WebsiteOverlay;

/**
 * Golden-image harness for the map renderer. It is gated on the system property golden.saves so CI never runs it.
 *
 * <pre>
 * mvn -o test -Dtest=MapRenderGoldenHarness -Dgolden.saves=DIR -Dgolden.out=DIR -Dgolden.mode=baseline|compare
 *     [-Dgolden.baseline=DIR] [-Dgolden.only=game1,game2] [-Dgolden.ids=REGEX] [-Dgolden.eager=game1,game2]
 *     [-Dgolden.pngs=REGEX over game/renderId] [-DargLine=-Xmx10g]
 * </pre>
 *
 * Every render reloads the save from disk, because rendering mutates the game (fog filtering, tile distances, debt
 * pool icons). Rendering runs on the test thread through MapRenderer.resolve and MapRenderer.paint (the canvas without encoding), so static
 * mocks and the global RenderRandom mode apply. Random choices are pinned by RenderRandom.neutral() (or eager() for
 * the easter egg subset); the wall clock and AFK status are pinned with static mocks that keep
 * every other method real (CALLS_REAL_METHODS) so only the named method is replaced, and every network image fetch answers 404 (FakeImageNetwork), so custom
 * faction emoji and avatars are missing exactly as when the fetch fails. No pixel is masked.
 *
 * Per render the harness records the canvas size, a SHA-256 of the raster, CRC-like hashes per 128px block (to
 * localize a mismatch without storing pictures), the website overlays as order-sensitive JSON, the RenderRandom call
 * counts and per-phase timings. A PNG is written only on a mismatch (and in baseline mode when golden.pngs matches),
 * together with a diff picture that paints differing pixels red when the baseline PNG exists.
 */
@EnabledIfSystemProperty(named = "golden.saves", matches = ".+")
class MapRenderGoldenHarness extends BaseTi4Test {

    private static final int BLOCK = 128;
    private static final List<DisplayType> SEGMENT_DISPLAY_TYPES = List.of(DisplayType.all, DisplayType.map);
    private static final int BOARD_VARIANT_GAMES = 2;
    private static final int MIN_PLAYERS_FOR_DISTANCES = 4;

    private enum Mutation {
        NONE,
        SEGMENTS,
        BOARDS,
        HIDE_FLIP,
        DISTANCES
    }

    private record Spec(
            String id,
            DisplayType displayType,
            boolean privateView,
            String segment,
            Mutation mutation,
            boolean eager) {}

    private record RenderOutcome(
            String id,
            String error,
            int width,
            int height,
            String sha,
            String blocks,
            String overlays,
            String random,
            long constructorMs,
            long drawMs,
            long hashMs) {}

    private record RenderRequest(
            Game game, DisplayType displayType, GenericInteractionCreateEvent event, String segment) {}

    private record RenderedCanvas(
            BufferedImage canvas, List<WebsiteOverlay> overlays, long constructorNanos, long drawNanos)
            implements AutoCloseable {
        @Override
        public void close() {}
    }

    private static RenderedCanvas renderCanvas(RenderRequest request) {
        long start = System.nanoTime();
        RenderContext context = MapRenderer.resolve(
                new MapRenderRequest(request.game(), request.displayType(), request.event(), request.segment()));
        long constructed = System.nanoTime();
        BufferedImage canvas = MapRenderer.paint(context, RenderTimings.disabled());
        long drawn = System.nanoTime();
        return new RenderedCanvas(canvas, context.overlays().asList(), constructed - start, drawn - constructed);
    }

    @Test
    void renderCorpus() throws Exception {
        Path savesDir = Path.of(System.getProperty("golden.saves"));
        Path outDir = Path.of(requiredProperty("golden.out"));
        String mode = System.getProperty("golden.mode", "baseline");
        Assertions.assertTrue("baseline".equals(mode) || "compare".equals(mode), "golden.mode");
        boolean compare = "compare".equals(mode);
        Path baselineDir = Path.of(
                System.getProperty("golden.baseline", outDir.resolve("baseline").toString()));
        Path resultDir = outDir.resolve(mode);
        Files.createDirectories(resultDir);
        assertNoUserSettings();

        Set<String> only = csv(System.getProperty("golden.only"));
        Pattern idFilter = Pattern.compile(System.getProperty("golden.ids", ".*"));
        Pattern pngFilter = Pattern.compile(System.getProperty("golden.pngs", "(?!)"));
        List<Path> saves = listSaves(savesDir, only);
        Set<String> eagerGames = eagerGames(saves);

        StringBuilder summary = new StringBuilder();
        Totals totals = new Totals();
        boolean distancesPlanned = false;
        int fogGamesSeen = 0;

        try (MockedStatic<DateTimeHelper> dateTime = mockStatic(DateTimeHelper.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<AFKService> afk = mockStatic(AFKService.class, Mockito.CALLS_REAL_METHODS);
                FakeImageNetwork network = FakeImageNetwork.notFound()) {
            dateTime.when(DateTimeHelper::getFormattedTimestamp).thenReturn("2000.01.01 - 00.00.00");
            afk.when(() -> AFKService.userIsAFK(anyString())).thenReturn(false);

            for (Path save : saves) {
                String gameName = gameNameOf(save);
                Game probe = loadFresh(save);
                if (probe == null) {
                    summary.append(gameName).append(" LOAD FAILED\n");
                    totals.failures++;
                    continue;
                }
                boolean fog = probe.isFowMode();
                boolean eager = eagerGames.contains(gameName);
                boolean boards = fog && fogGamesSeen < BOARD_VARIANT_GAMES;
                boolean distances =
                        !fog && !distancesPlanned && probe.getRealPlayers().size() >= MIN_PLAYERS_FOR_DISTANCES;
                if (fog) fogGamesSeen++;
                if (distances) distancesPlanned = true;

                List<Spec> specs = plan(save, probe, fog, eager, boards, distances).stream()
                        .filter(spec -> idFilter.matcher(spec.id()).matches())
                        .toList();
                List<RenderOutcome> records = new ArrayList<>();
                Map<String, RenderOutcome> baseline =
                        compare ? readRecords(baselineDir.resolve(gameName + ".txt")) : Map.of();

                for (Spec spec : specs) {
                    RenderOutcome record = render(save, spec);
                    records.add(record);
                    totals.renders++;
                    totals.constructorMs += record.constructorMs();
                    totals.drawMs += record.drawMs();
                    if (compare) {
                        String verdict = verdict(baseline.get(spec.id()), record);
                        if (!"IDENTICAL".equals(verdict)) {
                            totals.mismatches++;
                            summary.append(gameName)
                                    .append('/')
                                    .append(spec.id())
                                    .append(' ')
                                    .append(verdict)
                                    .append('\n');
                            reportMismatch(
                                    save, spec, baselineDir, resultDir, baseline.get(spec.id()), record, summary);
                        } else {
                            totals.identical++;
                        }
                    }
                    if (pngFilter.matcher(gameName + "/" + spec.id()).matches() && record.error() == null) {
                        writePng(
                                save,
                                spec,
                                resultDir.resolve("png").resolve(gameName).resolve(spec.id() + ".png"));
                    }
                }
                writeRecords(resultDir.resolve(gameName + ".txt"), records);
                summary.append(String.format(
                        "%s renders=%d constructorMs=%d drawMs=%d%n",
                        gameName,
                        records.size(),
                        records.stream().mapToLong(RenderOutcome::constructorMs).sum(),
                        records.stream().mapToLong(RenderOutcome::drawMs).sum()));
                System.out.println("[golden] " + gameName + " renders=" + records.size());
            }
        }

        summary.append(String.format(
                "TOTAL mode=%s renders=%d identical=%d mismatches=%d loadFailures=%d constructorMs=%d drawMs=%d%n",
                mode,
                totals.renders,
                totals.identical,
                totals.mismatches,
                totals.failures,
                totals.constructorMs,
                totals.drawMs));
        Files.writeString(resultDir.resolve("summary.txt"), summary.toString());
        System.out.println(summary);
        if (compare) {
            Assertions.assertEquals(0, totals.mismatches, "renders differing from the baseline, see summary.txt");
        }
        Assertions.assertEquals(0, totals.failures, "saves that failed to load");
    }

    private static final class Totals {
        int renders;
        int identical;
        int mismatches;
        int failures;
        long constructorMs;
        long drawMs;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        Assertions.assertNotNull(value, "missing -D" + name);
        return value;
    }

    private static Set<String> csv(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Set.of(value.split(","));
    }

    private static void assertNoUserSettings() throws IOException {
        Path userSettings = Path.of(Storage.getStoragePath(), "user_settings");
        if (!Files.exists(userSettings)) return;
        try (Stream<Path> files = Files.list(userSettings)) {
            Assertions.assertEquals(
                    0,
                    files.count(),
                    "user settings feed AFK and decal rendering and would make renders machine dependent");
        }
    }

    private static List<Path> listSaves(Path dir, Set<String> only) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .filter(path -> only.isEmpty() || only.contains(gameNameOf(path)))
                    .sorted()
                    .toList();
        }
    }

    private static Set<String> eagerGames(List<Path> saves) {
        Set<String> configured = csv(System.getProperty("golden.eager"));
        if (!configured.isEmpty()) return configured;
        return saves.stream()
                .limit(4)
                .map(MapRenderGoldenHarness::gameNameOf)
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String gameNameOf(Path save) {
        String fileName = save.getFileName().toString();
        return fileName.substring(0, fileName.length() - ".txt".length());
    }

    private static Game loadFresh(Path save) {
        try {
            Class<?> loader = Class.forName("ti4.game.persistence.GameLoadService");
            Method readGame = loader.getDeclaredMethod("readGame", File.class);
            readGame.setAccessible(true);
            return (Game) readGame.invoke(null, save.toFile());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot reach GameLoadService.readGame", e);
        }
    }

    private static void apply(Mutation mutation, Game game) {
        switch (mutation) {
            case NONE -> {}
            case SEGMENTS -> applySegments(game);
            case BOARDS -> applyBoards(game);
            case HIDE_FLIP ->
                game.setFowOption(FOWOption.HIDE_PLAYER_INFOS, !game.getFowOption(FOWOption.HIDE_PLAYER_INFOS));
            case DISTANCES -> applyDistances(game);
        }
    }

    private static void applySegments(Game game) {
        MapSegment.setAutoSectors(game, true);
        List<Player> players = game.getRealPlayers();
        for (int i = 0; i < Math.min(2, players.size()); i++) {
            Tile home = players.get(i).getHomeSystemTile();
            if (home != null) {
                MapSegment.put(game, new MapSegment(i == 0 ? "hsone" : "hstwo", home.getPosition(), 2 - i));
            }
        }
    }

    private static void applyBoards(Game game) {
        for (Map.Entry<String, Tile> entry : new ArrayList<>(game.getTileMap().entrySet())) {
            String position = entry.getKey();
            if (!position.matches("\\d{3}") || position.charAt(0) > '2') continue;
            game.setTile(new Tile(entry.getValue().getTileID(), "a" + position));
            if (position.charAt(0) <= '1') {
                game.setTile(new Tile(entry.getValue().getTileID(), "b" + position));
            }
        }
        MapSegment.setAutoSectors(game, true);
    }

    private static void applyDistances(Game game) {
        Map<String, Integer> distances = new HashMap<>();
        int index = 0;
        for (String position : new java.util.TreeSet<>(game.getTileMap().keySet())) {
            distances.put(position, index++ % 7);
        }
        game.setTileDistances(distances);
    }

    private static List<String> segmentNames(Path save, Mutation mutation) {
        Game game = loadFresh(save);
        apply(mutation, game);
        List<String> names = new ArrayList<>();
        MapSegment.all(game).forEach(segment -> names.add(segment.name()));
        names.add(MapSegment.MAIN);
        return names.stream().distinct().sorted().toList();
    }

    private static List<Spec> plan(
            Path save, Game probe, boolean fog, boolean eager, boolean boards, boolean distances) {
        List<Spec> specs = new ArrayList<>();
        boolean hasViewer = fog && !probe.getRealPlayers().isEmpty();
        for (DisplayType type : DisplayType.values()) {
            specs.add(new Spec("pub-" + type.name(), type, false, null, Mutation.NONE, false));
            if (hasViewer) {
                specs.add(new Spec("fog-" + type.name(), type, true, null, Mutation.NONE, false));
            }
        }
        if (hasViewer) {
            for (DisplayType type : List.of(DisplayType.all, DisplayType.stats)) {
                specs.add(new Spec("hideflip-fog-" + type.name(), type, true, null, Mutation.HIDE_FLIP, false));
            }
        }
        if (fog) {
            addSegmentSpecs(specs, save, Mutation.SEGMENTS, "seg", hasViewer, SEGMENT_DISPLAY_TYPES);
        }
        if (boards) {
            for (DisplayType type : List.of(DisplayType.all, DisplayType.map, DisplayType.stats)) {
                specs.add(new Spec("boards-pub-" + type.name(), type, false, null, Mutation.BOARDS, false));
                if (hasViewer) {
                    specs.add(new Spec("boards-fog-" + type.name(), type, true, null, Mutation.BOARDS, false));
                }
            }
            addSegmentSpecs(specs, save, Mutation.BOARDS, "boardseg", hasViewer, SEGMENT_DISPLAY_TYPES);
        }
        if (distances) {
            specs.add(new Spec("dist-all", DisplayType.all, false, null, Mutation.DISTANCES, false));
            specs.add(new Spec("dist-map", DisplayType.map, false, null, Mutation.DISTANCES, false));
        }
        if (eager) {
            for (DisplayType type : List.of(DisplayType.all, DisplayType.landscape, DisplayType.stats)) {
                specs.add(new Spec("eager-pub-" + type.name(), type, false, null, Mutation.NONE, true));
            }
            if (hasViewer) {
                specs.add(new Spec("eager-fog-all", DisplayType.all, true, null, Mutation.NONE, true));
            }
            if (fog) {
                for (String name : segmentNames(save, Mutation.SEGMENTS)) {
                    specs.add(new Spec(
                            "eager-seg-" + sanitize(name) + (hasViewer ? "-fog" : "-pub") + "-all",
                            DisplayType.all,
                            hasViewer,
                            name,
                            Mutation.SEGMENTS,
                            true));
                }
            }
        }
        return specs;
    }

    private static void addSegmentSpecs(
            List<Spec> specs, Path save, Mutation mutation, String prefix, boolean hasViewer, List<DisplayType> types) {
        for (String name : segmentNames(save, mutation)) {
            for (DisplayType type : types) {
                specs.add(new Spec(
                        prefix + "-" + sanitize(name) + "-pub-" + type.name(), type, false, name, mutation, false));
                if (hasViewer) {
                    specs.add(new Spec(
                            prefix + "-" + sanitize(name) + "-fog-" + type.name(), type, true, name, mutation, false));
                }
            }
        }
    }

    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }

    private static RenderOutcome render(Path save, Spec spec) {
        Game game = loadFresh(save);
        if (game == null) {
            return failed(spec, "load returned null");
        }
        apply(spec.mutation(), game);
        GenericInteractionCreateEvent event = null;
        if (spec.privateView()) {
            event = privateViewOf(game.getRealPlayers().getFirst().getUserID());
        }
        RenderRequest request = new RenderRequest(game, spec.displayType(), event, spec.segment());
        try (AutoCloseable mode = spec.eager() ? RenderRandom.eager() : RenderRandom.neutral();
                RenderedCanvas rendered = renderCanvas(request)) {
            long hashStart = System.nanoTime();
            BufferedImage canvas = rendered.canvas();
            String sha = sha256(canvas);
            String blocks = blockHashes(canvas);
            long hashed = System.nanoTime();
            String overlays = overlaysJson(rendered.overlays());
            String random = randomCounts(RenderRandom.callCounts());
            return new RenderOutcome(
                    spec.id(),
                    null,
                    canvas.getWidth(),
                    canvas.getHeight(),
                    sha,
                    blocks,
                    overlays,
                    random,
                    rendered.constructorNanos() / 1_000_000,
                    rendered.drawNanos() / 1_000_000,
                    (hashed - hashStart) / 1_000_000);
        } catch (Exception | OutOfMemoryError e) {
            return failed(spec, e.getClass().getName() + ": " + oneLine(e.getMessage()));
        }
    }

    private static RenderOutcome failed(Spec spec, String error) {
        return new RenderOutcome(spec.id(), error, 0, 0, "", "", "", "", 0, 0, 0);
    }

    private static String oneLine(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private static GenericInteractionCreateEvent privateViewOf(String userId) {
        UserOverridenGenericInteractionCreateEvent event =
                mock(UserOverridenGenericInteractionCreateEvent.class, RETURNS_DEEP_STUBS);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Member member = mock(Member.class);
        when(member.getUser()).thenReturn(user);
        when(event.getUser()).thenReturn(user);
        when(event.getMember()).thenReturn(member);
        when(event.getMessageChannel().getName()).thenReturn("fog-" + userId + "-private");
        when(event.getChannel().getName()).thenReturn("fog-" + userId + "-private");
        return event;
    }

    private static int[] pixels(BufferedImage canvas) {
        if (canvas.getRaster().getDataBuffer() instanceof DataBufferInt buffer
                && canvas.getType() == BufferedImage.TYPE_INT_RGB) {
            return buffer.getData();
        }
        return canvas.getRGB(0, 0, canvas.getWidth(), canvas.getHeight(), null, 0, canvas.getWidth());
    }

    // A canvas cropped with getSubimage shares the larger backing array, so only the first width*height ints count.
    private static String sha256(BufferedImage canvas) throws Exception {
        int[] data = pixels(canvas);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update((canvas.getWidth() + "x" + canvas.getHeight() + ":").getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(1 << 20);
        int length = canvas.getWidth() * canvas.getHeight();
        for (int i = 0; i < length; ) {
            buffer.clear();
            int count = Math.min(length - i, buffer.capacity() / 4);
            buffer.asIntBuffer().put(data, i, count);
            buffer.limit(count * 4);
            digest.update(buffer);
            i += count;
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String blockHashes(BufferedImage canvas) {
        int[] data = pixels(canvas);
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        int columns = (width + BLOCK - 1) / BLOCK;
        int rows = (height + BLOCK - 1) / BLOCK;
        long[] hashes = new long[columns * rows];
        for (int y = 0; y < height; y++) {
            int rowBase = (y / BLOCK) * columns;
            for (int column = 0; column < columns; column++) {
                int from = y * width + column * BLOCK;
                int to = Math.min(y * width + width, from + BLOCK);
                long hash = hashes[rowBase + column];
                for (int i = from; i < to; i++) {
                    hash = hash * 1_000_003L + data[i];
                }
                hashes[rowBase + column] = hash;
            }
        }
        StringBuilder builder = new StringBuilder(hashes.length * 8);
        for (long hash : hashes) {
            builder.append(String.format("%08x", (int) (hash ^ (hash >>> 32))));
        }
        return builder.toString();
    }

    private static String overlaysJson(List<WebsiteOverlay> overlays) {
        StringBuilder json = new StringBuilder("[");
        for (WebsiteOverlay overlay : overlays) {
            if (json.length() > 1) json.append(',');
            json.append('{');
            appendString(json, "dataModel", overlay.getDataModel());
            appendString(json, "dataModelID", overlay.getDataModelID());
            appendString(json, "title", overlay.getTitle());
            appendString(json, "text", overlay.getText());
            json.append("\"boxXYWH\":").append(overlay.getBoxXYWH());
            json.append('}');
        }
        return json.append(']').toString();
    }

    private static void appendString(StringBuilder json, String key, String value) {
        if (value == null) return;
        json.append('"').append(key).append("\":\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) json.append(String.format("\\u%04x", (int) c));
                    else json.append(c);
                }
            }
        }
        json.append("\",");
    }

    private static String randomCounts(Map<String, Integer> counts) {
        StringBuilder builder = new StringBuilder();
        new TreeMap<>(counts).forEach((tag, count) -> {
            if (builder.length() > 0) builder.append(';');
            builder.append(tag).append('=').append(count);
        });
        return builder.toString();
    }

    private static String verdict(RenderOutcome expected, RenderOutcome actual) {
        if (expected == null) return "MISSING IN BASELINE";
        if (!java.util.Objects.equals(expected.error(), actual.error())) {
            return "ERROR DIFFERS expected=" + expected.error() + " actual=" + actual.error();
        }
        if (expected.error() != null) return "IDENTICAL";
        List<String> problems = new ArrayList<>();
        if (expected.width() != actual.width() || expected.height() != actual.height()) {
            problems.add("SIZE " + expected.width() + "x" + expected.height() + " -> " + actual.width() + "x"
                    + actual.height());
        } else if (!expected.sha().equals(actual.sha())) {
            problems.add("PIXELS");
        }
        if (!expected.overlays().equals(actual.overlays())) problems.add("OVERLAYS");
        if (!expected.random().equals(actual.random())) {
            problems.add("RANDOM-CALLS expected=[" + expected.random() + "] actual=[" + actual.random() + "]");
        }
        return problems.isEmpty() ? "IDENTICAL" : "DIFFERS: " + String.join(", ", problems);
    }

    private static void reportMismatch(
            Path save,
            Spec spec,
            Path baselineDir,
            Path resultDir,
            RenderOutcome expected,
            RenderOutcome actual,
            StringBuilder summary)
            throws Exception {
        if (expected == null || actual.error() != null || expected.error() != null) return;
        boolean sizeSame = expected.width() == actual.width() && expected.height() == actual.height();
        if (sizeSame && !expected.sha().equals(actual.sha())) {
            summary.append("  box(blocks)=").append(blockBox(expected, actual)).append('\n');
        }
        if (!sizeSame || !expected.sha().equals(actual.sha())) {
            String gameName = gameNameOf(save);
            Path actualPng = resultDir.resolve("mismatch").resolve(gameName).resolve(spec.id() + ".actual.png");
            writePng(save, spec, actualPng);
            Path baselinePng = baselineDir.resolve("png").resolve(gameName).resolve(spec.id() + ".png");
            if (sizeSame && Files.exists(baselinePng)) {
                summary.append("  ")
                        .append(writeExactDiff(
                                baselinePng, actualPng, actualPng.resolveSibling(spec.id() + ".diff.png")))
                        .append('\n');
            } else {
                summary.append("  no baseline png for exact diff; rerun baseline with -Dgolden.pngs=")
                        .append(gameName)
                        .append('/')
                        .append(spec.id())
                        .append('\n');
            }
        }
    }

    private static String blockBox(RenderOutcome expected, RenderOutcome actual) {
        int columns = (actual.width() + BLOCK - 1) / BLOCK;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        int count = 0;
        for (int i = 0; i * 8 < actual.blocks().length(); i++) {
            if (expected.blocks().regionMatches(i * 8, actual.blocks(), i * 8, 8)) continue;
            count++;
            int x = (i % columns) * BLOCK;
            int y = (i / columns) * BLOCK;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, Math.min(actual.width(), x + BLOCK));
            maxY = Math.max(maxY, Math.min(actual.height(), y + BLOCK));
        }
        return count + " blocks of " + BLOCK + "px within x=" + minX + ".." + maxX + " y=" + minY + ".." + maxY;
    }

    private static String writeExactDiff(Path baselinePng, Path actualPng, Path diffPng) throws IOException {
        BufferedImage expected = ImageIO.read(baselinePng.toFile());
        BufferedImage actual = ImageIO.read(actualPng.toFile());
        BufferedImage diff = new BufferedImage(actual.getWidth(), actual.getHeight(), BufferedImage.TYPE_INT_RGB);
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        long differing = 0;
        for (int y = 0; y < actual.getHeight(); y++) {
            for (int x = 0; x < actual.getWidth(); x++) {
                int a = actual.getRGB(x, y) & 0xFFFFFF;
                if (a == (expected.getRGB(x, y) & 0xFFFFFF)) {
                    diff.setRGB(x, y, a);
                } else {
                    diff.setRGB(x, y, 0xFF0000);
                    differing++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        ImageIO.write(diff, "png", diffPng.toFile());
        return "exact diff: " + differing + " pixels differ within x=" + minX + ".." + maxX + " y=" + minY + ".." + maxY
                + " -> " + diffPng;
    }

    private static void writePng(Path save, Spec spec, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Game game = loadFresh(save);
        apply(spec.mutation(), game);
        GenericInteractionCreateEvent event = spec.privateView()
                ? privateViewOf(game.getRealPlayers().getFirst().getUserID())
                : null;
        try (AutoCloseable mode = spec.eager() ? RenderRandom.eager() : RenderRandom.neutral();
                RenderedCanvas rendered =
                        renderCanvas(new RenderRequest(game, spec.displayType(), event, spec.segment()))) {
            ImageIO.write(rendered.canvas(), "png", target.toFile());
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeRecords(Path file, List<RenderOutcome> records) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (RenderOutcome record : records) {
            builder.append("RENDER ").append(record.id()).append('\n');
            if (record.error() != null) {
                builder.append("error ").append(record.error()).append('\n');
            } else {
                builder.append("size ")
                        .append(record.width())
                        .append(' ')
                        .append(record.height())
                        .append('\n');
                builder.append("sha ").append(record.sha()).append('\n');
                builder.append("blocks ").append(record.blocks()).append('\n');
                builder.append("random ").append(record.random()).append('\n');
                builder.append("overlays ").append(record.overlays()).append('\n');
            }
            builder.append("time ")
                    .append(record.constructorMs())
                    .append(' ')
                    .append(record.drawMs())
                    .append(' ')
                    .append(record.hashMs())
                    .append('\n');
            builder.append("END\n");
        }
        Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
    }

    private static Map<String, RenderOutcome> readRecords(Path file) throws IOException {
        Map<String, RenderOutcome> records = new LinkedHashMap<>();
        if (!Files.exists(file)) return records;
        String id = null;
        String error = null;
        int width = 0;
        int height = 0;
        String sha = "";
        String blocks = "";
        String overlays = "";
        String random = "";
        long[] time = new long[3];
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int space = line.indexOf(' ');
            String key = space < 0 ? line : line.substring(0, space);
            String value = space < 0 ? "" : line.substring(space + 1);
            switch (key) {
                case "RENDER" -> {
                    id = value;
                    error = null;
                    width = 0;
                    height = 0;
                    sha = "";
                    blocks = "";
                    overlays = "";
                    random = "";
                    time = new long[3];
                }
                case "error" -> error = value;
                case "size" -> {
                    String[] parts = value.split(" ");
                    width = Integer.parseInt(parts[0]);
                    height = Integer.parseInt(parts[1]);
                }
                case "sha" -> sha = value;
                case "blocks" -> blocks = value;
                case "random" -> random = value;
                case "overlays" -> overlays = value;
                case "time" -> {
                    String[] parts = value.split(" ");
                    time = new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
                }
                case "END" ->
                    records.put(
                            id,
                            new RenderOutcome(
                                    id, error, width, height, sha, blocks, overlays, random, time[0], time[1],
                                    time[2]));
                default -> {}
            }
        }
        return records;
    }
}
