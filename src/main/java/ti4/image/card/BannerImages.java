package ti4.image.card;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;

final class BannerImages {

    private static final BasicStroke THIN_OUTLINE = Strokes.STROKE_2;
    private static final BasicStroke ROUND_OUTLINE = Strokes.STROKE_6;
    private static final BasicStroke PHASE_OUTLINE = Strokes.STROKE_8;
    private static final double NINETY_DEGREES_RADIANS = 1.570_796_326_794_896_6;
    private static final int STRIP_HEIGHT = 50;
    private static final int STRIP_BACKGROUND_WIDTH = 325;
    private static final int FACTION_BANNER_WIDTH = 325;
    private static final int AGENDA_BANNER_WIDTH = 225;
    private static final int PHASE_BANNER_WIDTH = 511;
    private static final int PHASE_BANNER_HEIGHT = 331;
    private static final String AGENDA_STRIP_COLOR = "blue";

    private BannerImages() {}

    static BufferedImage faction(Player player) {
        BufferedImage banner = new BufferedImage(FACTION_BANNER_WIDTH, STRIP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage smallIcon = FactionIcons.scaled(player, 0.26f);
        BufferedImage largeIcon = FactionIcons.scaled(player, 1.4f);
        Graphics2D g2 = banner.createGraphics();
        drawStrip(g2, player.getColor());
        g2.drawImage(smallIcon, 2, 24, null);
        g2.drawImage(largeIcon, 180, -42, null);
        g2.setFont(Fonts.ti(16));
        g2.setColor(Color.WHITE);
        drawWhiteOnBlack(g2, player.bannerName(), 29, 44);
        int initiativeX = player.getInitiative() > 9 ? 287 : 300;
        drawWhiteOnBlack(g2, "#" + player.getInitiative(), initiativeX, 44);
        return banner;
    }

    static BufferedImage agenda(int number) {
        BufferedImage banner = new BufferedImage(AGENDA_BANNER_WIDTH, STRIP_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage agendaIcon =
                ImageHelper.readScaled(ImageResources.extra("agenda.png"), STRIP_HEIGHT, STRIP_HEIGHT);
        Graphics2D g2 = banner.createGraphics();
        drawStrip(g2, AGENDA_STRIP_COLOR);
        g2.drawImage(agendaIcon, 0, 0, null);
        g2.setFont(Fonts.ti(28));
        g2.setColor(Color.WHITE);
        drawWhiteOnBlack(g2, "Agenda #" + number, 55, 35);
        return banner;
    }

    static BufferedImage phase(String phase, int round, String phaseLabel) {
        BufferedImage banner = new BufferedImage(PHASE_BANNER_WIDTH, PHASE_BANNER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage background = ImageHelper.readScaled(
                ImageResources.extra(phase + "banner.png"), PHASE_BANNER_WIDTH, PHASE_BANNER_HEIGHT);
        Graphics2D g2 = banner.createGraphics();
        g2.drawImage(background, 0, 0, null);
        g2.setFont(Fonts.ti(48));
        g2.setColor(Color.WHITE);
        drawCentered(g2, phaseLabel.toUpperCase() + " PHASE", 110, PHASE_OUTLINE);
        g2.setFont(Fonts.ti(32));
        drawCentered(g2, "ROUND " + StringHelper.numberToWords(round).toUpperCase(), 221, ROUND_OUTLINE);
        return banner;
    }

    private static void drawStrip(Graphics2D g2, String color) {
        BufferedImage background = ImageHelper.readScaled(
                ImageResources.extra("factionbanner_background.png"), STRIP_BACKGROUND_WIDTH, STRIP_HEIGHT);
        String colorFile = "pa_pn_color_" + Mapper.getColorID(color) + ".png";
        BufferedImage colorBand = ImageHelper.readScaled(ImageResources.playerArea(colorFile), 1.5f);
        BufferedImage gradient = ImageHelper.read(ImageResources.extra("factionbanner_gradient.png"));
        g2.drawImage(background, 0, 0, null);
        g2.rotate(-NINETY_DEGREES_RADIANS);
        g2.drawImage(colorBand, -60, 0, null);
        g2.rotate(NINETY_DEGREES_RADIANS);
        g2.drawImage(gradient, 0, 0, null);
    }

    private static void drawWhiteOnBlack(Graphics2D g2, String text, int x, int y) {
        OutlinedText.draw(
                g2, text, x, y, Color.WHITE, HorizontalAlign.Left, VerticalAlign.Bottom, THIN_OUTLINE, Color.BLACK);
    }

    private static void drawCentered(Graphics2D g2, String text, int y, BasicStroke outline) {
        OutlinedText.draw(
                g2,
                text,
                PHASE_BANNER_WIDTH / 2,
                y,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                outline,
                Color.BLACK);
    }
}
