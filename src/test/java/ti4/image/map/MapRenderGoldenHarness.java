package ti4.image.map;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.persistence.GameSnapshots;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.DisplayType;
import ti4.helpers.RenderRandom;
import ti4.helpers.Storage;
import ti4.image.FakeImageNetwork;
import ti4.image.tile.SystemContextImage;
import ti4.image.tile.TileLayers;
import ti4.image.tile.TileStep;
import ti4.message.MessageHelper;
import ti4.service.fow.UserOverridenGenericInteractionCreateEvent;
import ti4.service.option.FOWOptionService.FOWOption;
import ti4.service.user.AFKService;
import ti4.testUtils.BaseTi4Test;
import ti4.website.model.WebsiteOverlay;

/**
 * Golden-image harness for the map renderer and the tile renderer's standalone entry points (system context images,
 * per-step tile layers, overview images). It is gated on the system property golden.saves so CI never runs it.
 *
 * <pre>
 * mvn -o test -Dtest=MapRenderGoldenHarness -Dgolden.saves=DIR -Dgolden.out=DIR -Dgolden.mode=baseline|compare
 *     [-Dgolden.baseline=DIR] [-Dgolden.only=game1,game2] [-Dgolden.ids=REGEX] [-Dgolden.eager=game1,game2]
 *     [-Dgolden.pngs=REGEX over game/renderId] [-Dgolden.inventory=true] [-Dgolden.statedump=DIR, one file per render] [-Dgolden.snapshot=true] [-DargLine=-Xmx10g]
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
@Isolated
class MapRenderGoldenHarness extends BaseTi4Test {

    private static final int BLOCK = 128;
    private static final List<DisplayType> SEGMENT_DISPLAY_TYPES = List.of(DisplayType.all, DisplayType.map);
    private static final int BOARD_VARIANT_GAMES = 2;
    private static final int MIN_PLAYERS_FOR_DISTANCES = 4;

    private static final int CONTEXT_HOME_SYSTEMS = 3;
    private static final int MAX_CONTEXT_RINGS = 2;
    private static final int NUCLEUS_PLAYERS = 6;
    private static final String NUCLEUS_TEMPLATE = "6pStandardNucleus";

    private enum Mutation {
        NONE,
        SEGMENTS,
        CONNECTIONS,
        BOARDS,
        HIDE_FLIP,
        DISTANCES,
        NUCLEUS,
        PLAYER_AREA_VARIETY,
        VEILED_HEART
    }

    // MAP is a whole map render. The other kinds cover the tile renderer's standalone entry points: the "system with
    // context" image (CONTEXT), every TileStep layer of every tile (STEPS, digested per tile) and the overview images
    // that composite scaled tile layers.
    private enum Kind {
        MAP,
        CONTEXT,
        STEPS,
        OVERVIEW_GM,
        OVERVIEW_PLAYER,
        OVERVIEW_MAP
    }

    // Who asks for the image. PUBLIC: no event. FOG: the first player's private-channel event. FOG_SECOND: that event
    // plus an explicit second player (StartCombatService). PUB_VIEWER: the first player's event from an ordinary
    // channel, so the tile renderer knows a viewer without a private view (sweep tokens, jails). EXPLICIT_FIRST: no
    // event plus an explicit first player (ButtonHelper). FOG_GM: a private-channel event from a user who is not a
    // player, so the view is private without a viewer.
    private enum Viewer {
        PUBLIC,
        FOG,
        FOG_SECOND,
        PUB_VIEWER,
        EXPLICIT_FIRST,
        FOG_GM
    }

    private static final String NON_PLAYER_USER_ID = "100000000000000001";
    private static final List<TileStep> VIEWER_SENSITIVE_STEPS =
            List.of(TileStep.Tile, TileStep.Extras, TileStep.Units);

    private record Spec(
            String id,
            Kind kind,
            DisplayType displayType,
            Viewer viewer,
            String segment,
            Mutation mutation,
            boolean eager,
            String focus,
            int rings,
            TileStep step) {}

    private static Spec mapSpec(
            String id, DisplayType displayType, boolean privateView, String segment, Mutation mutation, boolean eager) {
        Viewer viewer = privateView ? Viewer.FOG : Viewer.PUBLIC;
        return new Spec(id, Kind.MAP, displayType, viewer, segment, mutation, eager, null, 0, null);
    }

    private static Spec tileSpec(
            String id, Kind kind, Viewer viewer, Mutation mutation, String focus, int rings, TileStep step) {
        return new Spec(id, kind, DisplayType.all, viewer, null, mutation, false, focus, rings, step);
    }

    private record RenderOutcome(
            String id,
            String error,
            int width,
            int height,
            String sha,
            String blocks,
            String overlays,
            String random,
            String state,
            int messages,
            long constructorMs,
            long drawMs,
            long hashMs) {}

    private record RenderRequest(
            Game game, DisplayType displayType, GenericInteractionCreateEvent event, String segment) {}

    private record RenderedCanvas(
            BufferedImage canvas,
            List<WebsiteOverlay> overlays,
            LayerDigest layers,
            long constructorNanos,
            long drawNanos)
            implements AutoCloseable {
        @Override
        public void close() {}
    }

    private static int connectionStripRenders;
    private static final AtomicInteger MESSAGES = new AtomicInteger();
    private static final boolean INVENTORY = Boolean.getBoolean("golden.inventory");
    private static final List<String> MESSAGE_LOG = new ArrayList<>();
    private static final Map<String, List<String>> INVENTORY_BY_GAME = new TreeMap<>();
    private static final Map<String, Integer> INVENTORY_TALLY = new TreeMap<>();
    private static final int MESSAGE_SNIPPET = 90;
    private static final int ENTRY_SNIPPET = 70;
    private static final int CATEGORY_SNIPPET = 120;
    private static final int MAX_DIFF_ENTRIES = 6;

    private record LayerDigest(int tiles, String sha, String perTile) {}

    private static RenderedCanvas renderCanvas(RenderRequest request) {
        long start = System.nanoTime();
        RenderContext context = MapRenderer.resolve(
                new MapRenderRequest(request.game(), request.displayType(), request.event(), request.segment()));
        long constructed = System.nanoTime();
        if (!context.board().connections().isEmpty()) connectionStripRenders++;
        BufferedImage canvas = MapRenderer.draw(context, RenderTimings.disabled());
        long drawn = System.nanoTime();
        return new RenderedCanvas(canvas, context.overlays().asList(), null, constructed - start, drawn - constructed);
    }

    private static RenderedCanvas produce(Game game, Spec spec) throws Exception {
        List<Player> players = game.getRealPlayers();
        GenericInteractionCreateEvent event = eventFor(spec.viewer(), players);
        Player explicitViewer =
                switch (spec.viewer()) {
                    case FOG_SECOND -> players.get(1);
                    case EXPLICIT_FIRST -> players.getFirst();
                    default -> null;
                };
        long start = System.nanoTime();
        return switch (spec.kind()) {
            case MAP -> renderCanvas(new RenderRequest(game, spec.displayType(), event, spec.segment()));
            case CONTEXT -> timed(contextImage(game, event, spec.rings(), spec.focus(), explicitViewer), start);
            case OVERVIEW_GM -> timed(CompactOverviewGenerator.gmImage(game), start);
            case OVERVIEW_PLAYER -> timed(CompactOverviewGenerator.playerImage(game, players.getFirst(), event), start);
            case OVERVIEW_MAP -> timed(MapOverviewGenerator.render(game, true), start);
            case STEPS -> {
                LayerDigest digest = digestLayers(game, event, spec.step());
                yield new RenderedCanvas(null, List.of(), digest, 0, System.nanoTime() - start);
            }
        };
    }

    private static RenderedCanvas timed(BufferedImage canvas, long start) {
        return new RenderedCanvas(canvas, List.of(), null, 0, System.nanoTime() - start);
    }

    // The only two places that name the tile renderer's API, so a restructure edits nothing else in the harness.
    private static BufferedImage contextImage(
            Game game, GenericInteractionCreateEvent event, int rings, String focus, Player explicitViewer) {
        return SystemContextImage.render(game, event, rings, focus, explicitViewer);
    }

    private static Function<Tile, BufferedImage> layerSource(
            Game game, GenericInteractionCreateEvent event, TileStep step) {
        TileLayers layers = TileLayers.of(game, event, DisplayType.all);
        return tile -> layers.draw(tile, step);
    }

    // Draws one layer per tile of the viewer's tile map (fog-substituted for private views, as BoardSection does) and
    // folds each layer's pixels, or the class of the exception it threw, into one digest. perTile keeps a short hash
    // per position so a mismatch names the tiles that changed.
    private static LayerDigest digestLayers(Game game, GenericInteractionCreateEvent event, TileStep step)
            throws Exception {
        Function<Tile, BufferedImage> source = layerSource(game, event, step);
        Map<String, Tile> tiles = new TreeMap<>(FogView.of(game, event).tiles());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder perTile = new StringBuilder();
        for (Map.Entry<String, Tile> entry : tiles.entrySet()) {
            String outcome;
            try {
                BufferedImage layer = source.apply(entry.getValue());
                outcome = layer.getWidth() + "x" + layer.getHeight() + ":" + layerHash(layer);
            } catch (Exception e) {
                outcome = e.getClass().getName();
            }
            digest.update((entry.getKey() + "=" + outcome + "\n").getBytes(StandardCharsets.UTF_8));
            if (perTile.length() > 0) perTile.append(',');
            perTile.append(entry.getKey()).append(':').append(Integer.toHexString(outcome.hashCode()));
        }
        return new LayerDigest(tiles.size(), HexFormat.of().formatHex(digest.digest()), perTile.toString());
    }

    private static String layerHash(BufferedImage layer) {
        int[] data = layer.getRGB(0, 0, layer.getWidth(), layer.getHeight(), null, 0, layer.getWidth());
        long first = 17;
        long second = 31;
        for (int value : data) {
            first = first * 1_000_003L + value;
            second = (second ^ value) * 0x9E3779B97F4A7C15L;
        }
        return Long.toHexString(first) + Long.toHexString(second);
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
        boolean nucleusPlanned = false;
        int fogGamesSeen = 0;

        try (MockedStatic<DateTimeHelper> dateTime = mockStatic(DateTimeHelper.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<AFKService> afk = mockStatic(AFKService.class, Mockito.CALLS_REAL_METHODS);
                FakeImageNetwork network = FakeImageNetwork.notFound();
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class, invocation -> {
                    MESSAGES.incrementAndGet();
                    if (INVENTORY) MESSAGE_LOG.add(describeMessage(invocation));
                    return invocation.callRealMethod();
                })) {
            dateTime.when(DateTimeHelper::getFormattedTimestamp).thenReturn("2000.01.01 - 00.00.00");
            dateTime.when(DateTimeHelper::getFormattedTimestampWithMillis).thenReturn("2000.01.01 - 00.00.00.000");
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
                boolean nucleus =
                        !fog && !nucleusPlanned && probe.getRealPlayers().size() == NUCLEUS_PLAYERS;
                if (fog) fogGamesSeen++;
                if (distances) distancesPlanned = true;
                if (nucleus) nucleusPlanned = true;

                List<Spec> specs = plan(save, probe, fog, eager, boards, distances, nucleus).stream()
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
                if (INVENTORY) writeInventory(resultDir, gameName);
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
        summary.append("connectionStripRenders=").append(connectionStripRenders).append('\n');
        Files.writeString(resultDir.resolve("summary.txt"), summary.toString());
        if (INVENTORY) writeInventoryTally(resultDir);
        System.out.println(summary);
        if (compare) {
            Assertions.assertEquals(0, totals.mismatches, "renders differing from the baseline, see summary.txt");
        }
        Assertions.assertEquals(0, totals.failures, "saves that failed to load");
        if (only.isEmpty() && ".*".equals(idFilter.pattern())) {
            Assertions.assertTrue(connectionStripRenders > 0, "no render exercised the sector connection strip");
        }
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
            Game game = (Game) readGame.invoke(null, save.toFile());
            // golden.snapshot renders the in-memory copy the render pipeline takes at queue time, so a compare
            // run against the normal baseline proves the copy renders exactly like the loaded game.
            return Boolean.getBoolean("golden.snapshot") ? GameSnapshots.copy(game) : game;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot reach GameLoadService.readGame", e);
        }
    }

    private static void apply(Mutation mutation, Game game) {
        switch (mutation) {
            case NONE -> {}
            case SEGMENTS -> applySegments(game);
            case CONNECTIONS -> {
                applySegments(game);
                game.setFowOption(FOWOption.MAP_CONNECTIONS, true);
            }
            case BOARDS -> applyBoards(game);
            case HIDE_FLIP ->
                game.setFowOption(FOWOption.HIDE_PLAYER_INFOS, !game.getFowOption(FOWOption.HIDE_PLAYER_INFOS));
            case DISTANCES -> applyDistances(game);
            case NUCLEUS -> game.setMapTemplateID(NUCLEUS_TEMPLATE);
            case PLAYER_AREA_VARIETY -> PlayerAreaVariety.apply(game);
            case VEILED_HEART -> VeiledHeartVariety.apply(game);
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
            Path save, Game probe, boolean fog, boolean eager, boolean boards, boolean distances, boolean nucleus) {
        List<Spec> specs = new ArrayList<>();
        boolean hasViewer = fog && !probe.getRealPlayers().isEmpty();
        for (DisplayType type : DisplayType.values()) {
            specs.add(mapSpec("pub-" + type.name(), type, false, null, Mutation.NONE, false));
            if (hasViewer) {
                specs.add(mapSpec("fog-" + type.name(), type, true, null, Mutation.NONE, false));
            }
        }
        if (hasViewer) {
            for (DisplayType type : List.of(DisplayType.all, DisplayType.stats)) {
                specs.add(mapSpec("hideflip-fog-" + type.name(), type, true, null, Mutation.HIDE_FLIP, false));
            }
        }
        if (fog) {
            addSegmentSpecs(specs, save, Mutation.SEGMENTS, "seg", hasViewer, SEGMENT_DISPLAY_TYPES);
            if (hasViewer) {
                for (String name : segmentNames(save, Mutation.CONNECTIONS)) {
                    specs.add(new Spec(
                            "conn-" + sanitize(name) + "-fog-all",
                            Kind.MAP,
                            DisplayType.all,
                            Viewer.FOG,
                            name,
                            Mutation.CONNECTIONS,
                            false,
                            null,
                            0,
                            null));
                }
            }
        }
        if (boards) {
            for (DisplayType type : List.of(DisplayType.all, DisplayType.map, DisplayType.stats)) {
                specs.add(mapSpec("boards-pub-" + type.name(), type, false, null, Mutation.BOARDS, false));
                if (hasViewer) {
                    specs.add(mapSpec("boards-fog-" + type.name(), type, true, null, Mutation.BOARDS, false));
                }
            }
            addSegmentSpecs(specs, save, Mutation.BOARDS, "boardseg", hasViewer, SEGMENT_DISPLAY_TYPES);
        }
        if (distances) {
            specs.add(mapSpec("dist-all", DisplayType.all, false, null, Mutation.DISTANCES, false));
            specs.add(mapSpec("dist-map", DisplayType.map, false, null, Mutation.DISTANCES, false));
            for (DisplayType type : List.of(DisplayType.all, DisplayType.landscape, DisplayType.stats)) {
                specs.add(
                        mapSpec("variety-pub-" + type.name(), type, false, null, Mutation.PLAYER_AREA_VARIETY, false));
            }
        }
        if (eager) {
            for (DisplayType type : List.of(DisplayType.all, DisplayType.landscape, DisplayType.stats)) {
                specs.add(mapSpec("eager-pub-" + type.name(), type, false, null, Mutation.NONE, true));
            }
            if (hasViewer) {
                specs.add(mapSpec("eager-fog-all", DisplayType.all, true, null, Mutation.NONE, true));
            }
            if (fog) {
                for (String name : segmentNames(save, Mutation.SEGMENTS)) {
                    specs.add(mapSpec(
                            "eager-seg-" + sanitize(name) + (hasViewer ? "-fog" : "-pub") + "-all",
                            DisplayType.all,
                            hasViewer,
                            name,
                            Mutation.SEGMENTS,
                            true));
                }
            }
        }
        if (probe.isTwilightsFallMode() && probe.getRealPlayers().size() >= 2) {
            for (DisplayType type : List.of(DisplayType.all, DisplayType.stats)) {
                specs.add(mapSpec("veiled-pub-" + type.name(), type, false, null, Mutation.VEILED_HEART, false));
            }
            if (hasViewer) {
                specs.add(mapSpec("veiled-fog-all", DisplayType.all, true, null, Mutation.VEILED_HEART, false));
            }
        }
        addTileSpecs(specs, probe, hasViewer, nucleus);
        return specs;
    }

    private static void addTileSpecs(List<Spec> specs, Game probe, boolean hasViewer, boolean nucleus) {
        List<Viewer> everyRing = viewersFor(probe, hasViewer, List.of(Viewer.PUBLIC, Viewer.FOG));
        List<Viewer> oneRing = viewersFor(
                probe, hasViewer, List.of(Viewer.FOG_SECOND, Viewer.PUB_VIEWER, Viewer.EXPLICIT_FIRST, Viewer.FOG_GM));
        focusPositions(probe).forEach((label, position) -> {
            for (int rings = 0; rings <= MAX_CONTEXT_RINGS; rings++) {
                List<Viewer> viewers = rings == 1 ? concat(everyRing, oneRing) : everyRing;
                for (Viewer viewer : viewers) {
                    String id = "ctx-" + label + "-c" + rings + "-" + suffix(viewer);
                    specs.add(tileSpec(id, Kind.CONTEXT, viewer, Mutation.NONE, position, rings, null));
                }
            }
        });
        List<Viewer> stepViewers = viewersFor(probe, hasViewer, List.of(Viewer.PUB_VIEWER, Viewer.FOG_GM));
        for (TileStep step : TileStep.values()) {
            List<Viewer> viewers = VIEWER_SENSITIVE_STEPS.contains(step) ? concat(everyRing, stepViewers) : everyRing;
            for (Viewer viewer : viewers) {
                String id = "steps-" + step.name() + "-" + suffix(viewer);
                specs.add(tileSpec(id, Kind.STEPS, viewer, Mutation.NONE, null, 0, step));
            }
        }
        if (nucleus) {
            specs.add(tileSpec(
                    "steps-nucleus-template", Kind.STEPS, Viewer.PUBLIC, Mutation.NUCLEUS, null, 0, TileStep.Nucleus));
        }
        for (Viewer viewer : viewersFor(probe, hasViewer, List.of(Viewer.PUB_VIEWER, Viewer.FOG_GM))) {
            specs.add(new Spec(
                    "viewer-" + suffix(viewer) + "-all",
                    Kind.MAP,
                    DisplayType.all,
                    viewer,
                    null,
                    Mutation.NONE,
                    false,
                    null,
                    0,
                    null));
        }
        specs.add(tileSpec("overview-gm", Kind.OVERVIEW_GM, Viewer.PUBLIC, Mutation.NONE, null, 0, null));
        specs.add(tileSpec("overview-map", Kind.OVERVIEW_MAP, Viewer.PUBLIC, Mutation.NONE, null, 0, null));
        for (Viewer viewer : viewersFor(probe, hasViewer, List.of(Viewer.PUBLIC, Viewer.FOG))) {
            if (probe.getRealPlayers().isEmpty()) break;
            String id = "overview-player-" + suffix(viewer);
            specs.add(tileSpec(id, Kind.OVERVIEW_PLAYER, viewer, Mutation.NONE, null, 0, null));
        }
    }

    private static List<Viewer> viewersFor(Game probe, boolean hasViewer, List<Viewer> wanted) {
        int players = probe.getRealPlayers().size();
        return wanted.stream()
                .filter(viewer -> switch (viewer) {
                    case PUBLIC -> true;
                    case FOG, FOG_GM -> hasViewer;
                    case FOG_SECOND -> hasViewer && players >= 2;
                    case PUB_VIEWER, EXPLICIT_FIRST -> players >= 1;
                })
                .toList();
    }

    private static List<Viewer> concat(List<Viewer> first, List<Viewer> second) {
        return Stream.concat(first.stream(), second.stream()).toList();
    }

    private static String suffix(Viewer viewer) {
        return switch (viewer) {
            case PUBLIC -> "pub";
            case FOG -> "fog";
            case FOG_SECOND -> "fogp2";
            case PUB_VIEWER -> "pubviewer";
            case EXPLICIT_FIRST -> "nullp1";
            case FOG_GM -> "foggm";
        };
    }

    // Home systems, Mecatol, the first anomaly and the busiest system, deduplicated by position in that order.
    private static Map<String, String> focusPositions(Game game) {
        Map<String, String> byPosition = new LinkedHashMap<>();
        List<Player> players = game.getRealPlayers();
        for (int i = 0; i < Math.min(CONTEXT_HOME_SYSTEMS, players.size()); i++) {
            Tile home = players.get(i).getHomeSystemTile();
            if (home != null) byPosition.putIfAbsent(home.getPosition(), "hs" + (i + 1));
        }
        Tile mecatol = game.getMecatolTile();
        if (mecatol != null) byPosition.putIfAbsent(mecatol.getPosition(), "mecatol");
        Map<String, Tile> sorted = new TreeMap<>(game.getTileMap());
        sorted.values().stream()
                .filter(tile -> isAnomaly(game, tile))
                .findFirst()
                .ifPresent(tile -> byPosition.putIfAbsent(tile.getPosition(), "anomaly"));
        String busiest = null;
        int most = 0;
        for (Tile tile : sorted.values()) {
            int units = tile.getUnitHolders().values().stream()
                    .flatMap(holder -> holder.getUnits().values().stream())
                    .mapToInt(Integer::intValue)
                    .sum();
            if (units > most) {
                most = units;
                busiest = tile.getPosition();
            }
        }
        if (busiest != null) byPosition.putIfAbsent(busiest, "busiest");
        Map<String, String> byLabel = new LinkedHashMap<>();
        byPosition.forEach((position, label) -> byLabel.put(label, position));
        return byLabel;
    }

    private static boolean isAnomaly(Game game, Tile tile) {
        try {
            return tile.getTileModel() != null && tile.isAnomaly(game, null);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static void addSegmentSpecs(
            List<Spec> specs, Path save, Mutation mutation, String prefix, boolean hasViewer, List<DisplayType> types) {
        for (String name : segmentNames(save, mutation)) {
            for (DisplayType type : types) {
                specs.add(mapSpec(
                        prefix + "-" + sanitize(name) + "-pub-" + type.name(), type, false, name, mutation, false));
                if (hasViewer) {
                    specs.add(mapSpec(
                            prefix + "-" + sanitize(name) + "-fog-" + type.name(), type, true, name, mutation, false));
                }
            }
        }
    }

    private static String sanitize(String value) {
        return value.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
    }

    private static String currentRenderId = "";

    private static RenderOutcome render(Path save, Spec spec) {
        currentRenderId = spec.id();
        Game game = loadFresh(save);
        if (game == null) {
            return failed(spec, "load returned null");
        }
        apply(spec.mutation(), game);
        Snapshot before = INVENTORY ? snapshotOf(game) : null;
        MESSAGES.set(0);
        MESSAGE_LOG.clear();
        try (AutoCloseable mode = spec.eager() ? RenderRandom.eager() : RenderRandom.neutral();
                RenderedCanvas rendered = produce(game, spec)) {
            int messages = MESSAGES.get();
            if (INVENTORY) recordInventory(save, spec, game, before);
            String state = gameStateDigest(game);
            long hashStart = System.nanoTime();
            if (rendered.layers() != null) {
                LayerDigest layers = rendered.layers();
                return new RenderOutcome(
                        spec.id(),
                        null,
                        layers.tiles(),
                        0,
                        layers.sha(),
                        layers.perTile(),
                        "[]",
                        randomCounts(RenderRandom.callCounts()),
                        state,
                        messages,
                        rendered.constructorNanos() / 1_000_000,
                        rendered.drawNanos() / 1_000_000,
                        (System.nanoTime() - hashStart) / 1_000_000);
            }
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
                    state,
                    messages,
                    rendered.constructorNanos() / 1_000_000,
                    rendered.drawNanos() / 1_000_000,
                    (hashed - hashStart) / 1_000_000);
        } catch (Exception | OutOfMemoryError e) {
            return failed(spec, e.getClass().getName() + ": " + oneLine(e.getMessage()));
        }
    }

    private static RenderOutcome failed(Spec spec, String error) {
        return new RenderOutcome(spec.id(), error, 0, 0, "", "", "", "", "", 0, 0, 0, 0);
    }

    // Renders mutate the live game (fog filtering, tile distances, and anything a tile helper writes back), so the
    // save text after the render is part of the outcome. The last-modified stamp is the wall clock and is dropped.
    private static String gameStateDigest(Game game) throws Exception {
        List<String> kept = stateLines(game);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String line : kept) {
            digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        String state = HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        String dumpDir = System.getProperty("golden.statedump");
        if (dumpDir != null) {
            Path dump = Path.of(dumpDir, game.getName(), currentRenderId + ".txt");
            Files.createDirectories(dump.getParent());
            Files.write(dump, kept, StandardCharsets.UTF_8);
        }
        return state;
    }

    private static List<String> stateLines(Game game) throws Exception {
        Path file = Files.createTempFile("golden-state", ".txt");
        try {
            Class<?> saver = Class.forName("ti4.game.persistence.GameSaveService");
            Method saveGame = saver.getDeclaredMethod("saveGame", Game.class, Path.class);
            saveGame.setAccessible(true);
            saveGame.invoke(null, game, file);
            List<String> kept = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.startsWith(Constants.LAST_MODIFIED_DATE + " ")) continue;
                kept.add(sortedEnumKeyedLine(line));
            }
            return kept;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private record Snapshot(List<String> stateLines, String fogFlags) {}

    private static Snapshot snapshotOf(Game game) {
        try {
            return new Snapshot(stateLines(game), fogFlags(game));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // One chunk per real player: faction, whether fog is initialised, one 0/1 per sorted tile (hasFog), seen-tile
    // count.
    private static String fogFlags(Game game) {
        StringBuilder flags = new StringBuilder();
        for (Player player : game.getPlayers().values()) {
            if (!player.isRealPlayer()) continue;
            flags.append(player.getFaction())
                    .append(player.isFogInitialized() ? "+" : "-")
                    .append(':');
            for (Tile tile : new TreeMap<>(game.getTileMap()).values()) {
                flags.append(tile.hasFog(player) ? '1' : '0');
            }
            flags.append('#').append(player.getFogTiles().size()).append(';');
        }
        return flags.toString();
    }

    private static String describeMessage(org.mockito.invocation.InvocationOnMock invocation) {
        StringBuilder text = new StringBuilder(invocation.getMethod().getName());
        for (Object argument : invocation.getArguments()) {
            if (argument instanceof String string) {
                String line = oneLine(string);
                text.append(" | ").append(line, 0, Math.min(MESSAGE_SNIPPET, line.length()));
                break;
            }
        }
        return text.toString();
    }

    private static void recordInventory(Path save, Spec spec, Game game, Snapshot before) {
        Snapshot after = snapshotOf(game);
        List<String> entries = new ArrayList<>(changedEntries(before.stateLines(), after.stateLines()));
        if (!before.fogFlags().equals(after.fogFlags())) entries.add("fogflags " + fogFlagChange(before, after));
        for (String message : MESSAGE_LOG) entries.add("message " + message);
        if (entries.isEmpty()) return;
        List<String> lines = INVENTORY_BY_GAME.computeIfAbsent(gameNameOf(save), name -> new ArrayList<>());
        for (String entry : entries) {
            lines.add(spec.id() + " " + entry);
            INVENTORY_TALLY.merge(tallyKey(spec, entry), 1, Integer::sum);
        }
    }

    private static String tallyKey(Spec spec, String entry) {
        if (entry.startsWith("message ") || entry.startsWith("fogflags")) {
            return spec.kind() + " " + StringUtils.abbreviate(entry, CATEGORY_SNIPPET);
        }
        String[] parts = entry.split(" ", 3);
        String[] path = parts[0].split(":");
        String kind = path[0];
        if (parts.length > 1 && parts[1].startsWith("SECTION")) {
            return spec.kind() + " " + kind + " " + parts[1];
        }
        String field = path[path.length - 1].replaceAll("#\\d+$", "");
        String detail = field.contains("stored") && parts.length > 2 ? " " + parts[2].split(" ")[0] : "";
        return spec.kind() + " " + kind + ":" + StringUtils.abbreviate(field + detail, CATEGORY_SNIPPET);
    }

    private static String fogFlagChange(Snapshot before, Snapshot after) {
        String[] was = before.fogFlags().split(";");
        String[] now = after.fogFlags().split(";");
        List<String> changed = new ArrayList<>();
        for (int i = 0; i < Math.min(was.length, now.length); i++) {
            if (was[i].equals(now[i])) continue;
            int colon = now[i].indexOf(':');
            String faction = now[i].substring(0, colon - 1);
            String initialised = was[i].charAt(colon - 1) + ">" + now[i].charAt(colon - 1);
            int flipped = 0;
            for (int c = colon + 1; c < Math.min(was[i].length(), now[i].length()); c++) {
                if (was[i].charAt(c) == '#') break;
                if (was[i].charAt(c) != now[i].charAt(c)) flipped++;
            }
            String seen = was[i].substring(was[i].indexOf('#') + 1) + ">" + now[i].substring(now[i].indexOf('#') + 1);
            changed.add(faction + " init" + initialised + " tilesFlipped=" + flipped + " seen" + seen);
        }
        return String.join(", ", changed);
    }

    private static List<String> changedEntries(List<String> before, List<String> after) {
        Map<String, String> was = keyedLines(before);
        Map<String, String> now = keyedLines(after);
        Set<String> contextsWas = contextsOf(was);
        Set<String> contextsNow = contextsOf(now);
        List<String> changed = new ArrayList<>();
        for (String context : contextsNow) {
            if (!contextsWas.contains(context) && !"game".equals(context)) changed.add(context + " SECTION-ADDED");
        }
        for (String context : contextsWas) {
            if (!contextsNow.contains(context) && !"game".equals(context)) changed.add(context + " SECTION-REMOVED");
        }
        Set<String> keys = new java.util.TreeSet<>(was.keySet());
        keys.addAll(now.keySet());
        for (String key : keys) {
            String context = contextOf(key);
            if (!contextsWas.contains(context) || !contextsNow.contains(context)) continue;
            String old = was.get(key);
            String current = now.get(key);
            if (java.util.Objects.equals(old, current)) continue;
            changed.add(key + " " + entryDiff(old, current));
        }
        return changed;
    }

    private static String contextOf(String key) {
        if (key.startsWith("game:")) return "game";
        return key.substring(0, key.indexOf(':', key.indexOf(':') + 1));
    }

    private static Set<String> contextsOf(Map<String, String> keyed) {
        Set<String> contexts = new java.util.TreeSet<>();
        for (String key : keyed.keySet()) {
            contexts.add(contextOf(key));
        }
        return contexts;
    }

    private static Map<String, String> keyedLines(List<String> lines) {
        Map<String, String> keyed = new LinkedHashMap<>();
        String context = "game";
        String headerKind = null;
        Map<String, Integer> occurrences = new HashMap<>();
        for (String line : lines) {
            int space = line.indexOf(' ');
            String key = space < 0 ? line : line.substring(0, space);
            if (headerKind != null) {
                context = headerKind + ":" + key;
                headerKind = null;
                occurrences.clear();
            } else if ("-player-".equals(line) || "-tile-".equals(line)) {
                headerKind = line.substring(1, line.length() - 1);
            } else if (line.startsWith("-end") && ("-endplayer-".equals(line) || "-endtile-".equals(line))) {
                context = "game";
                occurrences.clear();
            }
            int occurrence = occurrences.merge(key, 1, Integer::sum);
            keyed.put(context + ":" + key + "#" + occurrence, line);
        }
        return keyed;
    }

    private static String entryDiff(String old, String current) {
        if (old == null) return "ADDED";
        if (current == null) return "REMOVED";
        Set<String> was = Set.of(valueOf(old).split("[;,]"));
        Set<String> now = Set.of(valueOf(current).split("[;,]"));
        List<String> parts = new ArrayList<>();
        for (String entry : now) {
            if (!was.contains(entry)) parts.add("+" + StringUtils.abbreviate(entry, ENTRY_SNIPPET));
        }
        for (String entry : was) {
            if (!now.contains(entry)) parts.add("-" + StringUtils.abbreviate(entry, ENTRY_SNIPPET));
        }
        java.util.Collections.sort(parts);
        return parts.isEmpty()
                ? "REORDERED"
                : String.join(" ", parts.subList(0, Math.min(MAX_DIFF_ENTRIES, parts.size())));
    }

    private static String valueOf(String line) {
        int space = line.indexOf(' ');
        return space < 0 ? "" : line.substring(space + 1);
    }

    private static void writeInventory(Path resultDir, String gameName) throws IOException {
        List<String> lines = INVENTORY_BY_GAME.get(gameName);
        if (lines == null) return;
        Path file = resultDir.resolve("inventory").resolve(gameName + ".txt");
        Files.createDirectories(file.getParent());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void writeInventoryTally(Path resultDir) throws IOException {
        List<String> lines = new ArrayList<>();
        INVENTORY_TALLY.forEach((key, count) -> lines.add(count + "\t" + key));
        Files.createDirectories(resultDir.resolve("inventory"));
        Files.write(resultDir.resolve("inventory").resolve("TALLY.txt"), lines, StandardCharsets.UTF_8);
    }

    // fow_options is written from a HashMap keyed by an enum, whose identity hash codes change between JVMs.
    private static String sortedEnumKeyedLine(String line) {
        String prefix = "fow_options ";
        if (!line.startsWith(prefix)) return line;
        List<String> entries =
                new ArrayList<>(List.of(line.substring(prefix.length()).split(";")));
        java.util.Collections.sort(entries);
        return prefix + String.join(";", entries);
    }

    // Helpful NullPointerException messages name the method that failed, so a pure move would change them; only the
    // exception class takes part in the verdict.
    private static String errorClass(String error) {
        if (error == null) return null;
        int colon = error.indexOf(':');
        return colon < 0 ? error : error.substring(0, colon);
    }

    private static String oneLine(String message) {
        return message == null ? "" : message.replace('\n', ' ').replace('\r', ' ');
    }

    private static GenericInteractionCreateEvent eventFor(Viewer viewer, List<Player> players) {
        return switch (viewer) {
            case PUBLIC, EXPLICIT_FIRST -> null;
            case FOG, FOG_SECOND -> privateViewOf(players.getFirst().getUserID());
            case PUB_VIEWER -> channelEventOf(players.getFirst().getUserID(), "game-actions");
            case FOG_GM -> channelEventOf(NON_PLAYER_USER_ID, "fog-gm-private");
        };
    }

    private static GenericInteractionCreateEvent channelEventOf(String userId, String channelName) {
        GenericInteractionCreateEvent event = mock(GenericInteractionCreateEvent.class, RETURNS_DEEP_STUBS);
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        Member member = mock(Member.class);
        when(member.getUser()).thenReturn(user);
        when(event.getUser()).thenReturn(user);
        when(event.getMember()).thenReturn(member);
        when(event.getMessageChannel().getName()).thenReturn(channelName);
        when(event.getChannel().getName()).thenReturn(channelName);
        return event;
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
        if (!java.util.Objects.equals(errorClass(expected.error()), errorClass(actual.error()))) {
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
        if (!expected.state().equals(actual.state())) problems.add("GAME-STATE");
        if (expected.messages() != actual.messages()) {
            problems.add("MESSAGES expected=" + expected.messages() + " actual=" + actual.messages());
        }
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
        if (spec.kind() == Kind.STEPS) {
            summary.append("  tiles differing: ")
                    .append(differingTiles(expected, actual))
                    .append('\n');
            return;
        }
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

    private static String differingTiles(RenderOutcome expected, RenderOutcome actual) {
        Set<String> before = Set.of(expected.blocks().split(","));
        List<String> changed = new ArrayList<>();
        for (String entry : actual.blocks().split(",")) {
            if (!before.contains(entry)) changed.add(entry.substring(0, Math.max(0, entry.indexOf(':'))));
        }
        return changed.isEmpty() ? "(tile set differs)" : String.join(" ", changed);
    }

    private static void writePng(Path save, Spec spec, Path target) throws IOException {
        if (spec.kind() == Kind.STEPS) return;
        Files.createDirectories(target.getParent());
        Game game = loadFresh(save);
        apply(spec.mutation(), game);
        try (AutoCloseable mode = spec.eager() ? RenderRandom.eager() : RenderRandom.neutral();
                RenderedCanvas rendered = produce(game, spec)) {
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
                builder.append("state ").append(record.state()).append('\n');
                builder.append("messages ").append(record.messages()).append('\n');
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
        String state = "";
        int messages = 0;
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
                    state = "";
                    messages = 0;
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
                case "state" -> state = value;
                case "messages" -> messages = Integer.parseInt(value);
                case "time" -> {
                    String[] parts = value.split(" ");
                    time = new long[] {Long.parseLong(parts[0]), Long.parseLong(parts[1]), Long.parseLong(parts[2])};
                }
                case "END" ->
                    records.put(
                            id,
                            new RenderOutcome(
                                    id, error, width, height, sha, blocks, overlays, random, state, messages, time[0],
                                    time[1], time[2]));
                default -> {}
            }
        }
        return records;
    }
}
