package ti4.image.map;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.MegagameGroupTokens;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.StarfieldPage;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.service.megagame.MegagameGroupService;

@UtilityClass
public class MegagameGroupsImage {

    private static final int COLUMN_WIDTH = 260;
    private static final int TITLE_HEIGHT = 70;
    private static final int HEADER_HEIGHT = 130;
    private static final int ROW_HEIGHT = 190;
    private static final int TINT_INSET = 3;
    private static final int CIRCLE_SIZE = 96;
    private static final int FACTION_SIZE = 112;
    private static final int FACTION_TOP = 10;
    private static final int BADGE_SIZE = 58;
    private static final int SPEAKER_WIDTH = 66;
    private static final int NAME_LINE = 24;
    private static final float PLAYED_OPACITY = 0.3f;
    private static final Color PLAYED_TEXT = new Color(150, 150, 150);
    private static final Color STRIKE = new Color(220, 60, 60);
    private static final Font GREEK_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 60);
    private static final int TITLE_MAX_FONT = 38;
    private static final int TITLE_MIN_FONT = 18;
    private static final List<Offset> BADGE_OFFSETS =
            List.of(new Offset(FACTION_SIZE - 40, 54), new Offset(-18, 54), new Offset(FACTION_SIZE - 40, -6));

    private record Offset(int x, int y) {}

    public static BufferedImage render(Game game) {
        return render(MegagameGroupsView.of(game));
    }

    public static BufferedImage render(Game game, MegagameGroupService.Layout layout, boolean showCards) {
        return render(MegagameGroupsView.of(game, layout, showCards));
    }

    public static BufferedImage render(MegagameGroupsView view) {
        int width = view.groups().size() * COLUMN_WIDTH;
        int height = TITLE_HEIGHT + HEADER_HEIGHT + view.rows() * ROW_HEIGHT;
        StarfieldPage page = new StarfieldPage(width, height);
        Graphics2D g2 = page.graphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        drawTitle(g2, view.title(), page.left() + 16, page.top() + TITLE_HEIGHT / 2, width - 32);
        int x = page.left();
        int top = page.top() + TITLE_HEIGHT;
        for (MegagameGroupsView.Group group : view.groups()) {
            drawGroup(g2, group, x, top, height - TITLE_HEIGHT, view.showCards());
            x += COLUMN_WIDTH;
        }
        return page.crop(page.left() + width, page.top() + height);
    }

    private static void drawTitle(Graphics2D g2, String title, int x, int y, int maxWidth) {
        int size = TITLE_MAX_FONT;
        g2.setFont(Fonts.ti(size));
        while (size > TITLE_MIN_FONT && g2.getFontMetrics().stringWidth(title) > maxWidth) {
            size -= 2;
            g2.setFont(Fonts.ti(size));
        }
        OutlinedText.draw(
                g2,
                title,
                x,
                y,
                Color.WHITE,
                HorizontalAlign.Left,
                VerticalAlign.Center,
                new BasicStroke(3),
                Color.BLACK);
    }

    private static void drawGroup(
            Graphics2D g2, MegagameGroupsView.Group group, int left, int top, int height, boolean showCards) {
        Color colour = MegagameGroupService.colour(group.id());
        g2.setPaint(new GradientPaint(0, top, withAlpha(colour, 90), 0, top + height, withAlpha(colour, 10)));
        g2.fillRect(left + TINT_INSET, top, COLUMN_WIDTH - 2 * TINT_INSET, height);
        drawGroupCircle(g2, group.id(), colour, left + COLUMN_WIDTH / 2, top + HEADER_HEIGHT / 2);
        List<MegagameGroupsView.Member> members = group.members();
        for (int row = 0; row < members.size(); row++) {
            int rowTop = top + HEADER_HEIGHT + row * ROW_HEIGHT;
            drawMember(g2, group.id(), colour, members.get(row), left, rowTop, showCards);
        }
    }

    private static void drawGroupCircle(Graphics2D g2, String group, Color colour, int cx, int cy) {
        int radius = CIRCLE_SIZE / 2;
        g2.setColor(colour);
        g2.fillOval(cx - radius, cy - radius, CIRCLE_SIZE, CIRCLE_SIZE);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(4));
        g2.drawOval(cx - radius, cy - radius, CIRCLE_SIZE, CIRCLE_SIZE);
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

    private static void drawMember(
            Graphics2D g2,
            String group,
            Color colour,
            MegagameGroupsView.Member member,
            int left,
            int top,
            boolean showCards) {
        if (member.picking()) {
            drawGlow(g2, colour, left + 10, top + 4, COLUMN_WIDTH - 20, ROW_HEIGHT - 8);
        }
        int factionLeft = left + (COLUMN_WIDTH - FACTION_SIZE) / 2;
        int factionTop = top + FACTION_TOP;
        BufferedImage icon = FactionIcons.scaled(member.faction(), member.customEmoji(), FACTION_SIZE, FACTION_SIZE);
        if (icon != null) {
            g2.drawImage(icon, factionLeft, factionTop, null);
        }
        if (member.speaker()) {
            drawSpeakerToken(g2, group, left + 6, top + 8);
        }
        if (!showCards) {
            return;
        }
        List<MegagameGroupsView.Card> cards = member.cards();
        for (int i = 0; i < Math.min(cards.size(), BADGE_OFFSETS.size()); i++) {
            Offset offset = BADGE_OFFSETS.get(i);
            drawBadge(g2, cards.get(i), factionLeft + offset.x(), factionTop + offset.y());
        }
        g2.setFont(Fonts.ti(24));
        int lineY = top + ROW_HEIGHT - 36 - (cards.size() - 1) * NAME_LINE;
        for (MegagameGroupsView.Card card : cards) {
            drawCardName(g2, card, left + COLUMN_WIDTH / 2, lineY);
            lineY += NAME_LINE;
        }
    }

    private static void drawSpeakerToken(Graphics2D g2, String group, int x, int y) {
        BufferedImage token = MegagameGroupTokens.plainSpeakerToken(group);
        if (token == null) {
            return;
        }
        int height = SPEAKER_WIDTH * token.getHeight() / token.getWidth();
        g2.drawImage(token, x, y, SPEAKER_WIDTH, height, null);
    }

    private static void drawBadge(Graphics2D g2, MegagameGroupsView.Card card, int x, int y) {
        String path = ImageResources.strategyCardIcon(card.iconNumber());
        BufferedImage icon = path == null ? null : ImageHelper.readScaled(path, BADGE_SIZE, BADGE_SIZE);
        if (icon == null) {
            return;
        }
        Composite previous = g2.getComposite();
        if (card.played()) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, PLAYED_OPACITY));
        }
        g2.drawImage(icon, x, y, null);
        g2.setComposite(previous);
    }

    private static void drawCardName(Graphics2D g2, MegagameGroupsView.Card card, int centreX, int y) {
        String text = card.number() + " " + card.name();
        Color fill = card.played() ? PLAYED_TEXT : Color.WHITE;
        OutlinedText.draw(
                g2,
                text,
                centreX,
                y,
                fill,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                new BasicStroke(2),
                Color.BLACK);
        if (card.played()) {
            int halfWidth = g2.getFontMetrics().stringWidth(text) / 2;
            g2.setColor(STRIKE);
            g2.setStroke(new BasicStroke(3));
            g2.drawLine(centreX - halfWidth - 2, y, centreX + halfWidth + 2, y);
        }
    }

    private static void drawGlow(Graphics2D g2, Color colour, int x, int y, int width, int height) {
        for (int ring = 4; ring >= 1; ring--) {
            g2.setColor(withAlpha(colour, 60 + (4 - ring) * 50));
            g2.setStroke(new BasicStroke(ring * 3));
            g2.draw(new RoundRectangle2D.Float(x, y, width, height, 24, 24));
        }
    }

    private static Color withAlpha(Color colour, int alpha) {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }
}
