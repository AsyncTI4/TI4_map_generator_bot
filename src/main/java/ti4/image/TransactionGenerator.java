package ti4.image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Function;
import ti4.ResourceHelper;
import ti4.helpers.Helper;
import ti4.helpers.Storage;
import ti4.helpers.TransactionHelper;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.image.MapGenerator.VerticalAlign;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.LeaderEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;

public class TransactionGenerator {

    private static final BasicStroke stroke2 = new BasicStroke(2.0f);
    private static final BasicStroke stroke5 = new BasicStroke(5.0f);
    private static final double NINETY_DEGREES_RADIANS = Math.toRadians(90);
    private static final double NEGATIVE_NINETY_DEGREES_RADIANS = -NINETY_DEGREES_RADIANS;

    public static BufferedImage drawTransactableStuffImage(Player p1, Player p2) {
        int width = 500, height = 160;
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) img.getGraphics();
        g2.setBackground(Color.black);

        double widthRatio = 11.0 / 16.0;
        double heightRatio = 22.0 / 16.0;
        int pnHeight = (int) (height * heightRatio);
        int pnWidth = (int) (width * widthRatio);
        // Add player 1's color
        String pn1 = "pa_pn_color_" + Mapper.getColorID(p1.getColor()) + ".png";
        BufferedImage color1 =
                ImageHelper.readScaled(ResourceHelper.getInstance().getPAResource(pn1), pnHeight, pnWidth);
        g2.rotate(NEGATIVE_NINETY_DEGREES_RADIANS);
        g2.drawImage(color1, -1 * pnHeight, 0, null);
        g2.rotate(NINETY_DEGREES_RADIANS);

        // Add player 2's color
        String pn2 = "pa_pn_color_" + Mapper.getColorID(p2.getColor()) + ".png";
        BufferedImage color2 =
                ImageHelper.readScaled(ResourceHelper.getInstance().getPAResource(pn2), pnHeight, pnWidth);
        g2.rotate(NINETY_DEGREES_RADIANS);
        g2.drawImage(color2, height - pnHeight, -1 * width, null);
        g2.rotate(NEGATIVE_NINETY_DEGREES_RADIANS);

        // Faction Icons
        int x = 5, y = 5;
        g2.drawImage(DrawingUtil.getPlayerFactionIconImageScaled(p1, 40, 40), x, y, null);
        x = width - 45;
        g2.drawImage(DrawingUtil.getPlayerFactionIconImageScaled(p2, 40, 40), x, y, null);

        // Faction Names
        g2.setFont(Storage.getFont18());
        x = 50;
        y = 25;
        drawStringMultilineVertCenter(g2, p1.bannerName(), x, y, (width - 100) / 2, HorizontalAlign.Left);
        x = width - 50;
        drawStringMultilineVertCenter(g2, p2.bannerName(), x, y, (width - 100) / 2, HorizontalAlign.Right);

        // STUFF TIME !!!
        int emojiSize = 50;
        // First Line, TGs and Comms and PNs (and ACs)
        y = 50;
        x = 5;
        // TGs
        drawEmojiWithCenteredInt(g2, MiscEmojis.tg, p1.getTg(), x, y);
        drawEmojiWithCenteredInt(g2, MiscEmojis.tg, p2.getTg(), width - x - emojiSize, y);
        x += emojiSize + 5;
        // Comms
        drawEmojiWithCenteredInt(g2, MiscEmojis.comm, p1.getCommodities(), x, y);
        drawEmojiWithCenteredInt(g2, MiscEmojis.comm, p2.getCommodities(), width - x - emojiSize, y);
        // PNs
        x += emojiSize + 5;
        drawEmojiWithCenteredInt(g2, CardEmojis.PN, p1.getPromissoryNotes().size(), x, y);
        drawEmojiWithCenteredInt(g2, CardEmojis.PN, p2.getPromissoryNotes().size(), width - x - emojiSize, y);
        // ACs
        if (p1.hasAbility("arbiters") || p2.hasAbility("arbiters")) {
            x += emojiSize + 5;
            drawEmojiWithCenteredInt(
                    g2, CardEmojis.ActionCard, p1.getActionCards().size(), x, y);
            drawEmojiWithCenteredInt(
                    g2, CardEmojis.ActionCard, p2.getActionCards().size(), width - x - emojiSize, y);
        }

        // Second Line, Frags
        // Frags
        Function<String, Integer> p1fragcount = str ->
                (int) p1.getFragments().stream().filter(f -> f.startsWith(str)).count();
        Function<String, Integer> p2fragcount = str ->
                (int) p2.getFragments().stream().filter(f -> f.startsWith(str)).count();
        y = 105;
        x = 5;
        drawEmojiWithCenteredInt(g2, ExploreEmojis.CFrag, p1fragcount.apply("crf"), x, y);
        drawEmojiWithCenteredInt(g2, ExploreEmojis.CFrag, p2fragcount.apply("crf"), width - x - emojiSize, y);
        x += emojiSize + 5;
        drawEmojiWithCenteredInt(g2, ExploreEmojis.HFrag, p1fragcount.apply("hrf"), x, y);
        drawEmojiWithCenteredInt(g2, ExploreEmojis.HFrag, p2fragcount.apply("hrf"), width - x - emojiSize, y);
        x += emojiSize + 5;
        drawEmojiWithCenteredInt(g2, ExploreEmojis.IFrag, p1fragcount.apply("irf"), x, y);
        drawEmojiWithCenteredInt(g2, ExploreEmojis.IFrag, p2fragcount.apply("irf"), width - x - emojiSize, y);
        x += emojiSize + 5;
        drawEmojiWithCenteredInt(g2, ExploreEmojis.UFrag, p1fragcount.apply("urf"), x, y);
        drawEmojiWithCenteredInt(g2, ExploreEmojis.UFrag, p2fragcount.apply("urf"), width - x - emojiSize, y);

        return img;
    }

    /** See also {@link TransactionHelper.buildTransactionOffer} */
    public static BufferedImage drawTradeOfferMeme(Game game, Player p1, Player p2) {
        BufferedImage meme = ImageHelper.read(ResourceHelper.getInstance().getExtraFile("trade_offer_base.png"));
        if (meme == null) return null;
        BufferedImage img = new BufferedImage(meme.getWidth(), meme.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.drawImage(meme, 0, 0, null);

        // Draw faction icons
        DrawingUtil.drawPlayerFactionIconImage(g2, p1, 31, 139, 50, 50);
        DrawingUtil.drawPlayerFactionIconImage(g2, p2, 358, 139, 50, 50);

        // Draw the hero icon (or a good doggo) over the face in the meme
        TI4Emoji hero = LeaderEmojis.getLeaderEmoji(p1.getFaction() + "hero");
        if (MiscEmojis.goodDogs().contains(hero))
            DrawingUtil.drawEmoji(g2, hero, 210, 294, 200); // good doggies are smaller
        else DrawingUtil.drawEmoji(g2, hero, 60, 294, 500);

        List<String> transactionItems = p1.getTransactionItemsWithPlayer(p2);
        for (Player player : List.of(p1, p2)) {
            boolean sendingNothing = true;
            HorizontalAlign hAlign = player == p2 ? HorizontalAlign.Left : HorizontalAlign.Right;
            int x = player == p2 ? 10 : 590;
            int y = 200;

            int emojiXShift = player == p2 ? 55 : -55;
            int emojiShift1 = player == p2 ? 0 : -50;
            int emojiRow = 0;
            int emojiCount = 0;
            g2.setFont(Storage.getFont24());
            for (String item : transactionItems) {
                if (!item.contains("sending" + player.getFaction())) continue;
                sendingNothing = false;

                String thingToTransact = item.split("_")[2];
                String furtherDetail = item.split("_")[3];
                int amountToTransact = 1;
                if ("frags".equalsIgnoreCase(thingToTransact)
                        || (("PNs".equalsIgnoreCase(thingToTransact) || "ACs".equalsIgnoreCase(thingToTransact))
                                && furtherDetail.contains("generic"))) {
                    amountToTransact = Integer.parseInt("" + furtherDetail.charAt(furtherDetail.length() - 1));
                    furtherDetail = furtherDetail.substring(0, furtherDetail.length() - 1);
                }

                boolean skipYShift = false;
                boolean isEmoji = List.of("TGs", "Comms", "ACs", "PNs", "Frags").contains(thingToTransact);
                int emojiX = x + emojiShift1;
                if (isEmoji) {
                    if (emojiRow == 0 || emojiCount == 3) {
                        emojiRow = y;
                        emojiCount = 1;
                    } else {
                        emojiX += emojiCount * emojiXShift;
                        emojiCount++;
                        skipYShift = true;
                    }
                }

                switch (thingToTransact) {
                    case "TGs" -> {
                        amountToTransact = Integer.parseInt(furtherDetail);
                        drawEmojiWithCenteredInt(g2, MiscEmojis.tg, amountToTransact, emojiX, emojiRow);
                        if (!skipYShift) y += 55;
                    }
                    case "Comms" -> {
                        amountToTransact = Integer.parseInt(furtherDetail);
                        drawEmojiWithCenteredInt(g2, MiscEmojis.comm, amountToTransact, emojiX, emojiRow);
                        if (!skipYShift) y += 55;
                    }
                    case "ACs" -> {
                        Integer amt = amountToTransact == 1 ? null : amountToTransact;
                        drawEmojiWithCenteredInt(g2, CardEmojis.ActionCard, amt, emojiX, emojiRow);
                        if (!skipYShift) y += 55;
                    }
                    case "PNs" -> {
                        Integer amt = amountToTransact == 1 ? null : amountToTransact;
                        drawEmojiWithCenteredInt(g2, CardEmojis.PN, amt, emojiX, emojiRow);
                        if (!skipYShift) y += 55;
                    }
                    case "Frags" -> {
                        Integer amt = amountToTransact == 1 ? null : amountToTransact;
                        drawEmojiWithCenteredInt(g2, ExploreEmojis.getFragEmoji(furtherDetail), amt, emojiX, emojiRow);
                        if (!skipYShift) y += 55;
                    }
                    case "SendDebt" -> {
                        amountToTransact = Integer.parseInt(furtherDetail);
                        String txt = amountToTransact + " debt tokens";
                        y += drawStringMultiLine(g2, txt, x, y + 40, 250, hAlign);
                    }
                    case "ClearDebt" -> {
                        amountToTransact = Integer.parseInt(furtherDetail);
                        String txt = "Clear " + amountToTransact + " debt";
                        y += drawStringMultiLine(g2, txt, x, y + 40, 250, hAlign);
                    }
                    case "shipOrders" -> {
                        String txt = Mapper.getRelic(furtherDetail).getName();
                        y += drawStringMultiLine(g2, txt, x, y + 40, 250, hAlign);
                    }
                    case "starCharts" -> {
                        String txt = Mapper.getRelic(furtherDetail).getName();
                        y += drawStringMultiLine(g2, txt, x, y + 40, 250, hAlign);
                    }
                    case "Planets", "AlliancePlanets", "dmz" -> {
                        TI4Emoji emoji = PlanetEmojis.getPlanetEmoji(furtherDetail);
                        drawEmojiWithCenteredInt(g2, emoji, null, emojiX, y);

                        String txt = Helper.getPlanetRepresentationNoResInf(furtherDetail, game);
                        int textX = x + (hAlign == HorizontalAlign.Left ? 55 : -55);
                        y += drawStringMultiLine(g2, txt, textX, y + 40, 250, hAlign);
                    }
                    case "action" -> {
                        String txt = "In-game " + furtherDetail + " action";
                        y += drawStringMultiLine(g2, txt, x, y + 40, 250, hAlign);
                    }
                    default -> {
                        String txt = "\"" + item + "\"";
                        y += drawStringMultiLine(g2, txt, x, y + 40, 250, hAlign);
                    }
                }
            }
            if (sendingNothing) {
                String nothing = game.getStoredValue(player.getFaction() + "NothingMessage");
                if (nothing.isEmpty()) {
                    nothing = TransactionHelper.getNothingMessage();
                    game.setStoredValue(player.getFaction() + "NothingMessage", nothing);
                }
                g2.setFont(Storage.getFont24());
                y += drawStringMultiLine(g2, nothing, x, y + 40, 250, hAlign);
            }
            if (y > img.getHeight()) return null; // overflowed, default back to text
        }

        return img;
    }

    private static int totalMultilineHeight(Graphics2D g2, int numLines) {
        return g2.getFontMetrics().getAscent()
                + (numLines - 1) * g2.getFontMetrics().getHeight();
    }

    private static void drawStringMultilineVertCenter(
            Graphics2D g2, String text, int x, int centerY, int width, HorizontalAlign hAlign) {
        int lineHeight = g2.getFontMetrics().getHeight();
        List<String> toDraw = DrawingUtil.layoutText(g2, text, width);
        int height = totalMultilineHeight(g2, toDraw.size());
        int deltaY = g2.getFontMetrics().getAscent() - height / 2;
        for (String line : toDraw) {
            DrawingUtil.superDrawString(g2, line, x, centerY + deltaY, null, hAlign, null, stroke2, null);
            deltaY += lineHeight;
        }
    }

    private static int drawStringMultiLine(
            Graphics2D g2, String text, int x, int y, int width, HorizontalAlign hAlign) {
        int lineHeight = g2.getFontMetrics().getHeight();
        List<String> toDraw = DrawingUtil.layoutText(g2, text, width);
        int deltaY = 0;
        for (String line : toDraw) {
            DrawingUtil.superDrawString(g2, line, x, y + deltaY, null, hAlign, null, stroke2, null);
            deltaY += lineHeight;
        }
        return deltaY;
    }

    private static void drawEmojiWithCenteredInt(Graphics2D g2, TI4Emoji emoji, Integer amount, int x, int y) {
        if (amount != null && amount == 0) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
        DrawingUtil.drawEmoji(g2, emoji, x, y, 50);
        if (amount != null && amount == 0) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

        Font before = g2.getFont();
        g2.setFont(Storage.getFont32());
        if (amount != null)
            DrawingUtil.superDrawString(
                    g2,
                    "" + amount,
                    x + 25,
                    y + 25,
                    Color.white,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    stroke5,
                    Color.black);
        g2.setFont(before);
    }
}
