package ti4.image;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentMatchers;
import org.mockito.MockSettings;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.RenderRandom;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageUploads;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.model.MapTemplateModel.MapTemplateTile;
import ti4.model.Mapper;
import ti4.model.TileHelper;
import ti4.model.TileModel;
import ti4.service.UnitDecalService;
import ti4.service.draft.DraftLoadService;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftSliceHelper;
import ti4.service.draft.MantisBuildImageGeneratorService;
import ti4.service.draft.MantisMapBuildContext;
import ti4.service.draft.NucleusImageGeneratorService;
import ti4.service.draft.SliceImageGeneratorService;
import ti4.service.draft.TestData;
import ti4.service.emoji.ApplicationEmojiCacheService.CachedEmoji;
import ti4.service.emoji.ApplicationEmojiService;
import ti4.service.milty.MiltyDraftHelper;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyDraftManager.PlayerDraft;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;
import ti4.testUtils.BaseTi4Test;

/**
 * Golden images for the pictures that are drawn outside the map and player-area renderers: the slice, Nucleus and
 * Mantis draft images, the map template previews, the Jimbo tile grid and the colour and decal sample grids. Gated on
 * golden.scattered.saves so CI never runs it.
 *
 * <pre>
 * mvn -o test -Dtest=ScatteredImagesGoldenHarness -Dgolden.scattered.saves=DIR -Dgolden.scattered.out=DIR
 *     -Dgolden.scattered.mode=baseline|compare [-Dgolden.scattered.baseline=FILE] [-Dgolden.only=a,b] [-Dgolden.ids=REGEX over case ids]
 *     [-Dgolden.scattered.pngs=REGEX over case ids]
 * </pre>
 *
 * Synthetic cases build their game and draft state in code (the finished six player draft from the test resources,
 * variants of it, and one generated draft per bundled template) and need no save. Corpus cases reload a save per case
 * and draw the Mantis map image of every game, and the slice and Nucleus images of games with enough players, using
 * that game's real players as the draft participants.
 *
 * Randomness runs in RenderRandom.neutral() per case and the per-tag call counts are part of each record; network
 * images come from FakeImageNetwork.deterministicImages(), so emoji are placeholders derived from their URL. Uploads
 * are captured at ImageUploads and the Discord send is a no-op. The sample commands place their picture at a
 * random offset of a starfield page, so their background is replaced by a transparent one: the cropped picture then
 * no longer depends on the offset. Each record holds the canvas size, a SHA-256 of the ARGB raster, the random call
 * counts, the number of MessageHelper calls and, for deterministic names, the upload name and a digest of its
 * description.
 */
@EnabledIfSystemProperty(named = "golden.scattered.saves", matches = ".+")
@Isolated
class ScatteredImagesGoldenHarness extends BaseTi4Test {

    private static final List<String> USER_IDS = List.of(
            "900000000000000001",
            "900000000000000002",
            "900000000000000003",
            "900000000000000004",
            "900000000000000005",
            "900000000000000006",
            "900000000000000007",
            "900000000000000008");
    private static final List<String> USER_NAMES =
            List.of("Alice", "Bob", "Carol the Unreasonably Long Named", "Dave", "Eve", "Frank", "Grace", "Heidi");
    private static final String BASE_FACTIONS = "keleresflavor_null,xxcha,yin,winnu,l1z1x,hacan,keleresm,sardakk";
    private static final String DRAFT_GAME_NAME = "goldendraft";
    private static final int DRAFT_PLAYERS = 6;
    private static final int GRID_TILE_COUNT = 13;
    private static final List<String> SAMPLE_HUES = List.of("RED", "BLUE", "MULTI");
    private static final List<String> SAMPLE_DECAL_COLORS = List.of("blue", "black", "white");
    private static final Predicate<String> FIRST_THREE_PICKERS =
            shortId -> List.of("u0", "u1", "u2").contains(shortId);
    private static final AtomicInteger MESSAGES = new AtomicInteger();

    private final Map<String, String> results = new LinkedHashMap<>();
    private Pattern pngFilter = Pattern.compile("(?!)");
    private Path pngDir;
    private Path mismatchDir;
    private Map<String, String> expected;
    private final Pattern idFilter = Pattern.compile(System.getProperty("golden.ids", ".*"));
    private BufferedImage capturedImage;
    private String capturedName;

    @Test
    void renderScatteredImages() throws Exception {
        Path savesDir = Path.of(System.getProperty("golden.scattered.saves"));
        Path outDir = Path.of(requiredProperty("golden.scattered.out"));
        String mode = System.getProperty("golden.scattered.mode", "baseline");
        Assertions.assertTrue("baseline".equals(mode) || "compare".equals(mode), "golden.scattered.mode");
        pngFilter = Pattern.compile(System.getProperty("golden.scattered.pngs", "(?!)"));
        pngDir = outDir.resolve(mode);
        mismatchDir = outDir.resolve("compare");
        Files.createDirectories(pngDir);
        expected = "compare".equals(mode) ? readBaseline(outDir) : null;
        List<Path> saves = listSaves(savesDir, System.getProperty("golden.only", ""));

        try (MockedStatic<ImageUploads> uploads = mockStatic(ImageUploads.class, stubOnly(Mockito.CALLS_REAL_METHODS));
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class, stubOnly(invocation -> {
                    MESSAGES.incrementAndGet();
                    if ("sendFileUploadToChannel".equals(invocation.getMethod().getName())) return null;
                    return invocation.callRealMethod();
                }));
                MockedStatic<ApplicationEmojiService> emojis =
                        mockStatic(ApplicationEmojiService.class, stubOnly(Mockito.CALLS_REAL_METHODS));
                FakeImageNetwork network = FakeImageNetwork.deterministicImages()) {
            emojis.when(() -> ApplicationEmojiService.getApplicationEmoji(anyString()))
                    .thenAnswer(invocation -> applicationEmoji(invocation.getArgument(0)));
            uploads.when(() -> ImageUploads.createFileUpload(any(BufferedImage.class), anyString()))
                    .thenAnswer(invocation -> capture(invocation.getArgument(0), invocation.getArgument(1)));
            uploads.when(() -> ImageUploads.createFileUpload(any(BufferedImage.class), anyString(), anyString()))
                    .thenAnswer(invocation -> capture(invocation.getArgument(0), invocation.getArgument(1)));

            syntheticSlices();
            syntheticNucleus();
            syntheticMantis();
            templatePreviews();
            sampleGrids();
            for (Path save : saves) {
                corpusImages(save);
            }
        }

        StringBuilder report = new StringBuilder();
        results.forEach(
                (id, value) -> report.append(id).append(' ').append(value).append('\n'));
        Files.writeString(outDir.resolve(mode).resolve("scattered.txt"), report.toString());
        System.out.println("[golden-scattered] cases=" + results.size());
        if ("compare".equals(mode)) {
            compare(outDir, report.toString());
        }
    }

    private static Map<String, String> readBaseline(Path outDir) throws Exception {
        Path baselineFile = Path.of(System.getProperty(
                "golden.scattered.baseline",
                outDir.resolve("baseline").resolve("scattered.txt").toString()));
        Map<String, String> baseline = new LinkedHashMap<>();
        for (String line : Files.readAllLines(baselineFile)) {
            int space = line.indexOf(' ');
            if (space > 0) baseline.put(line.substring(0, space), line.substring(space + 1));
        }
        return baseline;
    }

    private void compare(Path outDir, String report) throws Exception {
        Map<String, String> baseline = expected;
        boolean filtered = !System.getProperty("golden.only", "").isBlank();
        List<String> mismatches = new ArrayList<>();
        int identical = 0;
        for (Map.Entry<String, String> entry : results.entrySet()) {
            String expected = baseline.get(entry.getKey());
            if (entry.getValue().equals(expected)) {
                identical++;
                continue;
            }
            mismatches.add(entry.getKey() + " expected=" + expected + " actual=" + entry.getValue());
        }
        if (!filtered) {
            for (String id : baseline.keySet()) {
                if (!results.containsKey(id)) mismatches.add(id + " missing");
            }
        }
        String summary = "TOTAL cases=" + results.size() + " identical=" + identical + " mismatches="
                + mismatches.size() + "\n" + String.join("\n", mismatches) + "\n";
        Files.createDirectories(outDir.resolve("compare"));
        Files.writeString(outDir.resolve("compare").resolve("summary.txt"), summary);
        System.out.println("[golden-scattered] " + summary);
        Assertions.assertTrue(mismatches.isEmpty(), summary);
        Assertions.assertFalse(report.isBlank());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Slice images
    // ------------------------------------------------------------------------------------------------------------

    private void syntheticSlices() {
        String finished = TestData.getTestFile(TestData.FINISHED_6P_DRAFT_FILE);
        String partial = keepPicks(finished, FIRST_THREE_PICKERS);
        String none = keepPicks(finished, _ -> false);
        String defaultTemplate =
                Mapper.getDefaultMapTemplateForPlayerCount(DRAFT_PLAYERS).getID();

        recordUpload(
                "slice/finished",
                () -> SliceImageGeneratorService.tryGenerateImage(
                        draftManager(draftGame(defaultTemplate, false), finished), "slice_finished", null),
                true);
        recordUpload(
                "slice/partial",
                () -> SliceImageGeneratorService.tryGenerateImage(
                        draftManager(draftGame(defaultTemplate, false), partial), "slice_partial", null),
                true);
        recordUpload(
                "slice/none",
                () -> SliceImageGeneratorService.tryGenerateImage(
                        draftManager(draftGame(defaultTemplate, false), none), "slice_none", null),
                true);
        recordUpload(
                "slice/restricted",
                () -> SliceImageGeneratorService.tryGenerateImage(
                        draftManager(draftGame(defaultTemplate, false), finished),
                        "slice_restricted",
                        List.of("A", "C")),
                true);
        recordUpload(
                "slice/fow-finished",
                () -> SliceImageGeneratorService.tryGenerateImage(
                        draftManager(draftGame(defaultTemplate, true), finished), "slice_fow", null),
                true);
        recordUpload(
                "slice/fow-partial",
                () -> SliceImageGeneratorService.tryGenerateImage(
                        draftManager(draftGame(defaultTemplate, true), partial), "slice_fow_partial", null),
                true);

        for (MapTemplateModel template : sortedTemplates()) {
            if (template.isNucleusTemplate() || template.tileDisplayCoords().size() < 2) continue;
            int players = Math.min(Math.max(template.getPlayerCount(), 2), USER_IDS.size());
            int slices = Math.min(Math.max(template.getPlayerCount(), 2), 12);
            String alias = template.getAlias();
            recordUpload(
                    "slice/template-" + alias,
                    () -> {
                        Game game = draftGame(template.getAlias(), false, players);
                        String draft = generatedSliceDraft(game, template, slices, players);
                        return SliceImageGeneratorService.tryGenerateImage(
                                draftManager(game, draft), "slice_" + alias, null);
                    },
                    true);
        }

        recordUpload("slice/legacy-milty", () -> MiltyDraftHelper.generateImage(legacyMiltyGame(false)), true);
        recordUpload("slice/legacy-milty-fow", () -> MiltyDraftHelper.generateImage(legacyMiltyGame(true)), true);
    }

    private Game legacyMiltyGame(boolean fog) {
        Game game = draftGame(
                Mapper.getDefaultMapTemplateForPlayerCount(DRAFT_PLAYERS).getID(), fog);
        MapTemplateModel template = Mapper.getMapTemplate(game.getMapTemplateID());
        MiltyDraftManager manager = game.getMiltyDraftManager();
        manager.setMapTemplate(template.getAlias());
        List<MiltyDraftSlice> slices = generatedSlices(game, template, 5);
        manager.getSlices().addAll(slices);
        String[] factions = {"xxcha", "keleresm", null};
        for (int i = 0; i < 3; i++) {
            PlayerDraft draft = new PlayerDraft();
            draft.setFaction(factions[i]);
            draft.setSlice(slices.get(i));
            draft.setPosition(i + 1);
            manager.getDraft().put(USER_IDS.get(i), draft);
        }
        return game;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Nucleus images
    // ------------------------------------------------------------------------------------------------------------

    private void syntheticNucleus() {
        for (MapTemplateModel template : sortedTemplates()) {
            if (!template.isNucleusTemplate()) continue;
            int players = Math.min(template.getPlayerCount(), USER_IDS.size());
            String alias = template.getAlias();
            for (String variant : List.of("full", "partial", "empty")) {
                recordUpload(
                        "nucleus/" + alias + "-" + variant,
                        () -> {
                            Game game = nucleusGame(template, players);
                            String draft = nucleusDraft(players, variant);
                            return NucleusImageGeneratorService.tryGenerateImage(
                                    draftManager(game, draft), "nucleus_" + alias + "_" + variant, null);
                        },
                        true);
            }
        }
        recordUpload(
                "nucleus/restricted-throws",
                () -> {
                    MapTemplateModel template = Mapper.getMapTemplate("6pStandardNucleus");
                    Game game = nucleusGame(template, DRAFT_PLAYERS);
                    return NucleusImageGeneratorService.tryGenerateImage(
                            draftManager(game, nucleusDraft(DRAFT_PLAYERS, "full")),
                            "nucleus_restricted",
                            List.of("A"));
                },
                true);
    }

    private static Game nucleusGame(MapTemplateModel template, int players) {
        Game game = draftGame(template.getAlias(), false, players);
        game.getDraftTileManager().reset(game);
        List<MiltyDraftTile> blue = sortedById(game.getDraftTileManager().getBlue());
        List<MiltyDraftTile> red = sortedById(game.getDraftTileManager().getRed());
        int index = 0;
        for (MapTemplateTile templateTile : template.getTemplateTiles()) {
            Tile tile;
            if (templateTile.getStaticTileId() != null) {
                tile = new Tile(AliasHandler.resolveTile(templateTile.getStaticTileId()), templateTile.getPos());
            } else if (templateTile.getNucleusNumbers() != null
                    && !templateTile.getNucleusNumbers().isEmpty()) {
                MiltyDraftTile draftTile =
                        index % 3 == 2 ? red.get((index / 3) % red.size()) : blue.get(index % blue.size());
                tile = new Tile(draftTile.getTile().getTileID(), templateTile.getPos());
                index++;
            } else {
                tile = MapTemplateHelper.getTileFromTemplateTile(templateTile);
            }
            if (tile != null) game.setTile(tile);
        }
        game.setTile(new Tile("18", "000"));
        return game;
    }

    private static String nucleusDraft(int players, String variant) {
        StringBuilder draft = new StringBuilder(playersSegment(USER_IDS.subList(0, players)));
        draft.append("&o:PublicSnakeDraftOrchestrator|0,false");
        draft.append("&d:FactionDraftable|").append(BASE_FACTIONS);
        draft.append("&d:SpeakerOrderDraftable|").append(players);
        draft.append("&d:SeatDraftable|").append(players);
        int picked =
                switch (variant) {
                    case "full" -> players;
                    case "partial" -> Math.max(1, players / 2);
                    default -> 0;
                };
        for (int i = 0; i < picked; i++) {
            draft.append("&pp:u").append(i).append("|Seat|seat").append(players - i);
            draft.append("&pp:u").append(i).append("|SpeakerOrder|pick").append(i + 1);
            if (i == 0) draft.append("&pp:u0|Faction|keleresm");
            if (i == 1) draft.append("&pp:u1|Faction|winnu");
        }
        return draft.toString();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Mantis images
    // ------------------------------------------------------------------------------------------------------------

    private void syntheticMantis() {
        for (MapTemplateModel template : sortedTemplates()) {
            String alias = template.getAlias();
            recordUpload("mantis/" + alias, () -> mantis(mantisGame(template), "mantis_" + alias, true, "27"), true);
        }
        MapTemplateModel standard = Mapper.getDefaultMapTemplateForPlayerCount(DRAFT_PLAYERS);
        recordUpload("mantis/bare", () -> mantis(mantisGame(standard), "mantis_bare", false, null), true);
        recordUpload("mantis/pending-only", () -> mantis(mantisGame(standard), "mantis_pending", false, "26"), true);
        recordUpload(
                "mantis/pending-occupied",
                () -> {
                    Game game = mantisGame(standard);
                    game.setTile(new Tile("27", "br"));
                    return mantis(game, "mantis_occupied", true, "26");
                },
                true);
        recordUpload(
                "mantis/unknown-highlight",
                () -> {
                    Game game = mantisGame(standard);
                    return MantisBuildImageGeneratorService.tryGenerateImage(
                            mantisContext(game), "mantis_unknown", List.of("999", firstPosition(game)), null);
                },
                true);
    }

    private static Game mantisGame(MapTemplateModel template) {
        Game game = draftGame(template.getAlias(), false, Math.min(template.getPlayerCount(), USER_IDS.size()));
        for (MapTemplateTile templateTile : template.getTemplateTiles()) {
            Tile tile = MapTemplateHelper.getTileFromTemplateTile(templateTile);
            if (tile != null) game.setTile(tile);
        }
        game.setTile(new Tile("18", "000"));
        return game;
    }

    private static FileUpload mantis(Game game, String key, boolean highlights, String pendingTileId) {
        List<String> positions = highlights ? firstPositions(game, 3) : null;
        return MantisBuildImageGeneratorService.tryGenerateImage(mantisContext(game), key, positions, pendingTileId);
    }

    private static MantisMapBuildContext mantisContext(Game game) {
        return new MantisMapBuildContext(
                game,
                game.getMapTemplateID() == null ? null : Mapper.getMapTemplate(game.getMapTemplateID()),
                2,
                Function.identity(),
                null,
                null,
                _ -> {},
                _ -> {},
                _ -> {},
                _ -> Optional.empty(),
                List.of(),
                List::of,
                _ -> {});
    }

    private static List<String> firstPositions(Game game, int count) {
        return game.getTileMap().keySet().stream()
                .filter(position -> position.matches("\\d+"))
                .sorted()
                .limit(count)
                .toList();
    }

    private static String firstPosition(Game game) {
        return firstPositions(game, 1).getFirst();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Template previews
    // ------------------------------------------------------------------------------------------------------------

    private void templatePreviews() {
        for (MapTemplateModel template : sortedTemplates()) {
            String alias = template.getAlias();
            recordUpload(
                    "preview/" + alias,
                    () -> MapTemplateHelper.generateTemplatePreviewImage(null, draftGame(alias, false), template),
                    true);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Image grids
    // ------------------------------------------------------------------------------------------------------------

    private void sampleGrids() throws Exception {
        List<TileModel> tiles = sortedTileModels();
        int stride = Math.max(1, tiles.size() / GRID_TILE_COUNT);
        List<TileModel> spread = new ArrayList<>();
        for (int i = 0; i < GRID_TILE_COUNT; i++) spread.add(tiles.get(i * stride));
        Method tilesImage = reflectiveMethod("ti4.helpers.async.JimboImageHelper", "tilesImage", List.class);
        recordUpload("grid/jimbo-tiles-13", () -> invokeStatic(tilesImage, spread), true);
        recordUpload("grid/jimbo-tiles-5", () -> invokeStatic(tilesImage, spread.subList(0, 5)), true);
        recordUpload("grid/jimbo-tiles-1", () -> invokeStatic(tilesImage, spread.subList(0, 1)), true);
        recordUpload("grid/jimbo-tiles-empty", () -> invokeStatic(tilesImage, List.of()), true);

        try (MockedStatic<ImageHelper> helper = mockStatic(ImageHelper.class, stubOnly(invocation -> {
                    if ("read".equals(invocation.getMethod().getName())
                            && invocation.getArguments().length == 1
                            && String.valueOf((Object) invocation.getArgument(0))
                                    .endsWith("starfield.png")) {
                        return new BufferedImage(2400, 3600, BufferedImage.TYPE_INT_ARGB);
                    }
                    return invocation.callRealMethod();
                }));
                MockedStatic<UnitDecalService> decals =
                        mockStatic(UnitDecalService.class, stubOnly(Mockito.CALLS_REAL_METHODS))) {
            decals.when(() -> UnitDecalService.userMayUseDecal(anyString(), anyString()))
                    .thenAnswer(invocation -> decalIsPublic(invocation.getArgument(1)));
            Object colors = newCommand("ti4.discord.interactions.commands.help.SampleColors");
            Method colorsRun = executeMethod(colors);
            recordUpload(
                    "grid/colors-all", () -> runCommand(colors, colorsRun, commandEvent(Constants.HUE, null)), false);
            for (String hue : SAMPLE_HUES) {
                recordUpload(
                        "grid/colors-" + hue.toLowerCase(),
                        () -> runCommand(colors, colorsRun, commandEvent(Constants.HUE, hue)),
                        false);
            }
            Object sampleDecals = newCommand("ti4.discord.interactions.commands.help.SampleDecals");
            Method decalsRun = executeMethod(sampleDecals);
            List<String> decalIds = Mapper.getDecals().stream()
                    .filter(ScatteredImagesGoldenHarness::decalIsPublic)
                    .sorted()
                    .toList();
            String firstWord = Mapper.getDecalName(decalIds.getFirst()).split(" ")[0];
            Map<String, String> decalHues = new LinkedHashMap<>();
            decalHues.put("all", null);
            decalHues.put("other", "Other");
            decalHues.put("word", firstWord);
            for (String color : SAMPLE_DECAL_COLORS) {
                decalHues.forEach((label, hue) -> recordUpload(
                        "grid/decals-" + label + "-" + color,
                        () -> runCommand(sampleDecals, decalsRun, decalEvent(hue, color)),
                        false));
            }
        }
    }

    private static MockSettings stubOnly(Answer<?> answer) {
        return Mockito.withSettings().stubOnly().defaultAnswer(answer);
    }

    private static boolean decalIsPublic(String decalId) {
        return !List.of(
                        "caballed",
                        "cb_10",
                        "cb_11",
                        "cb_52",
                        "cb_93",
                        "cb_94",
                        "cb_97",
                        "cb_12",
                        "cb_34",
                        "cb_35",
                        "cb_36",
                        "cb_37",
                        "cb_38",
                        "cb_39",
                        "cb_40",
                        "cb_42",
                        "cb_54",
                        "cb_103")
                .contains(decalId);
    }

    private static SlashCommandInteractionEvent commandEvent(String optionName, String value) {
        SlashCommandInteractionEvent event = mock(SlashCommandInteractionEvent.class);
        if (value != null) {
            OptionMapping mapping = mock(OptionMapping.class);
            when(mapping.getAsString()).thenReturn(value);
            when(event.getOption(optionName)).thenReturn(mapping);
        }
        return event;
    }

    private static SlashCommandInteractionEvent decalEvent(String hue, String color) {
        SlashCommandInteractionEvent event = commandEvent(Constants.DECAL_HUE, hue);
        User user = mock(User.class);
        when(user.getId()).thenReturn(USER_IDS.getFirst());
        when(event.getUser()).thenReturn(user);
        doReturn(color).when(event).getOption(eq(Constants.COLOR), ArgumentMatchers.<String>any(), any(Function.class));
        return event;
    }

    private static FileUpload runCommand(Object command, Method execute, SlashCommandInteractionEvent event) {
        try {
            execute.invoke(command, event);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.getCause() == null ? e : e.getCause());
        }
        return FileUpload.fromData(new byte[0], "command.png");
    }

    private static Object newCommand(String className) throws ReflectiveOperationException {
        Constructor<?> constructor = Class.forName(className).getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Method executeMethod(Object command) throws ReflectiveOperationException {
        Method execute = command.getClass().getDeclaredMethod("execute", SlashCommandInteractionEvent.class);
        execute.setAccessible(true);
        return execute;
    }

    private static Method reflectiveMethod(String className, String name, Class<?>... parameters)
            throws ReflectiveOperationException {
        Method method = Class.forName(className).getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    private static FileUpload invokeStatic(Method method, Object... arguments) {
        try {
            return (FileUpload) method.invoke(null, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e.getCause() == null ? e : e.getCause());
        }
    }

    private static List<TileModel> sortedTileModels() {
        return TileHelper.getAllTileModels().stream()
                .sorted(Comparator.comparing(TileModel::getAlias))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Corpus games
    // ------------------------------------------------------------------------------------------------------------

    private void corpusImages(Path save) {
        String name = save.getFileName().toString().replace(".txt", "");
        Game probe = load(save);
        if (probe == null) {
            results.put("corpus/" + name + "/load", "LOAD FAILED");
            return;
        }
        List<String> realIds =
                probe.getRealPlayers().stream().map(Player::getUserID).toList();
        recordUpload(
                "corpus/" + name + "/mantis",
                () -> {
                    Game game = load(save);
                    return mantis(game, "mantis_" + name, true, game.getTileByPosition("br") == null ? "26" : null);
                },
                true);
        if (realIds.size() < DRAFT_PLAYERS) return;
        String finished = TestData.getTestFile(TestData.FINISHED_6P_DRAFT_FILE);
        String remapped = remapUsers(finished, realIds.subList(0, DRAFT_PLAYERS));
        String defaultTemplate =
                Mapper.getDefaultMapTemplateForPlayerCount(DRAFT_PLAYERS).getID();
        recordUpload(
                "corpus/" + name + "/slice",
                () -> {
                    Game game = load(save);
                    game.setMapTemplateID(defaultTemplate);
                    return SliceImageGeneratorService.tryGenerateImage(
                            DraftLoadService.loadDraftManager(game, remapped), "slice_" + name, null);
                },
                true);
        if (realIds.size() != DRAFT_PLAYERS) return;
        recordUpload(
                "corpus/" + name + "/nucleus",
                () -> {
                    Game game = load(save);
                    game.setMapTemplateID("6pStandardNucleus");
                    return NucleusImageGeneratorService.tryGenerateImage(
                            DraftLoadService.loadDraftManager(game, remapped), "nucleus_" + name, null);
                },
                true);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Draft building blocks
    // ------------------------------------------------------------------------------------------------------------

    private static Game draftGame(String templateAlias, boolean fog) {
        return draftGame(templateAlias, fog, DRAFT_PLAYERS);
    }

    private static Game draftGame(String templateAlias, boolean fog, int players) {
        Game game = new Game();
        game.setName(DRAFT_GAME_NAME);
        for (int i = 0; i < players; i++) {
            game.addPlayer(USER_IDS.get(i), USER_NAMES.get(i));
        }
        game.setMapTemplateID(templateAlias);
        game.setFowMode(fog);
        return game;
    }

    private static DraftManager draftManager(Game game, String draft) {
        String adapted = remapUsers(draft, game.getPlayerIDs().stream().sorted().toList());
        return DraftLoadService.loadDraftManager(game, adapted);
    }

    private static String playersSegment(List<String> userIds) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < userIds.size(); i++) entries.add(userIds.get(i) + ",u" + i);
        return "p:" + String.join("|", entries);
    }

    private static final Pattern PLAYERS_SEGMENT = Pattern.compile("^p:([^&]*)");

    private static List<String> usersOf(String draft) {
        Matcher matcher = PLAYERS_SEGMENT.matcher(draft);
        Assertions.assertTrue(matcher.find());
        List<String> users = new ArrayList<>();
        for (String entry : matcher.group(1).split("\\|")) users.add(entry.split(",")[0]);
        return users;
    }

    private static String remapUsers(String draft, List<String> newUsers) {
        List<String> oldUsers = usersOf(draft);
        String remapped = draft;
        for (int i = 0; i < oldUsers.size() && i < newUsers.size(); i++) {
            remapped = remapped.replace(oldUsers.get(i) + ",", "@@" + i + "@@,");
        }
        for (int i = 0; i < oldUsers.size() && i < newUsers.size(); i++) {
            remapped = remapped.replace("@@" + i + "@@,", newUsers.get(i) + ",");
        }
        return remapped;
    }

    private static String keepPicks(String draft, Predicate<String> keepShortId) {
        return Stream.of(draft.split("&"))
                .filter(segment -> {
                    if (!segment.startsWith("pp:")) return true;
                    return keepShortId.test(segment.substring(3, segment.indexOf('|')));
                })
                .collect(Collectors.joining("&"));
    }

    private static String generatedSliceDraft(Game game, MapTemplateModel template, int slices, int players) {
        List<String> sliceTiles = generatedSlices(game, template, slices).stream()
                .map(MiltyDraftSlice::ttsString)
                .toList();
        StringBuilder draft = new StringBuilder(playersSegment(USER_IDS.subList(0, players)));
        draft.append("&o:PublicSnakeDraftOrchestrator|0,false");
        draft.append("&d:FactionDraftable|").append(BASE_FACTIONS);
        draft.append("&d:SliceDraftable|").append(String.join(";", sliceTiles));
        String[] factions = {"winnu", "xxcha", "keleresm", "hacan"};
        for (int i = 0; i < Math.min(players, 4); i++) {
            draft.append("&pp:u").append(i).append("|Slice|").append((char) ('A' + i));
            draft.append("&pp:u").append(i).append("|Faction|").append(factions[i]);
        }
        return draft.toString();
    }

    private static List<MiltyDraftSlice> generatedSlices(Game game, MapTemplateModel template, int count) {
        game.getDraftTileManager().reset(game);
        List<MiltyDraftTile> blue = byFeatures(game.getDraftTileManager().getBlue());
        List<MiltyDraftTile> red = sortedById(game.getDraftTileManager().getRed());
        int tilesPerSlice = Math.max(1, template.tileDisplayCoords().size() - 1);
        int bluePerSlice = Math.min(template.bluePerPlayer(), tilesPerSlice);
        List<MiltyDraftSlice> slices = new ArrayList<>();
        for (int s = 0; s < count; s++) {
            List<String> ids = new ArrayList<>();
            for (int b = 0; b < bluePerSlice; b++) {
                ids.add(blue.get((s * bluePerSlice + b) % blue.size()).getTile().getTileID());
            }
            for (int r = 0; ids.size() < tilesPerSlice; r++) {
                ids.add(red.get((s * 2 + r) % red.size()).getTile().getTileID());
            }
            MiltyDraftSlice slice = DraftSliceHelper.parseSlicesFromString(String.join(",", ids))
                    .getFirst();
            slice.setName(Character.toString((char) ('A' + s)));
            slices.add(slice);
        }
        return slices;
    }

    private static List<MiltyDraftTile> byFeatures(List<MiltyDraftTile> tiles) {
        return tiles.stream()
                .sorted(Comparator.comparingInt(ScatteredImagesGoldenHarness::featureCount)
                        .reversed()
                        .thenComparing(tile -> tile.getTile().getTileID()))
                .toList();
    }

    private static int featureCount(MiltyDraftTile tile) {
        int skips = tile.getTile().getPlanetUnitHolders().stream()
                .mapToInt(planet -> ((Planet) planet).getTechSpecialities().size())
                .sum();
        return skips
                + (tile.isHasAlphaWH() ? 1 : 0)
                + (tile.isHasBetaWH() ? 1 : 0)
                + (tile.isHasOtherWH() ? 1 : 0)
                + (tile.isLegendary() ? 1 : 0);
    }

    private static List<MiltyDraftTile> sortedById(List<MiltyDraftTile> tiles) {
        return tiles.stream()
                .sorted(Comparator.comparing(tile -> tile.getTile().getTileID()))
                .toList();
    }

    private static List<MapTemplateModel> sortedTemplates() {
        return Mapper.getMapTemplates().stream()
                .sorted(Comparator.comparing(MapTemplateModel::getAlias))
                .toList();
    }

    // ------------------------------------------------------------------------------------------------------------
    // Recording
    // ------------------------------------------------------------------------------------------------------------

    private FileUpload capture(BufferedImage image, String name) {
        capturedImage = image;
        capturedName = name;
        return FileUpload.fromData(new byte[0], "captured.png");
    }

    private static CachedEmoji applicationEmoji(String name) {
        String id = Long.toString(100_000_000_000_000_000L + Math.floorMod(name.hashCode(), 1_000_000_000));
        return new CachedEmoji(name, id, "<:" + name + ":" + id + ">", 0);
    }

    private void recordUpload(String id, Supplier<FileUpload> renderer, boolean namedDeterministically) {
        if (!idFilter.matcher(id).matches()) return;
        capturedImage = null;
        capturedName = null;
        MESSAGES.set(0);
        try (AutoCloseable random = RenderRandom.neutral()) {
            FileUpload upload = renderer.get();
            if (upload == null || capturedImage == null) {
                results.put(id, "NULL");
                return;
            }
            StringBuilder value = new StringBuilder();
            value.append(capturedImage.getWidth())
                    .append('x')
                    .append(capturedImage.getHeight())
                    .append(' ')
                    .append(sha(capturedImage));
            value.append(" random=").append(randomCounts());
            value.append(" messages=").append(MESSAGES.get());
            if (namedDeterministically) {
                value.append(" name=").append(capturedName);
                String description = upload.getDescription();
                value.append(" desc=")
                        .append(
                                description == null
                                        ? "none"
                                        : sha(description.getBytes(StandardCharsets.UTF_8))
                                                .substring(0, 16));
            }
            results.put(id, value.toString());
            if (pngFilter.matcher(id).matches()) {
                writePng(pngDir, id, capturedImage);
            } else if (expected != null && !value.toString().equals(expected.get(id))) {
                writePng(mismatchDir, id, capturedImage);
            }
        } catch (Throwable e) {
            System.out.println("[golden-scattered] " + id + " failed: " + e);
            e.printStackTrace(System.out);
            results.put(id, "ERROR " + e.getClass().getSimpleName());
        }
    }

    private static String randomCounts() {
        StringBuilder builder = new StringBuilder();
        RenderRandom.callCounts().forEach((tag, count) -> {
            if (builder.length() > 0) builder.append(';');
            builder.append(tag).append('=').append(count);
        });
        return builder.toString();
    }

    private static String sha(BufferedImage image) {
        BufferedImage argb = image;
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = argb.createGraphics();
            g2.drawImage(image, 0, 0, null);
            g2.dispose();
        }
        int width = argb.getWidth();
        int height = argb.getHeight();
        int[] pixels = argb.getRGB(0, 0, width, height, null, 0, width);
        ByteBuffer bytes = ByteBuffer.allocate(pixels.length * 4);
        bytes.asIntBuffer().put(pixels);
        return sha(bytes.array());
    }

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writePng(Path dir, String id, BufferedImage image) {
        try {
            Files.createDirectories(dir);
            ImageIO.write(
                    image, "png", dir.resolve(id.replace('/', '_') + ".png").toFile());
        } catch (Exception e) {
            System.out.println("[golden-scattered] could not write " + id + ": " + e);
        }
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        Assertions.assertNotNull(value, "missing -D" + name);
        return value;
    }

    private static List<Path> listSaves(Path dir, String only) throws Exception {
        List<String> wanted = only.isBlank() ? List.of() : List.of(only.split(","));
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".txt"))
                    .filter(path -> wanted.isEmpty()
                            || wanted.contains(path.getFileName().toString().replace(".txt", "")))
                    .sorted()
                    .toList();
        }
    }

    private static Game load(Path save) {
        try {
            Class<?> loader = Class.forName("ti4.game.persistence.GameLoadService");
            Method readGame = loader.getDeclaredMethod("readGame", File.class);
            readGame.setAccessible(true);
            return (Game) readGame.invoke(null, save.toFile());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot reach GameLoadService.readGame", e);
        }
    }
}
