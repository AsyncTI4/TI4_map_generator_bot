package ti4.image.card;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.TextWrap;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.io.RemoteImages;
import ti4.model.Mapper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.fow.FogMemoryService;

public final class TransactionGenerator {

    private static final BasicStroke TEXT_OUTLINE = Strokes.STROKE_2;
    private static final BasicStroke COUNT_OUTLINE = Strokes.STROKE_5;
    private static final double NINETY_DEGREES_RADIANS = 1.570_796_326_794_896_6;
    private static final String DEFAULT_NOTHING_MESSAGE = "Nothing";
    private static final int SHEET_WIDTH = 500;
    private static final int SHEET_HEIGHT = 160;
    private static final int SHEET_MARGIN = 5;
    private static final int ICON_SIZE = 40;
    private static final int NAME_INSET = 50;
    private static final int NAME_CENTER_Y = 25;
    private static final int EMOJI_SIZE = 50;
    private static final int EMOJI_STEP = EMOJI_SIZE + 5;
    private static final int FIRST_ROW_Y = 50;
    private static final int SECOND_ROW_Y = 105;
    private static final List<String> EMOJI_ITEMS = List.of("TGs", "Comms", "ACs", "PNs", "Frags");
    private static final int MEME_EMOJIS_PER_ROW = 3;
    private static final int MEME_LINE_STEP = 55;
    private static final int MEME_TEXT_WIDTH = 250;
    private static final int MEME_TEXT_DROP = 40;

    private TransactionGenerator() {}

    private record Side(Player player, boolean canSeeSheet, boolean mirrored) {

        int x(int offsetFromOwnEdge, int itemWidth) {
            return mirrored ? SHEET_WIDTH - offsetFromOwnEdge - itemWidth : offsetFromOwnEdge;
        }
    }

    private record Cell(Function<Player, TI4Emoji> emoji, ToIntFunction<Player> count) {

        static Cell of(TI4Emoji emoji, ToIntFunction<Player> count) {
            return new Cell(_ -> emoji, count);
        }

        static Cell fragments(TI4Emoji emoji, String prefix) {
            return of(emoji, player -> (int) player.getFragments().stream()
                    .filter(fragment -> fragment.startsWith(prefix))
                    .count());
        }
    }

    private record TransactionItem(String raw, String kind, String detail, int amount) {

        static TransactionItem parse(String item) {
            String[] parts = item.split("_");
            String kind = parts[2];
            String detail = item.replace(parts[0] + "_" + parts[1] + "_" + parts[2] + "_", "");
            int amount = 1;
            if (hasTrailingCount(kind, detail)) {
                amount = Integer.parseInt("" + detail.charAt(detail.length() - 1));
                detail = detail.substring(0, detail.length() - 1);
            }
            return new TransactionItem(item, kind, detail, amount);
        }

        private static boolean hasTrailingCount(String kind, String detail) {
            return "frags".equalsIgnoreCase(kind)
                    || (("PNs".equalsIgnoreCase(kind) || "ACs".equalsIgnoreCase(kind)) && detail.contains("generic"));
        }

        boolean isEmoji() {
            return EMOJI_ITEMS.contains(kind);
        }

        Integer countUnlessSingle() {
            return amount == 1 ? null : amount;
        }
    }

    public static BufferedImage drawTransactableStuffImage(Player p1, Player p2) {
        return drawTransactableStuffImage(p1, p2, null);
    }

    public static BufferedImage drawTransactableStuffImage(Player p1, Player p2, Player viewer) {
        FogMemoryService.ensureInitialized(p1.getGame(), viewer);
        List<Side> sides =
                List.of(new Side(p1, canSeeSheet(p1, viewer), false), new Side(p2, canSeeSheet(p2, viewer), true));
        BufferedImage image = new BufferedImage(SHEET_WIDTH, SHEET_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setBackground(Color.black);
        drawColorBands(g2, p1, p2);

        for (Side side : sides) {
            drawIdentityIcon(g2, side.player(), side.canSeeSheet(), side.x(SHEET_MARGIN, ICON_SIZE), SHEET_MARGIN);
        }
        g2.setFont(Fonts.ti(18));
        for (Side side : sides) {
            drawStringMultilineVertCenter(
                    g2,
                    identityName(side.player(), side.canSeeSheet()),
                    side.x(NAME_INSET, 0),
                    NAME_CENTER_Y,
                    (SHEET_WIDTH - 2 * NAME_INSET) / 2,
                    side.mirrored() ? HorizontalAlign.Right : HorizontalAlign.Left);
        }
        drawCellRow(g2, sides, firstRowCells(sides), FIRST_ROW_Y);
        drawCellRow(g2, sides, fragmentCells(), SECOND_ROW_Y);
        return image;
    }

    private static void drawColorBands(Graphics2D g2, Player p1, Player p2) {
        int bandHeight = (int) (SHEET_HEIGHT * (22.0 / 16.0));
        int bandWidth = (int) (SHEET_WIDTH * (11.0 / 16.0));
        BufferedImage firstBand = colorBand(p1, bandWidth, bandHeight);
        g2.rotate(-NINETY_DEGREES_RADIANS);
        g2.drawImage(firstBand, -bandHeight, 0, null);
        g2.rotate(NINETY_DEGREES_RADIANS);
        BufferedImage secondBand = colorBand(p2, bandWidth, bandHeight);
        g2.rotate(NINETY_DEGREES_RADIANS);
        g2.drawImage(secondBand, SHEET_HEIGHT - bandHeight, -SHEET_WIDTH, null);
        g2.rotate(-NINETY_DEGREES_RADIANS);
    }

    private static BufferedImage colorBand(Player player, int width, int height) {
        String file = "pa_pn_color_" + Mapper.getColorID(player.getColor()) + ".png";
        return ImageHelper.readScaled(ImageResources.playerArea(file), width, height);
    }

    private static List<Cell> firstRowCells(List<Side> sides) {
        List<Cell> cells = new ArrayList<>(List.of(
                Cell.of(MiscEmojis.tg, Player::getTg),
                Cell.of(MiscEmojis.comm, Player::getCommodities),
                Cell.of(CardEmojis.PN, player -> player.getPromissoryNotes().size())));
        if (sides.stream().anyMatch(TransactionGenerator::showsActionCards)) {
            cells.add(new Cell(
                    CardEmojis::getACEmoji, player -> player.getActionCards().size()));
        }
        return cells;
    }

    private static boolean showsActionCards(Side side) {
        Player player = side.player();
        return side.canSeeSheet() && (player.hasAbility("arbiters") || player.hasTech("tf-guildships"));
    }

    private static List<Cell> fragmentCells() {
        return List.of(
                Cell.fragments(ExploreEmojis.CFrag, "crf"),
                Cell.fragments(ExploreEmojis.HFrag, "hrf"),
                Cell.fragments(ExploreEmojis.IFrag, "irf"),
                Cell.fragments(ExploreEmojis.UFrag, "urf"));
    }

    private static void drawCellRow(Graphics2D g2, List<Side> sides, List<Cell> cells, int y) {
        for (int column = 0; column < cells.size(); column++) {
            Cell cell = cells.get(column);
            int offset = SHEET_MARGIN + column * EMOJI_STEP;
            for (Side side : sides) {
                Player player = side.player();
                drawEmojiWithCenteredCount(
                        g2,
                        cell.emoji().apply(player),
                        cell.count().applyAsInt(player),
                        side.canSeeSheet(),
                        side.x(offset, EMOJI_SIZE),
                        y);
            }
        }
    }

    private static boolean canSeeSheet(Player target, Player viewer) {
        Game game = target.getGame();
        return viewer == null || !game.isFowMode() || FoWHelper.canSeeStatsOfPlayerInCurrentFog(game, target, viewer);
    }

    private static void drawIdentityIcon(Graphics2D g2, Player player, boolean canSeeSheet, int x, int y) {
        if (canSeeSheet) {
            g2.drawImage(FactionIcons.scaled(player, ICON_SIZE, ICON_SIZE), x, y, null);
        } else {
            RemoteImages.drawEmoji(g2, ColorEmojis.getColorEmoji(player.getColor()), x, y, ICON_SIZE);
        }
    }

    private static String identityName(Player player, boolean canSeeSheet) {
        return canSeeSheet
                ? player.bannerName()
                : player.getFactionNameOrColor().toUpperCase();
    }

    private static void drawEmojiWithCenteredCount(
            Graphics2D g2, TI4Emoji emoji, int amount, boolean canSeeSheet, int x, int y) {
        if (canSeeSheet) {
            drawEmojiWithCenteredInt(g2, emoji, amount, x, y);
        } else {
            drawEmojiWithCenteredText(g2, emoji, "?", false, x, y);
        }
    }

    public static BufferedImage drawTradeOfferMeme(Game game, Player p1, Player p2) {
        BufferedImage meme = ImageHelper.read(ImageResources.extra("trade_offer_base.png"));
        if (meme == null) return null;
        BufferedImage image = new BufferedImage(meme.getWidth(), meme.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.drawImage(meme, 0, 0, null);
        FactionIcons.draw(g2, p1, 31, 139, 50, 50);
        FactionIcons.draw(g2, p2, 358, 139, 50, 50);
        drawHeroOverTheFace(g2, p1);

        List<String> items = p1.getTransactionItemsWithPlayer(p2);
        for (Player sender : List.of(p1, p2)) {
            int bottom = drawSentItems(g2, game, sender, sender == p2, items);
            if (bottom > image.getHeight()) return null;
        }
        return image;
    }

    private static void drawHeroOverTheFace(Graphics2D g2, Player player) {
        TI4Emoji hero = LeaderEmojis.getLeaderEmoji(player.getFaction() + "hero");
        if (MiscEmojis.goodDogs().contains(hero)) {
            RemoteImages.drawEmoji(g2, hero, 210, 294, 200);
        } else {
            RemoteImages.drawEmoji(g2, hero, 60, 294, 500);
        }
    }

    private static int drawSentItems(Graphics2D g2, Game game, Player sender, boolean leftColumn, List<String> items) {
        HorizontalAlign align = leftColumn ? HorizontalAlign.Left : HorizontalAlign.Right;
        int x = leftColumn ? 10 : 590;
        int y = 200;
        int emojiStep = leftColumn ? 55 : -55;
        int emojiStart = x + (leftColumn ? 0 : -50);
        int emojiRowY = 0;
        int emojisInRow = 0;
        boolean sendingNothing = true;
        g2.setFont(Fonts.ti(24));
        for (String raw : items) {
            if (!raw.contains("sending" + sender.getFaction())) continue;
            sendingNothing = false;
            TransactionItem item = TransactionItem.parse(raw);

            boolean sharesEmojiRow = false;
            int emojiX = emojiStart;
            if (item.isEmoji()) {
                if (emojiRowY == 0 || emojisInRow == MEME_EMOJIS_PER_ROW) {
                    emojiRowY = y;
                    emojisInRow = 1;
                } else {
                    emojiX += emojisInRow * emojiStep;
                    emojisInRow++;
                    sharesEmojiRow = true;
                }
            }
            int lineStep = sharesEmojiRow ? 0 : MEME_LINE_STEP;
            switch (item.kind()) {
                case "TGs" -> {
                    drawEmojiWithCenteredInt(g2, MiscEmojis.tg, Integer.parseInt(item.detail()), emojiX, emojiRowY);
                    y += lineStep;
                }
                case "Comms" -> {
                    drawEmojiWithCenteredInt(g2, MiscEmojis.comm, Integer.parseInt(item.detail()), emojiX, emojiRowY);
                    y += lineStep;
                }
                case "ACs" -> {
                    drawEmojiWithCenteredInt(
                            g2, CardEmojis.getACEmoji(game), item.countUnlessSingle(), emojiX, emojiRowY);
                    y += lineStep;
                }
                case "PNs" -> {
                    drawEmojiWithCenteredInt(g2, CardEmojis.PN, item.countUnlessSingle(), emojiX, emojiRowY);
                    y += lineStep;
                }
                case "Frags" -> {
                    TI4Emoji fragment = ExploreEmojis.getFragEmoji(item.detail());
                    drawEmojiWithCenteredInt(g2, fragment, item.countUnlessSingle(), emojiX, emojiRowY);
                    y += lineStep;
                }
                case "SendDebt" -> y += drawMemeText(g2, Integer.parseInt(item.detail()) + " debt tokens", x, y, align);
                case "ClearDebt" ->
                    y += drawMemeText(g2, "Clear " + Integer.parseInt(item.detail()) + " debt", x, y, align);
                case "shipOrders", "starCharts" ->
                    y += drawMemeText(g2, Mapper.getRelic(item.detail()).getName(), x, y, align);
                case "Planets", "AlliancePlanets", "dmz" -> {
                    drawEmojiWithCenteredInt(g2, PlanetEmojis.getPlanetEmoji(item.detail()), null, emojiX, y);
                    String planet = Helper.getPlanetRepresentationNoResInf(item.detail(), game);
                    int textX = x + (align == HorizontalAlign.Left ? 55 : -55);
                    y += drawMemeText(g2, planet, textX, y, align);
                }
                case "action" -> y += drawMemeText(g2, "In-game " + item.detail() + " action", x, y, align);
                case "details" -> y += drawMemeText(g2, item.detail().replace("fin777", " "), x, y, align);
                default -> y += drawMemeText(g2, "\"" + item.raw() + "\"", x, y, align);
            }
        }
        if (sendingNothing) {
            g2.setFont(Fonts.ti(24));
            y += drawMemeText(g2, nothingMessage(game, sender), x, y, align);
        }
        return y;
    }

    private static String nothingMessage(Game game, Player sender) {
        String stored = game.getStoredValue(sender.getFaction() + "NothingMessage");
        return stored.isEmpty() ? DEFAULT_NOTHING_MESSAGE : stored;
    }

    private static int drawMemeText(Graphics2D g2, String text, int x, int y, HorizontalAlign align) {
        return drawStringMultiLine(g2, text, x, y + MEME_TEXT_DROP, MEME_TEXT_WIDTH, align);
    }

    private static int totalMultilineHeight(Graphics2D g2, int numLines) {
        return g2.getFontMetrics().getAscent()
                + (numLines - 1) * g2.getFontMetrics().getHeight();
    }

    private static void drawStringMultilineVertCenter(
            Graphics2D g2, String text, int x, int centerY, int width, HorizontalAlign hAlign) {
        int lineHeight = g2.getFontMetrics().getHeight();
        List<String> lines = TextWrap.layoutLines(text, g2.getFontMetrics(), width);
        int height = totalMultilineHeight(g2, lines.size());
        int deltaY = g2.getFontMetrics().getAscent() - height / 2;
        for (String line : lines) {
            OutlinedText.draw(g2, line, x, centerY + deltaY, null, hAlign, null, TEXT_OUTLINE, null);
            deltaY += lineHeight;
        }
    }

    private static int drawStringMultiLine(
            Graphics2D g2, String text, int x, int y, int width, HorizontalAlign hAlign) {
        int lineHeight = g2.getFontMetrics().getHeight();
        List<String> lines = TextWrap.layoutLines(text, g2.getFontMetrics(), width);
        int deltaY = 0;
        for (String line : lines) {
            OutlinedText.draw(g2, line, x, y + deltaY, null, hAlign, null, TEXT_OUTLINE, null);
            deltaY += lineHeight;
        }
        return deltaY;
    }

    private static void drawEmojiWithCenteredInt(Graphics2D g2, TI4Emoji emoji, Integer amount, int x, int y) {
        boolean faded = amount != null && amount == 0;
        drawEmojiWithCenteredText(g2, emoji, amount == null ? null : "" + amount, faded, x, y);
    }

    private static void drawEmojiWithCenteredText(
            Graphics2D g2, TI4Emoji emoji, String text, boolean faded, int x, int y) {
        if (faded) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        RemoteImages.drawEmoji(g2, emoji, x, y, EMOJI_SIZE);
        if (faded) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        Font before = g2.getFont();
        g2.setFont(Fonts.ti(32));
        if (text != null) {
            OutlinedText.draw(
                    g2,
                    text,
                    x + EMOJI_SIZE / 2,
                    y + EMOJI_SIZE / 2,
                    Color.white,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    COUNT_OUTLINE,
                    Color.black);
        }
        g2.setFont(before);
    }
}
