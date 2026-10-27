package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import ti4.image.io.Fonts;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.UnitTokenPosition;

final class ReinforcementNumber {

    private ReinforcementNumber() {}

    static void paint(Graphics2D g2, String unitID, int x, int y, int reinforcementsCount, String color) {
        String id = "number_" + unitID;
        UnitTokenPosition textPosition = MapPositionMapper.getReinforcementsPosition(id);
        if (textPosition == null) {
            return;
        }
        Point position = textPosition.getPosition(id);
        String text = String.valueOf(reinforcementsCount);

        g2.setFont(Fonts.ti(35));
        int offset = 20 - g2.getFontMetrics().stringWidth(text) / 2;
        boolean darkUnit = reinforcementsCount > 0
                && "_blk.png"
                        .equalsIgnoreCase(
                                Mapper.getColor(Mapper.getColorID(color)).blackWhiteFileSuffix());
        g2.setColor(reinforcementsCount <= 0 ? Color.YELLOW : darkUnit ? Color.WHITE : Color.BLACK);
        for (int i = -2; i <= 2; i++) {
            for (int j = (i == -2 || i == 2 ? -1 : -2); j <= (i == -2 || i == 2 ? 1 : 2); j++) {
                g2.drawString(text, x + position.x + offset + i, y + position.y + j + 28);
            }
        }
        g2.setColor(reinforcementsCount <= 0 ? Color.RED : darkUnit ? Color.BLACK : Color.WHITE);
        g2.drawString(text, x + position.x + offset, y + position.y + 28);
    }
}
