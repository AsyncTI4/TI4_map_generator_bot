package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersBreakthroughHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.PaImage;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.image.io.RemoteImages;
import ti4.logging.BotLogger;
import ti4.model.BreakthroughModel;
import ti4.service.emoji.MiscEmojis;

final class BreakthroughCards {

    private static final Color ACTIVE_COLOR = new Color(19, 249, 236);
    private static final int CARD_STEP = 48;

    private BreakthroughCards() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        int startX = x;
        for (String bt : player.getBreakthroughIDs()) {
            x = drawBreakthrough(ctx, player, bt, x, y);
        }
        return x + (startX == x ? 0 : 20);
    }

    private static boolean isShown(Game game, Player player, BreakthroughModel model) {
        return model != null
                && ((game.isThundersEdge() && !game.isTwilightsFallMode())
                        || player.hasUnlockedBreakthrough(model.getID()));
    }

    private static int drawBreakthrough(PlayerAreaContext ctx, Player player, String bt, int x, int y) {
        Game game = ctx.game();
        BreakthroughModel model = player.getBreakthroughModel(bt);
        if (!isShown(game, player, model)) return x;
        String name = model.getShortName();
        boolean exhausted = player.isBreakthroughExhausted(bt);
        boolean unlocked = player.isBreakthroughUnlocked(bt);

        try {
            Graphics2D g2 = ctx.g2();
            Color textColor = Color.white;
            if (unlocked && player.isBreakthroughActive(bt)) textColor = ACTIVE_COLOR;
            else if (!unlocked || exhausted) textColor = Color.gray;
            g2.setColor(textColor);
            g2.setStroke(Strokes.STROKE_2);

            FactionIcons.drawFaction(g2, model.getFaction().orElse(null), x - 1, y + 108, 42, 42);
            BreakthroughModel copiedDataBreach = "netrunnersbt".equals(bt)
                    ? NetrunnersBreakthroughHandler.getCopiedDataBreachBreakthrough(game, player)
                    : null;
            BreakthroughModel background = copiedDataBreach == null ? model : copiedDataBreach;
            PaImage.draw(g2, x, y, background.getBackgroundResource(unlocked && !exhausted));
            if (model.getShrinkName()) {
                g2.setFont(Fonts.ti(16));
                VerticalText.drawOneOrTwoLines(g2, model.getShortName(), x + 11, y + 144, 120);
            } else {
                g2.setFont(Fonts.ti(18));
                VerticalText.drawOneOrTwoLines(g2, model.getShortName(), x + 9, y + 144, 120);
            }

            if (!unlocked) textColor = Color.red;
            g2.setColor(textColor);
            CardRect.draw(ctx, x, y - 2, CardRect.WIDTH, CardRect.HEIGHT, model);
            if (copiedDataBreach != null) {
                String copiedFaction = NetrunnersBreakthroughHandler.getDataBreachTargetFaction(game, player);
                if (copiedFaction != null) {
                    FactionIcons.drawFaction(g2, copiedFaction, x + 20, y + 2, 20, 20);
                }
            }
            if (player.getBreakthroughTGs(bt) > 0) {
                drawTradeGoods(g2, player.getBreakthroughTGs(bt), x, y);
            }
        } catch (Exception e) {
            BotLogger.error("Error displaying breakthrough: " + name, e);
            return x;
        }
        return x + CARD_STEP;
    }

    private static void drawTradeGoods(Graphics2D g2, int tradeGoods, int x, int y) {
        BufferedImage tg = RemoteImages.emoji(MiscEmojis.tg, 40);
        g2.drawImage(tg, x + 2, y - 40, null);
        g2.setFont(Fonts.ti(32));
        OutlinedText.draw(
                g2,
                Integer.toString(tradeGoods),
                x + 22,
                y - 20,
                Color.white,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                Strokes.STROKE_8,
                Color.black);
    }
}
