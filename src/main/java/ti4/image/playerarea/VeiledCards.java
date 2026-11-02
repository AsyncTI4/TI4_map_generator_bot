package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import ti4.game.Player;
import ti4.image.draw.VerticalText;
import ti4.image.io.Fonts;
import ti4.service.VeiledHeartService;
import ti4.service.VeiledHeartService.VeiledCardType;

final class VeiledCards {

    private static final int CARD_STEP = 48;

    private VeiledCards() {}

    static int draw(Graphics2D graphics, int x, int y, VeiledCardType type, int deltaX, Player player) {
        graphics.setColor(Color.LIGHT_GRAY);
        graphics.setFont(Fonts.ti(18));
        String text = "VEILED\n" + type.toString();
        int cardCount = VeiledHeartService.countVeiledCards(type, player);
        for (int i = 0; i < cardCount; ++i) {
            VerticalText.drawOneOrTwoLines(graphics, text, x + deltaX + 7, y + 116, 116);
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            deltaX += CARD_STEP;
        }
        return deltaX;
    }
}
