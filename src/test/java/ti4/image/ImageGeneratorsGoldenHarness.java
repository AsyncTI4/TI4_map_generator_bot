package ti4.image;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mockStatic;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.RenderRandom;
import ti4.image.card.BannerGenerator;
import ti4.image.card.TransactionGenerator;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.HexOverlays;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PlayerTokens;
import ti4.image.draw.ShapeDrawing;
import ti4.image.draw.Strokes;
import ti4.image.draw.TextWrap;
import ti4.image.draw.VerticalAlign;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.image.io.ImageUploads;
import ti4.image.io.RemoteImages;
import ti4.image.map.CompactOverviewGenerator;
import ti4.image.map.MapOverviewGenerator;
import ti4.image.tile.HyperlaneTileGenerator;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.model.Mapper;
import ti4.service.emoji.ApplicationEmojiCacheService.CachedEmoji;
import ti4.service.emoji.ApplicationEmojiService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.map.CustomHyperlaneService;
import ti4.service.user.AFKService;
import ti4.testUtils.BaseTi4Test;

/**
 * Golden images for the pictures the map harness does not draw: transaction sheets and the trade-offer meme, the
 * faction, agenda and phase banners, the GM and player overviews, generated hyperlane tiles, and a contact sheet that
 * calls every shared drawing helper with fixed arguments. Gated on golden.gen.saves so CI never runs it.
 *
 * <pre>
 * mvn -o test -Dtest=ImageGeneratorsGoldenHarness -Dgolden.gen.saves=DIR -Dgolden.gen.out=DIR
 *     -Dgolden.gen.mode=baseline|compare [-Dgolden.gen.baseline=FILE] [-Dgolden.only=a,b] [-Dgolden.gen.pngs=REGEX]
 * </pre>
 *
 * Every case reloads its save, because several generators write to the game (fog memory, stored "nothing" messages).
 * Randomness runs in RenderRandom.neutral(); network images come from FakeImageNetwork.deterministicImages(), so emoji
 * and custom faction icons are drawn as URL-derived placeholders. Banners are captured at ImageUploads, and the
 * Discord send is a no-op; a send that fails afterwards (no guild in tests) still records the captured picture. Each case records the canvas size and a SHA-256 of its ARGB raster; in compare mode a PNG of
 * every mismatching case is written next to the report (and in any mode for ids matching golden.gen.pngs).
 */
@EnabledIfSystemProperty(named = "golden.gen.saves", matches = ".+")
@Isolated
class ImageGeneratorsGoldenHarness extends BaseTi4Test {

    private static final int PAIRS_PER_GAME = 2;
    private static final List<String> PHASES = List.of("strategy", "action", "agenda", "status");
    private static final List<String> HYPERLANE_MATRICES = List.of(
            "0,0,0,1,0,0;0,0,0,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0",
            "0,1,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0",
            "0,0,1,0,0,0;0,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0",
            "0,0,0,0,0,0;0,0,0,0,1,0;0,0,0,0,0,0;0,0,0,0,0,1;0,1,0,0,0,0;0,0,0,1,0,0",
            "1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,1,0,0;0,0,1,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0",
            "0,1,1,1,0,0;1,0,0,0,0,0;1,0,0,0,0,0;1,0,0,0,0,0;0,0,0,0,0,0;0,0,0,0,0,0");

    private final Map<String, String> results = new LinkedHashMap<>();
    private final Map<String, BufferedImage> images = new LinkedHashMap<>();
    private BufferedImage capturedUpload;

    @Test
    void renderGenerators() throws Exception {
        Path savesDir = Path.of(System.getProperty("golden.gen.saves"));
        Path outDir = Path.of(System.getProperty("golden.gen.out"));
        String mode = System.getProperty("golden.gen.mode", "baseline");
        Assertions.assertTrue("baseline".equals(mode) || "compare".equals(mode), "golden.gen.mode");
        Pattern pngs = Pattern.compile(System.getProperty("golden.gen.pngs", "(?!)"));
        Files.createDirectories(outDir.resolve(mode));
        List<Path> saves = listSaves(savesDir, System.getProperty("golden.only", ""));

        try (MockedStatic<DateTimeHelper> dateTime = mockStatic(DateTimeHelper.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<AFKService> afk = mockStatic(AFKService.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<ImageUploads> uploads = mockStatic(ImageUploads.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<MessageHelper> messages = mockStatic(MessageHelper.class, Mockito.CALLS_REAL_METHODS);
                MockedStatic<ApplicationEmojiService> emojis =
                        mockStatic(ApplicationEmojiService.class, Mockito.CALLS_REAL_METHODS);
                FakeImageNetwork network = FakeImageNetwork.deterministicImages();
                AutoCloseable random = RenderRandom.neutral()) {
            dateTime.when(DateTimeHelper::getFormattedTimestamp).thenReturn("2000.01.01 - 00.00.00");
            afk.when(() -> AFKService.userIsAFK(anyString())).thenReturn(false);
            emojis.when(() -> ApplicationEmojiService.getApplicationEmoji(anyString()))
                    .thenAnswer(invocation -> applicationEmoji(invocation.getArgument(0)));
            uploads.when(() -> ImageUploads.createFileUpload(any(BufferedImage.class), anyString()))
                    .thenAnswer(invocation -> capture(invocation.getArgument(0)));
            uploads.when(() -> ImageUploads.createFileUpload(any(BufferedImage.class), anyString(), anyString()))
                    .thenAnswer(invocation -> capture(invocation.getArgument(0)));
            messages.when(() -> MessageHelper.sendFileUploadToChannel(
                            nullable(MessageChannel.class), any(FileUpload.class)))
                    .thenAnswer(_ -> null);

            drawingHelpersSheet(saves.getFirst());
            for (Path save : saves) {
                renderGame(save);
            }
        }

        StringBuilder report = new StringBuilder();
        results.forEach(
                (id, value) -> report.append(id).append(' ').append(value).append('\n'));
        Files.writeString(outDir.resolve(mode).resolve("generators.txt"), report.toString());
        for (Map.Entry<String, BufferedImage> entry : images.entrySet()) {
            if (pngs.matcher(entry.getKey()).matches())
                writePng(outDir.resolve(mode), entry.getKey(), entry.getValue());
        }
        if ("compare".equals(mode)) {
            compare(outDir, report.toString());
        }
    }

    private void compare(Path outDir, String report) throws Exception {
        Path baselineFile = Path.of(System.getProperty(
                "golden.gen.baseline",
                outDir.resolve("baseline").resolve("generators.txt").toString()));
        Map<String, String> baseline = new LinkedHashMap<>();
        for (String line : Files.readAllLines(baselineFile)) {
            int space = line.indexOf(' ');
            if (space > 0) baseline.put(line.substring(0, space), line.substring(space + 1));
        }
        List<String> mismatches = new ArrayList<>();
        int identical = 0;
        for (Map.Entry<String, String> entry : results.entrySet()) {
            String expected = baseline.get(entry.getKey());
            if (entry.getValue().equals(expected)) {
                identical++;
                continue;
            }
            mismatches.add(entry.getKey() + " expected=" + expected + " actual=" + entry.getValue());
            BufferedImage image = images.get(entry.getKey());
            if (image != null) writePng(outDir.resolve("compare"), entry.getKey(), image);
        }
        for (String id : baseline.keySet()) {
            if (!results.containsKey(id)) mismatches.add(id + " missing");
        }
        String summary = "TOTAL cases=" + results.size() + " identical=" + identical + " mismatches="
                + mismatches.size() + "\n" + String.join("\n", mismatches) + "\n";
        Files.writeString(outDir.resolve("compare").resolve("summary.txt"), summary);
        System.out.println("[golden-gen] " + summary);
        Assertions.assertTrue(mismatches.isEmpty(), summary);
        Assertions.assertFalse(report.isBlank());
    }

    private void renderGame(Path save) {
        String name = save.getFileName().toString().replace(".txt", "");
        Game probe = load(save);
        if (probe == null) {
            results.put(name + "/load", "LOAD FAILED");
            return;
        }
        List<Player> players = probe.getRealPlayers();
        for (int i = 0; i + 1 < players.size() && i < PAIRS_PER_GAME; i++) {
            int first = i;
            String pair = name + "/pair" + i;
            record(
                    pair + "/stuff",
                    save,
                    game -> TransactionGenerator.drawTransactableStuffImage(real(game, first), real(game, first + 1)));
            record(
                    pair + "/stuff-viewer",
                    save,
                    game -> TransactionGenerator.drawTransactableStuffImage(
                            real(game, first), real(game, first + 1), real(game, first)));
            record(pair + "/trade-offer", save, game -> tradeOffer(game, real(game, first), real(game, first + 1)));
        }
        for (int i = 0; i < Math.min(players.size(), 3); i++) {
            int index = i;
            recordUpload(
                    name + "/banner-faction" + i, save, game -> BannerGenerator.drawFactionBanner(real(game, index)));
        }
        recordUpload(name + "/banner-agenda", save, game -> BannerGenerator.drawAgendaBanner(3, game));
        for (String phase : PHASES) {
            recordUpload(name + "/banner-" + phase, save, game -> BannerGenerator.drawPhaseBanner(phase, 4, null));
        }
        recordUpload(
                name + "/banner-alt", save, game -> BannerGenerator.drawPhaseBanner("status", 12, null, "Scoring"));
        record(name + "/compact-gm", save, CompactOverviewGenerator::gmImage);
        if (probe.isFowMode() && !players.isEmpty()) {
            record(
                    name + "/compact-player",
                    save,
                    game -> CompactOverviewGenerator.playerImage(game, real(game, 0), null));
        }
        record(name + "/overview", save, game -> MapOverviewGenerator.render(game, false));
        if (probe.isFowMode()) {
            record(name + "/overview-sectors", save, game -> MapOverviewGenerator.render(game, true));
        }
        if (name.equals(firstGameName)) {
            for (int i = 0; i < HYPERLANE_MATRICES.size(); i++) {
                String matrix = HYPERLANE_MATRICES.get(i);
                record(name + "/hyperlane" + i, save, game -> hyperlane(game, matrix));
                record(name + "/hyperlane" + i + "-again", save, game -> hyperlane(game, matrix));
                String rotated = CustomHyperlaneService.rotateMatrix60(matrix);
                record(name + "/hyperlane" + i + "-rotated", save, game -> hyperlane(game, rotated));
            }
            record(name + "/hyperlane-empty", save, game -> hyperlane(game, null));
        }
    }

    private String firstGameName;

    private static Player real(Game game, int index) {
        return game.getRealPlayers().get(index);
    }

    private static BufferedImage tradeOffer(Game game, Player sender, Player receiver) {
        String prefix = "sending" + sender.getFaction() + "_receiving" + receiver.getFaction() + "_";
        sender.addTransactionItem(prefix + "TGs_3");
        sender.addTransactionItem(prefix + "Comms_2");
        sender.addTransactionItem(prefix + "ACs_generic2");
        sender.addTransactionItem(prefix + "PNs_generic1");
        sender.addTransactionItem(prefix + "Frags_crf1");
        sender.addTransactionItem(prefix + "SendDebt_2");
        sender.addTransactionItem(prefix + "action_strategy");
        sender.addTransactionItem(prefix + "details_a long free text promise that has to wrap over lines");
        sender.getPlanets().stream()
                .findFirst()
                .ifPresent(planet -> sender.addTransactionItem(prefix + "Planets_" + planet));
        game.setStoredValue(receiver.getFaction() + "NothingMessage", "Absolutely nothing, as agreed");
        return TransactionGenerator.drawTradeOfferMeme(game, sender, receiver);
    }

    /**
     * Seat 0 gets a custom Discord emoji as faction icon (fetched from FakeImageNetwork, 5 px smaller) and seat 1 a
     * unicode emoji (drawn with the emoji font and cropped), so both non-file branches of the icon lookup are pinned.
     */
    private static Game withCustomIcons(Game game) {
        real(game, 0).setFactionEmoji("<:customicon:123456789012345678>");
        real(game, 1).setFactionEmoji("🦊");
        return game;
    }

    /** Every TI4Emoji resolves to a custom emoji with a name-derived id, so emoji pictures are drawn from placeholders. */
    private static CachedEmoji applicationEmoji(String name) {
        String id = Long.toString(100_000_000_000_000_000L + Math.floorMod(name.hashCode(), 1_000_000_000));
        return new CachedEmoji(name, id, "<:" + name + ":" + id + ">", 0);
    }

    private static BufferedImage hyperlane(Game game, String matrix) {
        String position = "101";
        if (matrix != null) game.getCustomHyperlaneData().put(position, matrix);
        return HyperlaneTileGenerator.generateHyperlaneTile(new Tile("hl", position), game);
    }

    private void drawingHelpersSheet(Path save) {
        firstGameName = save.getFileName().toString().replace(".txt", "");
        record("sheet/text", save, ImageGeneratorsGoldenHarness::textSheet);
        record("sheet/tokens", save, ImageGeneratorsGoldenHarness::tokenSheet);
        record("sheet/hex-borders", save, _ -> hexBorderSheet());
        record("sheet/tokens-custom-icons", save, game -> tokenSheet(withCustomIcons(game)));
        record("sheet/stuff-custom-icons", save, game -> {
            withCustomIcons(game);
            return TransactionGenerator.drawTransactableStuffImage(real(game, 0), real(game, 1));
        });
        record("sheet/trade-offer-custom-icons", save, game -> {
            withCustomIcons(game);
            return tradeOffer(game, real(game, 0), real(game, 1));
        });
        recordUpload(
                "sheet/banner-custom-icon",
                save,
                game -> BannerGenerator.drawFactionBanner(real(withCustomIcons(game), 0)));
        recordUpload(
                "sheet/banner-unicode-icon",
                save,
                game -> BannerGenerator.drawFactionBanner(real(withCustomIcons(game), 1)));
        StringBuilder layouts = new StringBuilder();
        Graphics2D g2 = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        for (Font font : List.of(Fonts.ti(16), Fonts.ti(24), Fonts.ti(32))) {
            g2.setFont(font);
            for (int width : List.of(40, 120, 300)) {
                layouts.append(String.join("|", TextWrap.layoutLines(SAMPLE, g2.getFontMetrics(), width)))
                        .append('\n');
            }
        }
        results.put("sheet/layout-text", sha(layouts.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static final String SAMPLE =
            "The quick brown fox jumps over the lazy dog\nSupercalifragilisticexpialidocious words break, too";

    private static BufferedImage textSheet(Game game) {
        BufferedImage image = new BufferedImage(1400, 1200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2.setFont(Fonts.ti(24));
        int y = 40;
        for (HorizontalAlign h : HorizontalAlign.values()) {
            for (VerticalAlign v : VerticalAlign.values()) {
                OutlinedText.draw(g2, "Align " + h + v, 300, y, Color.ORANGE, h, v, Strokes.STROKE_4, Color.BLUE);
                y += 34;
            }
        }
        OutlinedText.draw(g2, "null colours", 20, y, null, null, null, null, null);
        OutlinedText.draw(g2, "plain", 300, y, Color.GREEN, null, null, null, null);
        OutlinedText.draw(
                g2,
                "via Graphics",
                500,
                y,
                Color.CYAN,
                HorizontalAlign.Left,
                VerticalAlign.Top,
                Strokes.STROKE_2,
                Color.BLACK);
        y += 40;
        OutlinedText.drawCenteredWhiteOnBlack(g2, "Centered default", 300, y);
        OutlinedText.drawCenteredWhiteOnBlack(g2, "Centered via Graphics", 800, y);
        y += 40;
        OutlinedText.drawCentered(g2, "Centered red", 300, y, Color.RED, Strokes.STROKE_6, Color.WHITE);
        OutlinedText.drawCentered(g2, "Centered gfx", 800, y, Color.YELLOW, Strokes.STROKE_3, Color.BLACK);
        y += 40;
        OutlinedText.drawCentered(g2, "In a rectangle", new Rectangle(20, y, 400, 60), Fonts.ti(32));
        y += 80;
        g2.setFont(Fonts.ti(20));
        g2.setColor(Color.WHITE);
        int x = 40;
        VerticalText.draw(g2, "Vertical text", x, 700, Fonts.ti(20));
        VerticalText.draw(g2, "Right aligned", x += 40, 700, Fonts.ti(20), true);
        g2.setFont(Fonts.ti(18));
        for (String text : List.of(
                "Short",
                "Two words here please",
                "Line\nbreak given",
                "Nospacesatallinthisone",
                "a bbbbbbbbbbbbb",
                "ccccccccccccc d")) {
            VerticalText.drawOneOrTwoLines(g2, text, x += 60, 700, 120);
            VerticalText.drawOneOrTwoLines(g2, text, x += 60, 700, 120, true);
        }
        VerticalText.drawTwoLines(g2, "First row\nsecond row", x += 60, 700, 150);
        VerticalText.drawWrappedColumns(g2, "Many debt holders listed here in a long string", x += 60, 700, 90);
        VerticalText.drawWrappedColumns(g2, "Unbreakablelongwordwithoutspaces and more", x += 200, 700, 90);
        y = 760;
        ShapeDrawing.redX(g2, 100, y + 50, 60, true);
        ShapeDrawing.redX(g2, 200, y + 50, 40, false);
        g2.setStroke(Strokes.STROKE_5);
        ShapeDrawing.gradientOutline(g2, Color.RED, Color.BLUE, 300, y, 200, 100);
        ShapeDrawing.gradientOutline(g2, Color.GREEN, null, new Rectangle(550, y, 200, 100));
        ShapeDrawing.gradientOutline(g2, null, Color.MAGENTA, new Rectangle(800, y, 200, 100));
        RemoteImages.drawEmoji(g2, MiscEmojis.tg, 1050, y, 50);
        g2.dispose();
        return image;
    }

    private static BufferedImage tokenSheet(Game game) {
        List<Player> players = game.getRealPlayers();
        BufferedImage image = new BufferedImage(1600, 160 * Math.max(1, players.size()), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int y = 10;
        for (Player player : players) {
            int x = 10;
            PlayerTokens.drawCommandCounters(g2, Mapper.getCCID(player.getColor()), x, y, 3, player, false, true);
            PlayerTokens.drawCommandCounters(
                    g2, Mapper.getCCID(player.getColor()), x += 200, y, 2, player, true, false);
            PlayerTokens.drawCommandCounters(
                    g2, Mapper.getCCID(player.getColor()), x += 200, y, 0, player, false, true);
            PlayerTokens.readAndDrawControlToken(g2, player, x += 120, y, false, 1.0f);
            PlayerTokens.readAndDrawControlToken(g2, player, x += 80, y, true, 0.6f);
            FactionIcons.draw(g2, player, x += 80, y, 95, 95);
            FactionIcons.drawFaded(g2, player, x += 100, y, 60, 60, 0.4f);
            FactionIcons.drawFaded(g2, player, x += 70, y, 60, 60, 1.0f);
            FactionIcons.drawUnderlay(g2, player, x += 70, y, 80, 80);
            g2.drawImage(FactionIcons.of(player), x += 90, y, null);
            g2.drawImage(FactionIcons.scaled(player, 0.5f), x += 100, y, null);
            g2.drawImage(FactionIcons.ofFaction(player.getFaction(), 40, 30), x += 60, y, null);
            BufferedImage tintedBackground = HexOverlays.tint(ColorUtil.getPlayerMainColor(player), 0.4f);
            g2.drawImage(tintedBackground, x += 50, y, 100, 90, null);
            Player byMarker = PlayerTokens.ownerOfToken(players, Mapper.getControlID(player.getColor()));
            g2.setColor(byMarker == player ? Color.GREEN : Color.RED);
            g2.fillRect(x += 110, y, 10, 10);
            y += 160;
        }
        g2.dispose();
        return image;
    }

    private static BufferedImage hexBorderSheet() {
        List<ColorModel> colors = Mapper.getColors();
        int columns = 8;
        int rows = (colors.size() + columns - 1) / columns;
        BufferedImage image = new BufferedImage(columns * 200, rows * 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        for (int i = 0; i < colors.size(); i++) {
            ColorModel color = colors.get(i);
            int x = (i % columns) * 200;
            int y = (i / columns) * 400;
            g2.drawImage(HexOverlays.border("solid", color, List.of()), x, y, 200, 200, null);
            g2.drawImage(HexOverlays.border("dash", color, List.of(1, 4), 2.0f), x, y + 200, 200, 200, null);
            String suffix = Mapper.getColor(color.getAlias()).blackWhiteFileSuffix();
            g2.setColor("_blk.png".equals(suffix) ? Color.BLACK : Color.WHITE);
            g2.fillRect(x, y, 8, 8);
        }
        g2.dispose();
        return image;
    }

    private Object capture(BufferedImage image) {
        capturedUpload = image;
        return FileUpload.fromData(new byte[0], "captured.png");
    }

    private void recordUpload(String id, Path save, Consumer<Game> sender) {
        record(id, save, game -> {
            capturedUpload = null;
            try {
                sender.accept(game);
            } catch (RuntimeException e) {
                if (capturedUpload == null) throw e;
            }
            return capturedUpload;
        });
    }

    private void record(String id, Path save, Function<Game, BufferedImage> renderer) {
        try {
            Game game = load(save);
            BufferedImage image = renderer.apply(game);
            if (image == null) {
                results.put(id, "NULL");
                return;
            }
            results.put(id, image.getWidth() + "x" + image.getHeight() + " " + sha(image));
            images.put(id, image);
        } catch (Throwable e) {
            results.put(id, "ERROR " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static String sha(BufferedImage image) {
        BufferedImage argb = image;
        if (image.getType() != BufferedImage.TYPE_INT_ARGB) {
            argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = argb.createGraphics();
            g2.drawImage(image, 0, 0, null);
            g2.dispose();
        }
        int[] pixels = ((DataBufferInt) argb.getRaster().getDataBuffer()).getData();
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
            ImageIO.write(
                    image, "png", dir.resolve(id.replace('/', '_') + ".png").toFile());
        } catch (Exception e) {
            System.out.println("[golden-gen] could not write " + id + ": " + e);
        }
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
