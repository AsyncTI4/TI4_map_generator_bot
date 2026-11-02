package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.discord.interactions.buttons.handlers.relics.theodisi.LostLegaciesRelicHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsBRButtonHandler;
import ti4.game.Player;
import ti4.helpers.RelicHelper;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.PaImage;
import ti4.image.draw.PlayerTokens;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.Mapper;
import ti4.model.RelicModel;
import ti4.model.Source.ComponentSource;

final class RelicCards {

    private static final int CARD_STEP = 48;
    private static final int AXIS_ORDER_WIDTH = 54;
    private static final int AXIS_ORDER_STEP = 58;
    private static final int GROUP_GAP = 10;
    private static final List<String> BOONS = List.of("economicboon", "naturesboon", "diplomaticboon", "cosmicboon");

    private RelicCards() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        if (player.getRelics().isEmpty()) {
            return x;
        }
        ctx.g2().setStroke(Strokes.STROKE_2);

        List<String> relics = new ArrayList<>(player.getRelics());
        List<String> fakeRelics = relics.stream()
                .filter(relic -> Mapper.getRelic(relic).isFakeRelic())
                .filter(relic -> !relic.contains("axisorder"))
                .toList();
        List<String> axisOrderRelics = player.getRelics().stream()
                .filter(relic -> relic.contains("axisorder"))
                .toList();
        relics.removeAll(fakeRelics);
        relics.removeAll(axisOrderRelics);

        int cardX = x;
        for (String relicID : relics) {
            drawRelic(ctx, player, relicID, cardX, y);
            cardX += CARD_STEP;
        }
        if (!fakeRelics.isEmpty()) {
            cardX += GROUP_GAP;
        }
        for (String relicID : fakeRelics) {
            drawFakeRelic(ctx, player, relicID, cardX, y);
            cardX += CARD_STEP;
        }
        if (!axisOrderRelics.isEmpty()) {
            cardX += GROUP_GAP;
        }
        for (String relicID : axisOrderRelics) {
            drawAxisOrderRelic(ctx, player, relicID, cardX, y);
            cardX += AXIS_ORDER_STEP;
        }
        return cardX + 20;
    }

    private static String statusSuffix(boolean exhausted) {
        return exhausted ? "_exh" : "_rdy";
    }

    private static boolean setExhaustedColor(Graphics2D g2, Player player, String relicID) {
        boolean isExhausted = player.getExhaustedRelics().contains(relicID);
        g2.setColor(isExhausted ? Color.GRAY : Color.WHITE);
        return isExhausted;
    }

    private static void drawRelic(PlayerAreaContext ctx, Player player, String relicID, int x, int y) {
        Graphics2D g2 = ctx.g2();
        RelicModel relicModel = Mapper.getRelic(relicID);
        boolean isExhausted = setExhaustedColor(g2, player, relicID);

        CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, relicModel);
        if (relicModel.getSource() == ComponentSource.absol) {
            PaImage.draw(g2, x, y, "pa_source_absol.png");
        }
        PaImage.draw(g2, x - 1, y - 2, "pa_relics_icon.png");

        if ("absol_quantumcore".equals(relicID)) {
            PaImage.draw(g2, x, y, "pa_tech_techicons_cyberneticwarfare" + statusSuffix(isExhausted) + ".png");
        }
        if (isOrReplaces(relicModel, relicID, "titanprototype")) {
            FactionIcons.drawFaction(g2, "relic", x - 1, y + 108, 42, 42);
        }

        if (isOrReplaces(relicModel, relicID, "emelpar")) {
            String emelpar = RelicHelper.sillySpelling();
            int spaceIndex = emelpar.lastIndexOf(' ');
            emelpar = emelpar.substring(0, spaceIndex) + "\n" + emelpar.substring(spaceIndex);
            g2.setFont(Fonts.ti(18));
            VerticalText.drawOneOrTwoLines(g2, emelpar, x + 7, y + 30, 120, true);
        } else if (relicModel.getShrinkName()) {
            g2.setFont(Fonts.ti(16));
            VerticalText.drawOneOrTwoLines(g2, relicModel.getShortName(), x + 9, y + 30, 120, true);
        } else {
            g2.setFont(Fonts.ti(18));
            VerticalText.drawOneOrTwoLines(g2, relicModel.getShortName(), x + 7, y + 30, 120, true);
        }
    }

    private static boolean isOrReplaces(RelicModel relicModel, String relicID, String baseID) {
        return baseID.equals(relicID)
                || baseID.equals(relicModel.getHomebrewReplacesID().orElse(""));
    }

    private static void drawFakeRelic(PlayerAreaContext ctx, Player player, String relicID, int x, int y) {
        Graphics2D g2 = ctx.g2();
        RelicModel relicModel = Mapper.getRelic(relicID);
        boolean isExhausted = setExhaustedColor(g2, player, relicID);

        CardRect.draw(ctx, x - 2, y - 2, CardRect.WIDTH, CardRect.HEIGHT, relicModel);
        PaImage.draw(g2, x, y, "pa_relics_fakerelicicon.png");

        String relicFileName = "pa_relics_" + relicID + statusSuffix(isExhausted) + ".png";
        String resourcePath = ImageResources.playerArea(relicFileName);
        try {
            BufferedImage relicImage = ImageHelper.read(resourcePath);
            if (relicImage == null) {
                g2.setFont(Fonts.ti(20));
                VerticalText.drawTwoLines(g2, relicModel.getShortName(), x + 5, y + 140, 130);
            } else {
                g2.drawImage(relicImage, x, y, null);
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), "Bad file: " + relicFileName, e);
        }
        if (MonumentsBRButtonHandler.hasArmageddonProjectSuperweapon(ctx.game(), player, relicID)) {
            PlayerTokens.readAndDrawControlToken(g2, player, x + 10, y + 60, false, 0.5f);
        }
        if (BOONS.contains(relicID)) {
            int tokenCount = LostLegaciesRelicHandler.getBoonTokens(ctx.game(), player, relicID);
            for (int token = 0; token < tokenCount; token++) {
                PlayerTokens.readAndDrawControlToken(
                        g2, player, x + 2 + token % 3 * 14, y + 52 + token / 3 * 13, false, 0.25f);
            }
        }
    }

    private static void drawAxisOrderRelic(PlayerAreaContext ctx, Player player, String relicID, int x, int y) {
        boolean isExhausted = setExhaustedColor(ctx.g2(), player, relicID);
        CardRect.draw(ctx, x - 2, y - 2, AXIS_ORDER_WIDTH, CardRect.HEIGHT, Mapper.getRelic(relicID));
        PaImage.draw(ctx.g2(), x, y, "pa_relics_" + relicID + statusSuffix(isExhausted) + ".png");
    }
}
