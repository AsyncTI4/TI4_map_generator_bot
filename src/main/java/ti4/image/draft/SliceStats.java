package ti4.image.draft;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.game.Planet;
import ti4.game.UnitHolder;
import ti4.image.io.Fonts;
import ti4.image.io.RemoteImages;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

final class SliceStats {

    private static final BasicStroke INNER_STROKE = new BasicStroke(4.0f);
    private static final BasicStroke OUTLINE_STROKE = new BasicStroke(9.0f);
    private static final int EMOJI_SIZE = 40;
    private static final int CENTER_X = 172;
    private static final List<Point> FEATURE_POINTS = List.of(
            new Point(83, 3),
            new Point(220, 3),
            new Point(60, 43),
            new Point(243, 43),
            new Point(37, 83),
            new Point(266, 83),
            new Point(14, 123),
            new Point(289, 123));

    private SliceStats() {}

    static void drawCenteredLabel(Graphics2D graphics, String text, Color fill, Point base, int offsetY) {
        DraftText.centered(graphics, text, fill, base.x + CENTER_X, base.y + offsetY, OUTLINE_STROKE);
    }

    static void drawName(Graphics2D graphics, String name, Point base) {
        graphics.setColor(Color.white);
        graphics.setFont(Fonts.ti(64));
        drawCenteredLabel(graphics, name, Color.white, base, 50);
    }

    static void drawFeatures(Graphics2D graphics, MiltyDraftSlice slice, Point base) {
        graphics.setStroke(INNER_STROKE);
        int index = 0;
        for (TI4Emoji feature : featureEmojis(slice)) {
            Point point = FEATURE_POINTS.get(index);
            BufferedImage featureImage = RemoteImages.emoji(feature, EMOJI_SIZE);
            graphics.setColor(Color.black);
            graphics.fillRoundRect(point.x + base.x, point.y + base.y, EMOJI_SIZE, EMOJI_SIZE, EMOJI_SIZE, EMOJI_SIZE);
            graphics.drawImage(featureImage, point.x + base.x, point.y + base.y, null);
            index++;
            if (index >= FEATURE_POINTS.size()) break;
        }
    }

    static void drawTotals(Graphics2D graphics, MiltyDraftSlice slice, Point base) {
        String totals = slice.getTotalRes() + "/" + slice.getTotalInf();
        String optimal = "(" + slice.getOptimalRes() + "/" + slice.getOptimalInf() + "+" + slice.getOptimalFlex() + ")";
        graphics.setColor(Color.white);
        graphics.setFont(Fonts.ti(50));
        drawCenteredLabel(graphics, totals, Color.white, base, 110);
        drawCenteredLabel(graphics, optimal, Color.white, base, 165);
    }

    private static List<TI4Emoji> featureEmojis(MiltyDraftSlice slice) {
        List<TI4Emoji> wormholes = new ArrayList<>();
        List<TI4Emoji> legendary = new ArrayList<>();
        List<TI4Emoji> blueSkips = new ArrayList<>();
        List<TI4Emoji> greenSkips = new ArrayList<>();
        List<TI4Emoji> yellowSkips = new ArrayList<>();
        List<TI4Emoji> redSkips = new ArrayList<>();
        for (MiltyDraftTile tile : slice.getTiles()) {
            if (tile.isHasAlphaWH()) wormholes.add(MiscEmojis.WHalpha);
            if (tile.isHasBetaWH()) wormholes.add(MiscEmojis.WHbeta);
            if (tile.isHasOtherWH()) wormholes.add(MiscEmojis.WHgamma);
            if (tile.isLegendary()) legendary.add(MiscEmojis.LegendaryPlanet);

            for (UnitHolder unitHolder : tile.getTile().getPlanetUnitHolders()) {
                if (unitHolder instanceof Planet planet) {
                    for (String speciality : planet.getTechSpecialities()) {
                        switch (speciality) {
                            case "propulsion" -> blueSkips.add(TechEmojis.PropulsionTech);
                            case "biotic" -> greenSkips.add(TechEmojis.BioticTech);
                            case "cybernetic" -> yellowSkips.add(TechEmojis.CyberneticTech);
                            case "warfare" -> redSkips.add(TechEmojis.WarfareTech);
                        }
                    }
                }
            }
        }
        List<TI4Emoji> features = new ArrayList<>();
        features.addAll(yellowSkips);
        features.addAll(blueSkips);
        features.addAll(greenSkips);
        features.addAll(redSkips);
        features.addAll(wormholes);
        features.addAll(legendary);
        return features;
    }
}
