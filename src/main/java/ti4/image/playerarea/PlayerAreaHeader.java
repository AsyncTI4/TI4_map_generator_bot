package ti4.image.playerarea;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.draw.FactionIcons;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.RowWrap;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.draw.WebsiteOverlays;
import ti4.image.io.Fonts;
import ti4.image.io.RemoteImages;
import ti4.service.fow.FogViewer;

public final class PlayerAreaHeader {

    static final int ROW_HEIGHT = 34;
    private static final int WIDTH_BESIDE_LEADERS = 715;
    private static final int RIGHT_MARGIN = 20;
    private static final String TEAMMATE_SEPARATOR = " — ";
    private static final int NEIGHBOUR_INDENT = 9;
    private static final int NEIGHBOUR_ICON_SIZE = 26;
    private static final int NEIGHBOUR_ICON_STEP = 27;
    private static final int NEIGHBOUR_LABEL_WIDTH = 115;

    private final PlayerAreaContext ctx;
    private final AreaSlot slot;
    private final Player player;
    private final int x;

    private PlayerAreaHeader(PlayerAreaContext ctx, AreaSlot slot, Player player, int x) {
        this.ctx = ctx;
        this.slot = slot;
        this.player = player;
        this.x = x;
    }

    private record Teammate(String userID, String name, int width) {}

    private record NeighbourToken(String text, Font font, int baselineDrop, Player player, int leadingGap, int width) {
        int advance() {
            return leadingGap + width;
        }
    }

    public static int extraHeight(Game game, NeighbourMap neighbours, Player player, int areaLeft, int areaRight) {
        Graphics2D probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try {
            PlayerAreaContext probeContext =
                    PlayerAreaContext.of(probe, game, FogViewer.unfogged(game), new WebsiteOverlays(), neighbours);
            return new PlayerAreaHeader(probeContext, new AreaSlot(areaLeft, areaRight), player, areaLeft).height()
                    - ROW_HEIGHT;
        } finally {
            probe.dispose();
        }
    }

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int x, int y) {
        return new PlayerAreaHeader(ctx, slot, player, x).draw(y);
    }

    static boolean showsNeighbours(Game game, Player player) {
        return PlayerAreaColumns.isMegaGame(game)
                && !game.isFowMode()
                && player.getFaction() != null
                && !player.isEliminated();
    }

    private int draw(int y) {
        boolean neighboursInHeader = showsNeighbours(ctx.game(), player);
        int height = drawTeammateNames(y, !neighboursInHeader);
        if (neighboursInHeader) {
            height += drawNeighbourRows(y + height);
        }
        return height;
    }

    private int height() {
        boolean neighboursInHeader = showsNeighbours(ctx.game(), player);
        int height = teammateNamesHeight(!neighboursInHeader);
        if (neighboursInHeader) {
            height += neighbourRows().size() * ROW_HEIGHT;
        }
        return height;
    }

    private int teammateNamesHeight(boolean lastHeaderRow) {
        List<String> teammateIDs = orderedTeammateIDs();
        ctx.g2().setFont(Fonts.ti(32));
        if (teammateIDs.size() == 1) {
            String userName = PlayerNames.discordName(ctx.game(), player, player.getUserID());
            return soloNameNeedsTwoRows(userName, lastHeaderRow) ? 2 * ROW_HEIGHT : ROW_HEIGHT;
        }
        return (1 + teammateRows(teammateIDs, lastHeaderRow).size()) * ROW_HEIGHT;
    }

    private int drawTeammateNames(int y, boolean lastHeaderRow) {
        List<String> teammateIDs = orderedTeammateIDs();
        ctx.g2().setFont(Fonts.ti(32));
        if (teammateIDs.size() == 1) {
            return drawSoloPlayerName(y, lastHeaderRow);
        }

        drawText(PlayerNames.factionDisplayName(player), x, y);
        int yDelta = ROW_HEIGHT;
        int separatorWidth = ctx.g2().getFontMetrics().stringWidth(TEAMMATE_SEPARATOR);
        for (List<Teammate> row : teammateRows(teammateIDs, lastHeaderRow)) {
            drawTeammateRow(row, y + yDelta, separatorWidth);
            yDelta += ROW_HEIGHT;
        }
        return yDelta;
    }

    private List<String> orderedTeammateIDs() {
        List<String> teammateIDs = new ArrayList<>(player.getTeamMateIDs());
        teammateIDs.remove(player.getUserID());
        teammateIDs.addFirst(player.getUserID());
        return teammateIDs;
    }

    private int drawSoloPlayerName(int y, boolean lastHeaderRow) {
        String userName = PlayerNames.discordName(ctx.game(), player, player.getUserID());
        int xDraw = x + avatarTextOffset();
        int yDelta = 0;
        if (soloNameNeedsTwoRows(userName, lastHeaderRow)) {
            drawText(PlayerNames.factionDisplayName(player), xDraw, y);
            yDelta += ROW_HEIGHT;
            drawText(userName, xDraw, y + yDelta);
        } else {
            drawText(userName + " " + PlayerNames.factionDisplayName(player), xDraw, y);
        }
        drawAvatar(player.getUserID(), x, y + yDelta);
        return yDelta + ROW_HEIGHT;
    }

    private boolean soloNameNeedsTwoRows(String userName, boolean lastHeaderRow) {
        String fullText = userName + " " + PlayerNames.factionDisplayName(player);
        int strokeBuffer = (int) Strokes.STROKE_2.getLineWidth();
        int maxWidth = (lastHeaderRow ? WIDTH_BESIDE_LEADERS : width()) - avatarTextOffset();
        return ctx.g2().getFontMetrics().stringWidth(fullText) + strokeBuffer > maxWidth;
    }

    private List<List<Teammate>> teammateRows(List<String> teammateIDs, boolean lastHeaderRow) {
        FontMetrics metrics = ctx.g2().getFontMetrics();
        List<Teammate> teammates = new ArrayList<>();
        for (String teammateID : teammateIDs) {
            String name = PlayerNames.discordName(ctx.game(), player, teammateID);
            teammates.add(new Teammate(teammateID, name, avatarTextOffset() + metrics.stringWidth(name)));
        }
        int separatorWidth = metrics.stringWidth(TEAMMATE_SEPARATOR);
        int lastRowWidth = lastHeaderRow ? WIDTH_BESIDE_LEADERS : width();
        return RowWrap.wrapIntoRows(teammates, Teammate::width, separatorWidth, width(), lastRowWidth);
    }

    private void drawTeammateRow(List<Teammate> row, int rowTop, int separatorWidth) {
        int cursor = x;
        for (Teammate teammate : row) {
            if (cursor > x) {
                drawText(TEAMMATE_SEPARATOR, cursor, rowTop);
                cursor += separatorWidth;
            }
            drawAvatar(teammate.userID(), cursor, rowTop);
            drawText(teammate.name(), cursor + avatarTextOffset(), rowTop);
            cursor += teammate.width();
        }
    }

    private int width() {
        return slot.rightEdge() - x - RIGHT_MARGIN;
    }

    private int avatarTextOffset() {
        return ctx.game().hideUserNames() ? 0 : RemoteImages.DISCORD_AVATAR_SIZE + 8;
    }

    private void drawText(String text, int textX, int rowTop) {
        OutlinedText.draw(
                ctx.g2(),
                text,
                textX,
                rowTop + 10,
                Color.white,
                HorizontalAlign.Left,
                VerticalAlign.Top,
                Strokes.STROKE_2,
                Color.black);
    }

    private void drawAvatar(String userID, int avatarX, int rowTop) {
        if (!ctx.game().hideUserNames()) {
            ctx.g2().drawImage(RemoteImages.discordAvatar(userID), avatarX, rowTop + 5, null);
        }
    }

    private List<List<NeighbourToken>> neighbourRows() {
        return RowWrap.wrapIntoRows(
                neighbourTokens(),
                NeighbourToken::advance,
                0,
                width() - NEIGHBOUR_INDENT,
                WIDTH_BESIDE_LEADERS - NEIGHBOUR_INDENT);
    }

    private List<NeighbourToken> neighbourTokens() {
        Set<Player> neighbours = ctx.neighbours().withEquivalents(player);
        List<NeighbourToken> tokens = new ArrayList<>();
        String label = neighbours.isEmpty() ? "No Neighbors" : "Neighbors: ";
        tokens.add(new NeighbourToken(label, Fonts.ti(20), 0, null, 0, NEIGHBOUR_LABEL_WIDTH));
        neighbours.stream()
                .filter(neighbour -> neighbour.getFaction() != null)
                .forEach(neighbour -> tokens.add(new NeighbourToken(null, null, 0, neighbour, 0, NEIGHBOUR_ICON_STEP)));

        Set<Player> guildShips = GuildShipPartners.of(ctx.game(), player, neighbours);
        if (!guildShips.isEmpty()) {
            tokens.add(new NeighbourToken("(", Fonts.ti(30), 2, null, NEIGHBOUR_ICON_STEP, 20));
            guildShips.stream()
                    .filter(partner -> partner.getFaction() != null)
                    .forEach(partner -> tokens.add(new NeighbourToken(null, null, 0, partner, 0, NEIGHBOUR_ICON_STEP)));
            tokens.add(new NeighbourToken(")", Fonts.ti(30), 2, null, 0, 12));
        }
        return tokens;
    }

    private int drawNeighbourRows(int y) {
        Graphics2D g2 = ctx.g2();
        int yDelta = 0;
        for (List<NeighbourToken> row : neighbourRows()) {
            int rowTop = y + yDelta;
            int cursor = x + NEIGHBOUR_INDENT;
            for (NeighbourToken token : row) {
                cursor += token.leadingGap();
                if (token.player() != null) {
                    FactionIcons.draw(g2, token.player(), cursor, rowTop + 6, NEIGHBOUR_ICON_SIZE, NEIGHBOUR_ICON_SIZE);
                } else {
                    g2.setFont(token.font());
                    int baseline = rowTop + 26 + token.baselineDrop();
                    OutlinedText.draw(
                            g2, token.text(), cursor, baseline, Color.red, null, null, Strokes.STROKE_2, Color.black);
                }
                cursor += token.width();
            }
            yDelta += ROW_HEIGHT;
        }
        return yDelta;
    }
}
