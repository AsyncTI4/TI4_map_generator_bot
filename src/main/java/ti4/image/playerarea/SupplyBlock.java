package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.HydromorphicHelper;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.InfinitySymbol;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PaImage;
import ti4.image.draw.ShapeDrawing;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;
import ti4.model.ExploreModel;
import ti4.model.Mapper;

final class SupplyBlock {

    static final int OFFSET_FROM_ICON = 120;
    private static final int TWILIGHTS_FALL_SHIFT = 65;
    private static final int FRAGMENT_STEP = 15;
    private static final Set<String> STONEBORN_FRAGMENTS = Set.of(
            "stonebornculturalfragment",
            "stonebornculturalfragment2",
            "stonebornhazardousfragment",
            "stonebornhazardousfragment2",
            "stonebornindustrialfragment",
            "stonebornindustrialfragment2",
            "stonebornunknownfragment",
            "stonebornunknownfragment2");
    private static final int FRAGMENT_PAIR_GAP = 25;

    private SupplyBlock() {}

    static void draw(PlayerAreaContext ctx, Player player, int areaLeft, int y) {
        int x = areaLeft + OFFSET_FROM_ICON;
        drawCommandCounters(ctx.g2(), player, x, y);
        int xOffset = drawCards(ctx, player, x, y);
        drawFragmentPair(
                ctx,
                player,
                x - xOffset,
                y,
                Constants.FRONTIER,
                "pa_fragment_urf.png",
                Constants.INDUSTRIAL,
                "pa_fragment_irf.png");
        drawFragmentPair(
                ctx,
                player,
                x - xOffset,
                y + 73,
                Constants.HAZARDOUS,
                "pa_fragment_hrf.png",
                Constants.CULTURAL,
                "pa_fragment_crf.png");
    }

    private static void drawCommandCounters(Graphics2D g2, Player player, int x, int y) {
        g2.setFont(Fonts.ti(32));
        g2.setColor(Color.WHITE);
        g2.drawString(player.getCCRepresentation(), x + 40, y + 75);
        g2.drawString("T/F/S", x + 40, y + 35);

        int additionalFleetSupply = 0;
        if (player.hasAbility("edict") || player.hasAbility("edict_y")) {
            additionalFleetSupply += player.getMahactCC().size();
        }
        if (player.hasAbility("armada")) {
            additionalFleetSupply += 2;
        }
        if (additionalFleetSupply > 0) {
            g2.drawString("+" + additionalFleetSupply + " FS", x + 40, y + 105);
        }
    }

    private static int drawCards(PlayerAreaContext ctx, Player player, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        String acImage = game.isTwilightsFallMode() ? "pa_cardbacks_ac_tf.png" : "pa_cardbacks_ac.png";
        String tradeGoodImage = game.isNomadCoin() ? "pa_cardbacks_nomadcoin.png" : "pa_cardbacks_tradegoods.png";
        boolean showHydromorphicCards = HydromorphicHelper.shouldShowInPlayerArea(player);
        g2.setStroke(Strokes.STROKE_1);
        drawCardCount(g2, x + 150, y, "pa_cardbacks_so.png", Integer.toString(player.getSo()), true);
        drawCardCount(g2, x + 215, y, acImage, Integer.toString(player.getAcCount()), true);
        if (showHydromorphicCards) {
            drawCardCount(
                    g2,
                    x + 280,
                    y,
                    "pa_cardbacks_hc.png",
                    Integer.toString(player.getHydromorphicCards().size()),
                    true);
        }

        int xOffset = showHydromorphicCards ? -65 : 0;
        if (game.isTwilightsFallMode()) {
            xOffset += TWILIGHTS_FALL_SHIFT;
        } else {
            drawCardCount(g2, x + 280 - xOffset, y, "pa_cardbacks_pn.png", Integer.toString(player.getPnCount()), true);
        }
        if (player.isInfiniteTg()) {
            drawInfiniteCard(g2, x + 345 - xOffset, y, tradeGoodImage);
        } else {
            drawCardCount(g2, x + 345 - xOffset, y, tradeGoodImage, Integer.toString(player.getTg()), false);
        }
        String comms = player.getCommodities() + "/" + player.getCommoditiesTotal();
        drawCardCount(g2, x + 410 - xOffset, y, "pa_cardbacks_commodities.png", comms, false);
        return xOffset;
    }

    private static void drawInfiniteCard(Graphics2D g2, int x, int y, String image) {
        Rectangle card = PaImage.draw(g2, x, y, image);
        InfinitySymbol.draw(g2, (int) card.getCenterX(), y + 62, 34, Color.WHITE, Color.BLACK);
    }

    private static void drawCardCount(Graphics2D g2, int x, int y, String image, String count, boolean outlined) {
        Rectangle card = PaImage.draw(g2, x, y, image);
        OutlinedText.drawCenteredWhiteOnBlack(g2, count, (int) card.getCenterX(), y + 75);
        if (outlined) {
            ShapeDrawing.gradientOutline(g2, Color.black, null, card);
        }
    }

    private static void drawFragmentPair(
            PlayerAreaContext ctx,
            Player player,
            int x,
            int y,
            String firstTrait,
            String firstImage,
            String secondTrait,
            String secondImage) {
        int xDelta = drawFragments(ctx, player, x, y, firstTrait, firstImage, 0);
        drawFragments(ctx, player, x, y, secondTrait, secondImage, xDelta + FRAGMENT_PAIR_GAP);
    }

    private static int drawFragments(
            PlayerAreaContext ctx, Player player, int x, int y, String trait, String fragmentImage, int xDelta) {
        Graphics2D g2 = ctx.g2();
        List<String> fragments = player.getFragments().stream()
                .filter(fragmentId -> {
                    ExploreModel fragment = Mapper.getExplore(fragmentId);
                    return fragment != null && trait.equalsIgnoreCase(fragment.getType());
                })
                .toList();
        for (int i = 0; i < fragments.size(); i++) {
            if (fragments.get(i).startsWith("supermassive")) {
                PaImage.drawScaled(g2, x + 457 + xDelta + i * FRAGMENT_STEP, y - 43, fragmentImage, 108, 110);
            }
        }
        for (int i = 0; i < fragments.size(); i++) {
            if (!fragments.get(i).startsWith("supermassive")) {
                int fragmentX = x + 475 + xDelta + i * FRAGMENT_STEP;
                int fragmentY = y - 25;
                PaImage.draw(g2, fragmentX, fragmentY, fragmentImage);
                if (STONEBORN_FRAGMENTS.contains(fragments.get(i))) {
                    FactionIcons.drawFaction(g2, "stoneborn", fragmentX + 9, fragmentY + 10, 54, 50);
                }
            }
        }
        return xDelta + fragments.size() * FRAGMENT_STEP;
    }
}
