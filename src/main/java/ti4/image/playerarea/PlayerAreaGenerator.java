package ti4.image.playerarea;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.font.GlyphVector;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.tyris.TyrisBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.relics.theodisi.LostLegaciesRelicHandler;
import ti4.discord.interactions.buttons.handlers.unit.monuments.MonumentsBRButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.helpers.RenderRandom;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;
import ti4.image.ColorUtil;
import ti4.image.DrawingUtil;
import ti4.image.EidolonDecal;
import ti4.image.HorizontalAlign;
import ti4.image.ImageHelper;
import ti4.image.Mapper;
import ti4.image.NeighbourMap;
import ti4.image.PaImage;
import ti4.image.PositionMapper;
import ti4.image.RowWrap;
import ti4.image.VerticalAlign;
import ti4.image.WebsiteOverlays;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.AbilityModel;
import ti4.model.BreakthroughModel;
import ti4.model.ColorModel;
import ti4.model.ExploreModel;
import ti4.model.LeaderModel;
import ti4.model.ModelInterface;
import ti4.model.PlanetModel;
import ti4.model.PlanetTypeModel.PlanetType;
import ti4.model.PromissoryNoteModel;
import ti4.model.RelicModel;
import ti4.model.Source.ComponentSource;
import ti4.model.StrategyCardModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.VeiledHeartService;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.user.AFKService;

public class PlayerAreaGenerator {

    private static final Stroke stroke1 = new BasicStroke(1.0f);
    private static final Stroke stroke2 = new BasicStroke(2.0f);
    private static final Stroke stroke3 = new BasicStroke(3.0f);
    private static final Stroke stroke4 = new BasicStroke(4.0f);
    private static final Stroke stroke5 = new BasicStroke(5.0f);
    private static final Stroke stroke8 = new BasicStroke(8.0f);

    private final Graphics graphics;
    private final Game game;
    private final boolean isFoWPrivate;
    private final Player frogPlayer;
    private final WebsiteOverlays websiteOverlays;
    private final int totalWidth;
    private int rightEdge;
    private int columnOffset;
    private final int scoreTokenSpacing;
    private final NeighbourMap neighbours;
    private final ResourceHelper resourceHelper = ResourceHelper.getInstance();
    private final PlayerAreaContext ctx;

    public static final int MEGA_GAME_MIN_PLAYERS = 9;
    public static final int COLUMN_GAP = 10;
    private static final int PLAYER_AREA_GAP = 15;
    private static final int HEADER_ROW_HEIGHT = 34;
    private static final int HEADER_WIDTH_BESIDE_LEADERS = 715;
    private static final int HEADER_RIGHT_MARGIN = 20;
    private static final String TEAMMATE_SEPARATOR = " — ";
    private static final int NEIGHBOR_ICON_SIZE = 26;
    private static final int NEIGHBOR_ICON_STEP = 27;
    private static final int NEIGHBOR_LABEL_WIDTH = 115;

    public PlayerAreaGenerator(
            Graphics graphics,
            Game game,
            boolean isFoWPrivate,
            Player frogPlayer,
            WebsiteOverlays websiteOverlays,
            int totalWidth,
            int scoreTokenSpacing) {
        this(
                graphics,
                game,
                isFoWPrivate,
                frogPlayer,
                websiteOverlays,
                totalWidth,
                scoreTokenSpacing,
                new NeighbourMap(game));
    }

    public PlayerAreaGenerator(
            Graphics graphics,
            Game game,
            boolean isFoWPrivate,
            Player frogPlayer,
            WebsiteOverlays websiteOverlays,
            int totalWidth,
            int scoreTokenSpacing,
            NeighbourMap neighbours) {
        this.graphics = graphics;
        this.game = game;
        this.isFoWPrivate = isFoWPrivate;
        this.frogPlayer = frogPlayer;
        this.websiteOverlays = websiteOverlays;
        this.totalWidth = totalWidth;
        this.rightEdge = totalWidth;
        this.scoreTokenSpacing = scoreTokenSpacing;
        this.neighbours = neighbours;
        this.ctx = PlayerAreaContext.of(
                (Graphics2D) graphics, game, isFoWPrivate, frogPlayer, websiteOverlays, neighbours, scoreTokenSpacing);
    }

    private AreaSlot slot() {
        return new AreaSlot(0, 0, rightEdge, columnOffset);
    }

    public static boolean isMegaGame(Game game) {
        return game.getRealPlayers().size() >= MEGA_GAME_MIN_PLAYERS;
    }

    public static <T> List<List<T>> splitIntoColumns(Game game, List<T> playerAreas) {
        if (!isMegaGame(game)) {
            return List.of(playerAreas);
        }
        int leftColumnSize = (playerAreas.size() + 1) / 2;
        return List.of(playerAreas.subList(0, leftColumnSize), playerAreas.subList(leftColumnSize, playerAreas.size()));
    }

    public static int columnWidth(int totalWidth, int left, int columnCount) {
        return (totalWidth - left - COLUMN_GAP * (columnCount - 1)) / columnCount;
    }

    public int drawAllPlayerAreas(Point topLeftOfAllPAs) {
        graphics.setFont(Storage.getFont32());
        int x = topLeftOfAllPAs.x;
        List<Player> playersWithAreas = game.getRealAndEliminatedPlayers().stream()
                .filter(player -> !skipsPlayerArea(player))
                .toList();
        List<List<Player>> columns = splitIntoColumns(game, playersWithAreas);
        int columnWidth = columnWidth(totalWidth, x, columns.size());

        int y = topLeftOfAllPAs.y;
        int rowCount = columns.getFirst().size();
        for (int row = 0; row < rowCount; row++) {
            List<PlayerAreaFrame> frames = new ArrayList<>();
            for (int column = 0;
                    column < columns.size() && row < columns.get(column).size();
                    column++) {
                int columnLeft = x + column * (columnWidth + COLUMN_GAP);
                rightEdge = column == columns.size() - 1 ? totalWidth : columnLeft + columnWidth;
                columnOffset = columnLeft - x;
                frames.add(drawPlayerAreaContent(columns.get(column).get(row), new Point(columnLeft, y)));
            }
            int rowHeight = frames.stream()
                    .mapToInt(frame -> frame.bounds().height)
                    .max()
                    .orElse(0);
            frames.forEach(frame -> drawFrame(frame, rowHeight));
            if (rowHeight > 0) y += rowHeight + PLAYER_AREA_GAP;
        }
        rightEdge = totalWidth;
        columnOffset = 0;
        int bottom = y;

        String spectatorNames = game.getPlayers().values().stream()
                .filter(Player::isSpectator)
                .map(Player::getUserName)
                .collect(Collectors.joining(", "));

        if (!spectatorNames.isEmpty()) {
            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            bottom = drawString((Graphics2D) graphics, "Spectators: " + spectatorNames, x, bottom + 15, totalWidth - x);
        }
        return bottom;
    }

    private boolean skipsPlayerArea(Player player) {
        boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, frogPlayer);
        return (player.isDummy() && player.isNpc())
                || convertToGeneric
                || ("neutral".equals(player.getFaction()) && player.isDummy());
    }

    private static int drawString(Graphics2D g2, String text, int x, int y, float maxWidth) {
        var attributedString = new AttributedString(text);
        attributedString.addAttribute(TextAttribute.FONT, g2.getFont());
        AttributedCharacterIterator characterIterator = attributedString.getIterator();

        var lineBreakMeasurer = new LineBreakMeasurer(characterIterator, g2.getFontRenderContext());

        float drawPosY = y;

        while (lineBreakMeasurer.getPosition() < characterIterator.getEndIndex()) {
            TextLayout layout = lineBreakMeasurer.nextLayout(maxWidth);
            drawPosY += layout.getAscent();
            layout.draw(g2, x, drawPosY);
            drawPosY += layout.getDescent() + layout.getLeading();
        }
        return (int) Math.ceil(drawPosY);
    }

    public Rectangle drawPlayerAreaOLD(Player player, Point topLeft) {
        PlayerAreaFrame frame = drawPlayerAreaContent(player, topLeft);
        drawFrame(frame, frame.bounds().height);
        return frame.bounds();
    }

    private record PlayerAreaFrame(Rectangle bounds, Color mainColor, Color accentColor, Stroke stroke) {
        static PlayerAreaFrame unframed(Rectangle bounds) {
            return new PlayerAreaFrame(bounds, null, null, null);
        }
    }

    private void drawFrame(PlayerAreaFrame frame, int height) {
        if (frame.stroke() == null) {
            return;
        }
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(frame.stroke());
        Rectangle bounds = frame.bounds();
        DrawingUtil.drawRectWithTwoColorGradient(
                g2, frame.mainColor(), frame.accentColor(), bounds.x, bounds.y, bounds.width, height);
    }

    private PlayerAreaFrame drawPlayerAreaContent(Player player, Point topLeft) {
        int x = topLeft.x;
        int y = topLeft.y;
        Graphics2D g2 = (Graphics2D) graphics;

        if (skipsPlayerArea(player)) {
            return PlayerAreaFrame.unframed(new Rectangle(topLeft));
        }

        Color mainColor = ColorUtil.getPlayerMainColor(player);
        Color accentColor = ColorUtil.getPlayerAccentColor(player);

        boolean neighborsInHeader = showsNeighborsInHeader(player);
        y += drawTeammateNames(player, x, y, !neighborsInHeader);
        if (neighborsInHeader) {
            y += drawNeighborRows(player, x, y);
        }

        if (player.getFaction() == null || "null".equals(player.getColor()) || player.getColor() == null) {
            y += 2;
            return PlayerAreaFrame.unframed(new Rectangle(topLeft.x, topLeft.y, x - topLeft.x, y - topLeft.y));
        }

        // PAINT FACTION ICON
        y += 2;
        DrawingUtil.drawPlayerFactionIconImage(graphics, player, x, y, 95, 95);
        if (!player.hasCustomFactionEmoji()) {
            addWebsiteOverlay(player.getFactionModel(), x + 10, y + 10, 75, 75);
        }
        y += 4;

        // PAINT STRATEGY CARDS (SCs)
        drawStrategyCards(player, new Point(x + 90, y));

        // Status
        drawPlayerStatus(player, x, y);

        // Eliminated Rectangle
        g2.setStroke(stroke5);
        if (player.isEliminated()) {
            y += 95;
            Point tl = new Point(topLeft.x - 5, topLeft.y);
            int width = rightEdge - topLeft.x;
            int height = y - topLeft.y;
            return new PlayerAreaFrame(
                    new Rectangle(tl.x, tl.y, width, height), mainColor, accentColor, g2.getStroke());
        }

        // Unfollowed SCs
        int xSpacer = 0;
        if (!player.getUnfollowedSCs().isEmpty()) {
            graphics.setFont(Storage.getFont20());
            int drawX = x + 9;
            int drawY = y + 125;
            xSpacer = 165;
            DrawingUtil.superDrawString(
                    g2, "Needs to Follow: ", drawX, drawY, Color.red, null, null, stroke2, Color.black);
            for (int sc : player.getUnfollowedSCs()) {
                Color textColor = ColorUtil.getSCColor(sc, game, true);
                String drawText = String.valueOf(sc);
                int len = graphics.getFontMetrics().stringWidth(drawText);
                DrawingUtil.superDrawString(
                        g2, drawText, drawX + xSpacer, drawY, textColor, null, null, stroke2, Color.black);
                xSpacer += len + 8;
            }
        }

        // Neighborts
        if (!game.isFowMode() && !neighborsInHeader) {
            graphics.setFont(Storage.getFont20());
            int drawX = x + 9;
            int drawY = y + 125;
            Set<Player> neighbors = neighbours.withEquivalents(player);
            Set<Player> guildShips = guildShipPartners(player, neighbors);
            if (neighbors.isEmpty()) {
                DrawingUtil.superDrawString(
                        g2, "No Neighbors", drawX + xSpacer, drawY, Color.red, null, null, stroke2, Color.black);
                xSpacer += 115;
            } else {
                DrawingUtil.superDrawString(
                        g2, "Neighbors: ", drawX + xSpacer, drawY, Color.red, null, null, stroke2, Color.black);
                xSpacer += 115;
                for (Player p2 : neighbors) {
                    String faction2 = p2.getFaction();
                    if (faction2 != null) {
                        DrawingUtil.drawPlayerFactionIconImage(graphics, p2, x + xSpacer, y + 125 - 20, 26, 26);
                        xSpacer += 27;
                    }
                }
            }
            if (!guildShips.isEmpty()) {
                graphics.setFont(Storage.getFont30());
                xSpacer += 27;
                DrawingUtil.superDrawString(
                        g2, "(", drawX + xSpacer, drawY + 2, Color.red, null, null, stroke2, Color.black);
                xSpacer += 20;
                for (Player p2 : guildShips) {
                    String faction2 = p2.getFaction();
                    if (faction2 != null) {
                        DrawingUtil.drawPlayerFactionIconImage(graphics, p2, x + xSpacer, y + 125 - 20, 26, 26);
                        xSpacer += 27;
                    }
                }
                DrawingUtil.superDrawString(
                        g2, ")", drawX + xSpacer - 8, drawY + 2, Color.red, null, null, stroke2, Color.black);
                xSpacer += 4;
            }
        }

        // CCs
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);
        String ccCount = player.getCCRepresentation();
        x += 120;
        graphics.drawString(ccCount, x + 40, y + 75);
        graphics.drawString("T/F/S", x + 40, y + 35);

        // Additional Fleet Supply
        int additionalFleetSupply = 0;
        if (player.hasAbility("edict") || player.hasAbility("edict_y")) {
            additionalFleetSupply += player.getMahactCC().size();
        }
        if (player.hasAbility("armada")) {
            additionalFleetSupply += 2;
        }
        if (additionalFleetSupply > 0) {
            graphics.drawString("+" + additionalFleetSupply + " FS", x + 40, y + 105);
        }

        // Cards
        String acImage = game.isTwilightsFallMode() ? "pa_cardbacks_ac_tf.png" : "pa_cardbacks_ac.png";
        String soImage = "pa_cardbacks_so.png";
        String pnImage = "pa_cardbacks_pn.png";
        String tradeGoodImage = game.isNomadCoin() ? "pa_cardbacks_nomadcoin.png" : "pa_cardbacks_tradegoods.png";
        String commoditiesImage = "pa_cardbacks_commodities.png";
        g2.setStroke(stroke1);
        Rectangle card = drawPAImage(x + 150, y, soImage);
        DrawingUtil.superDrawStringCenteredDefault(
                g2, Integer.toString(player.getSo()), (int) card.getCenterX(), y + 75);
        DrawingUtil.drawRectWithTwoColorGradient(g2, Color.black, null, card);

        card = drawPAImage(x + 215, y, acImage);
        DrawingUtil.superDrawStringCenteredDefault(
                g2, Integer.toString(player.getAcCount()), (int) card.getCenterX(), y + 75);
        DrawingUtil.drawRectWithTwoColorGradient(g2, Color.black, null, card);

        int xOffset = 0;
        if (game.isTwilightsFallMode()) {
            xOffset = 65;
        } else {
            card = drawPAImage(x + 280, y, pnImage);
            DrawingUtil.superDrawStringCenteredDefault(
                    g2, Integer.toString(player.getPnCount()), (int) card.getCenterX(), y + 75);
            DrawingUtil.drawRectWithTwoColorGradient(g2, Color.black, null, card);
        }

        // Trade Goods
        card = drawPAImage(x + 345 - xOffset, y, tradeGoodImage);
        DrawingUtil.superDrawStringCenteredDefault(
                g2, Integer.toString(player.getTg()), (int) card.getCenterX(), y + 75);

        // Comms
        card = drawPAImage(x + 410 - xOffset, y, commoditiesImage);
        String comms = player.getCommodities() + "/" + player.getCommoditiesTotal();
        DrawingUtil.superDrawStringCenteredDefault(g2, comms, (int) card.getCenterX(), y + 75);

        // Fragments
        String urfImage = "pa_fragment_urf.png";
        String irfImage = "pa_fragment_irf.png";
        int xDelta = 0;
        xDelta = drawFrags(y, x - xOffset, 0, player, Constants.FRONTIER, urfImage, xDelta);
        xDelta += 25;
        xDelta = drawFrags(y, x - xOffset, 0, player, Constants.INDUSTRIAL, irfImage, xDelta);

        int xDelta2 = 0;
        String hrfImage = "pa_fragment_hrf.png";
        String crfImage = "pa_fragment_crf.png";
        xDelta2 = drawFrags(y + 73, x - xOffset, 0, player, Constants.HAZARDOUS, hrfImage, xDelta2);
        xDelta2 += 25;
        xDelta2 = drawFrags(y + 73, x - xOffset, 0, player, Constants.CULTURAL, crfImage, xDelta2);

        xDelta = x + 600;
        // xDelta = x + 550 + Math.max(xDelta, xDelta2); DISABLE AUTO-SCALE BASED ON
        // AMOUNT OF FRAGS - ALIGNS PLAYERS' LEADERS/PLANETS
        int yPlayArea = y - 30;
        y += 85;
        y += 200;

        // Secret Objectives
        int soCount = objectivesSO(yPlayArea + 170, player);

        int xDeltaSecondRow = xDelta;
        int yPlayAreaSecondRow = yPlayArea + 160;
        if (!player.getPlanets().isEmpty()) {
            xDeltaSecondRow = planetInfo(player, xDeltaSecondRow, yPlayAreaSecondRow);
        }

        RightBlocks.draw(ctx, slot(), player, yPlayArea, yPlayAreaSecondRow);

        if (player.hasAbility("ancient_blueprints")) {
            xDelta = bentorBluePrintInfo(player, xDelta, yPlayArea);
        }

        if (!player.getLeaders().isEmpty() || game.isVeiledHeartMode()) {
            xDelta = leaderInfo(player, xDelta, yPlayArea, game);
        }

        // if (player.getAllDebtTokens().values().values().stream().anyMatch(i -> i > 0))
        xDelta = debtInfo(player, xDelta, yPlayArea, game);

        if (!player.getRelics().isEmpty()) {
            xDelta = relicInfo(player, xDelta, yPlayArea);
        }
        xDelta = omenDice(player, xDelta, yPlayArea);

        if (!player.getPromissoryNotesInPlayArea().isEmpty()) {
            xDelta = pnInfo(player, xDelta, yPlayArea, game);
        }
        xDelta = allBreakthroughInfo(player, xDelta, yPlayArea, game);
        xDelta = techInfo(player, xDelta, yPlayArea, game);

        if (!player.getNotResearchedFactionTechs().isEmpty()) {
            xDelta = factionTechInfo(player, xDelta, yPlayArea);
        }
        xDelta += 20;

        if (!player.getAbilities().isEmpty()) {
            xDelta = abilityInfo(player, xDelta, yPlayArea);
        }

        if (!player.getPromissoryNotesOwned().isEmpty()) {
            xDelta = drawOwnedPromissoryNotes(player, xDelta, yPlayArea);
        }

        if (soCount >= 4) {
            y += 23 + (soCount - 4) * 43;
        }

        // Draw Full Rect
        Rectangle r = new Rectangle(topLeft.x, topLeft.y, rightEdge - topLeft.x - 5, y - topLeft.y);
        return new PlayerAreaFrame(r, mainColor, accentColor, g2.getStroke());
    }

    private static String factionDisplayName(Player player) {
        StringBuilder text = new StringBuilder();
        if (player.getDisplayName() != null && !"null".equalsIgnoreCase(player.getDisplayName())) {
            text.append("[").append(player.getDisplayName()).append("]");
        } else if (player.getFactionModel() == null) {
            text.append("[").append(StringUtils.capitalize(player.getFaction())).append("]");
        } else {
            text.append("[").append(player.getFactionModel().getShortName()).append("]");
        }

        ColorModel colorModel = Mapper.getColor(player.getColor());
        if (colorModel != null && !text.isEmpty())
            text.append(" (").append(colorModel.getDisplayName()).append(")");
        return text.toString();
    }

    private String getPlayerNameFromID(Player player, String id) {
        String afk = AFKService.userIsAFK(id) ? " (AFK)" : "";
        try {
            User user = JdaService.jda.getUserById(id);
            Member member = game.getGuild().getMember(user);
            if (member == null) {
                member = JdaService.guildPrimary.getMember(user);
            }

            if (member != null) return member.getEffectiveName() + afk;
            return user.getEffectiveName() + afk;
        } catch (Exception _) {
        }
        return player.getUserName() + afk;
    }

    public static int estimateExtraHeaderHeight(
            Game game, NeighbourMap neighbours, Player player, int areaLeft, int areaRight) {
        Graphics probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).getGraphics();
        try {
            return new PlayerAreaGenerator(probe, game, false, null, new WebsiteOverlays(), areaRight, 0, neighbours)
                            .headerHeight(player, areaLeft)
                    - HEADER_ROW_HEIGHT;
        } finally {
            probe.dispose();
        }
    }

    private int headerHeight(Player player, int x) {
        boolean neighborsInHeader = showsNeighborsInHeader(player);
        int height = teammateNamesHeight(player, x, !neighborsInHeader);
        if (neighborsInHeader) {
            height += neighborRows(player, x).size() * HEADER_ROW_HEIGHT;
        }
        return height;
    }

    private record Teammate(String userID, String name, int width) {}

    private record NeighborToken(String text, Font font, int baselineDrop, Player player, int leadingGap, int width) {
        int advance() {
            return leadingGap + width;
        }
    }

    private int teammateNamesHeight(Player player, int x, boolean lastHeaderRow) {
        List<String> teammateIDs = orderedTeammateIDs(player);
        graphics.setFont(Storage.getFont32());
        if (teammateIDs.size() == 1) {
            String userName = getPlayerNameFromID(player, player.getUserID());
            return soloNameNeedsTwoRows(player, userName, x, lastHeaderRow) ? 2 * HEADER_ROW_HEIGHT : HEADER_ROW_HEIGHT;
        }
        return (1 + teammateRows(player, teammateIDs, x, lastHeaderRow).size()) * HEADER_ROW_HEIGHT;
    }

    private int drawTeammateNames(Player player, int x, int y, boolean lastHeaderRow) {
        List<String> teammateIDs = orderedTeammateIDs(player);
        graphics.setFont(Storage.getFont32());
        if (teammateIDs.size() == 1) {
            return drawSoloPlayerName(player, x, y, lastHeaderRow);
        }

        drawHeaderText(factionDisplayName(player), x, y);
        int yDelta = HEADER_ROW_HEIGHT;
        int separatorWidth = graphics.getFontMetrics().stringWidth(TEAMMATE_SEPARATOR);
        for (List<Teammate> row : teammateRows(player, teammateIDs, x, lastHeaderRow)) {
            drawTeammateRow(row, x, y + yDelta, separatorWidth);
            yDelta += HEADER_ROW_HEIGHT;
        }
        return yDelta;
    }

    private static List<String> orderedTeammateIDs(Player player) {
        List<String> teammateIDs = new ArrayList<>(player.getTeamMateIDs());
        teammateIDs.remove(player.getUserID());
        teammateIDs.addFirst(player.getUserID());
        return teammateIDs;
    }

    private int drawSoloPlayerName(Player player, int x, int y, boolean lastHeaderRow) {
        String userName = getPlayerNameFromID(player, player.getUserID());
        int xDraw = x + avatarTextOffset();
        int yDelta = 0;
        if (soloNameNeedsTwoRows(player, userName, x, lastHeaderRow)) {
            drawHeaderText(factionDisplayName(player), xDraw, y);
            yDelta += HEADER_ROW_HEIGHT;
            drawHeaderText(userName, xDraw, y + yDelta);
        } else {
            drawHeaderText(userName + " " + factionDisplayName(player), xDraw, y);
        }
        drawAvatar(player.getUserID(), x, y + yDelta);
        return yDelta + HEADER_ROW_HEIGHT;
    }

    private boolean soloNameNeedsTwoRows(Player player, String userName, int x, boolean lastHeaderRow) {
        String fullText = userName + " " + factionDisplayName(player);
        int strokeBuffer = (int) ((BasicStroke) stroke2).getLineWidth();
        int maxWidth = (lastHeaderRow ? HEADER_WIDTH_BESIDE_LEADERS : headerWidth(x)) - avatarTextOffset();
        return graphics.getFontMetrics().stringWidth(fullText) + strokeBuffer > maxWidth;
    }

    private List<List<Teammate>> teammateRows(Player player, List<String> teammateIDs, int x, boolean lastHeaderRow) {
        FontMetrics metrics = graphics.getFontMetrics();
        List<Teammate> teammates = new ArrayList<>();
        for (String teammateID : teammateIDs) {
            String name = getPlayerNameFromID(player, teammateID);
            teammates.add(new Teammate(teammateID, name, avatarTextOffset() + metrics.stringWidth(name)));
        }
        int separatorWidth = metrics.stringWidth(TEAMMATE_SEPARATOR);
        int lastRowWidth = lastHeaderRow ? HEADER_WIDTH_BESIDE_LEADERS : headerWidth(x);
        return RowWrap.wrapIntoRows(teammates, Teammate::width, separatorWidth, headerWidth(x), lastRowWidth);
    }

    private void drawTeammateRow(List<Teammate> row, int x, int rowTop, int separatorWidth) {
        int cursor = x;
        for (Teammate teammate : row) {
            if (cursor > x) {
                drawHeaderText(TEAMMATE_SEPARATOR, cursor, rowTop);
                cursor += separatorWidth;
            }
            drawAvatar(teammate.userID(), cursor, rowTop);
            drawHeaderText(teammate.name(), cursor + avatarTextOffset(), rowTop);
            cursor += teammate.width();
        }
    }

    private int headerWidth(int x) {
        return rightEdge - x - HEADER_RIGHT_MARGIN;
    }

    private int avatarTextOffset() {
        return game.hideUserNames() ? 0 : DrawingUtil.DISCORD_AVATAR_SIZE + 8;
    }

    private void drawHeaderText(String text, int x, int rowTop) {
        DrawingUtil.superDrawString(
                graphics,
                text,
                x,
                rowTop + 10,
                Color.white,
                HorizontalAlign.Left,
                VerticalAlign.Top,
                stroke2,
                Color.black);
    }

    private void drawAvatar(String userID, int x, int rowTop) {
        if (!game.hideUserNames()) {
            graphics.drawImage(DrawingUtil.getUserDiscordAvatar(userID), x, rowTop + 5, null);
        }
    }

    private boolean showsNeighborsInHeader(Player player) {
        return isMegaGame(game) && !game.isFowMode() && player.getFaction() != null && !player.isEliminated();
    }

    private Set<Player> guildShipPartners(Player player, Set<Player> neighbors) {
        Set<Player> guildShips = new LinkedHashSet<>();
        for (Player p2 : game.getRealPlayers()) {
            if (player == p2 || neighbors.contains(p2)) {
                continue;
            }
            if (game.isAgeOfCommerceMode()
                    || player.hasAbility("guild_ships")
                    || player.getPromissoryNotesInPlayArea().contains("convoys")
                    || p2.getPromissoryNotesInPlayArea().contains("convoys")
                    || p2.hasAbility("guild_ships")
                    || player.getPromissoryNotesInPlayArea().contains("sigma_trade_convoys")
                    || p2.getPromissoryNotesInPlayArea().contains("sigma_trade_convoys")
                    || player.getPromissoryNotesInPlayArea().contains("viability_trade_convoys")
                    || p2.getPromissoryNotesInPlayArea().contains("viability_trade_convoys")) {
                guildShips.add(p2);
            }
        }
        return guildShips;
    }

    private List<List<NeighborToken>> neighborRows(Player player, int x) {
        int indent = 9;
        return RowWrap.wrapIntoRows(
                neighborTokens(player),
                NeighborToken::advance,
                0,
                headerWidth(x) - indent,
                HEADER_WIDTH_BESIDE_LEADERS - indent);
    }

    private List<NeighborToken> neighborTokens(Player player) {
        Set<Player> neighbors = neighbours.withEquivalents(player);
        List<NeighborToken> tokens = new ArrayList<>();
        String label = neighbors.isEmpty() ? "No Neighbors" : "Neighbors: ";
        tokens.add(new NeighborToken(label, Storage.getFont20(), 0, null, 0, NEIGHBOR_LABEL_WIDTH));
        neighbors.stream()
                .filter(neighbor -> neighbor.getFaction() != null)
                .forEach(neighbor -> tokens.add(new NeighborToken(null, null, 0, neighbor, 0, NEIGHBOR_ICON_STEP)));

        Set<Player> guildShips = guildShipPartners(player, neighbors);
        if (!guildShips.isEmpty()) {
            tokens.add(new NeighborToken("(", Storage.getFont30(), 2, null, NEIGHBOR_ICON_STEP, 20));
            guildShips.stream()
                    .filter(partner -> partner.getFaction() != null)
                    .forEach(partner -> tokens.add(new NeighborToken(null, null, 0, partner, 0, NEIGHBOR_ICON_STEP)));
            tokens.add(new NeighborToken(")", Storage.getFont30(), 2, null, 0, 12));
        }
        return tokens;
    }

    private int drawNeighborRows(Player player, int x, int y) {
        Graphics2D g2 = (Graphics2D) graphics;
        int yDelta = 0;
        for (List<NeighborToken> row : neighborRows(player, x)) {
            int rowTop = y + yDelta;
            int cursor = x + 9;
            for (NeighborToken token : row) {
                cursor += token.leadingGap();
                if (token.player() != null) {
                    DrawingUtil.drawPlayerFactionIconImage(
                            graphics, token.player(), cursor, rowTop + 6, NEIGHBOR_ICON_SIZE, NEIGHBOR_ICON_SIZE);
                } else {
                    graphics.setFont(token.font());
                    int baseline = rowTop + 26 + token.baselineDrop();
                    DrawingUtil.superDrawString(
                            g2, token.text(), cursor, baseline, Color.red, null, null, stroke2, Color.black);
                }
                cursor += token.width();
            }
            yDelta += HEADER_ROW_HEIGHT;
        }
        return yDelta;
    }

    private void drawStrategyCards(Player player, Point tl) {
        List<Integer> playerSCs = new ArrayList<>(player.getSCs());
        Collections.sort(playerSCs);

        Point center = new Point(tl.x + 30, tl.y + 42);
        if (player.hasTheZeroToken()) {
            drawSC(0, center, true);
        }
        if (player.hasAbility("patience")) {
            drawSC(9, center, true);
        }

        boolean big = playerSCs.size() == 1 && !player.hasTheZeroToken() && !player.hasAbility("patience");
        List<Point> smallPoints = List.of(
                new Point(center.x - 16, center.y - 16),
                new Point(center.x + 16, center.y - 16),
                new Point(center.x - 16, center.y + 16),
                new Point(center.x + 16, center.y + 16));
        int count = 0;
        for (int sc : playerSCs) {
            Point pt = big ? center : smallPoints.get(count);
            drawSC(sc, pt, big);
            ++count;
            if ((count) > 3) break;
        }
    }

    private void drawSC(int sc, Point pt, boolean big) {
        Color scColor = ColorUtil.getSCColor(sc, game);
        StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
        graphics.setFont(big ? Storage.getFont64() : Storage.getFont32());

        // Draw the SC number
        if (sc == ButtonHelper.getKyroHeroSC(game)) sc = 9;
        DrawingUtil.superDrawStringCentered(graphics, Integer.toString(sc), pt.x, pt.y, scColor, stroke4, Color.black);
        if (Color.GRAY.equals(scColor)) {
            int off = big ? 15 : 8;
            DrawingUtil.drawRedX((Graphics2D) graphics, pt.x + off, pt.y + off, 2 * off, big);
        }

        // Add the overlay
        if (scModel != null) {
            int overlaySize = big ? 50 : 30;
            addWebsiteOverlay(scModel, pt.x, pt.y, overlaySize / 2, overlaySize);
        }
    }

    private void drawPlayerStatus(Player player, int x, int y) {
        String activePlayerID = game.getActivePlayerID();
        String phase = game.getPhaseOfGame();

        Graphics2D g2 = (Graphics2D) graphics;
        AffineTransform transform = g2.getTransform();
        g2.translate(x + 47 - 3, y + 47 - 6);
        g2.rotate(-Math.PI / 4);
        g2.setFont(Storage.getFont20());
        if (player.isEliminated()) {
            DrawingUtil.superDrawStringCentered(
                    g2, "ELIMINATED", 0, 0, ColorUtil.EliminatedColor, stroke4, Color.BLACK);
        } else if (player.isDummy()) {
            DrawingUtil.superDrawStringCentered(g2, "DUMMY", 0, 0, ColorUtil.EliminatedColor, stroke4, Color.BLACK);
        } else if (player.isNpc()) {
            DrawingUtil.superDrawStringCentered(g2, "NPC", 0, 0, ColorUtil.EliminatedColor, stroke4, Color.BLACK);
        } else if (player.isPassed()) {
            DrawingUtil.superDrawStringCentered(g2, "PASSED", 0, 0, ColorUtil.PassedColor, stroke4, Color.BLACK);
        } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
            DrawingUtil.superDrawStringCentered(g2, "ACTIVE", 0, 0, ColorUtil.ActiveColor, stroke4, Color.BLACK);
        }
        g2.setTransform(transform);
    }

    private int omenDice(Player player, int x, int y) {
        int deltaX = 0;
        if (player.hasAbility("divination")
                && !ButtonHelperAbilities.getAllOmenDie(game).isEmpty()) {

            Graphics2D g2 = (Graphics2D) graphics;
            g2.setStroke(stroke2);

            for (int i = 0; i < ButtonHelperAbilities.getAllOmenDie(game).size(); i++) {
                String omen = "pa_ds_myko_omen_"
                        + ButtonHelperAbilities.getAllOmenDie(game).get(i) + ".png";
                omen = omen.replace("10", "0");
                graphics.drawRect(x + deltaX - 2, y - 2, 52, 152);

                drawPAImage(x + deltaX, y, omen);
                deltaX += 56;
            }
            return x + deltaX + 20;
        }
        return x + deltaX;
    }

    private int bentorBluePrintInfo(Player player, int x, int y) {
        int deltaX = 0;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);
        graphics.setColor(Color.WHITE);
        String bluePrintFileNamePrefix = "pa_ds_bent_blueprint_";
        boolean hasFoundAny = false;
        if (player.isHasFoundCulFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "crf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.isHasFoundHazFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "hrf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.isHasFoundIndFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "irf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        if (player.isHasFoundUnkFrag()) {
            graphics.drawRect(x + deltaX - 2, y - 2, 44, 152);
            drawPAImage(x + deltaX, y, bluePrintFileNamePrefix + "urf.png");
            hasFoundAny = true;
            deltaX += 48;
        }
        return x + deltaX + (hasFoundAny ? 20 : 0);
    }

    private int pnInfo(Player player, int x, int y, Game game) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        for (String pn : player.getPromissoryNotesInPlayArea()) {
            graphics.setColor(Color.WHITE);

            boolean commanderUnlocked = false;
            Player promissoryNoteOwner = game.getPNOwner(pn);
            if (promissoryNoteOwner == null) { // nobody owns this note - possibly eliminated player
                String error = game.getName() + " " + player.getUserName()
                        + "  `GenerateMap.pnInfo` is trying to display a Promissory Note without an owner - possibly an eliminated player: "
                        + pn;
                BotLogger.warning(new LogOrigin(player), error);
                continue;
            }
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pn);
            if (promissoryNote == null) continue;

            if (pn.endsWith("_an")) { // Overlay for alliance commander
                if (promissoryNoteOwner.getLeader(Constants.COMMANDER).isPresent()
                        && promissoryNoteOwner
                                .getLeader(Constants.COMMANDER)
                                .get()
                                .getLeaderModel()
                                .isPresent()) {
                    LeaderModel leaderModel = promissoryNoteOwner
                            .getLeader(Constants.COMMANDER)
                            .get()
                            .getLeaderModel()
                            .get();
                    drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, leaderModel);
                }
            } else {
                drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, promissoryNote);
            }

            for (Player player_ : player.getOtherRealPlayers()) {
                String playerColor = player_.getColor();
                String playerFaction = player_.getFaction();
                if (playerColor != null && playerColor.equals(promissoryNoteOwner.getColor())
                        || playerFaction != null && playerFaction.equals(promissoryNoteOwner.getFaction())) {
                    String pnColorFile = "pa_pn_color_" + Mapper.getColorID(playerColor) + ".png";
                    drawPAImage(x + deltaX, y, pnColorFile);
                    if (game.isFrankenGame()) {
                        drawFactionIconImage(
                                graphics, promissoryNote.getFaction().orElse(""), x + deltaX - 1, y + 86, 42, 42);
                    }
                    DrawingUtil.drawPlayerFactionIconImage(
                            graphics, promissoryNoteOwner, x + deltaX - 1, y + 108, 42, 42);
                    Leader leader = player_.unsafeGetLeader(Constants.COMMANDER);
                    if (leader != null) {
                        commanderUnlocked = !leader.isLocked();
                    }
                    break;
                }
            }

            graphics.setColor(Color.WHITE);
            if (pn.endsWith("_sftt")) {
                pn = "sftt";
            } else if (pn.endsWith("_an")) {
                pn = "alliance";
                if (!commanderUnlocked) {
                    pn += "_exh";
                    graphics.setColor(Color.GRAY);
                }
            }

            PlanetModel attachedPlanet = findAttachedPlanet(promissoryNote);
            writePromissoryName(promissoryNote, x + deltaX + 9, y + 4, attachedPlanet);
            deltaX += 48;
        }
        return x + deltaX + 20;
    }

    private void writePromissoryName(PromissoryNoteModel model, int x, int y, PlanetModel attached) {
        String text = model.getShortName();
        if (attached != null) text += "\n@" + attached.getShortNamePNAttach();

        if (model.getShrinkName()) {
            graphics.setFont(Storage.getFont16());
            DrawingUtil.drawOneOrTwoLinesOfTextVertically(graphics, text, x, y, 120, true);
        } else {
            graphics.setFont(Storage.getFont18());
            DrawingUtil.drawOneOrTwoLinesOfTextVertically(graphics, text, x - 2, y, 120, true);
        }
    }

    private PlanetModel findAttachedPlanet(PromissoryNoteModel model) {
        if (model.getAttachment().isEmpty() || model.getAttachment().get().isBlank()) return null;

        String tokenID = model.getAttachment().get();
        for (Tile tile : game.getTileMap().values()) {
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                if (unitHolder.getTokenList().stream().anyMatch(token -> token.contains(tokenID))) {
                    return Mapper.getPlanet(unitHolder.getName());
                }
            }
        }
        return null;
    }

    private int drawFrags(int y, int x, int yDelta, Player player, String trait, String fragmentImage, int xDelta) {
        List<String> fragments = player.getFragments().stream()
                .filter(fragmentId -> {
                    ExploreModel fragment = Mapper.getExplore(fragmentId);
                    return fragment != null && trait.equalsIgnoreCase(fragment.getType());
                })
                .toList();
        for (int i = 0; i < fragments.size(); i++) {
            if (fragments.get(i).startsWith("supermassive")) {
                drawPAImageScaled(x + 457 + xDelta + i * 15, y + yDelta - 43, fragmentImage, 108, 110);
            }
        }
        for (int i = 0; i < fragments.size(); i++) {
            if (!fragments.get(i).startsWith("supermassive")) {
                drawPAImage(x + 475 + xDelta + i * 15, y + yDelta - 25, fragmentImage);
            }
        }
        return xDelta + fragments.size() * 15;
    }

    private int relicInfo(Player player, int x, int y) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        List<String> relics = new ArrayList<>(player.getRelics());
        List<String> fakeRelics = relics.stream()
                .filter(relic -> Mapper.getRelic(relic).isFakeRelic())
                .filter(relic -> !relic.contains("axisorder"))
                .toList();
        List<String> axisOrderRelics = player.getRelics().stream()
                .filter(relic -> relic.contains("axisorder"))
                .toList();

        relics.removeAll(fakeRelics);
        relics.removeAll(axisOrderRelics);

        List<String> exhaustedRelics = player.getExhaustedRelics();

        int rectW = 44;
        int rectH = 152;
        int rectY = y - 2;
        for (String relicID : relics) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            int rectX = x + deltaX - 2;
            drawRectWithOverlay(g2, rectX, rectY, rectW, rectH, relicModel);
            if (relicModel.getSource() == ComponentSource.absol) {
                drawPAImage(x + deltaX, y, "pa_source_absol.png");
            }
            drawPAImage(x + deltaX - 1, y - 2, "pa_relics_icon.png");

            String relicStatus = isExhausted ? "_exh" : "_rdy";

            // ABSOL QUANTUMCORE
            if ("absol_quantumcore".equals(relicID)) {
                drawPAImage(x + deltaX, y, "pa_tech_techicons_cyberneticwarfare" + relicStatus + ".png");
            }
            if ("titanprototype".equals(relicID)
                    || "titanprototype"
                            .equals(relicModel.getHomebrewReplacesID().orElse(""))) {
                drawFactionIconImage(graphics, "relic", x + deltaX - 1, y + 108, 42, 42);
            }

            if ("emelpar".equals(relicID)
                    || "emelpar".equals(relicModel.getHomebrewReplacesID().orElse(""))) {
                String empelar = RelicHelper.sillySpelling();
                int spaceIndex = empelar.lastIndexOf(' ');
                empelar = empelar.substring(0, spaceIndex) + "\n" + empelar.substring(spaceIndex);
                graphics.setFont(Storage.getFont18());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(g2, empelar, x + deltaX + 7, y + 30, 120, true);
            } else if (relicModel.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        g2, relicModel.getShortName(), x + deltaX + 9, y + 30, 120, true);
            } else {
                graphics.setFont(Storage.getFont18());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        g2, relicModel.getShortName(), x + deltaX + 7, y + 30, 120, true);
            }

            deltaX += 48;
        }

        // FAKE RELICS
        if (!fakeRelics.isEmpty()) {
            deltaX += 10;
        }
        for (String relicID : fakeRelics) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, relicModel);

            drawPAImage(x + deltaX, y, "pa_relics_fakerelicicon.png");

            String relicStatus = isExhausted ? "_exh" : "_rdy";

            String relicFileName = "pa_relics_" + relicID + relicStatus + ".png";
            String resourcePath = ResourceHelper.getPAResource(relicFileName);
            BufferedImage resourceBufferedImage;
            try {
                resourceBufferedImage = ImageHelper.read(resourcePath);
                if (resourceBufferedImage == null) {
                    g2.setFont(Storage.getFont20());
                    DrawingUtil.drawTwoLinesOfTextVertically(
                            g2, relicModel.getShortName(), x + deltaX + 5, y + 140, 130);
                } else {
                    graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
                }
            } catch (Exception e) {
                BotLogger.error(new LogOrigin(player), "Bad file: " + relicFileName, e);
            }
            if (MonumentsBRButtonHandler.hasArmageddonProjectSuperweapon(game, player, relicID)) {
                DrawingUtil.getAndDrawControlToken(graphics, player, x + deltaX + 10, y + 60, false, 0.5f);
            }
            if (List.of("economicboon", "naturesboon", "diplomaticboon", "cosmicboon")
                    .contains(relicID)) {
                int tokenCount = LostLegaciesRelicHandler.getBoonTokens(game, player, relicID);
                for (int token = 0; token < tokenCount; token++) {
                    DrawingUtil.getAndDrawControlToken(
                            graphics, player, x + deltaX + 2 + token % 3 * 14, y + 52 + token / 3 * 13, false, 0.25f);
                }
            }

            deltaX += 48;
        }

        // AXIS ORDER FAKE RELICS
        if (!axisOrderRelics.isEmpty()) {
            deltaX += 10;
        }
        for (String relicID : axisOrderRelics) {
            RelicModel relicModel = Mapper.getRelic(relicID);
            boolean isExhausted = exhaustedRelics.contains(relicID);
            if (isExhausted) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 54, 152, relicModel);

            String relicStatus = isExhausted ? "_exh" : "_rdy";
            String relicFileName = "pa_relics_" + relicID + relicStatus + ".png";
            drawPAImage(x + deltaX, y, relicFileName);

            deltaX += 58;
        }
        return x + deltaX + 20;
    }

    private int leaderInfo(Player player, int x, int y, Game game) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        Comparator<Leader> leaderComparator = (leader1, leader2) -> {
            int leaderRank1 =
                    switch (leader1.getType()) {
                        case Constants.AGENT -> 0;
                        case Constants.ENVOY -> 1;
                        case Constants.COMMANDER -> 2;
                        case Constants.HERO -> 3;
                        default -> -1;
                    };
            int leaderRank2 =
                    switch (leader2.getType()) {
                        case Constants.AGENT -> 0;
                        case Constants.ENVOY -> 1;
                        case Constants.COMMANDER -> 2;
                        case Constants.HERO -> 3;
                        default -> -1;
                    };
            if (leaderRank1 == leaderRank2) {
                LeaderModel leaderModel1 = Mapper.getLeader(leader1.getId());
                LeaderModel leaderModel2 = Mapper.getLeader(leader2.getId());
                String leaderName1 = leaderModel1 == null ? leader1.getId() : leaderModel1.getName();
                String leaderName2 = leaderModel2 == null ? leader2.getId() : leaderModel2.getName();
                return leaderName1.compareToIgnoreCase(leaderName2);
            }
            return leaderRank1 - leaderRank2;
        };
        List<Leader> allLeaders = new ArrayList<>(player.getLeaders());
        allLeaders.sort(leaderComparator);

        boolean shouldDrawVeiledGenomes = game.isVeiledHeartMode();
        for (Leader leader : allLeaders) {
            if (shouldDrawVeiledGenomes && !Constants.AGENT.equalsIgnoreCase(leader.getType())) {
                // The last unveiled genome has been drawn, so now draw the veiled genomes.
                deltaX = VeiledHeartService.veiledField(
                        graphics, x, y, VeiledHeartService.VeiledCardType.GENOME, deltaX, player);
                // The veiled genomes have been drawn, so don't draw them again.
                shouldDrawVeiledGenomes = false;
            }

            boolean isExhaustedLocked = leader.isExhausted() || leader.isLocked();
            if (isExhaustedLocked) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            String status = isExhaustedLocked ? "_exh" : "_rdy";
            drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, Mapper.getLeader(leader.getId()));

            if (Mapper.isValidLeader(leader.getId())) {
                LeaderModel leaderModel = Mapper.getLeader(leader.getId());
                String fac = leaderModel.getFaction();
                if (leader.getId().contains("redcreuss")) {
                    fac = "redcreuss";
                } else if (leader.getId().contains("orlandohero")) {
                    fac = "bastion";
                }
                drawFactionIconImage(graphics, fac, x + deltaX - 1, y + 108, 42, 42);
            }

            if (leader.getTgCount() != 0) {
                graphics.setColor(ColorUtil.TradeGoodColor);
                graphics.setFont(Storage.getFont32());
                int offset = 20 - graphics.getFontMetrics().stringWidth("" + leader.getTgCount()) / 2;
                graphics.drawString(Integer.toString(leader.getTgCount()), x + deltaX + offset, y + 25);
            } else {
                String pipID;
                switch (leader.getType()) {
                    case Constants.AGENT -> pipID = "i";
                    case Constants.COMMANDER -> pipID = "ii";
                    case Constants.HERO -> pipID = "iii";
                    case Constants.ENVOY -> pipID = "agenda";
                    default -> pipID = "";
                }

                if (!pipID.isEmpty()) {
                    String leaderPipInfo = "pa_leaders_pips_" + pipID;
                    if (!isExhaustedLocked && leader.isActive()) {
                        leaderPipInfo += "_active" + ".png";
                    } else {
                        leaderPipInfo += status + ".png";
                    }
                    drawPAImage(x + deltaX, y, leaderPipInfo);
                }
            }

            LeaderModel leaderModel = Mapper.getLeader(leader.getId());
            if (leaderModel == null) {
                g2.setFont(Storage.getFont14());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(g2, leader.getId(), x + deltaX + 7, y + 30, 120, true);
                deltaX += 48;
                continue;
            }
            boolean shrink = game.isTwilightsFallMode() ? leaderModel.getTFShrinkName() : leaderModel.getShrinkName();
            String name = game.isTwilightsFallMode() ? leaderModel.getTFShortName() : leaderModel.getShortName();

            if ("yssarilagent".equalsIgnoreCase(leader.getId()) && !game.isTwilightsFallMode()) {
                DrawingUtil.drawTextVertically(
                        g2, "Clever, Clever".toUpperCase(), x + deltaX + 8, y + 30, Storage.getFont14(), true);
                DrawingUtil.drawTextVertically(
                        g2, "Ssruu".toUpperCase(), x + deltaX + 23, y + 30, Storage.getFont18(), true);
            } else if (shrink) {
                g2.setFont(Storage.getFont16());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(g2, name, x + deltaX + 9, y + 30, 120, true);
            } else {
                g2.setFont(Storage.getFont18());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(g2, name, x + deltaX + 7, y + 30, 120, true);
            }

            deltaX += 48;
        }

        if (game.isVeiledHeartMode()) {
            if (shouldDrawVeiledGenomes) {
                // The veiled genomes were not drawn earlier because there are no non-agent leaders.
                // The veiled genomes should still be drawn, so do it now.
                deltaX = VeiledHeartService.veiledField(
                        graphics, x, y, VeiledHeartService.VeiledCardType.GENOME, deltaX, player);
            }
            // Draw the veiled paradigms after all other leaders (directly after the heroes, if any).
            deltaX = VeiledHeartService.veiledField(
                    graphics, x, y, VeiledHeartService.VeiledCardType.PARADIGM, deltaX, player);
        }

        if (player.hasAbility("imperia") || player.hasAbility("imperia_y")) {
            deltaX += 5;
            List<String> mahactCCs = player.getMahactCC();
            Collection<Player> players = game.getRealPlayersNDummies();
            if (game.isMinorFactionsMode()) {
                players = game.getRealPlayers();
            }
            for (Player player_ : players) {
                if (player_ != player) {
                    String playerColor = player_.getColor();
                    if (mahactCCs.contains(playerColor)) {
                        Leader leader_ = player_.unsafeGetLeader(Constants.COMMANDER);
                        if (leader_ != null) {
                            String status_ = leader_.isLocked() ? "_exh" : "_rdy";
                            String imperiaColorFile = "pa_leaders_imperia" + status_ + ".png";
                            drawRectWithOverlay(
                                    graphics, x + deltaX - 2, y - 2, 44, 152, Mapper.getLeader(leader_.getId()));
                            DrawingUtil.drawPlayerFactionIconImage(graphics, player_, x + deltaX - 1, y + 108, 42, 42);
                            drawPAImage(x + deltaX, y, imperiaColorFile);
                            String leaderPipInfo = "pa_leaders_pips_ii" + status_ + ".png";
                            drawPAImage(x + deltaX, y, leaderPipInfo);
                            deltaX += 48;
                        }
                    }
                }
            }
        }

        Player lichPoolOwner = game.getRevenantCommanderOwner(player);
        if (lichPoolOwner != null) {
            deltaX += 5;
            Collection<Player> players = game.getRealPlayersNDummies();
            if (game.isMinorFactionsMode()) {
                players = game.getRealPlayers();
            }

            for (Player otherPlayer : players) {
                if (otherPlayer.equals(lichPoolOwner)
                        || lichPoolOwner.getDebtTokenCount(otherPlayer.getColor(), "lich") < 1) {
                    continue;
                }

                Leader commander = game.getRevenantLichCommander(lichPoolOwner, otherPlayer);
                if (commander == null) {
                    continue;
                }

                String status = commander.isLocked() ? "_exh" : "_rdy";
                String overlay = "pa_leaders_lich" + status + ".png";
                drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, Mapper.getLeader(commander.getId()));
                DrawingUtil.drawPlayerFactionIconImage(graphics, otherPlayer, x + deltaX - 1, y + 108, 42, 42);
                drawPAImage(x + deltaX, y, overlay);
                drawPAImage(x + deltaX, y, "pa_leaders_pips_ii" + status + ".png");
                deltaX += 48;
            }
        }

        return x + deltaX + 20;
    }

    private int debtInfo(Player player, int x, int y, Game game) {
        int startX = x;
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);
        graphics.setColor(Color.WHITE);

        for (Entry<String, Map<String, Integer>> pool :
                player.getAllDebtTokens().entrySet()) {
            if (pool.getValue().values().stream().noneMatch(i -> i > 0)) {
                continue;
            }

            String bankSource = game.getDebtPoolIcon(pool.getKey());
            if (bankSource == null) {
                if (Constants.DEBT_DEFAULT_POOL.equalsIgnoreCase(pool.getKey())) {
                    bankSource = "pa_debtaccount.png";
                    drawPAImage(x, y, bankSource);
                } else {
                    bankSource = TI4Emoji.getRandomizedEmoji(0, null).toString();
                }
            }

            if (!"pa_debtaccount.png".equals(bankSource)) {
                BufferedImage bankImage = null;
                Emoji bankEmoji = Emoji.fromFormatted(bankSource);
                if (bankEmoji instanceof CustomEmoji factionCustomEmoji) {
                    bankImage = ImageHelper.readURLScaled(factionCustomEmoji.getImageUrl(), 120, 120);
                    if (bankImage == null) {
                        bankEmoji = TI4Emoji.findEmojiFromJustName(factionCustomEmoji.getName())
                                .asEmoji();
                        game.setDebtPoolIcon(pool.getKey(), bankEmoji.getFormatted());
                        if (bankEmoji instanceof CustomEmoji e) {
                            bankImage = ImageHelper.readURLScaled(e.getImageUrl(), 120, 120);
                        }
                    }
                } else if (bankEmoji instanceof UnicodeEmoji uni) {
                    BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g3 = img.createGraphics();
                    g3.setFont(Storage.getEmojiFont());
                    BasicStroke stroke4 = new BasicStroke(4.0f);
                    DrawingUtil.superDrawString(
                            g3, uni.getFormatted(), 20, 60, Color.white, null, null, stroke4, Color.black);
                    GlyphVector gv = g3.getFont().createGlyphVector(g3.getFontRenderContext(), uni.getFormatted());
                    Rectangle rect = gv.getGlyphPixelBounds(0, g3.getFontRenderContext(), 20, 60);
                    int pad = 5;
                    BufferedImage img2 =
                            img.getSubimage(rect.x - pad, rect.y - pad, rect.width + pad * 2, rect.height + pad * 2);
                    bankImage = ImageHelper.scale(ImageHelper.square(img2), 120, 120);
                }
                g2.drawImage(bankImage, x + 20, y + 15, null);
            }

            int deltaX = 24;
            g2.setFont(Storage.getFont18());
            DrawingUtil.drawDebtBoxText(g2, pool.getKey().toUpperCase(), x - 4, y + 148, 146);

            int tokenDeltaY = 0;
            int playerCount = 0;
            int maxTokenDeltaX = 0;
            for (Entry<String, Integer> debtToken : pool.getValue().entrySet()) {
                Player debtPlayer = game.getPlayerByColorID(Mapper.getColorID(debtToken.getKey()))
                        .orElse(null);
                int numTokens = debtToken.getValue();
                if (numTokens == 0) continue;
                int overflow = 0;
                if (numTokens > 12) {
                    overflow = numTokens - 10;
                    numTokens = 10;
                }

                boolean hideFactionIcon = isFoWPrivate
                        && debtPlayer != null
                        && !FoWHelper.canSeeStatsOfPlayer(game, debtPlayer, frogPlayer);

                int tokenDeltaX = 0;
                String controlID = Mapper.getControlID(debtToken.getKey());
                if (controlID.contains("null")) {
                    continue;
                }

                float scale = 0.60f;
                BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);

                for (int i = 0; i < numTokens; i++) {
                    DrawingUtil.drawControlToken(
                            graphics,
                            controlTokenImage,
                            DrawingUtil.getPlayerByControlMarker(
                                    game.getPlayers().values(), controlID),
                            x + deltaX + tokenDeltaX,
                            y + 2 + tokenDeltaY,
                            hideFactionIcon,
                            scale);
                    tokenDeltaX += 15;
                }

                if (overflow > 0) {
                    graphics.setColor(Color.WHITE);
                    graphics.setFont(Storage.getFont18());
                    graphics.drawString("+" + overflow, x + deltaX + tokenDeltaX + 29, y + 22 + tokenDeltaY);
                    tokenDeltaX += 2 + graphics.getFontMetrics().stringWidth("+" + overflow);
                }

                tokenDeltaY += 29;
                maxTokenDeltaX = Math.max(maxTokenDeltaX, tokenDeltaX + 35);
                playerCount++;
                if (playerCount % 5 == 0) {
                    tokenDeltaY = 0;
                    deltaX += maxTokenDeltaX;
                    maxTokenDeltaX = 0;
                }
            }
            deltaX = Math.max(deltaX + maxTokenDeltaX, 152);
            graphics.drawRect(x - 2, y - 2, deltaX, 152);
            x += deltaX + 4;
        }

        return x + (startX == x ? 0 : 20);
    }

    private int abilityInfo(Player player, int x, int y) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);
        boolean addedAbilities = false;
        Comparator<String> abilityComparator = (ability1, ability2) -> {
            AbilityModel abilityModel1 = Mapper.getAbility(ability1);
            AbilityModel abilityModel2 = Mapper.getAbility(ability2);
            return abilityModel1.getName().compareToIgnoreCase(abilityModel2.getName());
        };
        List<String> allAbilities = new ArrayList<>(player.getAbilities());
        allAbilities.sort(abilityComparator);

        for (String abilityID : allAbilities) {
            String abilityFileName = null;
            switch (abilityID) {
                case "grace" -> abilityFileName = "pa_ds_edyn_grace";
                case "policy_the_people_connect" -> abilityFileName = "pa_ds_olra_policy_cpos";
                case "policy_the_people_control" -> abilityFileName = "pa_ds_olra_policy_cneg";
                case "policy_the_environment_preserve" -> abilityFileName = "pa_ds_olra_policy_hpos";
                case "policy_the_environment_plunder" -> abilityFileName = "pa_ds_olra_policy_hneg";
                case "policy_the_economy_empower" -> abilityFileName = "pa_ds_olra_policy_ipos";
                case "policy_the_economy_exploit" -> abilityFileName = "pa_ds_olra_policy_ineg";
            }

            boolean isExhaustedLocked = player.getExhaustedAbilities().contains(abilityID);
            if (isExhaustedLocked) {
                graphics.setColor(Color.GRAY);
            } else {
                graphics.setColor(Color.WHITE);
            }

            AbilityModel abilityModel = Mapper.getAbility(abilityID);
            if (abilityModel == null) {
                BotLogger.error("Ability null: " + abilityID);
            } else {
                if (abilityFileName != null) {
                    String status = isExhaustedLocked ? "_exh" : "_rdy";
                    abilityFileName += status + ".png";
                    String resourcePath = ResourceHelper.getPAResource(abilityFileName);

                    BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
                    graphics.drawImage(resourceBufferedImage, x + deltaX, y, null);
                    drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, abilityModel);
                } else {
                    if (game.isLiberationC4Mode() && "ghost".equals(abilityModel.getFaction())) {
                        drawFactionIconImage(g2, "redcreuss", x + deltaX - 1, y, 42, 42);
                    } else {
                        drawFactionIconImage(g2, abilityModel.getFaction(), x + deltaX - 1, y, 42, 42);
                    }
                    if (abilityModel.getShrinkName()) {
                        g2.setFont(Storage.getFont16());
                        DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                                g2, abilityModel.getShortName(), x + deltaX + 9, y + 144, 130);
                    } else {
                        g2.setFont(Storage.getFont18());
                        DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                                g2, abilityModel.getShortName(), x + deltaX + 7, y + 144, 130);
                    }
                    drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, abilityModel);
                }
                deltaX += 48;
                addedAbilities = true;
            }
        }

        return x + deltaX + (addedAbilities ? 20 : 0);
    }

    private int drawOwnedPromissoryNotes(Player player, int x, int y) {
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);
        boolean addedPNs = false;
        Comparator<String> pnComparator = (id1, id2) -> {
            PromissoryNoteModel model1 = Mapper.getPromissoryNote(id1);
            PromissoryNoteModel model2 = Mapper.getPromissoryNote(id2);
            return model1.getName().compareToIgnoreCase(model2.getName());
        };
        List<String> ownedPNs = new ArrayList<>(player.getPromissoryNotesOwned());
        ownedPNs.sort(pnComparator);

        for (String pnID : ownedPNs) {
            PromissoryNoteModel promissoryNote = Mapper.getPromissoryNote(pnID);
            if (!game.isShowOwnedPNsInPlayerArea()
                    && promissoryNote.getFaction().isEmpty()
                    && !promissoryNote.getPlayImmediately()) {
                continue;
            }

            if (promissoryNote.getSource() == ComponentSource.promises_promises) {
                drawPAImageScaled(x + deltaX + 1, y + 1, "pa_promissory_light_pp.png", 38, 28);
            } else {
                drawPAImageScaled(x + deltaX + 1, y + 1, "pa_promissory_light.png", 38, 28);
            }
            if (game.isFrankenGame() && promissoryNote.getFaction().isPresent()) {
                drawFactionIconImage(graphics, promissoryNote.getFaction().get(), x + deltaX - 1, y + 108, 42, 42);
            }
            Player playerWhoHasIt = null;
            if (!game.isFowMode() && promissoryNote.getPlayArea()) {
                found:
                for (Player player_ : game.getRealPlayers()) {
                    for (String pn_ : player_.getPromissoryNotesInPlayArea()) {
                        if (pn_.equals(pnID)) {
                            playerWhoHasIt = player_;
                            break found;
                        }
                    }
                }
            }
            graphics.setColor(playerWhoHasIt != null ? Color.GRAY : Color.WHITE);

            if ("dspntnel"
                    .equals(pnID)) { // for some reason "Plots Within Plots" gets cut off weirdly if handled normally
                graphics.setFont(Storage.getFont16());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        graphics, "Plots Within Plots", x + deltaX + 9, y + 144, 150);
            } else if (promissoryNote.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        graphics, promissoryNote.getShortName(), x + deltaX + 9, y + 144, 120);
            } else {
                graphics.setFont(Storage.getFont18());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        graphics, promissoryNote.getShortName(), x + deltaX + 7, y + 144, 120);
            }
            drawRectWithOverlay(g2, x + deltaX - 2, y - 2, 44, 152, promissoryNote);
            DrawingUtil.drawPlayerFactionIconImageOpaque(g2, playerWhoHasIt, x + deltaX - 1, y + 25, 42, 42, 0.5f);

            deltaX += 48;
            addedPNs = true;
        }
        return x + deltaX + (addedPNs ? 20 : 0);
    }

    private int planetInfo(Player player, int x, int y) {
        List<String> planets = player.getPlanets();
        int deltaX = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        // RESOURCE/INFLUENCE TOTALS
        drawPAImage(x + deltaX - 2, y - 2, "pa_resinf_info.png");
        graphics.setColor(Color.WHITE);
        drawRectWithOverlay(
                graphics,
                x + deltaX - 2,
                y - 2,
                152,
                152,
                "Resource & Influence Summary",
                "This is an overview of your resources and influence. The left side is resources, and the right side is influence.\nThe top number how many you have available\nThe middle number is the total\nThe bottom number is the 'optimal' available\nThe bottom-centre number is the flex 'optimal' available");
        if (player.hasUnlockedBreakthrough("xxchabt")
                || player.hasLeaderUnlocked("xxchahero")) { // XXCHA WITH UNLOCKED HERO
            int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, game);
            int totalPlayerResources = Helper.getPlayerResourcesTotal(player, game);
            if (Constants.gedsDeadId.equals(player.getUserID()) || RenderRandom.oneIn("playerArea.gedsDead", 100)) {
                drawPAImageOpaque(x + deltaX - 2, y - 2, "pa_resinf_info_xxcha_gedsdead.png", 0.9f);
            } else {
                drawPAImageOpaque(x + deltaX - 2, y - 2, "pa_resinf_info_xxcha.png", 0.9f);
            }
            drawFactionIconImageOpaque(graphics, "xxcha", x + deltaX + 75 - 94 / 2, y + 75 - 94 / 2, 95, 95, 0.15f);
            graphics.setColor(Color.WHITE);
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(availablePlayerResources),
                    new Rectangle(x + deltaX, y + 75 - 35 + 5, 150, 35),
                    Storage.getFont35());
            graphics.setColor(Color.GRAY);
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(totalPlayerResources),
                    new Rectangle(x + deltaX, y + 75 + 5, 150, 24),
                    Storage.getFont24());
        } else { // NOT XXCHA WITH UNLOCKED HERO
            int availablePlayerResources = Helper.getPlayerResourcesAvailable(player, game);
            int totalPlayerResources = Helper.getPlayerResourcesTotal(player, game);
            int availablePlayerResourcesOptimal = Helper.getPlayerOptimalResourcesAvailable(player, game);
            int availablePlayerInfluence = Helper.getPlayerInfluenceAvailable(player, game);
            int totalPlayerInfluence = Helper.getPlayerInfluenceTotal(player, game);
            int availablePlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceAvailable(player, game);
            int availablePlayerFlex = Helper.getPlayerFlexResourcesInfluenceAvailable(player, game);
            // int totalPlayerFlex = Helper.getPlayerFlexResourcesInfluenceTotal(player,
            // map);

            // RESOURCES
            graphics.setColor(Color.WHITE);
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(availablePlayerResources),
                    new Rectangle(x + deltaX + 30, y + 30, 32, 32),
                    Storage.getFont32());
            graphics.setColor(Color.GRAY);
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(totalPlayerResources),
                    new Rectangle(x + deltaX + 30, y + 55, 32, 32),
                    Storage.getFont20());
            graphics.setColor(Color.decode("#d5bd4f")); // greyish-yellow
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(availablePlayerResourcesOptimal),
                    new Rectangle(x + deltaX + 30, y + 90, 32, 32),
                    Storage.getFont18());
            // DrawingUtil.drawCenteredString(graphics, "OPT", new Rectangle(x + deltaX + 30, y + 100,
            // 32, 32), Storage.getFont8());
            // graphics.setColor(Color.GRAY);
            // DrawingUtil.drawCenteredString(graphics, String.valueOf(totalPlayerResourcesOptimal), new
            // Rectangle(x + deltaX + 34, y + 109, 32, 32), Storage.getFont32());

            // INFLUENCE
            graphics.setColor(Color.WHITE);
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(availablePlayerInfluence),
                    new Rectangle(x + deltaX + 90, y + 30, 32, 32),
                    Storage.getFont32());
            graphics.setColor(Color.GRAY);
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(totalPlayerInfluence),
                    new Rectangle(x + deltaX + 90, y + 55, 32, 32),
                    Storage.getFont20());
            graphics.setColor(Color.decode("#57b9d9")); // greyish-blue
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(availablePlayerInfluenceOptimal),
                    new Rectangle(x + deltaX + 90, y + 90, 32, 32),
                    Storage.getFont18());

            // FLEX
            graphics.setColor(Color.WHITE);
            if (Constants.cagesId.equals(player.getUserID())) graphics.setColor(Color.decode("#f616ce"));
            DrawingUtil.drawCenteredString(
                    graphics,
                    String.valueOf(availablePlayerFlex),
                    new Rectangle(x + deltaX, y + 115, 150, 20),
                    Storage.getFont18());
        }

        deltaX += 156;

        boolean randomizeList = player != frogPlayer && isFoWPrivate;
        if (randomizeList) {
            RenderRandom.shuffle("playerArea.planetShuffle", planets);
        }

        List<String> realPlanets = new ArrayList<>();
        List<String> nonTile = new ArrayList<>();
        List<String> fakePlanets = new ArrayList<>();
        for (String planet : planets) {
            PlanetModel model = Mapper.getPlanet(planet);

            Set<PlanetType> types = EnumSet.noneOf(PlanetType.class);
            if (model != null && model.getPlanetTypes() != null) types.addAll(model.getPlanetTypes());

            if (types.contains(PlanetType.FAKE) || types.contains(PlanetType.SPACESTATION)) {
                fakePlanets.add(planet);
            } else if (game.getTileFromPlanet(planet) == null) {
                nonTile.add(planet);
            } else {
                realPlanets.add(planet);
            }
        }

        Tile homeTile = player.getHomeSystemTile();
        if (homeTile != null) {
            if ("51".equals(homeTile.getTileID())) {
                Tile creussGate = game.getTile("17");
                if (creussGate != null) {
                    homeTile = creussGate;
                }
            } else if ("118".equals(homeTile.getTileID())) {
                Tile creussGate = game.getTile("94");
                if (creussGate != null) {
                    homeTile = creussGate;
                }
            }

            String homePos = homeTile.getPosition();
            Point homePosition = PositionMapper.getTilePosition(homePos);
            Comparator<String> planetComparator = (planet1, planet2) -> {
                if (homePosition == null) return 0;

                Tile tile1 = game.getTileFromPlanet(planet1);
                if (tile1 != null && "51".equals(tile1.getTileID())) {
                    Tile creussGate = game.getTile("17");
                    if (creussGate != null) tile1 = creussGate;
                } else if (tile1 != null && "118".equals(tile1.getTileID())) {
                    Tile creussGate = game.getTile("94");
                    if (creussGate != null) tile1 = creussGate;
                }

                Tile tile2 = game.getTileFromPlanet(planet2);
                if (tile2 != null && "51".equals(tile2.getTileID())) {
                    Tile creussGate = game.getTile("17");
                    if (creussGate != null) tile2 = creussGate;
                } else if (tile2 != null && "118".equals(tile2.getTileID())) {
                    Tile creussGate = game.getTile("94");
                    if (creussGate != null) tile2 = creussGate;
                }

                if (tile1 == null || tile2 == null)
                    return Comparator.nullsLast(null).compare(tile1, tile2);

                Point p1 = PositionMapper.getTilePosition(tile1.getPosition());
                Point p2 = PositionMapper.getTilePosition(tile2.getPosition());
                if (p1 == null || p2 == null) return Comparator.nullsLast(null).compare(p1, p2);

                int distance1, distance2;
                if (homePos.equals(tile1.getPosition())) {
                    distance1 = 0;
                } else if (tile1.isFracture()) {
                    distance1 = "styx".equals(planet1) ? 9000 : 8000;
                } else if (Arrays.asList("tl", "tr", "bl", "br").contains(tile1.getPosition())) {
                    distance1 = 7000;
                } else {
                    distance1 = ((homePosition.x - p1.x) * (homePosition.x - p1.x)
                            + (homePosition.y - p1.y) * (homePosition.y - p1.y));
                }
                if (homePos.equals(tile2.getPosition())) {
                    distance2 = 0;
                } else if (tile2.isFracture()) {
                    distance2 = "styx".equals(planet2) ? 9000 : 8000;
                } else if (Arrays.asList("tl", "tr", "bl", "br").contains(tile2.getPosition())) {
                    distance2 = 7000;
                } else {
                    distance2 = ((homePosition.x - p2.x) * (homePosition.x - p2.x)
                            + (homePosition.y - p2.y) * (homePosition.y - p2.y));
                }

                if (distance1 != distance2) {
                    return distance1 - distance2;
                }
                if (!tile1.getPosition().equalsIgnoreCase(tile2.getPosition())) {
                    return tile2.getPosition().compareToIgnoreCase(tile1.getPosition());
                }
                return planet1.compareToIgnoreCase(planet2);
            };
            realPlanets.sort(planetComparator);
        }

        for (String planet : realPlanets) {
            deltaX = drawPlanetInfo(player, planet, x, y, deltaX);
        }
        if (!nonTile.isEmpty()) {
            deltaX += 30;
            for (String planet : nonTile) {
                deltaX = drawPlanetInfo(player, planet, x, y, deltaX);
            }
        }

        if (!fakePlanets.isEmpty()) {
            deltaX += 30;
            for (String planet : fakePlanets) {
                deltaX = drawPlanetInfo(player, planet, x, y, deltaX);
            }
        }
        List<String> coexistingPlanets = game.getPlanetsPlayerIsCoexistingOn(player);
        if (!coexistingPlanets.isEmpty()) {
            deltaX += 30;
            for (String planet : coexistingPlanets) {
                deltaX = drawPlanetInfo(player, planet, x, y, deltaX);
            }
        }

        return x + deltaX + 20;
    }

    private int drawPlanetInfo(Player player, String planetName, int x, int y, int deltaX) {
        Map<String, Planet> planetsInfo = game.getPlanetsInfo();
        List<String> exhaustedPlanets = player.getExhaustedPlanets();
        List<String> exhaustedPlanetsAbilities = player.getExhaustedPlanetsAbilities();

        try {
            Planet planet = planetsInfo.get(planetName);
            if (planet == null) {
                player.removePlanet(planetName);
                BotLogger.warning(
                        new LogOrigin(player),
                        "Planet " + planetName + " not found in game " + game.getName()
                                + ". Removing planet from player.");
                return deltaX;
            }
            planet.updateTriadStats(player);
            planet.updateGroveStats(player);
            PlanetModel planetModel = planet.getPlanetModel();
            if (planetModel == null) return deltaX;
            boolean coexist = false;
            String coexistFaction = "";
            boolean isExhausted = exhaustedPlanets.contains(planetName);
            for (Player p2 : player.getGame().getRealPlayers()) {
                if (p2 != player && p2.getPlanets().contains(planetName)) {
                    isExhausted = true;
                    coexistFaction = p2.getFaction();
                    coexist = true;
                    if (coexistFaction.contains("keleres")) {
                        coexistFaction = "keleres";
                    }
                }
            }
            if (player.getGame()
                    .getPlayersPlanetsThatOthersAreCoexistingOn(player)
                    .contains(planetName)) {
                coexist = true;
            }

            graphics.setColor(isExhausted ? Color.GRAY : Color.WHITE);

            String statusOfPlanet = isExhausted ? "_exh" : "_rdy";
            graphics.drawRect(x + deltaX - 2, y - 2, 52, 152);

            // Display planet traits
            String planetDisplayIcon = planet.getOriginalPlanetType();
            List<PlanetType> originalPlanetTypes = Mapper.getPlanet(planetName).getPlanetTypes();
            if (originalPlanetTypes == null || originalPlanetTypes.isEmpty()) {
                planetDisplayIcon = "none";
            }
            if (originalPlanetTypes != null && originalPlanetTypes.contains(PlanetType.FACTION)) {
                planetDisplayIcon = Mapper.getPlanet(planetName).getFactionHomeworld();
                if (planetDisplayIcon == null) { // fallback to current player's faction
                    planetDisplayIcon = player.getFaction();
                }
                if (game.isLiberationC4Mode() && "ghost".equals(planetDisplayIcon)) {
                    planetDisplayIcon = "redcreuss";
                }
            }

            Set<String> planetTypes = planet.getPlanetTypes();
            if (!planetTypes.isEmpty() && planetTypes.size() > 1) {
                planetDisplayIcon = "combo_";
                if (planetTypes.contains("cultural")) planetDisplayIcon += "C";
                if (planetTypes.contains("hazardous")) planetDisplayIcon += "H";
                if (planetTypes.contains("industrial")) planetDisplayIcon += "I";
            }

            if (coexist && coexistFaction.isEmpty()) {
                drawFactionIconImage(graphics, "coexist", x + deltaX - 2 + 20, y - 2 + 40, 30, 30);
            }

            if (planetDisplayIcon != null && !planetDisplayIcon.isEmpty()) {
                if ("keleres".equals(player.getFaction())
                        && ("mentak".equals(planetDisplayIcon)
                                || "xxcha".equals(planetDisplayIcon)
                                || "argent".equals(planetDisplayIcon))) {
                    planetDisplayIcon = "keleres";
                }

                if (Mapper.isValidFaction(planetDisplayIcon) || "redcreuss".equals(planetDisplayIcon)) {
                    drawFactionIconImage(graphics, planetDisplayIcon, x + deltaX - 2, y - 2, 52, 52);
                } else if ("triad".equals(planetName)) {
                    drawPAImage(x + deltaX + 3, y - 2, "pa_relics_icon.png");
                } else {
                    String planetTypeName = "pc_attribute_" + planetDisplayIcon + ".png";
                    drawPlanetCardDetail(x + deltaX + 1, y + 2, planetTypeName);
                    if (coexist && Mapper.isValidFaction(coexistFaction)) {
                        drawFactionIconImage(graphics, coexistFaction, x + deltaX - 2 + 20, y - 2 + 40, 30, 30);
                    }
                }
            }

            // GLEDGE CORE
            if (planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG)) {
                String tokenPath = ResourceHelper.getInstance().getTokenFile(Constants.GLEDGE_CORE_PNG);
                BufferedImage image = ImageHelper.readScaled(tokenPath, 0.25f);
                graphics.drawImage(image, x + deltaX + 15, y + 112, null);
            }

            boolean hasAttachment = planet.hasAttachment();
            if (hasAttachment) {
                String planetChevrons = "pc_upgrade.png";
                if (planet.getTokenList().contains("attachment_tombofemphidia.png")) {
                    planetChevrons = "pc_upgrade_tomb.png";
                    ExploreModel tomb = Mapper.getExplore("toe");
                    addWebsiteOverlay(tomb, x + deltaX + 26, y + 40, 20, 20);
                }
                drawPlanetCardDetail(x + deltaX + 26, y + 40, planetChevrons);
            }

            if (planet.isLegendary()) {
                addWebsiteOverlay(planetModel, x + deltaX + 26, y + 60, 20, 20);
                String statusOfAbility = exhaustedPlanetsAbilities.contains(planetName) ? "_exh" : "_rdy";
                String planetLegendaryCresent = "pc_legendary" + statusOfAbility + ".png";
                drawPlanetCardDetail(x + deltaX + 26, y + 60, planetLegendaryCresent);
            }

            boolean hasBentorEncryptionKey =
                    planet.getTokenList().stream().anyMatch(token -> token.contains("encryptionkey"));
            // BENTOR ENCRYPTION KEY
            if (hasBentorEncryptionKey) {
                String imageFileName = "pc_tech_bentor_encryptionkey.png";
                addWebsiteOverlay("Bentor Encryption Key", null, x + deltaX + 26, y + 82, 20, 20);
                drawPlanetCardDetail(x + deltaX + 26, y + 82, imageFileName);
            }

            String originalTechSpeciality = planet.getOriginalTechSpeciality();
            if (StringUtils.isNotBlank(originalTechSpeciality) && !hasBentorEncryptionKey) {
                String planetTechSkip = "pc_tech_" + originalTechSpeciality + statusOfPlanet + ".png";
                if (planet.getTechSpecialities().size() > 1) {
                    StringBuilder techType = new StringBuilder();
                    for (String type : planet.getTechSpecialities()) {
                        techType.append(type.toLowerCase());
                    }
                    String abbrev = "";
                    switch (techType.toString()) {
                        case "propulsionpropulsion" -> abbrev = "bb";
                        case "warfarewarfare" -> abbrev = "rr";
                        case "cyberneticcybernetic" -> abbrev = "yy";
                        case "bioticbiotic" -> abbrev = "gg";
                        case "warfarepropulsion", "propulsionwarfare" -> abbrev = "br";
                        case "cyberneticpropulsion", "propulsioncybernetic" -> abbrev = "by";
                        case "cyberneticwarfare", "warfarecybernetic" -> abbrev = "ry";
                        case "bioticwarfare", "warfarebiotic" -> abbrev = "gr";
                        case "bioticcybernetic", "cyberneticbiotic" -> abbrev = "gy";
                        case "bioticpropulsion", "propulsionbiotic" -> abbrev = "bg";
                    }
                    if (!abbrev.isEmpty()) {
                        planetTechSkip = "pc_tech_" + abbrev + statusOfPlanet + ".png";
                    }
                }
                drawPlanetCardDetail(x + deltaX + 26, y + 82, planetTechSkip);
            } else if (!hasBentorEncryptionKey) {
                List<String> techSpeciality = planet.getTechSpeciality();
                for (String techSpec : techSpeciality) {
                    if (techSpec.isEmpty()) {
                        continue;
                    }
                    String planetTechSkip = "pc_tech_" + techSpec + statusOfPlanet + ".png";
                    drawPlanetCardDetail(x + deltaX + 26, y + 82, planetTechSkip);
                }
            }

            String resFileName = "pc_res" + statusOfPlanet + ".png";
            String infFileName = "pc_inf" + statusOfPlanet + ".png";
            int resources = planet.getResources();
            int influence = planet.getInfluence();
            if (planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                resFileName = "pc_res_khrask" + statusOfPlanet + ".png";
                addWebsiteOverlay("Garden World", null, x + deltaX, y, 20, 20);
            }

            drawPlanetCardDetail(x + deltaX + 26, y + 103, resFileName);
            drawPlanetCardDetail(x + deltaX + 26, y + 125, infFileName);

            graphics.setFont(Storage.getFont16());
            int offset = 11 - graphics.getFontMetrics().stringWidth("" + resources) / 2;
            if (planet.getTokenList().contains(Constants.GARDEN_WORLDS_PNG)) {
                graphics.setColor(Color.BLACK);
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        graphics.drawString("" + resources, x + deltaX + 26 + offset + i, y + 119 + j);
                    }
                }
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString("" + resources, x + deltaX + 26 + offset, y + 119);
            offset = 10 - graphics.getFontMetrics().stringWidth("" + influence) / 2;
            graphics.drawString("" + influence, x + deltaX + 27 + offset, y + 141);

            graphics.setColor(isExhausted ? Color.GRAY : Color.WHITE);
            if (planetModel.getShrinkNamePNAttach()) {
                DrawingUtil.drawTextVertically(
                        graphics,
                        planetModel.getShortName().toUpperCase(),
                        x + deltaX + 9,
                        y + 144,
                        Storage.getFont16());
            } else {
                DrawingUtil.drawTextVertically(
                        graphics,
                        planetModel.getShortName().toUpperCase(),
                        x + deltaX + 7,
                        y + 144,
                        Storage.getFont18());
            }

            if (!game.getStoredValue("CommsOnPlanet" + planet.getName()).isEmpty()) {
                int comms = Integer.parseInt(game.getStoredValue("CommsOnPlanet" + planet.getName()));
                graphics.setColor(Color.GRAY);
                graphics.setFont(Storage.getFont26());
                int offset2 = 20 - graphics.getFontMetrics().stringWidth("" + comms) / 2;
                if (game.isFacilitiesMode()) {
                    graphics.setColor(ColorUtil.TradeGoodColor);
                }
                graphics.drawString(Integer.toString(comms), x + deltaX + 15 + offset2, y + 67);
            }
            if (player.getHarvestCounter() > 0 && "uikos".equalsIgnoreCase(planet.getName())) {
                int comms = player.getHarvestCounter();
                graphics.setFont(Storage.getFont26());
                int offset2 = 20 - graphics.getFontMetrics().stringWidth("" + comms) / 2;
                graphics.setColor(Color.GRAY);

                graphics.drawString(Integer.toString(comms), x + deltaX + offset2 - 10, y + 69);
            }

            return deltaX + 56;
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), "could not print out planet: " + planetName.toLowerCase(), e);
        }
        return deltaX;
    }

    private void drawPlanetCardDetail(int x, int y, String resourceName) {
        String resourcePath = ResourceHelper.getInstance().getPlanetResource(resourceName);
        BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
        graphics.drawImage(resourceBufferedImage, x, y, null);
    }

    private int techInfo(Player player, int x, int y, Game game) {
        List<String> techs = new ArrayList<>(player.getTechs());
        if (player.hasUnlockedBreakthrough("tyrisbt")) {
            techs.addAll(TyrisBreakthroughHandler.getTyrisBTTechs(player, game));
        }
        Map<String, List<String>> techsFiltered = new HashMap<>();
        for (String tech : techs) {
            TechnologyModel techModel = Mapper.getTech(tech);
            if (techModel == null) {
                continue;
            }
            String techType = techModel.getFirstType().toString();
            if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
                techType = game.getStoredValue("colorChange" + tech);
            }
            List<String> techList = techsFiltered.get(techType);
            if (techList == null) {
                techList = new ArrayList<>();
            }
            techList.add(tech);
            techsFiltered.put(techType, techList);
        }
        Comparator<String> techComparator = (tech1, tech2) -> {
            TechnologyModel tech1Info = Mapper.getTech(tech1);
            TechnologyModel tech2Info = Mapper.getTech(tech2);
            return TechnologyModel.sortTechsByRequirements(tech1Info, tech2Info);
        };
        for (Map.Entry<String, List<String>> entry : techsFiltered.entrySet()) {
            List<String> list = entry.getValue();
            list.sort(techComparator);
        }

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        int deltaX = 0;

        List<String> exhaustedTechs = player.getExhaustedTechs();
        if (game.isTwilightsFallMode()) {
            deltaX = techField(x, y, techsFiltered.get("generictf"), exhaustedTechs, deltaX, player);
        }
        deltaX = techField(x, y, techsFiltered.get(Constants.PROPULSION), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.WARFARE), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.CYBERNETIC), exhaustedTechs, deltaX, player);
        deltaX = techField(x, y, techsFiltered.get(Constants.BIOTIC), exhaustedTechs, deltaX, player);

        if (game.isVeiledHeartMode()) {
            deltaX = VeiledHeartService.veiledField(
                    graphics, x, y, VeiledHeartService.VeiledCardType.ABILITY, deltaX, player);
        }

        deltaX = techFieldUnit(x, y, techsFiltered.get(Constants.UNIT_UPGRADE), deltaX, player, game);

        if (game.isVeiledHeartMode()) {
            deltaX = VeiledHeartService.veiledField(
                    graphics, x, y, VeiledHeartService.VeiledCardType.UNIT, deltaX, player);
        }

        List<String> purgedTechs = player.getPurgedTechs();
        if (!purgedTechs.isEmpty()) {
            purgedTechs = new ArrayList<>(purgedTechs);
            purgedTechs.removeIf(tech -> Mapper.getTech(tech) == null);
            purgedTechs.sort(techComparator);
            deltaX = techField(x, y, purgedTechs, Collections.emptyList(), deltaX, player);
        }
        return x + deltaX;
    }

    private int allBreakthroughInfo(Player player, int x, int y, Game game) {
        int startX = x;
        for (String bt : player.getBreakthroughIDs()) {
            x = breakthroughInfo(player, bt, x, y, game);
        }
        return x + (startX == x ? 0 : 20);
    }

    private int breakthroughInfo(Player player, String bt, int x, int y, Game game) {
        BreakthroughModel model = player.getBreakthroughModel(bt);
        if (model == null
                || ((!game.isThundersEdge() || game.isTwilightsFallMode())
                        && !player.hasUnlockedBreakthrough(model.getID()))) return x;
        String name = model.getShortName();
        String faction = model.getFaction().orElse(null);
        boolean exh = player.isBreakthroughExhausted(bt);
        boolean unl = player.isBreakthroughUnlocked(bt);

        // Draw something
        try {

            Color textColor = Color.white;
            if (unl && player.isBreakthroughActive(bt)) textColor = new Color(19, 249, 236);
            else if (!unl || exh) textColor = Color.gray;
            graphics.setColor(textColor);
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setStroke(stroke2);

            drawFactionIconImage(graphics, faction, x - 1, y + 108, 42, 42);
            BreakthroughModel copiedDataBreach = "netrunnersbt".equals(bt)
                    ? NetrunnersBreakthroughHandler.getCopiedDataBreachBreakthrough(game, player)
                    : null;
            String synergies = copiedDataBreach == null
                    ? model.getBackgroundResource(unl && !exh)
                    : copiedDataBreach.getBackgroundResource(unl && !exh);
            drawPAImage(x, y, synergies);
            if (model.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(graphics, model.getShortName(), x + 11, y + 144, 120);
            } else {
                graphics.setFont(Storage.getFont18());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(graphics, model.getShortName(), x + 9, y + 144, 120);
            }

            if (!unl) textColor = Color.red;
            graphics.setColor(textColor);
            drawRectWithOverlay(graphics, x, y - 2, 44, 152, model);
            if (copiedDataBreach != null) {
                String copiedFaction = NetrunnersBreakthroughHandler.getDataBreachTargetFaction(game, player);
                if (copiedFaction != null) {
                    drawFactionIconImage(graphics, copiedFaction, x + 20, y + 2, 20, 20);
                }
            }

            if (player.getBreakthroughTGs(bt) > 0) {
                BufferedImage tg = ImageHelper.readEmojiImageScaled(MiscEmojis.tg, 40);
                graphics.drawImage(tg, x + 2, y - 40, null);
                String tgs = Integer.toString(player.getBreakthroughTGs(bt));
                graphics.setFont(Storage.getFont32());
                DrawingUtil.superDrawString(
                        graphics,
                        tgs,
                        x + 22,
                        y - 20,
                        Color.white,
                        HorizontalAlign.Center,
                        VerticalAlign.Center,
                        stroke8,
                        Color.black);
            }
        } catch (Exception e) {
            BotLogger.error("Error displaying breakthrough: " + name, e);
            return x;
        }
        return x + 48;
    }

    private int factionTechInfo(Player player, int x, int y) {
        List<String> techs = player.getNotResearchedFactionTechs();
        if (techs.isEmpty()) {
            return x;
        }

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        int deltaX = factionTechField(player, x, y, techs, 0);
        return x + deltaX;
    }

    private int techField(int x, int y, List<String> techs, List<String> exhaustedTechs, int deltaX, Player player) {
        if (techs == null) {
            return deltaX;
        }
        boolean zealotsHeroActive = !game.getStoredValue("zealotsHeroTechs").isEmpty();
        List<String> zealotsTechs =
                Arrays.asList(game.getStoredValue("zealotsHeroTechs").split("-"));
        for (String tech : techs) {
            if (player.getSingularityTechs().contains(tech)) {
                continue;
            }
            boolean isExhausted = exhaustedTechs.contains(tech);
            boolean isPurged = player.getPurgedTechs().contains(tech);
            String techStatus = isExhausted ? "_exh.png" : "_rdy.png";

            TechnologyModel techModel = Mapper.getTech(tech);

            String techIcon = techModel.getImageFileModifier();

            // Handle Homebrew techs with modded colours
            if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
                techIcon = game.getStoredValue("colorChange" + tech);
            }

            // Draw Background Colour
            if (!techIcon.isEmpty()) {
                String techSpec = "pa_tech_techicons_" + techIcon + techStatus;
                drawPAImage(x + deltaX, y, techSpec);
            }

            // Zealots Hero Active
            if (zealotsHeroActive && zealotsTechs.contains(tech)) {
                String path = "pa_tech_techicons_zealots.png";
                drawPAImage(x + deltaX, y, path);
            }

            if (techModel.getSource() == ComponentSource.absol) {
                drawPAImage(x + deltaX, y, "pa_source_absol" + (isExhausted ? "_exh" : "") + ".png");
            }

            // Draw Faction Tech Icon
            if (techModel.getFaction().isPresent()) {

                if (game.isLiberationC4Mode()
                        && "ghost".equals(techModel.getFaction().get())) {
                    drawFactionIconImage(graphics, "redcreuss", x + deltaX - 1, y + 108, 42, 42);
                } else {
                    String faction = techModel.getFaction().get();
                    drawFactionIconImage(graphics, faction, x + deltaX - 1, y + 108, 42, 42);
                }
            } else {
                Color foreground =
                        switch (techModel.getFirstType()) {
                            case PROPULSION -> ColorUtil.PropulsionTech;
                            case BIOTIC -> ColorUtil.BioticTech;
                            case CYBERNETIC -> ColorUtil.CyberneticTech;
                            case WARFARE -> ColorUtil.WarfareTech;
                            default -> Color.WHITE;
                        };
                if (techModel.getTypes().size() > 1) {
                    foreground = Color.WHITE;
                }
                if (isExhausted || isPurged) {
                    foreground = Color.GRAY;
                }

                String initials = techModel.getInitials();
                if (initials.length() == 2) {
                    String left = initials.substring(0, 1);
                    String right = initials.substring(1, 2);
                    graphics.setFont(Storage.getFont32());
                    int offsetLeft = Math.max(0, 10 - graphics.getFontMetrics().stringWidth(left) / 2);
                    int offsetRight = Math.min(
                            40 - graphics.getFontMetrics().stringWidth(right),
                            30 - graphics.getFontMetrics().stringWidth(right) / 2);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(right, x + i + deltaX + offsetRight, y + j + 148);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(right, x + deltaX + offsetRight, y + 148);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(left, x + i + deltaX + offsetLeft, y + j + 139);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(left, x + deltaX + offsetLeft, y + 139);
                } else if (initials.length() == 3) {
                    String left = initials.substring(0, 1);
                    String middle = initials.substring(1, 2);
                    String right = initials.substring(2, 3);
                    graphics.setFont(Storage.getFont24());
                    int offsetLeft = Math.max(0, 7 - graphics.getFontMetrics().stringWidth(left) / 2);
                    int offsetMiddle = 20 - graphics.getFontMetrics().stringWidth(middle) / 2;
                    int offsetRight = Math.min(
                            40 - graphics.getFontMetrics().stringWidth(right),
                            33 - graphics.getFontMetrics().stringWidth(right) / 2);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(right, x + i + deltaX + offsetRight, y + j + 148);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(right, x + deltaX + offsetRight, y + 148);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(middle, x + i + deltaX + offsetMiddle, y + j + 141);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(middle, x + deltaX + offsetMiddle, y + 141);
                    graphics.setColor(Color.BLACK);
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            graphics.drawString(left, x + i + deltaX + offsetLeft, y + j + 134);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(left, x + deltaX + offsetLeft, y + 134);
                } else {
                    initials = initials.substring(0, 1);
                    graphics.setFont(Storage.getFont48());
                    int offset = 20 - graphics.getFontMetrics().stringWidth(initials) / 2;
                    graphics.setColor(Color.BLACK);
                    for (int i = -2; i <= 2; i++) {
                        for (int j = -2; j <= 2; j++) {
                            graphics.drawString(initials, x + i + deltaX + offset, y + j + 148);
                        }
                    }
                    graphics.setColor(foreground);
                    graphics.drawString(initials, x + deltaX + offset, y + 148);
                }
            }

            graphics.setColor(isExhausted ? Color.GRAY : Color.WHITE);
            if (isPurged) graphics.setColor(Color.RED);

            if (techModel.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        graphics, techModel.getShortName(), x + deltaX + 9, y + 116, 116);
            } else {
                graphics.setFont(Storage.getFont18());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        graphics, techModel.getShortName(), x + deltaX + 7, y + 116, 116);
            }
            if ("dslaner".equalsIgnoreCase(tech) || "tf-dslaner".equalsIgnoreCase(tech)) {
                DrawingUtil.drawTextVertically(
                        graphics, "" + player.getAtsCount(), x + deltaX + 15, y + 140, Storage.getFont16());
            }

            drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, techModel);
            deltaX += 48;
        }

        List<String> singularities = player.getSingularityTechs();
        if (!singularities.isEmpty()) {
            String initials = (player.hasTech("tf-singularityx") ? "X" : "")
                    + (player.hasTech("tf-singularityy") ? "Y" : "")
                    + (player.hasTech("tf-singularityz") ? "Z" : "");
            if (singularities.size() > initials.length()) {
                initials += "Q".repeat(singularities.size() - initials.length());
            }
            exhaustedTechs = player.getExhaustedTechs();
            for (int t = 0; t < singularities.size(); t++) {
                String tech = singularities.get(t);
                if (!techs.contains(tech)) {
                    continue;
                }
                boolean isExhausted = exhaustedTechs.contains(tech)
                        || exhaustedTechs.contains(
                                "tf-singularity" + initials.toLowerCase().charAt(t));
                drawPAImage(x + deltaX, y, "pa_tech_techicons_generictf_" + (isExhausted ? "exh" : "rdy") + ".png");
                TechnologyModel techModel = Mapper.getTech(tech);
                Color foreground = isExhausted ? Color.GRAY : Color.WHITE;
                graphics.setFont(Storage.getFont48());
                int offset = 20 - graphics.getFontMetrics().stringWidth(initials.substring(t, t + 1)) / 2;
                graphics.setColor(Color.BLACK);
                for (int i = -2; i <= 2; i++) {
                    for (int j = -2; j <= 2; j++) {
                        graphics.drawString(initials.substring(t, t + 1), x + i + deltaX + offset, y + j + 148);
                    }
                }
                graphics.setColor(foreground);
                graphics.drawString(initials.substring(t, t + 1), x + deltaX + offset, y + 148);
                if (techModel.getShrinkName()) {
                    graphics.setFont(Storage.getFont16());
                    DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                            graphics, techModel.getShortName(), x + deltaX + 9, y + 116, 116);
                } else {
                    graphics.setFont(Storage.getFont18());
                    DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                            graphics, techModel.getShortName(), x + deltaX + 7, y + 116, 116);
                }
                drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, techModel);
                deltaX += 48;
            }
        }
        return deltaX;
    }

    private int factionTechField(Player player, int x, int y, List<String> techs, int deltaX) {
        if (techs == null) {
            return deltaX;
        }

        for (String tech : techs) {
            graphics.setColor(Color.DARK_GRAY);

            TechnologyModel techModel = Mapper.getTech(tech);
            if (techModel == null || techModel.isUnitUpgrade()) {
                continue;
            }

            int types = 0;
            String techIcon = "";
            if (techModel.isPropulsionTech()) {
                types++;
                techIcon += "propulsion";
            }
            if (techModel.isCyberneticTech()) {
                types++;
                techIcon += "cybernetic";
            }
            if (techModel.isBioticTech()) {
                if (types < 2) techIcon += "biotic";
                types++;
            }
            if (techModel.isWarfareTech()) {
                if (types < 2) techIcon += "warfare";
                types++;
            }

            if (!game.getStoredValue("colorChange" + tech).isEmpty()) {
                techIcon = game.getStoredValue("colorChange" + tech);
            }

            if (!techIcon.isEmpty()) {
                String techSpec = "pa_tech_techicons_" + techIcon + "_exh.png";
                drawPAImage(x + deltaX, y, techSpec);
            }

            if (techModel.getFaction().isPresent()) {
                if (game.isLiberationC4Mode()
                        && "ghost".equals(techModel.getFaction().get())) {
                    drawFactionIconImageOpaque(graphics, "redcreuss", x + deltaX + 1, y + 108, 42, 42, 0.5f);
                } else {
                    drawFactionIconImageOpaque(
                            graphics, techModel.getFaction().get(), x + deltaX + 1, y + 108, 42, 42, 0.5f);
                }
            }

            if (techModel.getShrinkName()) {
                graphics.setFont(Storage.getFont16());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        graphics, techModel.getShortName(), x + deltaX + 9, y + 116, 116);
            } else {
                graphics.setFont(Storage.getFont18());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        graphics, techModel.getShortName(), x + deltaX + 7, y + 116, 116);
            }

            drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, techModel);
            deltaX += 48;
        }
        return deltaX;
    }

    private static Point getUnitTechOffsets(String asyncId, boolean getFactionIconOffset) {
        asyncId = AliasHandler.resolveUnit(asyncId);
        switch (asyncId) {
            case "gf" -> {
                if (getFactionIconOffset) return new Point(3, 17);
                return new Point(3, 2);
            }
            case "fs" -> {
                if (getFactionIconOffset) return new Point(185, 101);
                return new Point(151, 67);
            }
            case "ff" -> {
                if (getFactionIconOffset) return new Point(7, 72);
                return new Point(7, 59);
            }
            case "dn" -> {
                if (getFactionIconOffset) return new Point(116, 99);
                return new Point(93, 72);
            }
            case "dd" -> {
                if (getFactionIconOffset) return new Point(62, 106);
                return new Point(52, 99);
            }
            case "cv" -> {
                if (getFactionIconOffset) return new Point(105, 38);
                return new Point(82, 11);
            }
            case "ca" -> {
                if (getFactionIconOffset) return new Point(149, 24);
                return new Point(126, 1);
            }
            case "ws" -> {
                if (getFactionIconOffset) return new Point(204, 21);
                return new Point(191, 4);
            }
            case "sd", "csd" -> {
                if (getFactionIconOffset) return new Point(52, 65);
                return new Point(46, 49);
            }
            case "pd", "pds" -> {
                if (getFactionIconOffset) return new Point(51, 15);
                return new Point(47, 2);
            }
            case "mf" -> {
                if (getFactionIconOffset) return new Point(9, 110);
                return new Point(3, 102);
            }
            default -> {
                return new Point(0, 0);
            }
        }
    }

    private int techFieldUnit(int x, int y, List<String> techs, int deltaX, Player player, Game game) {
        drawPAImage(x + deltaX, y, "pa_tech_unitupgrade_outlines.png");

        boolean brokenWarSun = ButtonHelper.isLawInPlay(game, "schematics");
        Set<String> unitPainted = new HashSet<>();

        // Add unit upgrade images
        if (techs != null) {
            boolean zealotsHeroActive = !game.getStoredValue("zealotsHeroTechs").isEmpty();
            List<String> zealotsTechs =
                    Arrays.asList(game.getStoredValue("zealotsHeroTechs").split("-"));
            for (String tech : techs) {
                TechnologyModel techInformation = Mapper.getTech(tech);
                if (techInformation == null || !techInformation.isUnitUpgrade()) {
                    continue;
                }

                UnitModel unit = Mapper.getUnitModelByTechUpgrade(techInformation.getAlias());
                if (unit == null) {
                    BotLogger.warning(
                            new LogOrigin(player),
                            game.getName() + " " + player.getUserName() + " Could not load unit associated with tech: "
                                    + techInformation.getAlias());
                    continue;
                }

                Point unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
                UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
                drawPAUnitUpgrade(deltaX + x + unitOffset.x, y + unitOffset.y, unitKey);
                unitPainted.add(unit.getAsyncId());

                if (zealotsHeroActive && zealotsTechs.contains(tech)) {
                    String path = "pa_tech_unitsnew_zealots_" + tech + ".png";
                    try {
                        path = ResourceHelper.getPAResource(path);
                        BufferedImage img = ImageHelper.read(path);
                        graphics.drawImage(img, deltaX + x + unitOffset.x, y + unitOffset.y, null);
                    } catch (Exception e) {
                        // Do Nothing
                        BotLogger.error(new LogOrigin(player), "Could not display active zealot tech", e);
                    }
                }
            }
        }

        // draw non-tech upgrades e.g. NRA Voltron, TF splice units
        List<UnitModel> playerUnitModels = new ArrayList<>(player.getUnitModels());
        for (UnitModel unit : playerUnitModels) {
            boolean drawUpgradeWithoutTech =
                    unit.getIsUpgrade() && unit.getRequiredTechId().isEmpty();
            drawUpgradeWithoutTech |= "fs".equals(unit.getAsyncId())
                    && player.hasUnlockedBreakthrough("nekrobt")
                    && !game.getStoredValue("valefarZ").isEmpty();
            if (drawUpgradeWithoutTech) {
                Point unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
                UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
                drawPAUnitUpgrade(deltaX + x + unitOffset.x, y + unitOffset.y, unitKey);
                unitPainted.add(unit.getAsyncId());
            }
        }

        UnitModel flagship = player.getUnitByBaseType("flagship");
        if (flagship != null && flagship.getId().startsWith("sigma_")) {
            Point unitOffset = getUnitTechOffsets(flagship.getAsyncId(), false);
            UnitKey unitKey = Mapper.getUnitKey(flagship.getAsyncId(), player.getColor());
            drawPAUnitUpgrade(deltaX + x + unitOffset.x, y + unitOffset.y, unitKey);
            unitPainted.add("fs");
            String pipsPath = "pa_leaders_pips_";
            if (flagship.getId().endsWith("_1")) {
                pipsPath += "i";
            } else if (flagship.getId().endsWith("_2")) {
                pipsPath += "ii";
            } else if (flagship.getId().endsWith("_3")) {
                pipsPath += "iii";
            }
            pipsPath += "_rdy.png";
            drawPAImage(deltaX + x + unitOffset.x + 32, y + unitOffset.y + 37, pipsPath);
        }

        boolean zealotsHeroPurged = "true".equals(game.getStoredValue("zealotsHeroPurged"));
        if (zealotsHeroPurged) {
            for (String tech : player.getPurgedTechs()) {
                TechnologyModel techInformation = Mapper.getTech(tech);
                if (techInformation == null || !techInformation.isUnitUpgrade()) {
                    continue;
                }

                UnitModel unit = Mapper.getUnitModelByTechUpgrade(techInformation.getAlias());
                if (unit == null) {
                    BotLogger.warning(
                            new LogOrigin(player),
                            game.getName() + " " + player.getUserName() + " Could not load unit associated with tech: "
                                    + techInformation.getAlias());
                    continue;
                }

                Point unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
                String path = "pa_tech_unitsnew_zealotspurged_" + tech + ".png";
                try {
                    path = ResourceHelper.getPAResource(path);
                    BufferedImage img = ImageHelper.read(path);
                    graphics.drawImage(img, deltaX + x + unitOffset.x, y + unitOffset.y, null);
                } catch (Exception e) {
                    // Do Nothing
                    BotLogger.error(new LogOrigin(player), "Could not display purged zealot tech", e);
                }
            }
        }

        if (brokenWarSun) {
            UnitModel unit = Mapper.getUnitModelByTechUpgrade("ws");
            Point unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
            UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());
            BufferedImage wsCrackImage = ImageHelper.read(ResourceHelper.getInstance()
                    .getTokenFile("agenda_publicize_weapon_schematics"
                            + (player.hasWarsunTech()
                                    ? DrawingUtil.getBlackWhiteFileSuffix(unitKey.colorID())
                                    : "_blk.png")));
            graphics.drawImage(wsCrackImage, deltaX + x + unitOffset.x, y + unitOffset.y, null);
        }

        // Add the blank warsun if player has no warsun
        if (player.getUnitsByAsyncID("ws").isEmpty()) {
            playerUnitModels.add(Mapper.getUnit("nowarsun"));
        }

        int tfMechCount = 0, tfMechIndex = 0;
        int tfFlagCount = 0, tfFlagIndex = 0;
        if (game.isTwilightsFallMode()) {
            for (UnitModel unit : playerUnitModels) {
                switch (unit.getAsyncId()) {
                    case "mf" -> tfMechCount++;
                    case "fs" -> tfFlagCount++;
                }
            }
        }
        // Add faction icons on top of upgraded or upgradable units
        for (UnitModel unit : playerUnitModels) {
            boolean isPurged = unit.getRequiredTechId().isPresent()
                    && player.getPurgedTechs().contains(unit.getRequiredTechId().get());
            Point unitFactionOffset = getUnitTechOffsets(unit.getAsyncId(), true);
            Point unitOffset = getUnitTechOffsets(unit.getAsyncId(), false);
            if (unit.getFaction().isPresent()) {
                UnitKey unitKey = Mapper.getUnitKey(unit.getAsyncId(), player.getColor());

                if ("fs".equals(unit.getAsyncId())) {
                    String unitFaction = unit.getFaction().orElse("nekro").toLowerCase();

                    if (player.hasUnlockedBreakthrough("nekrobt")) {
                        List<String> flagships = new ArrayList<>(
                                Arrays.asList(game.getStoredValue("valefarZ").split("\\|")));
                        Point offs = new Point(unitFactionOffset.x - 24, unitFactionOffset.y - 24);
                        if (flagships.size() > 1) {
                            offs.translate(0, -8);
                            unitFactionOffset.translate(0, 8);
                        }
                        for (String fsFaction : flagships) {
                            drawFactionIconImageBorder(
                                    graphics,
                                    fsFaction,
                                    deltaX + x + offs.x,
                                    y + offs.y,
                                    32,
                                    32,
                                    unitPainted.contains(unit.getAsyncId()));
                            Player p2 = game.getPlayerFromColorOrFaction(fsFaction);
                            UnitModel otherFlagship = (p2 == null ? null : p2.getUnitByBaseType("flagship"));
                            if (otherFlagship != null) {
                                addWebsiteOverlay(otherFlagship, deltaX + x + offs.x, y + offs.y, 32, 32);
                            }
                            offs.translate(16, 8);
                        }
                    } else if (game.getStoredValue("valefarZ").contains(unitFaction)) {
                        String tokenFile = ResourceHelper.getResourceFromFolder("extra/", "marker_valefarZ.png");
                        BufferedImage valefarZ = ImageHelper.read(tokenFile);
                        graphics.drawImage(
                                valefarZ, deltaX + x + unitFactionOffset.x - 10, y + unitFactionOffset.y + 10, null);
                        unitFactionOffset.translate(-24, -24);
                    } else if (game.isTwilightsFallMode() && tfFlagCount >= 2) {
                        if (!unit.getIsUpgrade()) {
                            if (tfFlagCount >= 3) unitFactionOffset.translate(0, 8);
                        } else {
                            if (tfFlagCount >= 3) unitFactionOffset.translate(0, -8);
                            switch (++tfFlagIndex) {
                                case 1 -> unitFactionOffset.translate(-24, -24);
                                case 2 -> unitFactionOffset.translate(-8, -16);
                                case 3 -> unitFactionOffset.translate(8, -8);
                                case 4 -> unitFactionOffset.translate(24, 0);
                                case 5 -> unitFactionOffset.translate(40, 8);
                            }
                        }
                    }
                }

                if ("mf".equals(unit.getAsyncId()) && game.isTwilightsFallMode() && tfMechCount >= 2) {
                    if (!unit.getIsUpgrade()) {
                        unitFactionOffset.translate(-12, 12);
                    } else {
                        tfMechIndex++;
                        switch (10 * tfMechCount + tfMechIndex) {
                            case 21 -> unitFactionOffset.translate(12, -12);
                            case 31 -> unitFactionOffset.translate(-4, -16);
                            case 32 -> unitFactionOffset.translate(16, 4);
                            case 41 -> unitFactionOffset.translate(-12, -12);
                            case 42 -> unitFactionOffset.translate(12, -12);
                            case 43 -> unitFactionOffset.translate(12, 12);
                        }
                    }
                }

                drawFactionIconImageBorder(
                        graphics,
                        unit.getFaction().get().toLowerCase(),
                        deltaX + x + unitFactionOffset.x,
                        y + unitFactionOffset.y,
                        32,
                        32,
                        unitPainted.contains(unit.getAsyncId()));
                EidolonDecal.drawIfMaximum(unitKey, deltaX + x + unitOffset.x, y + unitOffset.y, graphics, game);
            }
            if (isPurged) {
                DrawingUtil.drawRedX(
                        (Graphics2D) graphics, deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, 20, true);
            }
            // Unit Overlays
            addWebsiteOverlay(unit, deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, 32, 32);
        }
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x + deltaX - 2, y - 2, 252, 152);
        deltaX += 256;
        return deltaX;
    }

    private static void drawFactionIconImage(Graphics graphics, String faction, int x, int y, int width, int height) {
        drawFactionIconImageOpaque(graphics, faction, x, y, width, height, null, false);
    }

    private static void drawFactionIconImageBorder(
            Graphics graphics, String faction, int x, int y, int width, int height) {
        drawFactionIconImageOpaque(graphics, faction, x, y, width, height, null, true);
    }

    private static void drawFactionIconImageBorder(
            Graphics graphics, String faction, int x, int y, int width, int height, boolean border) {
        drawFactionIconImageOpaque(graphics, faction, x, y, width, height, null, border);
    }

    private void drawFactionIconImageOpaque(
            Graphics g, String faction, int x, int y, int width, int height, Float opacity) {
        drawFactionIconImageOpaque(graphics, faction, x, y, width, height, opacity, false);
    }

    private static void drawFactionIconImageOpaque(
            Graphics g, String faction, int x, int y, int width, int height, Float opacity, boolean border) {
        BufferedImage icon = DrawingUtil.getFactionIconImageScaled(faction, width, height);
        if (icon == null) {
            return;
        }

        if (border) {
            try {
                // this is over-engineered, allowing for any radius for the inner and outer strokes,
                // due to me playing around with values during debugging
                // it might be possible to make this more efficient by hard-coding the radii
                // NB: `int` is 32 bit two's complement, such that the range of positive numbers is
                // 0x00000000 - 0x7FFFFFFF, and the range of negative numbers is 0x80000000 - 0xFFFFFFFF
                // this affects Math.max, and so requires the unsigned right-shift `>>>` to compare properly
                int[] pixels = icon.getRGB(0, 0, width, height, null, 0, width);
                for (int i = 0; i < pixels.length; i++) {
                    pixels[i] &= 0xFF000000;
                }
                BufferedImage mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                mask.setRGB(0, 0, width, height, pixels, 0, width);

                final int radInner = 1;
                final int radOuter = 2 + radInner;
                BufferedImage maskInner =
                        new BufferedImage(width + 2 * radInner, height + 2 * radInner, BufferedImage.TYPE_INT_ARGB);
                BufferedImage maskOuter =
                        new BufferedImage(width + 2 * radOuter, height + 2 * radOuter, BufferedImage.TYPE_INT_ARGB);
                for (int i = -radOuter; i < width + radOuter; i++) {
                    for (int j = -radOuter; j < height + radOuter; j++) {
                        int pxInner = 0;
                        int pxOuter = 0;
                        for (int u = -radOuter; u <= radOuter; u++) {
                            for (int v = -radOuter; v <= radOuter; v++) {
                                if (i + u < 0) continue;
                                if (j + v < 0) continue;
                                if (i + u >= width) continue;
                                if (j + v >= height) continue;
                                if (u * u + v * v <= radInner * radInner) {
                                    pxInner = Math.max(pxInner, mask.getRGB(i + u, j + v) >>> 24);
                                }
                                if (u * u + v * v <= radOuter * radOuter) {
                                    pxOuter = Math.max(pxOuter, mask.getRGB(i + u, j + v) >>> 24);
                                }
                            }
                        }
                        maskOuter.setRGB(i + radOuter, j + radOuter, (pxOuter << 24) | 0x00FFFFFF);
                        if (i + radInner < 0) continue;
                        if (j + radInner < 0) continue;
                        if (i + radInner >= width + 2 * radInner) continue;
                        if (j + radInner >= height + 2 * radInner) continue;
                        maskInner.setRGB(i + radInner, j + radInner, (pxInner << 24));
                    }
                }

                Graphics2D g2 = (Graphics2D) g;
                g2.drawImage(maskOuter, x - radOuter, y - radOuter, null);
                g2.drawImage(maskInner, x - radInner, y - radInner, null);
            } catch (Exception e) {
                BotLogger.error("Could not display faction icon image border: " + faction, e);
            }
        }
        try {
            Graphics2D g2 = (Graphics2D) g;
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(icon, x, y, null);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.error("Could not display faction icon image: " + faction, e);
        }
    }

    private Rectangle drawPAImage(int x, int y, String resourceName) {
        return PaImage.draw(graphics, x, y, resourceName);
    }

    private String getUnitPath(UnitKey unit) {
        return resourceHelper.getUnitFile(unit);
    }

    private void drawPAUnitUpgrade(int x, int y, UnitKey unitKey) {
        try {
            String path = getUnitPath(unitKey);
            BufferedImage img = ImageHelper.read(path);
            graphics.drawImage(img, x, y, null);
        } catch (Exception e) {
            // Do Nothing
            BotLogger.error("Could not display UU", e);
        }
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int size) {
        PaImage.drawScaled(graphics, x, y, resourceName, size, size);
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int width, int height) {
        PaImage.drawScaled(graphics, x, y, resourceName, width, height);
    }

    private void drawPAImageOpaque(int x, int y, String resourceName, float opacity) {
        PaImage.drawOpaque((Graphics2D) graphics, x, y, resourceName, opacity);
    }

    private int objectivesSO(int y, Player player) {
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke3);

        Map<String, Player> players = game.getPlayers();
        Map<String, String> secretObjectives = Mapper.getSecretObjectivesJustNames();
        Map<String, Integer> customPublicVP = game.getCustomPublicVP();
        Set<String> secret = secretObjectives.keySet();
        graphics.setFont(Storage.getFont26());
        graphics.setColor(ColorUtil.Stage1RevealedColor);

        Map<String, List<String>> scoredSecretObjectives = new LinkedHashMap<>();
        Map<String, Integer> secrets = new LinkedHashMap<>(player.getSecrets());

        for (String id : secrets.keySet()) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
            scoredSecretObjectives.put(id, List.of(player.getUserID()));
        }
        if (player.isSearchWarrant()) {
            graphics.setColor(Color.LIGHT_GRAY);
            Map<String, Integer> revealedSecrets = new LinkedHashMap<>(secrets);
            y = displaySecretObjectives(y, new LinkedHashMap<>(), revealedSecrets, players, secretObjectives, secret);
        }
        Map<String, Integer> secretsScored = new LinkedHashMap<>(player.getSecretsScored());
        for (String id : game.getSoToPoList()) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
            secretsScored.remove(id);
        }
        Map<String, Integer> revealedSecretObjectives = new LinkedHashMap<>(secretsScored);
        for (String id : secretsScored.keySet()) {
            id = id.replace("extra1", "");
            id = id.replace("extra2", "");
            scoredSecretObjectives.put(id, List.of(player.getUserID()));
        }
        graphics.setColor(Color.RED);
        displaySecretObjectives(y, scoredSecretObjectives, revealedSecretObjectives, players, secretObjectives, secret);
        if (player.isSearchWarrant()) {
            return secretsScored.size() + player.getSecrets().size();
        }
        return secretsScored.size();
    }

    private int displaySecretObjectives(
            int y,
            Map<String, List<String>> scoredPublicObjectives,
            Map<String, Integer> revealedPublicObjectives,
            Map<String, Player> players,
            Map<String, String> publicObjectivesState,
            Set<String> po) {
        Set<String> keysToRemove = new HashSet<>();
        for (Map.Entry<String, Integer> revealed : revealedPublicObjectives.entrySet()) {
            int x = 50 + columnOffset;

            String key = revealed.getKey();
            if (!po.contains(key)) {
                continue;
            }
            String name = publicObjectivesState.get(key);
            Integer index = revealedPublicObjectives.get(key);
            if (index == null) {
                continue;
            }
            keysToRemove.add(key);

            graphics.drawString("(" + index + ") " + name, x, y + 23);

            List<String> scoredPlayerID = scoredPublicObjectives.get(key);
            if (scoredPlayerID != null) {
                drawScoreControlMarkers(x + 515, y, players, scoredPlayerID, false, true);
            }
            drawRectWithOverlay(graphics, x - 4, y - 5, 600, 38, Mapper.getSecretObjective(key));

            y += 43;
        }
        keysToRemove.forEach(revealedPublicObjectives::remove);

        return y;
    }

    private void drawScoreControlMarkers(
            int x,
            int y,
            Map<String, Player> players,
            List<String> scoredPlayerID,
            boolean multiScoring,
            boolean fixedColumn) {
        try {
            int tempX = 0;
            for (Map.Entry<String, Player> playerEntry : players.entrySet()) {
                Player player = playerEntry.getValue();
                String userID = player.getUserID();

                boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, frogPlayer);
                if (scoredPlayerID.contains(userID)) {
                    String controlID =
                            convertToGeneric ? Mapper.getControlID("gray") : Mapper.getControlID(player.getColor());
                    if (controlID.contains("null")) {
                        continue;
                    }

                    float scale = 0.55f;

                    BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);

                    if (multiScoring) {
                        int frequency = Collections.frequency(scoredPlayerID, userID);
                        for (int i = 0; i < frequency; i++) {
                            DrawingUtil.drawControlToken(
                                    graphics, controlTokenImage, player, x + tempX, y, convertToGeneric, scale);
                            tempX += scoreTokenSpacing;
                        }
                    } else {
                        DrawingUtil.drawControlToken(
                                graphics, controlTokenImage, player, x + tempX, y, convertToGeneric, scale);
                    }
                }
                if (!multiScoring && !fixedColumn) {
                    tempX += scoreTokenSpacing;
                }
            }
        } catch (Exception e) {
            BotLogger.error("Error drawing score control token markers", e);
        }
    }

    private void drawRectWithOverlay(
            Graphics g, int x, int y, int width, int height, String overlayTitle, String overlayText) {
        g.drawRect(x, y, width, height);
        addWebsiteOverlay(overlayTitle, overlayText, x, y, width, height);
    }

    private void drawRectWithOverlay(Graphics g, int x, int y, int width, int height, ModelInterface dataModel) {
        g.drawRect(x, y, width, height);
        if (dataModel == null) {
            addWebsiteOverlay("missingDataModel", "missingDataModel", x, y, width, height);
        } else {
            addWebsiteOverlay(dataModel, x, y, width, height);
        }
    }

    private void addWebsiteOverlay(String title, String text, int x, int y, int width, int height) {
        websiteOverlays.add(title, text, x, y, width, height);
    }

    private void addWebsiteOverlay(ModelInterface dataModel, int x, int y, int width, int height) {
        websiteOverlays.add(dataModel, x, y, width, height);
    }
}
