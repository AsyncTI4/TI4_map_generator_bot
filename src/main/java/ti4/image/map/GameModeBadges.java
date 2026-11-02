package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import ti4.game.Game;
import ti4.helpers.TIGLHelper;
import ti4.helpers.TIGLHelper.TIGLRank;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.io.Fonts;

final class GameModeBadges {

    static final int ROOM_ABOVE_HEADER = 95;

    private static final int BADGE_ABOVE_NAME = -150;

    private GameModeBadges() {}

    static boolean isShown(Game game) {
        return game.isCompetitiveTIGLGame()
                || game.isAbsolMode()
                || game.isNoSwapMode()
                || game.isMiltyModMode()
                || game.isDiscordantStarsMode();
    }

    static void draw(RenderContext ctx, Graphics2D g2, int x, int y) {
        Game game = ctx.game();
        WebsiteOverlays overlays = ctx.overlays();
        int deltaX = 0;
        int deltaY = BADGE_ABOVE_NAME;
        if (game.isCompetitiveTIGLGame()) {
            PaImage.drawGeneral(
                    g2,
                    x + deltaX,
                    y + deltaY,
                    TIGLHelper.isFracturedTIGLGame(game) ? "GameMode_TIGL_Fractured.png" : "GameMode_TIGL.png");
            TIGLRank rank = game.getMinimumTIGLRankAtGameStart();
            if (rank != null) {
                g2.setFont(Fonts.ti(18));
                OutlinedText.draw(
                        g2,
                        rank.getShortName(),
                        x + deltaX + 50,
                        y + deltaY + 75,
                        Color.WHITE,
                        HorizontalAlign.Center,
                        VerticalAlign.Top,
                        Strokes.STROKE_2,
                        Color.BLACK);
                g2.setFont(Fonts.ti(32));
            }
            deltaX += 100;
        }
        if (game.isAbsolMode()) {
            PaImage.drawGeneral(g2, x + deltaX, y + deltaY, "GameMode_Absol.png");
            overlays.add("Absol", null, x + deltaX, y + deltaY, 90, 90);
            deltaX += 100;
        }
        if (game.isNoSwapMode()) {
            PaImage.drawGeneral(g2, x + deltaX, y + deltaY, "NoSwap.png");
            overlays.add("No Support Swap", null, x + deltaX, y + deltaY, 180, 90);
            deltaX += 190;
        }
        if (game.isMiltyModMode()) {
            PaImage.drawGeneral(g2, x + deltaX, y + deltaY, "GameMode_MiltyMod.png");
            overlays.add("MiltyMod", null, x + deltaX, y + deltaY, 90, 90);
            deltaX += 100;
        }
        if (game.isDiscordantStarsMode()) {
            PaImage.drawGeneral(g2, x + deltaX, y + deltaY, "GameMode_DiscordantStars.png");
            overlays.add("Discordant Stars", null, x + deltaX, y + deltaY, 90, 90);
        }
    }
}
