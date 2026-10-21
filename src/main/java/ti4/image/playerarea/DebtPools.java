package ti4.image.playerarea;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.Map.Entry;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.ImageHelper;
import ti4.image.Mapper;
import ti4.image.PaImage;
import ti4.image.Strokes;
import ti4.service.emoji.TI4Emoji;

final class DebtPools {

    private static final String DEFAULT_BANK_IMAGE = "pa_debtaccount.png";
    private static final int TOKEN_ROWS = 5;

    private DebtPools() {}

    static int draw(PlayerAreaContext ctx, Player player, int x, int y) {
        int startX = x;
        Graphics2D g2 = ctx.g2();
        g2.setStroke(Strokes.STROKE_2);
        g2.setColor(Color.WHITE);

        for (Entry<String, Map<String, Integer>> pool :
                player.getAllDebtTokens().entrySet()) {
            if (pool.getValue().values().stream().noneMatch(i -> i > 0)) {
                continue;
            }
            x = drawPool(ctx, pool, x, y);
        }
        return x + (startX == x ? 0 : 20);
    }

    private static int drawPool(PlayerAreaContext ctx, Entry<String, Map<String, Integer>> pool, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        String bankSource = game.getDebtPoolIcon(pool.getKey());
        if (bankSource == null) {
            if (Constants.DEBT_DEFAULT_POOL.equalsIgnoreCase(pool.getKey())) {
                bankSource = DEFAULT_BANK_IMAGE;
                PaImage.draw(g2, x, y, bankSource);
            } else {
                bankSource = TI4Emoji.getRandomizedEmoji(0, game.getName() + pool.getKey())
                        .toString();
                game.setDebtPoolIcon(pool.getKey(), bankSource);
            }
        }
        if (!DEFAULT_BANK_IMAGE.equals(bankSource)) {
            g2.drawImage(bankImage(game, pool.getKey(), bankSource), x + 20, y + 15, null);
        }

        int deltaX = 24;
        g2.setFont(Storage.getFont18());
        DrawingUtil.drawDebtBoxText(g2, pool.getKey().toUpperCase(), x - 4, y + 148, 146);

        int tokenDeltaY = 0;
        int playerCount = 0;
        int maxTokenDeltaX = 0;
        for (Entry<String, Integer> debtToken : pool.getValue().entrySet()) {
            int numTokens = debtToken.getValue();
            if (numTokens == 0) continue;
            String controlID = Mapper.getControlID(debtToken.getKey());
            if (controlID.contains("null")) {
                continue;
            }
            int tokenDeltaX = drawTokenRow(ctx, debtToken.getKey(), controlID, numTokens, x + deltaX, y + tokenDeltaY);

            tokenDeltaY += 29;
            maxTokenDeltaX = Math.max(maxTokenDeltaX, tokenDeltaX + 35);
            playerCount++;
            if (playerCount % TOKEN_ROWS == 0) {
                tokenDeltaY = 0;
                deltaX += maxTokenDeltaX;
                maxTokenDeltaX = 0;
            }
        }
        deltaX = Math.max(deltaX + maxTokenDeltaX, 152);
        g2.drawRect(x - 2, y - 2, deltaX, 152);
        return x + deltaX + 4;
    }

    private static int drawTokenRow(
            PlayerAreaContext ctx, String debtColor, String controlID, int numTokens, int x, int y) {
        Game game = ctx.game();
        Graphics2D g2 = ctx.g2();
        Player debtPlayer =
                game.getPlayerByColorID(Mapper.getColorID(debtColor)).orElse(null);
        int overflow = 0;
        if (numTokens > 12) {
            overflow = numTokens - 10;
            numTokens = 10;
        }
        boolean hideFactionIcon = debtPlayer != null && ctx.hidesStatsOf(debtPlayer);

        float scale = 0.60f;
        BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);
        int tokenDeltaX = 0;
        for (int i = 0; i < numTokens; i++) {
            DrawingUtil.drawControlToken(
                    g2,
                    controlTokenImage,
                    DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlID),
                    x + tokenDeltaX,
                    y + 2,
                    hideFactionIcon,
                    scale);
            tokenDeltaX += 15;
        }

        if (overflow > 0) {
            g2.setColor(Color.WHITE);
            g2.setFont(Storage.getFont18());
            g2.drawString("+" + overflow, x + tokenDeltaX + 29, y + 22);
            tokenDeltaX += 2 + g2.getFontMetrics().stringWidth("+" + overflow);
        }
        return tokenDeltaX;
    }

    private static BufferedImage bankImage(Game game, String poolName, String bankSource) {
        Emoji bankEmoji = Emoji.fromFormatted(bankSource);
        if (bankEmoji instanceof CustomEmoji customEmoji) {
            BufferedImage bankImage = ImageHelper.readURLScaled(customEmoji.getImageUrl(), 120, 120);
            if (bankImage != null) {
                return bankImage;
            }
            Emoji replacement =
                    TI4Emoji.findEmojiFromJustName(customEmoji.getName()).asEmoji();
            game.setDebtPoolIcon(poolName, replacement.getFormatted());
            if (replacement instanceof CustomEmoji replacementCustom) {
                return ImageHelper.readURLScaled(replacementCustom.getImageUrl(), 120, 120);
            }
            return null;
        }
        if (bankEmoji instanceof UnicodeEmoji unicodeEmoji) {
            return unicodeEmojiImage(unicodeEmoji);
        }
        return null;
    }

    private static BufferedImage unicodeEmojiImage(UnicodeEmoji emoji) {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g3 = img.createGraphics();
        g3.setFont(Storage.getEmojiFont());
        DrawingUtil.superDrawString(
                g3, emoji.getFormatted(), 20, 60, Color.white, null, null, new BasicStroke(4.0f), Color.black);
        GlyphVector gv = g3.getFont().createGlyphVector(g3.getFontRenderContext(), emoji.getFormatted());
        Rectangle rect = gv.getGlyphPixelBounds(0, g3.getFontRenderContext(), 20, 60);
        int pad = 5;
        BufferedImage cropped =
                img.getSubimage(rect.x - pad, rect.y - pad, rect.width + pad * 2, rect.height + pad * 2);
        return ImageHelper.scale(ImageHelper.square(cropped), 120, 120);
    }
}
