package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Player;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.io.Fonts;

final class UnitValues {

    private static final int ROW_HEIGHT = 39;
    private static final int SPACE_WIDTH = 70;
    private static final int GROUND_WIDTH = 80;
    private static final int BLOCK_WIDTH = 50 + SPACE_WIDTH + GROUND_WIDTH;

    private UnitValues() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        Graphics2D g2 = ctx.g2();
        int leftSide = slot.rightEdge() - BLOCK_WIDTH - xDeltaFromRight;
        ctx.overlays()
                .add(
                        "Fleet Stats",
                        "- Total Resources\n- Total Hit Points\n- Total Expected Hits",
                        leftSide,
                        y + 10,
                        BLOCK_WIDTH - 10,
                        ROW_HEIGHT * 4 - 10);
        int imageSize = ROW_HEIGHT - 2;
        PaImage.drawScaled(g2, leftSide, y + ROW_HEIGHT, "pa_resources.png", imageSize, imageSize);
        PaImage.drawScaled(g2, leftSide, y + ROW_HEIGHT * 2, "pa_health.png", imageSize, imageSize);
        PaImage.drawScaled(g2, leftSide, y + ROW_HEIGHT * 3, "pa_hit.png", imageSize, imageSize);
        leftSide += ROW_HEIGHT + 10;

        drawDividers(g2, leftSide, y);
        drawColumn(g2, player, "space", leftSide + (SPACE_WIDTH / 2), y);
        drawColumn(g2, player, "ground", leftSide + SPACE_WIDTH + (GROUND_WIDTH / 2) + 2, y);
        return xDeltaFromRight + BLOCK_WIDTH;
    }

    private static void drawDividers(Graphics2D g2, int left, int y) {
        Stroke orig = g2.getStroke();
        g2.setColor(Color.black);
        g2.setStroke(Strokes.STROKE_2);
        drawDividerLines(g2, left, y);
        g2.setColor(Color.white);
        g2.setStroke(Strokes.STROKE_1);
        drawDividerLines(g2, left, y);
        g2.setStroke(orig);
    }

    private static void drawDividerLines(Graphics2D g2, int left, int y) {
        g2.drawLine(left + SPACE_WIDTH, y + 25, left + SPACE_WIDTH, y + ROW_HEIGHT + 5);
        g2.drawLine(left, y + ROW_HEIGHT, left + SPACE_WIDTH + GROUND_WIDTH, y + ROW_HEIGHT);
    }

    private static void drawColumn(Graphics2D g2, Player player, String type, int xCenter, int yTop) {
        float resources = player.getTotalResourceValueOfUnits(type);
        String totResValue =
                resources == Math.floor(resources) ? String.valueOf((int) resources) : String.valueOf(resources);
        String healthValue = String.valueOf(player.getTotalHPValueOfUnits(type));
        String combatValue = String.valueOf(player.getTotalCombatValueOfUnits(type));

        g2.setFont(Fonts.ti(18));
        OutlinedText.drawCenteredWhiteOnBlack(g2, StringUtils.capitalize(type), xCenter, yTop + 32);
        g2.setFont(Fonts.ti(24));
        OutlinedText.drawCenteredWhiteOnBlack(g2, totResValue, xCenter, yTop + ROW_HEIGHT + 20);
        OutlinedText.drawCenteredWhiteOnBlack(g2, healthValue, xCenter, yTop + ROW_HEIGHT * 2 + 20);
        OutlinedText.drawCenteredWhiteOnBlack(g2, combatValue, xCenter, yTop + ROW_HEIGHT * 3 + 20);
    }
}
