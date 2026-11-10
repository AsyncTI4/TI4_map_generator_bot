package ti4.image.map;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.MegagameGroupTokens;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.StarfieldPage;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.model.StrategyCardModel;
import ti4.service.megagame.MegagameGroupService;

@UtilityClass
public class MegagameGroupsImage {

    private static final int SPEAKER_COLUMN = 170;
    private static final int FACTION_COLUMN = 200;
    private static final int HEADER_HEIGHT = 120;
    private static final int ROW_HEIGHT = 170;
    private static final int ICON_SIZE = 100;
    private static final int CIRCLE_SIZE = 90;
    private static final Color GRID = new Color(255, 255, 255, 170);
    private static final Font GREEK_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 60);

    public static BufferedImage render(Game game) {
        return render(game, MegagameGroupService.currentLayout(game), true);
    }

    public static BufferedImage render(Game game, MegagameGroupService.Layout layout, boolean showCards) {
        List<String> groups = layout.groups();
        int rows = groups.stream()
                .mapToInt(group -> layout.members().get(group).size())
                .max()
                .orElse(0);
        int width = groups.size() * (SPEAKER_COLUMN + FACTION_COLUMN);
        int height = HEADER_HEIGHT + rows * ROW_HEIGHT;
        StarfieldPage page = new StarfieldPage(width, height);
        Graphics2D g2 = page.graphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int x = page.left();
        for (String group : groups) {
            drawGroup(g2, game, layout, group, x, page.top(), rows, showCards);
            x += SPEAKER_COLUMN + FACTION_COLUMN;
        }
        drawGrid(g2, page.left(), page.top(), groups.size(), rows);
        return page.crop(page.left() + width, page.top() + height);
    }

    private static void drawGroup(
            Graphics2D g2,
            Game game,
            MegagameGroupService.Layout layout,
            String group,
            int left,
            int top,
            int rows,
            boolean showCards) {
        BufferedImage token = MegagameGroupTokens.plainSpeakerToken(group);
        if (token != null) {
            drawCentered(g2, token, left, top, SPEAKER_COLUMN, HEADER_HEIGHT);
        }
        drawGroupCircle(g2, group, left + SPEAKER_COLUMN, top);
        Player speaker = layout.speakers().get(group);
        Player picker = showCards && "strategy".equalsIgnoreCase(game.getPhaseOfGame())
                ? MegagameGroupService.currentPicker(game, group)
                : null;
        List<Player> members = layout.members().get(group);
        for (int row = 0; row < Math.min(rows, members.size()); row++) {
            Player member = members.get(row);
            int rowTop = top + HEADER_HEIGHT + row * ROW_HEIGHT;
            if (member.equals(speaker) && token != null) {
                drawCentered(g2, token, left, rowTop, SPEAKER_COLUMN, ROW_HEIGHT);
            }
            if (member.equals(picker)) {
                g2.setColor(MegagameGroupService.colour(group));
                g2.setStroke(new BasicStroke(6));
                g2.drawRect(left + SPEAKER_COLUMN + 4, rowTop + 4, FACTION_COLUMN - 8, ROW_HEIGHT - 8);
            }
            drawMember(g2, game, member, left + SPEAKER_COLUMN, rowTop, showCards);
        }
    }

    private static void drawGroupCircle(Graphics2D g2, String group, int left, int top) {
        int cx = left + FACTION_COLUMN / 2;
        int cy = top + HEADER_HEIGHT / 2;
        g2.setColor(MegagameGroupService.colour(group));
        g2.fillOval(cx - CIRCLE_SIZE / 2, cy - CIRCLE_SIZE / 2, CIRCLE_SIZE, CIRCLE_SIZE);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(3));
        g2.drawOval(cx - CIRCLE_SIZE / 2, cy - CIRCLE_SIZE / 2, CIRCLE_SIZE, CIRCLE_SIZE);
        g2.setFont(GREEK_FONT);
        OutlinedText.draw(
                g2,
                MegagameGroupService.greek(group),
                cx,
                cy,
                Color.BLACK,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                null,
                null);
    }

    private static void drawMember(Graphics2D g2, Game game, Player member, int left, int top, boolean showCards) {
        FactionIcons.draw(g2, member, left + (FACTION_COLUMN - ICON_SIZE) / 2, top + 12, ICON_SIZE, ICON_SIZE);
        if (!showCards) {
            return;
        }
        g2.setFont(Fonts.ti(22));
        int textY = top + ICON_SIZE + 36;
        for (int sc : member.getSCs()) {
            boolean played = Boolean.TRUE.equals(game.getScPlayed().get(sc));
            String name = game.getStrategyCardModelByInitiative(sc)
                    .map(StrategyCardModel::getName)
                    .orElse("");
            OutlinedText.draw(
                    g2,
                    sc + " " + name,
                    left + FACTION_COLUMN / 2,
                    textY,
                    played ? Color.GRAY : Color.WHITE,
                    HorizontalAlign.Center,
                    VerticalAlign.Center,
                    Strokes.STROKE_2,
                    Color.BLACK);
            textY += 24;
        }
    }

    private static void drawGrid(Graphics2D g2, int left, int top, int groups, int rows) {
        g2.setColor(GRID);
        g2.setStroke(new BasicStroke(2));
        int width = groups * (SPEAKER_COLUMN + FACTION_COLUMN);
        int height = HEADER_HEIGHT + rows * ROW_HEIGHT;
        for (int column = 0; column <= groups * 2; column++) {
            int x = left + column / 2 * (SPEAKER_COLUMN + FACTION_COLUMN) + (column % 2) * SPEAKER_COLUMN;
            g2.drawLine(x, top, x, top + height);
        }
        for (int row = 0; row <= rows + 1; row++) {
            int y = row == 0 ? top : top + HEADER_HEIGHT + (row - 1) * ROW_HEIGHT;
            g2.drawLine(left, y, left + width, y);
        }
    }

    private static void drawCentered(Graphics2D g2, BufferedImage image, int left, int top, int width, int height) {
        double scale = Math.min(1.0, Math.min((width - 16.0) / image.getWidth(), (height - 16.0) / image.getHeight()));
        int w = (int) (image.getWidth() * scale);
        int h = (int) (image.getHeight() * scale);
        g2.drawImage(image, left + (width - w) / 2, top + (height - h) / 2, w, h, null);
    }
}
