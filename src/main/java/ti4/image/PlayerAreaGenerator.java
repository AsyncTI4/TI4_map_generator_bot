package ti4.image;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CalendarHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.image.MapGenerator.VerticalAlign;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
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
import ti4.service.fow.GMService;
import ti4.service.user.AFKService;
import ti4.spring.jda.JdaService;
import ti4.website.model.WebsiteOverlay;

class PlayerAreaGenerator {

    private static final Stroke stroke1 = new BasicStroke(1.0f);
    private static final Stroke stroke2 = new BasicStroke(2.0f);
    private static final Stroke stroke3 = new BasicStroke(3.0f);
    private static final Stroke stroke4 = new BasicStroke(4.0f);
    private static final Stroke stroke5 = new BasicStroke(5.0f);
    private static final Stroke stroke8 = new BasicStroke(8.0f);
    private static final double NEGATIVE_NINETY_DEGREES_RADIANS = -1.5707963267948966;

    private final Graphics graphics;
    private final Game game;
    private final boolean isFoWPrivate;
    private final Player frogPlayer;
    private final List<WebsiteOverlay> websiteOverlays;
    private final int mapWidth;
    private final int scoreTokenSpacing;
    private final ResourceHelper resourceHelper = ResourceHelper.getInstance();
    private final Map<UnitKey, Integer> unitCount = new HashMap<>();

    PlayerAreaGenerator(
            Graphics graphics,
            Game game,
            boolean isFoWPrivate,
            Player frogPlayer,
            List<WebsiteOverlay> websiteOverlays,
            int mapWidth,
            int scoreTokenSpacing) {
        this.graphics = graphics;
        this.game = game;
        this.isFoWPrivate = isFoWPrivate;
        this.frogPlayer = frogPlayer;
        this.websiteOverlays = websiteOverlays;
        this.mapWidth = mapWidth;
        this.scoreTokenSpacing = scoreTokenSpacing;
    }

    void drawAllPlayerAreas(Point topLeftOfAllPAs) {
        graphics.setFont(Storage.getFont32());
        int x = topLeftOfAllPAs.x;
        int y = topLeftOfAllPAs.y;

        for (Player player : game.getRealAndEliminatedPlayers()) {
            Point tl = new Point(x, y);
            Rectangle rect = drawPlayerAreaOLD(player, tl);
            if (rect.height > 0) y += rect.height + 15;
        }

        String spectatorNames = game.getPlayers().values().stream()
                .filter(Player::isSpectator)
                .map(Player::getUserName)
                .collect(Collectors.joining(", "));

        if (!spectatorNames.isEmpty()) {
            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            drawString((Graphics2D) graphics, "Spectators: " + spectatorNames, x, y + 15, mapWidth);
        }
    }

    private void drawString(Graphics2D g2, String text, int x, int y, float maxWidth) {
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
    }

    private Rectangle drawPlayerAreaOLD(Player player, Point topLeft) {
        int x = topLeft.x;
        int y = topLeft.y;
        Graphics2D g2 = (Graphics2D) graphics;

        boolean convertToGeneric = isFoWPrivate && !FoWHelper.canSeeStatsOfPlayer(game, player, frogPlayer);
        if ((player.isDummy() && player.isNpc())
                || convertToGeneric
                || ("neutral".equals(player.getFaction()) && player.isDummy())) {
            return new Rectangle(topLeft);
        }

        // PAINT FACTION OR DISPLAY NAME
        List<String> teammateIDs = new ArrayList<>(player.getTeamMateIDs());
        if (!teammateIDs.getFirst().equals(player.getUserID())) {
            teammateIDs.remove(player.getUserID());
            teammateIDs.addFirst(player.getUserID());
        }

        // Faction/Colour/DisplayName
        // String factionText = player.getFactionModel() != null ? player.getFactionModel().getShortName() :
        // player.getFaction(); //TODO use this but make it look better
        String factionText = player.getFactionModel() == null
                ? StringUtils.capitalize(player.getFaction())
                : player.getFactionModel().getShortName();
        if (player.getDisplayName() != null && !"null".equalsIgnoreCase(player.getDisplayName())) {
            factionText = player.getDisplayName(); // overwrites faction
        }
        if (factionText != null && !"null".equalsIgnoreCase(factionText)) {
            factionText = "[" + factionText + "]";
        }

        ColorModel colorModel = Mapper.getColor(player.getColor());
        if (colorModel != null) {
            factionText += " (" + colorModel.getDisplayName() + ")";
        }
        if (factionText == null || "null".equalsIgnoreCase(factionText)) {
            factionText = "";
        }

        Color mainColor = ColorUtil.getPlayerMainColor(player);
        Color accentColor = ColorUtil.getPlayerAccentColor(player);

        // Player/Teammate Names
        for (String teammateID : teammateIDs) {
            User user = JdaService.jda.getUserById(teammateID);

            int leftJustified = x;
            int topOfName = y + 10;

            StringBuilder userName = new StringBuilder();
            if (!game.hideUserNames()) {
                Guild guild = game.getGuild();
                Member member = guild != null ? guild.getMemberById(teammateID) : null;
                if (member == null) {
                    member = JdaService.guildPrimary.getMemberById(teammateID);
                }
                userName.append(" ");

                if (member != null) {
                    userName.append(member.getEffectiveName());
                } else if (user != null) {
                    userName.append(user.getEffectiveName());
                } else {
                    userName.append(player.getUserName());
                }

                leftJustified += 30; // to accommodate avatar
            }
            if (AFKService.userIsAFK(teammateID)) {
                userName.append(" -- AFK");
            }

            graphics.setFont(Storage.getFont32());
            graphics.setColor(Color.WHITE);
            int usernameWidth = graphics.getFontMetrics().stringWidth(userName.toString());
            int factionTextWidth = graphics.getFontMetrics().stringWidth(factionText);
            int maxWidthForPlayerNameBeforeLeaders = 715;

            if (player.getUserID().equals(teammateID)) { // "real" player, first row
                if (factionTextWidth + usernameWidth
                        > maxWidthForPlayerNameBeforeLeaders) { // is a team, or too long, two lines
                    DrawingUtil.superDrawString(
                            graphics,
                            factionText,
                            x,
                            topOfName,
                            Color.WHITE,
                            HorizontalAlign.Left,
                            VerticalAlign.Top,
                            stroke2,
                            Color.BLACK);
                    y += 34;
                    DrawingUtil.superDrawString(
                            graphics,
                            userName.toString(),
                            leftJustified,
                            topOfName + 34,
                            Color.WHITE,
                            HorizontalAlign.Left,
                            VerticalAlign.Top,
                            stroke2,
                            Color.BLACK);
                } else { // can one-line it
                    String fullText = userName + " " + factionText;
                    DrawingUtil.superDrawString(
                            graphics,
                            fullText,
                            leftJustified,
                            topOfName,
                            Color.WHITE,
                            HorizontalAlign.Left,
                            VerticalAlign.Top,
                            stroke2,
                            Color.BLACK);
                }
            } else { // 2nd+ row, teammates - one-line it, just username
                DrawingUtil.superDrawString(
                        graphics,
                        userName.toString(),
                        leftJustified,
                        topOfName,
                        Color.WHITE,
                        HorizontalAlign.Left,
                        VerticalAlign.Top,
                        stroke2,
                        Color.BLACK);
            }

            // Avatar
            if (!game.hideUserNames()) {
                graphics.drawImage(DrawingUtil.getUserDiscordAvatar(user), x, y + 5, null);
            }

            y += 34;
        }

        if (player.getFaction() == null || "null".equals(player.getColor()) || player.getColor() == null) {
            y += 2;
            return new Rectangle(topLeft.x, topLeft.y, x - topLeft.x, y - topLeft.y);
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
            int width = mapWidth - topLeft.x;
            int height = y - topLeft.y;
            DrawingUtil.drawRectWithTwoColorGradient(g2, mainColor, accentColor, tl.x, tl.y, width, height);
            return new Rectangle(tl.x, tl.y, width, height);
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
        if (!game.isFowMode()) {
            graphics.setFont(Storage.getFont20());
            int drawX = x + 9;
            int drawY = y + 125;
            Set<Player> neighbors = player.getNeighbouringPlayers(true);
            Set<Player> guildShips = new HashSet<>();
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
                        || p2.getPromissoryNotesInPlayArea().contains("sigma_trade_convoys")) {
                    guildShips.add(p2);
                }
            }
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
        if (player.hasAbility("edict")) {
            additionalFleetSupply += player.getMahactCC().size();
        }
        if (player.hasAbility("armada")) {
            additionalFleetSupply += 2;
        }
        if (additionalFleetSupply > 0) {
            graphics.drawString("+" + additionalFleetSupply + " FS", x + 40, y + 105);
        }

        // Cards
        String acImage = "pa_cardbacks_ac.png";
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

        card = drawPAImage(x + 280, y, pnImage);
        DrawingUtil.superDrawStringCenteredDefault(
                g2, Integer.toString(player.getPnCount()), (int) card.getCenterX(), y + 75);
        DrawingUtil.drawRectWithTwoColorGradient(g2, Color.black, null, card);

        // Trade Goods
        card = drawPAImage(x + 345, y, tradeGoodImage);
        DrawingUtil.superDrawStringCenteredDefault(
                g2, Integer.toString(player.getTg()), (int) card.getCenterX(), y + 75);

        // Comms
        card = drawPAImage(x + 410, y, commoditiesImage);
        String comms = player.getCommodities() + "/" + player.getCommoditiesTotal();
        DrawingUtil.superDrawStringCenteredDefault(g2, comms, (int) card.getCenterX(), y + 75);

        // Fragments
        int urf = player.getUrf();
        int irf = player.getIrf();
        String urfImage = "pa_fragment_urf.png";
        String irfImage = "pa_fragment_irf.png";
        int xDelta = 0;
        xDelta = drawFrags(y, x, 0, urf, urfImage, xDelta);
        xDelta += 25;
        xDelta = drawFrags(y, x, 0, irf, irfImage, xDelta);

        int xDelta2 = 0;
        int hrf = player.getHrf();
        int crf = player.getCrf();
        String hrfImage = "pa_fragment_hrf.png";
        String crfImage = "pa_fragment_crf.png";
        xDelta2 = drawFrags(y + 73, x, 0, hrf, hrfImage, xDelta2);
        xDelta2 += 25;
        xDelta2 = drawFrags(y + 73, x, 0, crf, crfImage, xDelta2);

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

        // FIRST ROW RIGHT SIDE
        int xDeltaTop = 10; // starting at 10 to give a little buffer for the rectangle
        int xDeltaBottom = 10;

        // Row 1
        xDeltaTop = unitValues(player, xDeltaTop, yPlayArea);
        xDeltaTop = nombox(player, xDeltaTop, yPlayArea);
        // Row 2
        xDeltaBottom = reinforcements(player, xDeltaBottom, yPlayAreaSecondRow, unitCount);

        // EQUALIZE AND CONTINUE
        xDeltaTop = xDeltaBottom = plotCards(player, Math.max(xDeltaTop, xDeltaBottom), yPlayArea);

        // Row 1
        xDeltaTop = speakerToken(player, xDeltaTop, yPlayArea);

        // SECOND ROW RIGHT SIDE (faction tokens)
        xDeltaBottom = honorOrPathTokens(player, xDeltaBottom, yPlayAreaSecondRow);
        xDeltaBottom = crimsonRebellionTokens(player, xDeltaBottom, yPlayAreaSecondRow);
        xDeltaBottom = galvanizeTokens(player, xDeltaBottom, yPlayAreaSecondRow);
        xDeltaBottom = sleeperTokens(player, xDeltaBottom, yPlayAreaSecondRow);
        xDeltaBottom = creussWormholeTokens(player, xDeltaBottom, yPlayAreaSecondRow);
        xDeltaBottom = valefarZTokens(player, xDeltaBottom, yPlayAreaSecondRow);

        if (player.hasAbility("ancient_blueprints")) {
            xDelta = bentorBluePrintInfo(player, xDelta, yPlayArea);
        }

        if (!player.getLeaders().isEmpty() || game.isVeiledHeartMode()) {
            xDelta = leaderInfo(player, xDelta, yPlayArea, game);
        }

        if (player.getDebtTokens().values().stream().anyMatch(i -> i > 0)) {
            xDelta = debtInfo(player, xDelta, yPlayArea, game);
        }

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
        Rectangle r = new Rectangle(topLeft.x, topLeft.y, mapWidth - topLeft.x - 5, y - topLeft.y);
        DrawingUtil.drawRectWithTwoColorGradient(g2, mainColor, accentColor, r.x, r.y, r.width, r.height);
        return r;
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
        if (scColor.equals(Color.GRAY)) {
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

    private int speakerToken(Player player, int xDeltaFromRightSide, int yPlayAreaSecondRow) {
        if (player.getUserID().equals(game.getSpeakerUserID())) {
            xDeltaFromRightSide += 200;
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID(Constants.SPEAKER));
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                graphics.drawImage(bufferedImage, mapWidth - xDeltaFromRightSide, yPlayAreaSecondRow + 25, null);
            }
        }
        if (player.getUserID().equals(game.getTyrantUserID())) {
            xDeltaFromRightSide += 200;
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID("tyrant"));
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                graphics.drawImage(bufferedImage, mapWidth - xDeltaFromRightSide, yPlayAreaSecondRow + 25, null);
            }
        }
        return xDeltaFromRightSide;
    }

    private int plotCards(Player player, int xDelta, int yDelta) {
        boolean faceup = player.hasAbility("bladesorchestra");
        if (player.hasAbility("bladesorchestra") || player.hasAbility("plotsplots")) {
            xDelta += 230;

            Graphics2D g2 = (Graphics2D) graphics;
            DrawingUtil.drawRectWithTwoColorGradient(
                    g2,
                    ColorUtil.getPlayerMainColor(player),
                    ColorUtil.getPlayerAccentColor(player),
                    mapWidth - xDelta,
                    yDelta,
                    210,
                    300);

            graphics.setColor(Color.white);
            graphics.setFont(Storage.getFont20());
            yDelta += 20;

            List<Entry<String, Integer>> plots =
                    new ArrayList<>(player.getPlotCards().entrySet());
            plots.sort(Entry.comparingByValue()); // sort by number to keep a consistent and anonymous ordering
            for (Entry<String, Integer> entry : plots) {
                String alias = entry.getKey();
                Integer id = entry.getValue();
                int x = mapWidth - xDelta + 5;

                String name = faceup ? Mapper.getPlot(alias).getName() : "Plot " + id;
                DrawingUtil.superDrawString(
                        graphics, name, x, yDelta, Color.white, HorizontalAlign.Left, null, null, null);
                yDelta += 5;
                for (String faction : player.getPuppetedFactionsForPlot(alias)) {
                    Player p = game.getPlayerFromColorOrFaction(faction);
                    DrawingUtil.getAndDrawControlToken(graphics, p, x, yDelta, isFoWPrivate, 0.6f);
                    x += 40;
                }
                yDelta += 55;
            }
        }
        return xDelta;
    }

    private int displayRemainingFactionTokens(
            List<Point> points, BufferedImage img, int tokensRemaining, int xDeltaFromRight, int yDelta) {
        if (img != null) {
            int maxOffset = 0;
            for (int i = 0; i < tokensRemaining; i++) maxOffset = Math.max(maxOffset, points.get(i).x);

            xDeltaFromRight += maxOffset + img.getWidth() + 5;

            for (int i = 0; i < tokensRemaining; i++) {
                Point p = points.get(i);
                graphics.drawImage(img, mapWidth - xDeltaFromRight + p.x, yDelta + p.y, null);
            }
        }
        return xDeltaFromRight;
    }

    private int valefarZTokens(Player player, int xDeltaFromRightSide, int yDelta) {
        if (player.hasReadyBreakthrough("nekrobt")) {
            String tokenFile = ResourceHelper.getResourceFromFolder("extra/", "marker_valefarZ.png");
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            int maxTokens = 7;
            List<Point> points = new ArrayList<>();
            IntStream.range(0, maxTokens).forEach(i -> points.add(new Point(i * 35, 25 * ((i + 1) % 2))));

            int tokensUsed =
                    Arrays.asList(game.getStoredValue("valefarZ").split("\\|")).size();
            return displayRemainingFactionTokens(points, bufferedImage, 7 - tokensUsed, xDeltaFromRightSide, yDelta);
        }
        return xDeltaFromRightSide;
    }

    private int sleeperTokens(Player player, int xDeltaFromRightSide, int yDelta) {
        if (!player.hasAbility("awaken") || player.hasTech("tf-awaken")) {
            return xDeltaFromRightSide;
        }
        String sleeperFile = ResourceHelper.getInstance().getTokenFile(Constants.TOKEN_SLEEPER_PNG);
        BufferedImage bufferedImage = ImageHelper.read(sleeperFile);

        List<Point> points = new ArrayList<>();
        points.add(new Point(0, 15));
        points.add(new Point(50, 0));
        points.add(new Point(50, 50));
        points.add(new Point(100, 25));
        points.add(new Point(10, 40));

        int numToDisplay = 5 - game.getSleeperTokensPlacedCount();
        return displayRemainingFactionTokens(points, bufferedImage, numToDisplay, xDeltaFromRightSide, yDelta);
    }

    private int crimsonRebellionTokens(Player player, int xDeltaFromRightSide, int yDelta) {
        if (player.hasAbility("incursion")) {
            String breachFile = ResourceHelper.getInstance().getTokenFile(Constants.TOKEN_BREACH_ACTIVE);
            BufferedImage breachImage = ImageHelper.read(breachFile);
            int maxBreachTokens = 7;
            List<Point> points = new ArrayList<>();
            IntStream.range(0, maxBreachTokens).forEach(i -> points.add(new Point(i * 35, 25 * ((i + 1) % 2))));
            int totalBreaches = (int) game.getTileMap().values().stream()
                    .flatMap(t -> t.getUnitHolders().values().stream())
                    .flatMap(uh -> uh.getTokenList().stream())
                    .filter(tok ->
                            tok.equals(Constants.TOKEN_BREACH_ACTIVE) || tok.equals(Constants.TOKEN_BREACH_INACTIVE))
                    .count();
            if (totalBreaches > maxBreachTokens) {
                String msg = player.getRepresentation()
                        + ", there are too many Breach tokens on the board. Please review and resolve manually.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            xDeltaFromRightSide = displayRemainingFactionTokens(
                    points, breachImage, maxBreachTokens - totalBreaches, xDeltaFromRightSide, yDelta);
        }

        if (player.ownsPromissoryNote("sever")) {
            String severFile = ResourceHelper.getInstance().getTokenFile(Constants.TOKEN_SEVERED);
            BufferedImage severImage = ImageHelper.read(severFile);
            int maxSeverTokens = 1;
            List<Point> points = new ArrayList<>();
            IntStream.range(0, maxSeverTokens).forEach(i -> points.add(new Point(i * 20, 0)));
            int severtokens = (int) game.getTileMap().values().stream()
                    .flatMap(t -> t.getUnitHolders().values().stream())
                    .filter(uh -> uh.getTokenList().contains(Constants.TOKEN_SEVERED))
                    .count();

            if (severtokens > maxSeverTokens) {
                String msg = player.getRepresentation()
                        + ", there are too many Sever tokens on the board. Please review and resolve manually.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            xDeltaFromRightSide = displayRemainingFactionTokens(
                    points, severImage, maxSeverTokens - severtokens, xDeltaFromRightSide, yDelta);
        }
        return xDeltaFromRightSide;
    }

    private int galvanizeTokens(Player player, int xDeltaFromRightSide, int yDelta) {
        if (player.hasAbility("galvanize")) {
            String tokenFile = ResourceHelper.getResourceFromFolder("extra/", "marker_galvanize.png");
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            int maxGalvanizeTokens = 7;
            List<Point> points = new ArrayList<>();
            IntStream.range(0, maxGalvanizeTokens).forEach(i -> points.add(new Point(i * 20, 20 * ((i + 1) % 2))));
            int totGalvanized = game.getTileMap().values().stream()
                    .flatMap(t -> t.getUnitHolders().values().stream())
                    .mapToInt(UnitHolder::getTotalGalvanizedCount)
                    .sum();
            if (totGalvanized > maxGalvanizeTokens) {
                String msg = player.getRepresentation()
                        + ", there are too many Galvanized units on the board. Please review and resolve manually.";
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            }
            return displayRemainingFactionTokens(
                    points, bufferedImage, maxGalvanizeTokens - totGalvanized, xDeltaFromRightSide, yDelta);
        }
        return xDeltaFromRightSide;
    }

    private int honorOrPathTokens(Player player, int xDeltaFromRightSide, int yDelta) {
        if (player.getDishonorCounter() < 1
                && player.getHonorCounter() < 1
                && player.getPathTokenCounter() < 1
                && !game.isVeiledHeartMode()) {
            return xDeltaFromRightSide;
        }
        if (game.isVeiledHeartMode()) {
            DrawingUtil.superDrawStringCenteredDefault(
                    graphics,
                    "Veiled Cards: "
                            + ((game.getStoredValue("veiledCards" + player.getFaction()) + "spoof").split("_").length
                                    - 1),
                    mapWidth - xDeltaFromRightSide - 300,
                    yDelta + 50);
        } else {
            if (player.getHonorCounter() > 0) {
                DrawingUtil.superDrawStringCenteredDefault(
                        graphics,
                        "Honor Count: " + player.getHonorCounter(),
                        mapWidth - xDeltaFromRightSide - 300,
                        yDelta + 50);
            } else {
                DrawingUtil.superDrawStringCenteredDefault(
                        graphics,
                        "Path Token Count: " + player.getPathTokenCounter(),
                        mapWidth - xDeltaFromRightSide - 300,
                        yDelta + 50);
            }
            if (player.getDishonorCounter() > 0) {
                DrawingUtil.superDrawStringCenteredDefault(
                        graphics,
                        "Dishonor Count: " + player.getDishonorCounter(),
                        mapWidth - xDeltaFromRightSide - 300,
                        yDelta + 100);
            }
        }
        return xDeltaFromRightSide + 200;
    }

    private int creussWormholeTokens(Player player, int xDeltaSecondRowFromRightSide, int yPlayAreaSecondRow) {
        if (!"ghost".equalsIgnoreCase(player.getFaction())) {
            return xDeltaSecondRowFromRightSide;
        }
        boolean alphaOnMap = false;
        boolean betaOnMap = false;
        boolean gammaOnMap = false;
        String alphaID = Mapper.getTokenID("creussalpha");
        String betaID = Mapper.getTokenID("creussbeta");
        String gammaID = Mapper.getTokenID("creussgamma");
        for (Tile tile : game.getTileMap().values()) {
            Set<String> tileTokens = tile.getUnitHolders().get("space").getTokenList();
            alphaOnMap |= tileTokens.contains(alphaID);
            betaOnMap |= tileTokens.contains(betaID);
            gammaOnMap |= tileTokens.contains(gammaID);
        }
        yPlayAreaSecondRow += 25;

        xDeltaSecondRowFromRightSide += (alphaOnMap && betaOnMap && gammaOnMap ? 0 : 90);
        boolean reconstruction =
                (ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));
        boolean travelBan =
                ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban");

        if (!gammaOnMap) {
            String tokenFile = Mapper.getTokenPath(gammaID);
            if (game.isLiberationC4Mode()) {
                tokenFile = tokenFile.replace("token_creuss", "token_crimsoncreuss");
            }
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            graphics.drawImage(bufferedImage, mapWidth - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            xDeltaSecondRowFromRightSide += 40;
            yPlayAreaSecondRow += alphaOnMap || betaOnMap ? 38 : 19;
        }

        if (!betaOnMap) {
            String tokenFile = Mapper.getTokenPath(betaID);
            if (game.isLiberationC4Mode()) {
                tokenFile = tokenFile.replace("token_creuss", "token_crimsoncreuss");
            }
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            graphics.drawImage(bufferedImage, mapWidth - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            if (travelBan) {
                BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance()
                        .getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                graphics.drawImage(
                        blockedWormholeImage,
                        mapWidth - xDeltaSecondRowFromRightSide + 40,
                        yPlayAreaSecondRow + 40,
                        null);
            }
            if (reconstruction) {
                BufferedImage doubleWormholeImage = ImageHelper.readScaled(
                        ResourceHelper.getInstance().getTokenFile("token_whalpha.png"), 40.0f / 65);
                graphics.drawImage(
                        doubleWormholeImage, mapWidth - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            }
            xDeltaSecondRowFromRightSide += 40;
            yPlayAreaSecondRow += alphaOnMap || gammaOnMap ? 38 : 19;
        }

        if (!alphaOnMap) {
            String tokenFile = Mapper.getTokenPath(alphaID);
            if (game.isLiberationC4Mode()) {
                tokenFile = tokenFile.replace("token_creuss", "token_crimsoncreuss");
            }
            BufferedImage bufferedImage = ImageHelper.read(tokenFile);
            graphics.drawImage(bufferedImage, mapWidth - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            if (travelBan) {
                BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance()
                        .getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                graphics.drawImage(
                        blockedWormholeImage,
                        mapWidth - xDeltaSecondRowFromRightSide + 40,
                        yPlayAreaSecondRow + 40,
                        null);
            }
            if (reconstruction) {
                BufferedImage doubleWormholeImage = ImageHelper.readScaled(
                        ResourceHelper.getInstance().getTokenFile("token_whbeta.png"), 40.0f / 65);
                graphics.drawImage(
                        doubleWormholeImage, mapWidth - xDeltaSecondRowFromRightSide, yPlayAreaSecondRow, null);
            }
            xDeltaSecondRowFromRightSide += 40;
            yPlayAreaSecondRow += betaOnMap || gammaOnMap ? 38 : 19;
        }

        xDeltaSecondRowFromRightSide -= (alphaOnMap && betaOnMap && gammaOnMap ? 0 : 40);
        return xDeltaSecondRowFromRightSide;
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

    private int drawFrags(int y, int x, int yDelta, int urf, String urfImage, int xDelta) {
        for (int i = 0; i < urf; i++) {
            drawPAImage(x + 475 + xDelta, y + yDelta - 25, urfImage);
            xDelta += 15;
        }
        return xDelta;
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
            String resourcePath = ResourceHelper.getInstance().getPAResource(relicFileName);
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
                return Mapper.getLeader(leader1.getId())
                        .getName()
                        .compareToIgnoreCase(Mapper.getLeader(leader2.getId()).getName());
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
            if ("yssarilagent".equalsIgnoreCase(leader.getId())) {
                DrawingUtil.drawTextVertically(
                        g2, "Clever, Clever".toUpperCase(), x + deltaX + 8, y + 30, Storage.getFont14(), true);
                DrawingUtil.drawTextVertically(
                        g2, "Ssruu".toUpperCase(), x + deltaX + 23, y + 30, Storage.getFont18(), true);
            } else if (leaderModel.getShrinkName()) {
                g2.setFont(Storage.getFont16());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        g2, leaderModel.getShortName(), x + deltaX + 9, y + 30, 120, true);
            } else {
                g2.setFont(Storage.getFont18());
                DrawingUtil.drawOneOrTwoLinesOfTextVertically(
                        g2, leaderModel.getShortName(), x + deltaX + 7, y + 30, 120, true);
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

        if (player.hasAbility("imperia")) {
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

        return x + deltaX + 20;
    }

    private int debtInfo(Player player, int x, int y, Game game) {
        int deltaX = 0;
        int deltaY = 0;

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        String bankImage =
                "vaden".equalsIgnoreCase(player.getFaction()) ? "pa_ds_vaden_bank.png" : "pa_debtaccount.png";
        drawPAImage(x + deltaX, y, bankImage);

        deltaX += 24;
        deltaY += 2;

        int tokenDeltaY = 0;
        int playerCount = 0;
        int maxTokenDeltaX = 0;
        for (Entry<String, Integer> debtToken : player.getDebtTokens().entrySet()) {
            Player debtPlayer = game.getPlayerByColorID(Mapper.getColorID(debtToken.getKey()))
                    .orElse(null);
            boolean hideFactionIcon =
                    isFoWPrivate && debtPlayer != null && !FoWHelper.canSeeStatsOfPlayer(game, debtPlayer, frogPlayer);

            int tokenDeltaX = 0;
            String controlID = Mapper.getControlID(debtToken.getKey());
            if (controlID.contains("null")) {
                continue;
            }

            float scale = 0.60f;
            BufferedImage controlTokenImage = ImageHelper.readScaled(Mapper.getCCPath(controlID), scale);

            for (int i = 0; i < debtToken.getValue(); i++) {
                DrawingUtil.drawControlToken(
                        graphics,
                        controlTokenImage,
                        DrawingUtil.getPlayerByControlMarker(game.getPlayers().values(), controlID),
                        x + deltaX + tokenDeltaX,
                        y + deltaY + tokenDeltaY,
                        hideFactionIcon,
                        scale);
                tokenDeltaX += 15;
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
        graphics.setColor(Color.WHITE);
        graphics.drawRect(x - 2, y - 2, deltaX, 152);

        return x + deltaX + 10;
    }

    private int abilityInfo(Player player, int x, int y) {
        int deltaX = 10;

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
                System.out.println("Ability null: " + abilityID);
            } else {
                if (abilityFileName != null) {
                    String status = isExhaustedLocked ? "_exh" : "_rdy";
                    abilityFileName += status + ".png";
                    String resourcePath = ResourceHelper.getInstance().getPAResource(abilityFileName);

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
        int deltaX = 10;

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

    private int reinforcements(Player player, int xDeltaFromRightSide, int y, Map<UnitKey, Integer> unitMapCount) {
        Map<String, Tile> tileMap = game.getTileMap();
        int x = mapWidth - 440 - xDeltaFromRightSide;
        drawPAImage(x, y, "pa_reinforcements.png");
        if (unitMapCount.isEmpty()) {
            for (Tile tile : tileMap.values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    fillUnits(unitMapCount, unitHolder, false);
                }
            }
            for (Player player_ : game.getPlayers().values()) {
                UnitHolder nombox = player_.getNomboxTile().getSpaceUnitHolder();
                if (nombox == null) continue;
                fillUnits(unitMapCount, nombox, true);
            }
        }

        String playerColor = player.getColor();
        for (String unitID : Mapper.getUnitIDList()) {
            UnitKey unitKey = Mapper.getUnitKey(unitID, playerColor);
            if ("csd".equals(unitID)) continue;
            if ("cff".equals(unitID)) unitKey = Mapper.getUnitKey("ff", playerColor);
            if ("cgf".equals(unitID)) unitKey = Mapper.getUnitKey("gf", playerColor);

            int count = unitMapCount.getOrDefault(unitKey, 0);
            if ((player.ownsUnit("cabal_spacedock")
                            || player.ownsUnit("cabal_spacedock2")
                            || player.hasTech("tf-dimensionaltear"))
                    && "sd".equals(unitID)) {
                count += unitMapCount.getOrDefault(Mapper.getUnitKey("csd", playerColor), 0);
            }

            UnitTokenPosition reinforcementsPosition = PositionMapper.getReinforcementsPosition(unitID);

            if (reinforcementsPosition != null) {
                int unitCap = player.getUnitCap(unitID);
                if (unitCap == 0) {
                    unitCap = reinforcementsPosition.getPositionCount(unitID);
                }

                // Load voltron data
                UnitModel model = player == null ? null : player.getUnitFromUnitKey(unitKey);
                boolean voltron = model != null && "naaz_voltron".equals(model.getAlias());
                BufferedImage voltronDecal =
                        ImageHelper.read(ResourceHelper.getInstance().getDecalFile("Voltron.png"));

                int numInReinforcements = unitCap - count;
                if (player.hasUnlockedBreakthrough("freesystemsbt")
                        && model != null
                        && model.getUnitType() == UnitType.Infantry) {
                    for (String planet : player.getPlanetsAllianceMode()) {
                        UnitHolder planetUh = game.getUnitHolderFromPlanet(planet);
                        if (planetUh != null) {
                            if (planetUh.getUnitCount(UnitType.Infantry, player) > 0) {
                                numInReinforcements += 1;
                                numInReinforcements = Math.min(numInReinforcements, unitCap);
                            }
                        }
                    }
                }
                BufferedImage image = ImageHelper.read(getUnitPath(unitKey));
                String decal = player.getDecalFile(unitID);
                BufferedImage decalImage = null;
                if (decal != null) {
                    decalImage = ImageHelper.read(ResourceHelper.getInstance().getDecalFile(decal));
                }
                for (int i = 0; i < numInReinforcements; i++) {
                    Point position = reinforcementsPosition.getPosition(unitID);
                    if (position == null) break;

                    graphics.drawImage(image, x + position.x, y + position.y, null);
                    if (decalImage != null) {
                        graphics.drawImage(decalImage, x + position.x, y + position.y, null);
                    }
                    if (voltron && voltronDecal != null) {
                        graphics.drawImage(voltronDecal, x + position.x, y + position.y, null);
                    }
                }

                String unitName = unitKey.getUnitType().humanReadableName();
                if (!game.isHasEnded() && numInReinforcements < 0 && game.isCcNPlasticLimit()) {
                    String warningMessage = player.getRepresentation()
                            + " is exceeding unit plastic or cardboard limits for " + unitName
                            + ". Use buttons to remove (note that you cannot remove plastic pieces from systems with your command token in them).";
                    List<Button> removeButtons =
                            ButtonHelperModifyUnits.getRemoveThisTypeOfUnitButton(player, game, unitKey.asyncID());
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(), warningMessage, removeButtons);
                    GMService.logPlayerActivity(game, player, warningMessage);
                }

                if (numInReinforcements > -10) {
                    paintNumber(unitID, x, y, numInReinforcements, playerColor);
                }
            }
        }

        int ccCount = Helper.getCCCount(game, playerColor);
        String CC_TAG = "cc";
        UnitTokenPosition reinforcementsPosition = PositionMapper.getReinforcementsPosition(CC_TAG);
        if (reinforcementsPosition != null && playerColor != null) {
            int positionCount = reinforcementsPosition.getPositionCount(CC_TAG);
            if (!game.getStoredValue("ccLimit").isEmpty()) {
                positionCount = Integer.parseInt(game.getStoredValue("ccLimit"));
            }
            if (!game.getStoredValue("ccLimit" + playerColor).isEmpty()) {
                positionCount = Integer.parseInt(game.getStoredValue("ccLimit" + playerColor));
            }
            if (game.getPlayerFromColorOrFaction(playerColor) != null
                    && game.getPlayerFromColorOrFaction(playerColor).hasRelic("endurance_steroids")) {
                positionCount += 2;
            }
            int remainingReinforcements = positionCount - ccCount;
            if (remainingReinforcements > 0) {
                for (int i = 0; i < remainingReinforcements && i < 16; i++) {
                    try {
                        String ccID = Mapper.getCCID(playerColor);
                        Point position = reinforcementsPosition.getPosition(CC_TAG);
                        DrawingUtil.drawCCOfPlayer(graphics, ccID, x + position.x, y + position.y, 1, player, false);
                    } catch (Exception e) {
                        BotLogger.error(new LogOrigin(player), "Could not parse file for CC: " + playerColor, e);
                    }
                }
            }
            if (remainingReinforcements >= -5) {
                paintNumber(CC_TAG, x, y, remainingReinforcements, playerColor);
            }
        }
        return xDeltaFromRightSide + 450;
    }

    private static void fillUnits(
            Map<UnitKey, Integer> unitCount, UnitHolder unitHolder, boolean ignoreInfantryFighters) {
        for (UnitKey uk : unitHolder.getUnitKeys()) {
            int count = unitCount.getOrDefault(uk, 0);

            if (uk.getUnitType() == UnitType.Infantry || uk.getUnitType() == UnitType.Fighter) {
                if (ignoreInfantryFighters) continue;
                count++;
            } else {
                count += unitHolder.getUnitCount(uk);
            }
            unitCount.put(uk, count);
        }
    }

    private static boolean isWholeNumber(float number) {
        return number == Math.floor(number);
    }

    private int unitValues(Player player, int xDeltaFromRightSide, int y) {
        int verticalSpacing = 39;
        int spaceWidth = 70;
        int groundWidth = 80;
        int widthOfSection = 50 + spaceWidth + groundWidth;
        int leftSide = mapWidth - widthOfSection - xDeltaFromRightSide;
        addWebsiteOverlay(
                "Fleet Stats",
                "- Total Resources\n- Total Hit Points\n- Total Expected Hits",
                leftSide,
                y + 10,
                widthOfSection - 10,
                verticalSpacing * 4 - 10);
        int imageSize = verticalSpacing - 2;
        drawPAImageScaled(leftSide, y + verticalSpacing, "pa_resources.png", imageSize);
        drawPAImageScaled(leftSide, y + verticalSpacing * 2, "pa_health.png", imageSize);
        drawPAImageScaled(leftSide, y + verticalSpacing * 3, "pa_hit.png", imageSize);
        leftSide += verticalSpacing + 10;

        // Draw Lines
        Graphics2D g2 = (Graphics2D) graphics;
        Stroke orig = g2.getStroke();
        g2.setColor(Color.black);
        g2.setStroke(stroke2);
        g2.drawLine(leftSide + spaceWidth, y + 25, leftSide + spaceWidth, y + verticalSpacing + 5); // vertical line
        g2.drawLine(
                leftSide,
                y + verticalSpacing,
                leftSide + spaceWidth + groundWidth,
                y + verticalSpacing); // horizontal line
        g2.setColor(Color.white);
        g2.setStroke(stroke1);
        g2.drawLine(leftSide + spaceWidth, y + 25, leftSide + spaceWidth, y + verticalSpacing + 5); // vertical line
        g2.drawLine(
                leftSide,
                y + verticalSpacing,
                leftSide + spaceWidth + groundWidth,
                y + verticalSpacing); // horizontal line
        g2.setStroke(orig);

        // Draw unit values
        drawUnitValueColumn(player, "space", leftSide + (spaceWidth / 2), y);
        drawUnitValueColumn(player, "ground", leftSide + spaceWidth + (groundWidth / 2) + 2, y);
        return xDeltaFromRightSide + widthOfSection;
    }

    private void drawUnitValueColumn(Player player, String type, int xCenter, int yTop) {
        float resources = player.getTotalResourceValueOfUnits(type);
        String totResValue = isWholeNumber(resources) ? String.valueOf((int) resources) : String.valueOf(resources);
        String healthValue = String.valueOf(player.getTotalHPValueOfUnits(type));
        String combatValue = String.valueOf(player.getTotalCombatValueOfUnits(type));

        int verticalSpacing = 39;
        graphics.setFont(Storage.getFont18());
        DrawingUtil.superDrawStringCenteredDefault(graphics, StringUtils.capitalize(type), xCenter, yTop + 32);
        graphics.setFont(Storage.getFont24());
        DrawingUtil.superDrawStringCenteredDefault(graphics, totResValue, xCenter, yTop + verticalSpacing + 20);
        DrawingUtil.superDrawStringCenteredDefault(graphics, healthValue, xCenter, yTop + verticalSpacing * 2 + 20);
        DrawingUtil.superDrawStringCenteredDefault(graphics, combatValue, xCenter, yTop + verticalSpacing * 3 + 20);
    }

    private int nombox(Player player, int xDeltaFromRightSide, int y) {
        int widthOfNombox = 450;
        int x = mapWidth - widthOfNombox - xDeltaFromRightSide;
        UnitHolder unitHolder = player.getNomboxTile().getSpaceUnitHolder();
        if (unitHolder == null || unitHolder.getUnitKeys().isEmpty()) {
            return xDeltaFromRightSide;
        }

        Point infPoint = new Point(50, 75);
        Point fighterPoint = new Point(50, 125);
        Point mechPoint = new Point(100, 63);
        Point destroyerPoint = new Point(144, 63);
        Point cruiserPoint = new Point(185, 55);
        Point carrierPoint = new Point(235, 58);
        Point dreadnoughtPoint = new Point(284, 54);
        Point flagshipPoint = new Point(335, 47);
        Point warSunPoint = new Point(393, 56);

        String faction = player.getFaction();
        if (faction != null) {
            BufferedImage bufferedImage = DrawingUtil.getPlayerFactionIconImage(player);
            if (bufferedImage != null) {
                graphics.drawImage(bufferedImage, x + 178, y + 33, null);
            }
        }

        drawPAImage(x, y, "pa_nombox.png");

        Set<UnitKey> tempUnits = unitHolder.getUnitKeys();
        Set<UnitKey> units = new HashSet<>();

        for (UnitKey id : tempUnits) {
            if (id.getUnitType() == UnitType.Mech) {
                units.add(id);
            }
        }
        for (UnitKey key : units) {
            tempUnits.remove(key);
        }
        units.addAll(tempUnits);

        BufferedImage image = null;

        List<UnitType> order = List.of(
                UnitType.Mech,
                UnitType.Destroyer,
                UnitType.Cruiser,
                UnitType.Carrier,
                UnitType.Dreadnought,
                UnitType.Flagship,
                UnitType.Warsun,
                UnitType.Fighter,
                UnitType.Infantry);

        Map<UnitType, List<UnitKey>> collect = units.stream().collect(Collectors.groupingBy(UnitKey::getUnitType));
        for (UnitType orderKey : order) {
            List<UnitKey> keys = collect.get(orderKey);
            if (keys == null) {
                continue;
            }

            int countOfUnits = 0;
            for (UnitKey k : keys) {
                countOfUnits += unitHolder.getUnitCount(k);
            }
            int deltaY = 0;
            for (UnitKey unitKey : keys) {
                int unitCount = unitHolder.getUnitCount(unitKey);
                Integer bulkUnitCount = null;
                Player p = game.getPlayerFromColorOrFaction(unitKey.getColor());

                try {
                    String unitPath = getUnitPath(unitKey);
                    if (unitPath != null) {
                        if (unitKey.getUnitType() == UnitType.Fighter) {
                            unitPath = unitPath.replace(Constants.COLOR_FF, Constants.BULK_FF);
                            bulkUnitCount = unitCount;
                        } else if (unitKey.getUnitType() == UnitType.Infantry) {
                            unitPath = unitPath.replace(Constants.COLOR_GF, Constants.BULK_GF);
                            bulkUnitCount = unitCount;
                        }
                    }
                    image = ImageHelper.read(unitPath);
                } catch (Exception e) {
                    BotLogger.error(
                            new LogOrigin(player),
                            "Could not parse unit file for: " + unitKey + " in game " + game.getName(),
                            e);
                }
                if (bulkUnitCount != null && bulkUnitCount > 0) {
                    unitCount = 1;
                }
                if (image == null) {
                    BotLogger.error(new LogOrigin(player), "Could not find unit image for: " + unitKey);
                    continue;
                }

                Point position = new Point(x, y);
                boolean justNumber = false;
                switch (unitKey.getUnitType()) {
                    case Fighter -> {
                        position.translate(fighterPoint.x, fighterPoint.y);
                        justNumber = true;
                    }
                    case Infantry -> {
                        position.translate(infPoint.x, infPoint.y);
                        justNumber = true;
                    }
                    case Destroyer -> position.translate(destroyerPoint.x, destroyerPoint.y);
                    case Cruiser -> position.translate(cruiserPoint.x, cruiserPoint.y);
                    case Carrier -> position.translate(carrierPoint.x, carrierPoint.y);
                    case Dreadnought -> position.translate(dreadnoughtPoint.x, dreadnoughtPoint.y);
                    case Flagship -> position.translate(flagshipPoint.x, flagshipPoint.y);
                    case Warsun -> position.translate(warSunPoint.x, warSunPoint.y);
                    case Mech -> position.translate(mechPoint.x, mechPoint.y);
                    default -> {}
                }
                // Load voltron data
                UnitModel model = p == null ? null : p.getUnitFromUnitKey(unitKey);
                boolean voltron = model != null && "naaz_voltron".equals(model.getAlias());
                BufferedImage voltronDecal =
                        ImageHelper.read(ResourceHelper.getInstance().getDecalFile("Voltron.png"));

                BufferedImage spoopy = null;
                if (unitKey.getUnitType() == UnitType.Warsun) {
                    int chanceToSeeSpoop = CalendarHelper.isNearHalloween() ? 10 : 1000;
                    if (ThreadLocalRandom.current().nextInt(chanceToSeeSpoop) == 0) {
                        String spoopyPath = ResourceHelper.getInstance().getSpoopyFile();
                        spoopy = ImageHelper.read(spoopyPath);
                    }
                }

                if (justNumber) {
                    graphics.setFont(Storage.getFont40());
                    graphics.setColor(Color.WHITE);
                    graphics.drawString(Integer.toString(countOfUnits), position.x, position.y);
                    break;
                }
                position.y -= (countOfUnits * 7);

                Optional<BufferedImage> decal = Optional.ofNullable(p)
                        .map(player1 -> player1.getDecalFile(unitKey.asyncID()))
                        .map(decalFileName ->
                                ImageHelper.read(ResourceHelper.getInstance().getDecalFile(decalFileName)));

                for (int i = 0; i < unitCount; i++) {
                    graphics.drawImage(image, position.x, position.y + deltaY, null);
                    if (decal.isPresent()
                            && !List.of(UnitType.Fighter, UnitType.Infantry).contains(unitKey.getUnitType())) {
                        graphics.drawImage(decal.get(), position.x, position.y + deltaY, null);
                    }
                    if (spoopy != null) {
                        graphics.drawImage(spoopy, position.x, position.y + deltaY, null);
                    }
                    if (voltron) {
                        graphics.drawImage(voltronDecal, position.x, position.y + deltaY, null);
                    }

                    deltaY += 14;
                }
            }
        }
        return xDeltaFromRightSide + widthOfNombox;
    }

    private void paintNumber(String unitID, int x, int y, int reinforcementsCount, String color) {
        String id = "number_" + unitID;
        UnitTokenPosition textPosition = PositionMapper.getReinforcementsPosition(id);
        if (textPosition == null) {
            return;
        }
        Point position = textPosition.getPosition(id);

        graphics.setFont(Storage.getFont35());
        int offset = 20 - graphics.getFontMetrics().stringWidth("" + reinforcementsCount) / 2;
        if (reinforcementsCount <= 0) {
            graphics.setColor(Color.YELLOW);
        } else {
            String colorID = Mapper.getColorID(color);
            graphics.setColor(
                    "_blk.png".equalsIgnoreCase(DrawingUtil.getBlackWhiteFileSuffix(colorID))
                            ? Color.WHITE
                            : Color.BLACK);
        }
        for (int i = -2; i <= 2; i++) {
            for (int j = (i == -2 || i == 2 ? -1 : -2); j <= (i == -2 || i == 2 ? 1 : 2); j++) {
                graphics.drawString("" + reinforcementsCount, x + position.x + offset + i, y + position.y + j + 28);
            }
        }
        if (reinforcementsCount <= 0) {
            graphics.setColor(Color.RED);
        } else {
            String colorID = Mapper.getColorID(color);
            graphics.setColor(
                    "_blk.png".equalsIgnoreCase(DrawingUtil.getBlackWhiteFileSuffix(colorID))
                            ? Color.BLACK
                            : Color.WHITE);
        }
        graphics.drawString("" + reinforcementsCount, x + position.x + offset, y + position.y + 28);
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
            if (Constants.gedsDeadId.equals(player.getUserID()) || RandomHelper.isOneInX(100)) {
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
            // int totalPlayerResourcesOptimal =
            // Helper.getPlayerOptimalResourcesTotal(player, map);
            int availablePlayerInfluence = Helper.getPlayerInfluenceAvailable(player, game);
            int totalPlayerInfluence = Helper.getPlayerInfluenceTotal(player, game);
            int availablePlayerInfluenceOptimal = Helper.getPlayerOptimalInfluenceAvailable(player, game);
            // int totalPlayerInfluenceOptimal =
            // Helper.getPlayerOptimalInfluenceTotal(player, map);
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
            // DrawingUtil.drawCenteredString(graphics, String.valueOf(totalPlayerFlex), new Rectangle(x
            // + deltaX + 185, y + 109, 32, 32), Storage.getFont32());

        }

        deltaX += 156;

        boolean randomizeList = player != frogPlayer && isFoWPrivate;
        if (randomizeList) {
            Collections.shuffle(planets);
        }

        List<String> realPlanets = new ArrayList<>();
        List<String> nonTile = new ArrayList<>();
        List<String> fakePlanets = new ArrayList<>();
        for (String planet : planets) {
            PlanetModel model = Mapper.getPlanet(planet);

            Set<PlanetType> types = EnumSet.noneOf(PlanetType.class);
            if (model != null && model.getPlanetTypes() != null) types.addAll(model.getPlanetTypes());

            if (types.contains(PlanetType.FAKE)) {
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
            Point homePosition = PositionMapper.getTilePosition(homeTile.getPosition());
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

                int distance1 = ((homePosition.x - p1.x) * (homePosition.x - p1.x)
                        + (homePosition.y - p1.y) * (homePosition.y - p1.y));
                int distance2 = ((homePosition.x - p2.x) * (homePosition.x - p2.x)
                        + (homePosition.y - p2.y) * (homePosition.y - p2.y));
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
                    String techType = "";
                    for (String type : planet.getTechSpecialities()) {
                        techType += type.toLowerCase();
                    }
                    String abbrev = "";
                    switch (techType) {
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
        List<String> techs = player.getTechs();
        List<String> exhaustedTechs = player.getExhaustedTechs();
        List<String> purgedTechs = player.getPurgedTechs();

        Map<String, List<String>> techsFiltered = new HashMap<>();
        for (String tech : techs) {
            TechnologyModel techModel = Mapper.getTech(tech);
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
        purgedTechs.sort(techComparator);

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        int deltaX = 0;
        if (game.isTwilightsFallMode()) {
            deltaX = techField(x, y, techsFiltered.get("none"), exhaustedTechs, deltaX, player);
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
        deltaX = techGenSynthesis(x, y, deltaX, player, techsFiltered.get(Constants.UNIT_UPGRADE));

        if (game.isVeiledHeartMode()) {
            deltaX = VeiledHeartService.veiledField(
                    graphics, x, y, VeiledHeartService.VeiledCardType.UNIT, deltaX, player);
        }

        deltaX = techField(x, y, purgedTechs, Collections.emptyList(), deltaX, player);
        return x + deltaX + 20;
    }

    private int allBreakthroughInfo(Player player, int x, int y, Game game) {
        for (String bt : player.getBreakthroughIDs()) {
            x = breakthroughInfo(player, bt, x, y, game);
        }
        return x + 20;
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
            Color boxColor = Color.white;
            if (!unl) boxColor = Color.red;
            else if (exh) boxColor = Color.gray;
            if (unl && player.isBreakthroughActive(bt)) boxColor = new Color(19, 249, 236);

            Color textColor = Color.white;
            if (!unl || exh) textColor = Color.gray;

            String resource = model.getBackgroundResource();

            BufferedImage btBox = createPABox(name, resource, faction, boxColor, textColor, model.getShrinkName());
            drawRectWithOverlay(graphics, x, y - 3, 44, 154, model);
            graphics.drawImage(btBox, x, y - 3, null);

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
        return x + 44;
    }

    private BufferedImage createPABox(
            String displayText, String resource, String faction, Color boxOutline, Color textColor) {
        return createPABox(displayText, resource, faction, boxOutline, textColor, false);
    }

    private BufferedImage createPABox(
            String displayText, String resource, String faction, Color boxOutline, Color textColor, boolean shrink) {
        BufferedImage output = new BufferedImage(44, 154, BufferedImage.TYPE_INT_ARGB);
        BufferedImage textAndBox = new BufferedImage(44, 154, BufferedImage.TYPE_INT_ARGB);
        Graphics g = output.getGraphics();
        if (resource != null) drawPAImage(g, 2, 2, resource);
        if (faction != null) drawFactionIconImageOpaque(g, faction, 2, 109, 40, 40, 1.0f);

        Graphics2D g2 = textAndBox.createGraphics();
        AffineTransform orig = g2.getTransform();
        g2.setStroke(stroke2);
        g2.setColor(textColor);

        if (shrink) {
            g2.setFont(Storage.getFont16());
            DrawingUtil.drawOneOrTwoLinesOfTextVertically(g2, displayText, 11, 30, 120, true);
        } else {
            g2.setFont(Storage.getFont18());
            DrawingUtil.drawOneOrTwoLinesOfTextVertically(g2, displayText, 9, 30, 120, true);
        }

        g2.setColor(boxOutline);
        g2.drawRect(1, 1, 42, 152);

        g.drawImage(textAndBox, 0, 0, null);
        return output;
    }

    private int factionTechInfo(Player player, int x, int y) {
        List<String> techs = player.getNotResearchedFactionTechs();
        if (techs.isEmpty()) {
            return y;
        }

        Graphics2D g2 = (Graphics2D) graphics;
        g2.setStroke(stroke2);

        int deltaX = 20;
        deltaX = factionTechField(player, x, y, techs, deltaX);
        return x + deltaX + 20;
    }

    private int techField(int x, int y, List<String> techs, List<String> exhaustedTechs, int deltaX, Player player) {
        if (techs == null) {
            return deltaX;
        }
        boolean zealotsHeroActive = !game.getStoredValue("zealotsHeroTechs").isEmpty();
        List<String> zealotsTechs =
                Arrays.asList(game.getStoredValue("zealotsHeroTechs").split("-"));
        for (String tech : techs) {
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
                    if (player.getSingularityTechs().contains(tech)) {
                        faction = "nekro";
                    }
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
            if ("dslaner".equalsIgnoreCase(tech)) {
                DrawingUtil.drawTextVertically(
                        graphics, "" + player.getAtsCount(), x + deltaX + 15, y + 140, Storage.getFont16());
            }

            drawRectWithOverlay(graphics, x + deltaX - 2, y - 2, 44, 152, techModel);
            deltaX += 48;
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
            if (techModel.isUnitUpgrade()) {
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

    private int techGenSynthesis(int x, int y, int deltaX, Player player, List<String> techs) {
        int genSynthesisInfantry = player.getStasisInfantry();
        if ((techs == null && genSynthesisInfantry == 0) || !hasInfantryII(techs) && genSynthesisInfantry == 0) {
            return deltaX;
        }
        String techSpec = "pa_tech_techname_stasiscapsule.png";
        drawPAImage(x + deltaX, y, techSpec);
        if (genSynthesisInfantry < 20) {
            graphics.setFont(Storage.getFont35());
        } else {
            graphics.setFont(Storage.getFont30());
        }
        int centerX = 0;
        if (genSynthesisInfantry < 10) {
            centerX += 5;
        }
        graphics.drawString(String.valueOf(genSynthesisInfantry), x + deltaX + 3 + centerX, y + 148);
        drawRectWithOverlay(
                graphics,
                x + deltaX - 2,
                y - 2,
                44,
                152,
                "Gen Synthesis (Infantry II)",
                "Number of infantry to revive: " + genSynthesisInfantry);
        deltaX += 48;
        return deltaX;
    }

    private boolean hasInfantryII(List<String> techs) {
        if (techs == null) {
            return false;
        }
        for (String tech : techs) {
            TechnologyModel techInformation = Mapper.getTech(tech);
            if ("inf2".equals(techInformation.getBaseUpgrade().orElse("")) || "inf2".equals(tech)) {
                return true;
            }
        }
        return false;
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
                if (getFactionIconOffset) return new Point(5, 72);
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
                if (getFactionIconOffset) return new Point(5, 110);
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

        // Add unit upgrade images
        if (techs != null) {
            boolean zealotsHeroActive = !game.getStoredValue("zealotsHeroTechs").isEmpty();
            List<String> zealotsTechs =
                    Arrays.asList(game.getStoredValue("zealotsHeroTechs").split("-"));
            for (String tech : techs) {
                TechnologyModel techInformation = Mapper.getTech(tech);
                if (!techInformation.isUnitUpgrade()) {
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

                if (zealotsHeroActive && zealotsTechs.contains(tech)) {
                    String path = "pa_tech_unitsnew_zealots_" + tech + ".png";
                    try {
                        path = ResourceHelper.getInstance().getPAResource(path);
                        BufferedImage img = ImageHelper.read(path);
                        graphics.drawImage(img, deltaX + x + unitOffset.x, y + unitOffset.y, null);
                    } catch (Exception e) {
                        // Do Nothing
                        BotLogger.error(new LogOrigin(player), "Could not display active zealot tech", e);
                    }
                }
            }
        }

        UnitModel flagship = player.getUnitByBaseType("flagship");
        if (flagship != null && flagship.getId().startsWith("sigma_")) {
            Point unitOffset = getUnitTechOffsets(flagship.getAsyncId(), false);
            UnitKey unitKey = Mapper.getUnitKey(flagship.getAsyncId(), player.getColor());
            drawPAUnitUpgrade(deltaX + x + unitOffset.x, y + unitOffset.y, unitKey);
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
                if (!techInformation.isUnitUpgrade()) {
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
                    path = ResourceHelper.getInstance().getPAResource(path);
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
                                    ? DrawingUtil.getBlackWhiteFileSuffix(unitKey.getColorID())
                                    : "_blk.png")));
            graphics.drawImage(wsCrackImage, deltaX + x + unitOffset.x, y + unitOffset.y, null);
        }

        // Add the blank warsun if player has no warsun
        List<UnitModel> playerUnitModels = new ArrayList<>(player.getUnitModels());
        if (player.getUnitsByAsyncID("ws").isEmpty()) {
            playerUnitModels.add(Mapper.getUnit("nowarsun"));
        }
        // Add faction icons on top of upgraded or upgradable units
        for (UnitModel unit : playerUnitModels) {
            boolean isPurged = unit.getRequiredTechId().isPresent()
                    && player.getPurgedTechs().contains(unit.getRequiredTechId().get());
            Point unitFactionOffset = getUnitTechOffsets(unit.getAsyncId(), true);
            if (unit.getFaction().isPresent()) {
                boolean unitHasUpgrade = unit.getUpgradesFromUnitId().isPresent()
                        || unit.getUpgradesToUnitId().isPresent();
                boolean corsair = "mentak_cruiser3".equals(unit.getAlias());
                if (game.isFrankenGame()
                        || game.isTwilightsFallMode()
                        || corsair
                        || unitHasUpgrade
                        || "echoes".equals(player.getFactionModel().getAlias())) {
                    // Always paint the faction icon in franken
                    drawFactionIconImage(
                            graphics,
                            unit.getFaction().get().toLowerCase(),
                            deltaX + x + unitFactionOffset.x,
                            y + unitFactionOffset.y,
                            32,
                            32);
                }
                if ("naaz_voltron".equals(unit.getAlias())) {
                    // paint the special voltron decal
                    BufferedImage voltronDecal =
                            ImageHelper.read(ResourceHelper.getInstance().getDecalFile("Voltron.png"));
                    graphics.drawImage(voltronDecal, deltaX + x + unitFactionOffset.x, y + unitFactionOffset.y, null);
                }
                if ("fs".equals(unit.getAsyncId())) {
                    String unitFaction = unit.getFaction().orElse("nekro").toLowerCase();
                    if (player.hasUnlockedBreakthrough("nekrobt")) {
                        List<String> flagships = new ArrayList<>();
                        flagships.add(unitFaction);
                        flagships.addAll(
                                Arrays.asList(game.getStoredValue("valefarZ").split("\\|")));
                        Point offs = new Point(unitFactionOffset.x - 20, unitFactionOffset.y - 20);
                        for (String fsFaction : flagships) {
                            drawFactionIconImage(graphics, fsFaction, deltaX + x + offs.x, y + offs.y, 32, 32);
                            offs.translate(15, 12);
                        }
                    } else if (game.getStoredValue("valefarZ").contains(unitFaction)) {
                        String tokenFile = ResourceHelper.getResourceFromFolder("extra/", "marker_valefarZ.png");
                        BufferedImage valefarZ = ImageHelper.read(tokenFile);
                        graphics.drawImage(
                                valefarZ, deltaX + x + unitFactionOffset.x - 10, y + unitFactionOffset.y + 10, null);
                    }
                }
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
        deltaX += 270;
        return deltaX;
    }

    private void drawFactionIconImage(Graphics graphics, String faction, int x, int y, int width, int height) {
        drawFactionIconImageOpaque(graphics, faction, x, y, width, height, null);
    }

    private static void drawFactionIconImageOpaque(
            Graphics g, String faction, int x, int y, int width, int height, Float opacity) {
        try {
            BufferedImage resourceBufferedImage = DrawingUtil.getFactionIconImageScaled(faction, width, height);
            Graphics2D g2 = (Graphics2D) g;
            float opacityToSet = opacity == null ? 1.0f : opacity;
            boolean setOpacity = opacity != null && !opacity.equals(1.0f);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacityToSet));
            g2.drawImage(resourceBufferedImage, x, y, null);
            if (setOpacity) g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.error("Could not display faction icon image: " + faction, e);
        }
    }

    public static Rectangle drawPAImage(Graphics g, int x, int y, String resourceName) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            g.drawImage(resourceBufferedImage, x, y, null);
            if (resourceBufferedImage != null)
                return new Rectangle(x, y, resourceBufferedImage.getWidth(), resourceBufferedImage.getHeight());
        } catch (Exception e) {
            BotLogger.error("Could not display play area: " + resourceName, e);
        }
        return new Rectangle(new Point(x, y));
    }

    private Rectangle drawPAImage(int x, int y, String resourceName) {
        return drawPAImage(graphics, x, y, resourceName);
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
        drawPAImageScaled(graphics, x, y, resourceName, size, size);
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int width, int height) {
        drawPAImageScaled(graphics, x, y, resourceName, width, height);
    }

    public static void drawPAImageScaled(Graphics graphics, int x, int y, String resourceName, int width, int height) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.readScaled(resourcePath, width, height);
            graphics.drawImage(resourceBufferedImage, x, y, null);
        } catch (Exception e) {
            BotLogger.error("Could not display play area: " + resourceName, e);
        }
    }

    private void drawPAImageOpaque(int x, int y, String resourceName, float opacity) {
        try {
            String resourcePath = ResourceHelper.getInstance().getPAResource(resourceName);
            BufferedImage resourceBufferedImage = ImageHelper.read(resourcePath);
            Graphics2D g2 = (Graphics2D) graphics;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
            g2.drawImage(resourceBufferedImage, x, y, null);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
        } catch (Exception e) {
            BotLogger.error("Could not display play area: " + resourceName, e);
        }
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
        y = displaySecretObjectives(
                y, scoredSecretObjectives, revealedSecretObjectives, players, secretObjectives, secret);
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
            int x = 50;

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
        MapGenerator.addWebsiteOverlay(websiteOverlays, title, text, x, y, width, height);
    }

    private void addWebsiteOverlay(ModelInterface dataModel, int x, int y, int width, int height) {
        MapGenerator.addWebsiteOverlay(websiteOverlays, dataModel, x, y, width, height);
    }
}
