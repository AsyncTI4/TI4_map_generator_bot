package ti4.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisplayType;
import ti4.helpers.Storage;
import ti4.helpers.Units.UnitKey;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.ColorModel;
import ti4.model.ModelInterface;
import ti4.model.PlanetModel;
import ti4.model.StrategyCardModel;
import ti4.website.model.WebsiteOverlay;

public class MapGenerator implements AutoCloseable {

    private static final BasicStroke stroke2 = new BasicStroke(2.0f);
    private static final BasicStroke stroke3 = new BasicStroke(3.0f);
    private static final BasicStroke stroke4 = new BasicStroke(4.0f);
    private static final BasicStroke stroke5 = new BasicStroke(5.0f);
    private static final BasicStroke stroke6 = new BasicStroke(6.0f);

    private final RenderContext ctx;
    private final Graphics2D graphics;
    private final BufferedImage mainImage;
    private final GenericInteractionCreateEvent event;
    private final int scoreTokenSpacing;
    private final Game game;
    private final DisplayType displayType;
    private final DisplayType displayTypeBasic;
    private final RenderTimings timings;
    private final int width;
    private final int height;
    private final int heightForGameInfo;
    private final FogView fog;
    private final BoardLayout board;
    private final WebsiteOverlays websiteOverlays;
    private final int mapWidth;
    private final int playerAreasWidth;
    private final int stripWidth;

    MapGenerator(Game game, @Nullable DisplayType displayType, @Nullable GenericInteractionCreateEvent event) {
        this(game, displayType, event, null);
    }

    MapGenerator(
            Game game,
            @Nullable DisplayType displayType,
            @Nullable GenericInteractionCreateEvent event,
            @Nullable String requestedSegment) {
        this(
                RenderContext.build(new MapRenderRequest(game, displayType, event, requestedSegment)),
                RenderTimings.disabled());
    }

    MapGenerator(RenderContext ctx, RenderTimings timings) {
        this.ctx = ctx;
        this.timings = timings;
        game = ctx.game();
        event = ctx.event();
        displayTypeBasic = ctx.basicType();
        this.displayType = ctx.displayType();
        scoreTokenSpacing = ctx.scoreTokenSpacing();
        fog = ctx.fog();
        board = ctx.board();
        websiteOverlays = ctx.overlays();
        MapCanvasLayout canvas = ctx.canvas();
        width = canvas.width();
        height = canvas.height();
        heightForGameInfo = canvas.heightForGameInfo();
        mapWidth = canvas.mapWidth();
        playerAreasWidth = canvas.playerAreasWidth();
        stripWidth = canvas.stripWidth();

        mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        graphics = mainImage.createGraphics();
    }

    int imageWidth() {
        return width;
    }

    int imageHeight() {
        return height;
    }

    @Nullable
    String shownSegmentName() {
        return board.shownSegmentName();
    }

    Set<String> pinnedCorners() {
        return board.pinnedCorners();
    }

    int frameContentWidth() {
        return board.frameContentWidth();
    }

    Point pinnedCornerTileOrigin(String corner) {
        return board.pinnedCornerTileOrigin(corner);
    }

    boolean isInShownRegion(@Nullable String position) {
        return board.isInShownRegion(position);
    }

    BufferedImage drawCanvas() {
        paintCanvas();
        return mainImage;
    }

    List<WebsiteOverlay> overlays() {
        return websiteOverlays.asList();
    }

    private void paintCanvas() {
        timings.startTiles();
        BoardSection.draw(ctx, graphics);
        ConnectionStripSection.draw(ctx, graphics);
        timings.stopTiles();

        timings.startGraphics();
        drawImage();
    }

    private void drawImage() {
        int landscapeShift = ctx.canvas().landscapeShift();
        int y = HeaderSection.draw(ctx, graphics, landscapeShift);
        int x = BoardLayout.PLAYER_AREAS_LEFT + landscapeShift;
        int tempY = y;
        tempY = ScoreTrackSection.draw(ctx, graphics, landscapeShift, tempY + 20);
        if (PriorityTrackSection.isShown(game)) {
            tempY = PriorityTrackSection.draw(ctx, graphics, landscapeShift, tempY);
        }
        y = ObjectivesSection.draw(ctx, graphics, landscapeShift, tempY);
        y = LawsAndEventsSection.drawLaws(ctx, graphics, landscapeShift, y);
        y = LawsAndEventsSection.drawEvents(ctx, graphics, landscapeShift, y);
        if (MapSideStatsSection.drawsPlayerInfo(game, displayType, fog) && !board.isShowingSeparateFracture()) {
            playerInfo(game);
        }

        if (displayTypeBasic == DisplayType.all || displayTypeBasic == DisplayType.stats) {
            Point topLeft = new Point(x, y);
            new PlayerAreaGenerator(
                            graphics,
                            game,
                            fog.isPrivate(),
                            fog.viewer(),
                            websiteOverlays,
                            playerAreasWidth,
                            scoreTokenSpacing,
                            ctx.neighbours())
                    .drawAllPlayerAreas(topLeft);
        }
    }

    @Override
    public void close() {
        graphics.dispose();
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int size) {
        PlayerAreaGenerator.drawPAImageScaled(graphics, x, y, resourceName, size, size);
    }

    public enum HorizontalAlign {
        Left,
        Center,
        Right
    }

    public enum VerticalAlign {
        Top,
        Center,
        Bottom
    }

    private void playerInfo(Game game) {
        graphics.setFont(Storage.getFont32());
        graphics.setColor(Color.WHITE);

        int ringCount = Math.clamp(game.getRingCount(), BoardLayout.RING_MIN_COUNT, BoardLayout.RING_MAX_COUNT);
        MapSideStatLayout.Layout layout = ctx.statLayout().get();
        for (Player player : layout.statOrder()) {
            if (player.getFaction() == null || !player.isRealPlayer()) {
                continue;
            }
            if (layout.useNewSystem()) {
                if (board.shownSegment() != null && !board.framedStatFactions().contains(player.getFaction())) {
                    continue;
                }
                List<String> tiles = layout.playerStatTiles().get(player.getFaction());
                paintPlayerInfo(game, player, tiles);
            } else {
                paintPlayerInfoOld(game, player, ringCount);
            }
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        PlayerAreaGenerator.drawPAImage(graphics, x, y, resourceName);
    }

    private void paintPlayerInfo(Game game, Player player, List<String> statTiles) {
        boolean convertToGeneric = fog.shouldConvertToGeneric(player);
        if (convertToGeneric) {
            return;
        }

        // Get the map positions for each of the "stat tiles"
        if (statTiles == null || statTiles.size() < 3) return;
        Map<String, Point> points = new HashMap<>();
        for (String pos : statTiles) {
            Point p = PositionMapper.getTilePosition(pos);
            if (p == null) return;
            p = PositionMapper.getScaledTilePosition(game, pos, p.x, p.y, board.fractureYbump());
            p.translate(BoardLayout.EXTRA_X - board.frameOffsetX(), BoardLayout.EXTRA_Y - board.frameOffsetY());
            points.put(pos, p);
        }
        Point statTileMid = points.get(statTiles.get(0));
        Point statTile1 = points.get(statTiles.get(1));
        Point statTile2 = points.get(statTiles.get(2));

        int dir1 = 0, dir2 = 0, j = 0;
        for (String p : PositionMapper.getAdjacentTilePositions(statTiles.get(0))) {
            if (p.equals(statTiles.get(1))) dir1 = j;
            if (p.equals(statTiles.get(2))) dir2 = j;
            j++;
        }

        ColorModel playerColor = Mapper.getColor(player.getColor());
        float bgAlpha = 0.10f;
        Color bgColor = player.isActivePlayer() ? Color.green : (player.isPassed() ? Color.red : Color.BLACK);
        BufferedImage tint = DrawingUtil.tintedBackground(bgColor, bgAlpha);
        for (String pos : statTiles) {
            Point p = points.get(pos);
            List<Integer> adjDir = new ArrayList<>();
            List<String> adjPos = PositionMapper.getAdjacentTilePositions(pos);
            for (int i = 0; i < 6; i++) if (statTiles.contains(adjPos.get(i))) adjDir.add(i);
            BufferedImage hex = DrawingUtil.hexBorder(game.getHexBorderStyle(), playerColor, adjDir);
            graphics.drawImage(tint, p.x, p.y, null);
            graphics.drawImage(hex, p.x, p.y, null);
        }

        drawFactionIcon(player, statTileMid);
        HorizontalAlign center = HorizontalAlign.Center;
        drawUsername(game, player, statTileMid, center);
        drawFactionNameAndColorStroters(player, statTileMid, center);
        drawVictoryPoints(game, player, statTileMid, center);
        drawSecretObjectiveIcons(game, player, statTileMid);
        VerticalAlign bottom = VerticalAlign.Bottom;
        drawStrategyCardNumbers(game, player, statTileMid, center, bottom);
        // To be used for speaker and other stuff
        Point miscTile = drawCommandCounters(player, dir1, statTile1, statTile2, dir2);
        int offBoardHighlighting = drawOffBoardHighlighting(game, player, miscTile);
        drawSpeaker(player, miscTile, offBoardHighlighting);
        drawPassedActiveOrAfk(game, player, miscTile, center);
    }

    private void drawFactionIcon(Player player, Point statTileMid) {
        Point point;
        point = PositionMapper.getPlayerStats("factionicon");
        int size = 275;
        point.translate(statTileMid.x - (size / 2), statTileMid.y - (size / 2));
        DrawingUtil.drawPlayerFactionIconImageUnderlay(graphics, player, point.x, point.y, size, size);
        DrawingUtil.drawPlayerFactionIconImageOpaque(graphics, player, point.x, point.y, size, size, 0.40f);
    }

    private void drawUsername(Game game, Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        graphics.setFont(Storage.getFont32());
        String userName = player.getUserName();
        point = PositionMapper.getPlayerStats("newuserName");
        if (!game.hideUserNames()) {
            String name = userName.substring(0, Math.min(userName.length(), 15));
            DrawingUtil.superDrawString(
                    graphics,
                    name,
                    statTileMid.x + point.x,
                    statTileMid.y + point.y,
                    Color.WHITE,
                    center,
                    null,
                    stroke5,
                    Color.BLACK);
        }
    }

    private void drawFactionNameAndColorStroters(Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        graphics.setFont(Storage.getFont32());
        point = PositionMapper.getPlayerStats("newfaction");
        point.translate(statTileMid.x, statTileMid.y);
        String factionText = player.getFactionModel() == null
                ? StringUtils.capitalize(player.getFaction())
                : player.getFactionModel().getShortName();
        if (player.getDisplayName() != null && !"null".equals(player.getDisplayName())) {
            factionText = player.getDisplayName();
        }
        DrawingUtil.superDrawString(
                graphics, factionText, point.x, point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
    }

    private void drawVictoryPoints(Game game, Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        graphics.setFont(Storage.getFont32());
        String vpCount = "VP: " + player.getTotalVictoryPoints() + " / " + game.getVp();
        point = PositionMapper.getPlayerStats("newvp");
        point.translate(statTileMid.x, statTileMid.y);
        DrawingUtil.superDrawString(
                graphics, vpCount, point.x, point.y, Color.WHITE, center, null, stroke5, Color.BLACK);
    }

    private void drawSecretObjectiveIcons(Game game, Player player, Point statTileMid) {
        Point point;
        List<String> soToPoList = game.getSoToPoList();
        int unscoredSOs = player.getSecrets().size();
        int scoredSOs = (int) player.getSecretsScored().keySet().stream()
                .filter(so -> !soToPoList.contains(so))
                .count();
        int secretsEmpty = player.getMaxSOCount() - unscoredSOs - scoredSOs;
        int soOffset = (15 + 35 * player.getMaxSOCount()) / 2 - 50;

        String soHand = "pa_so-icon_hand.png";
        String soScored = "pa_so-icon_scored.png";
        String soEmpty = "pa_so-icon_empty.png";
        point = PositionMapper.getPlayerStats("newso");
        for (int i = 0; i < secretsEmpty; i++) {
            drawPAImage((point.x + statTileMid.x + soOffset), point.y + statTileMid.y, soEmpty);
            soOffset -= 35;
        }
        for (int i = 0; i < unscoredSOs; i++) {
            drawPAImage((point.x + statTileMid.x + soOffset), point.y + statTileMid.y, soHand);
            soOffset -= 35;
        }
        for (int i = 0; i < scoredSOs; i++) {
            drawPAImage((point.x + statTileMid.x + soOffset), point.y + statTileMid.y, soScored);
            soOffset -= 35;
        }
    }

    private int drawOffBoardHighlighting(Game game, Player player, Point miscTile) {
        int offBoardHighlighting = 0;
        if (displayType == DisplayType.legendaries) {
            boolean hasNanoForge = player.hasRelic("nanoforge") || player.hasRelic("absol_nanoforge");
            for (String planet : player.getPlanets()) {
                PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
                offBoardHighlighting +=
                        (custodiaVigilia.getLegendaryAbilityName() != null && game.getTileFromPlanet(planet) == null)
                                ? 1
                                : 0;
            }
            if (offBoardHighlighting >= 1) {
                String legendaryFile = ResourceHelper.getInstance().getGeneralFile("Legendary_complete.png");
                BufferedImage bufferedImage = ImageHelper.read(legendaryFile);
                if (offBoardHighlighting + (hasNanoForge ? 1 : 0) >= 2) {
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24_000.0f
                            / (offBoardHighlighting + (hasNanoForge ? 1 : 0))
                            / bufferedImage.getWidth()
                            / bufferedImage.getHeight()));
                    for (int i = 0; i < offBoardHighlighting; i++) {
                        graphics.drawImage(
                                bufferedImage,
                                miscTile.x
                                        + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2
                                        - 30
                                        + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1),
                                miscTile.y
                                        + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                        - 30
                                        + i * 60 / (offBoardHighlighting + (hasNanoForge ? 1 : 0) - 1)
                                        + (player.isSpeaker() ? 30 : 0),
                                null);
                    }
                } else {
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            }
            if (hasNanoForge) {
                String relicFile = ResourceHelper.getInstance().getGeneralFile("Relic.png");
                BufferedImage bufferedImage = ImageHelper.read(relicFile);
                if (offBoardHighlighting >= 1) {
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(24_000.0f
                            / (offBoardHighlighting + 1)
                            / bufferedImage.getWidth()
                            / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2 + 30,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    + 30
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                } else {
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
                offBoardHighlighting++;
            }
        } else if (displayType == DisplayType.wormholes && "ghost".equalsIgnoreCase(player.getFaction())) {
            boolean alphaOnMap = false;
            boolean betaOnMap = false;
            boolean gammaOnMap = false;
            String alphaID = Mapper.getTokenID("creussalpha");
            String betaID = Mapper.getTokenID("creussbeta");
            String gammaID = Mapper.getTokenID("creussgamma");
            for (Tile tile2 : game.getTileMap().values()) {
                Set<String> tileTokens = tile2.getUnitHolders().get("space").getTokenList();
                alphaOnMap |= tileTokens.contains(alphaID);
                betaOnMap |= tileTokens.contains(betaID);
                gammaOnMap |= tileTokens.contains(gammaID);
            }

            offBoardHighlighting = (alphaOnMap ? 0 : 1) + (betaOnMap ? 0 : 1) + (gammaOnMap ? 0 : 1);
            int x = miscTile.x + (BoardLayout.TILE_WIDTH - 80) / 2;
            x += (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 30 : 0);
            int y = miscTile.y + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 80) / 2 + (player.isSpeaker() ? 30 : 0);
            boolean reconstruction =
                    (ButtonHelper.isLawInPlay(game, "wormhole_recon") || ButtonHelper.isLawInPlay(game, "absol_recon"));
            boolean travelBan =
                    ButtonHelper.isLawInPlay(game, "travel_ban") || ButtonHelper.isLawInPlay(game, "absol_travelban");

            if (!gammaOnMap) {
                String tokenFile = Mapper.getTokenPath(gammaID);
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                graphics.drawImage(bufferedImage, x, y, null);
                x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
            }
            if (!betaOnMap) {
                String tokenFile = Mapper.getTokenPath(betaID);
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                graphics.drawImage(bufferedImage, x, y, null);
                if (travelBan) {
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance()
                            .getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    graphics.drawImage(blockedWormholeImage, x + 40, y + 40, null);
                }
                if (reconstruction) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(
                            ResourceHelper.getInstance().getTokenFile("token_whalpha.png"), 40.0f / 65);
                    graphics.drawImage(doubleWormholeImage, x, y, null);
                }
                x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
            }
            if (!alphaOnMap) {
                String tokenFile = Mapper.getTokenPath(alphaID);
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                graphics.drawImage(bufferedImage, x, y, null);
                if (travelBan) {
                    BufferedImage blockedWormholeImage = ImageHelper.read(ResourceHelper.getInstance()
                            .getTokenFile("agenda_wormhole_blocked" + (reconstruction ? "_half" : "") + ".png"));
                    graphics.drawImage(blockedWormholeImage, x + 40, y + 40, null);
                }
                if (reconstruction) {
                    BufferedImage doubleWormholeImage = ImageHelper.readScaled(
                            ResourceHelper.getInstance().getTokenFile("token_whbeta.png"), 40.0f / 65);
                    graphics.drawImage(doubleWormholeImage, x, y, null);
                }
                x -= (offBoardHighlighting == 3 ? 40 : 0) + (offBoardHighlighting == 2 ? 60 : 0);
            }
        } else if (displayType == DisplayType.anomalies
                && (player.ownsUnitSubstring("cabal_spacedock") || player.hasTech("tf-dimensionaltear"))) {
            UnitKey unitKey = Mapper.getUnitKey("sd", player.getColor());
            int unitNum = player.getUnitCap("sd") + player.getUnitCap("csd");
            unitNum = (unitNum == 0
                    ? PositionMapper.getReinforcementsPosition("sd").getPositionCount("sd")
                    : unitNum);
            for (Tile tile2 : game.getTileMap().values()) {
                for (UnitHolder unitHolder : tile2.getUnitHolders().values()) {
                    unitNum -= unitHolder.getUnitCount(unitKey);
                }
            }
            if (unitNum > 0) {
                int x = miscTile.x + (BoardLayout.TILE_WIDTH - 95) / 2;
                x += (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 30 : 0);
                int y = miscTile.y + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 95) / 2 + (player.isSpeaker() ? 30 : 0);
                String tokenFile = Mapper.getTokenPath("token_gravityrift.png");
                BufferedImage bufferedImage = ImageHelper.read(tokenFile);
                for (int i = 0; i < unitNum; i++) {
                    graphics.drawImage(bufferedImage, x, y, null);
                    x -= (unitNum == 3 ? 40 : 0) + (unitNum == 2 ? 60 : 0);
                }
                offBoardHighlighting += unitNum;
            }
        } else if (displayType == DisplayType.traits) {
            List<String> traitFiles = new ArrayList<>();
            for (String planet : player.getPlanets()) {
                PlanetModel custodiaVigilia = Mapper.getPlanet(planet);
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    String traitFile = "";
                    List<String> traits = planetReal.getPlanetType();

                    if ("faction".equalsIgnoreCase(planetReal.getOriginalPlanetType()) && traits.isEmpty()) {
                        if (custodiaVigilia.getFactionHomeworld() == null) {
                            traitFile = ResourceHelper.getInstance().getGeneralFile("Legendary_complete.png");
                        } else {
                            traitFile = ResourceHelper.getInstance()
                                    .getFactionFile(custodiaVigilia.getFactionHomeworld() + ".png");
                        }
                    } else if (traits.size() == 1) {
                        String t = planetReal.getPlanetType().getFirst();
                        traitFile = ResourceHelper.getInstance()
                                .getGeneralFile(("" + t.charAt(0)).toUpperCase()
                                        + t.substring(1).toLowerCase() + ".png");
                    } else if (!traits.isEmpty()) {
                        String t = "";
                        t += traits.contains("cultural") ? "C" : "";
                        t += traits.contains("hazardous") ? "H" : "";
                        t += traits.contains("industrial") ? "I" : "";
                        if ("CHI".equals(t)) {
                            traitFile =
                                    ResourceHelper.getInstance().getPlanetResource("pc_attribute_combo_CHI_big.png");
                        } else {
                            traitFile =
                                    ResourceHelper.getInstance().getPlanetResource("pc_attribute_combo_" + t + ".png");
                        }
                    }
                    traitFiles.add(traitFile);
                    offBoardHighlighting++;
                }
            }
            if (offBoardHighlighting >= 2) {
                for (int i = 0; i < offBoardHighlighting; i++) {
                    BufferedImage bufferedImage = ImageHelper.read(traitFiles.get(i));
                    if (bufferedImage == null) {
                        BotLogger.error("Could not load trait file " + traitFiles.get(i));
                        continue;
                    }
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                            24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x
                                    + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting - 1),
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting - 1)
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            } else if (offBoardHighlighting == 1) {
                BufferedImage bufferedImage = ImageHelper.read(traitFiles.getFirst());
                if (bufferedImage != null) {
                    bufferedImage = ImageHelper.scale(bufferedImage, (float)
                            Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (BoardLayout.TILE_WIDTH - bufferedImage.getWidth()) / 2,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            }
        } else if (displayType == DisplayType.techskips) {
            List<String> techFiles = new ArrayList<>();
            for (String planet : player.getPlanets()) {
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    List<String> skips = planetReal.getTechSpeciality();
                    skips.removeAll(Collections.singleton(null));
                    skips.removeAll(Collections.singleton(""));
                    if (skips.isEmpty()) {
                        continue;
                    }
                    for (String skip : skips) {
                        switch (skip.toLowerCase()) {
                            case "biotic":
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Biotic light.png"));
                                break;
                            case "cybernetic":
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Cybernetic light.png"));
                                break;
                            case "propulsion":
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Propulsion_light.png"));
                                break;
                            case "warfare":
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Warfare_light.png"));
                                break;
                            default:
                                techFiles.add(ResourceHelper.getInstance().getGeneralFile("Generic_Technology.png"));
                        }
                        offBoardHighlighting++;
                    }
                }
            }
            if (offBoardHighlighting >= 2) {
                for (int i = 0; i < offBoardHighlighting; i++) {
                    BufferedImage bufferedImage = ImageHelper.read(techFiles.get(i));
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                            24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x
                                    + (345 - bufferedImage.getWidth()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting - 1),
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    - 30
                                    + i * 60 / (offBoardHighlighting - 1)
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                }
            } else if (offBoardHighlighting == 1) {
                BufferedImage bufferedImage = ImageHelper.read(techFiles.getFirst());
                bufferedImage = ImageHelper.scale(bufferedImage, (float)
                        Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                        miscTile.y
                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                + (player.isSpeaker() ? 30 : 0),
                        null);
            }
        } else if (displayType == DisplayType.attachments) {
            Map<String, String> attachFiles = new HashMap<>();
            Map<String, Integer> attachCount = new HashMap<>();
            for (String planet : player.getPlanets()) {
                if (game.getTileFromPlanet(planet) == null) {
                    Planet planetReal = game.getPlanetsInfo().get(planet);
                    List<String> attach = new ArrayList<>(planetReal.getAttachments());
                    attach.removeAll(Collections.singleton(null));
                    attach.removeAll(Collections.singleton(""));
                    if (attach.isEmpty()) {
                        continue;
                    }
                    attachFiles.put(planet, ResourceHelper.getInstance().getGeneralFile("misc_chevrons_basic.png"));
                    if (attach.contains("attachment_tombofemphidia.png")) {
                        attachFiles.put(planet, ResourceHelper.getInstance().getGeneralFile("misc_chevrons_toe.png"));
                    }
                    attachCount.put(planet, attach.size());
                    offBoardHighlighting++;
                }
            }
            if (offBoardHighlighting >= 2) {
                for (Map.Entry<String, String> entry : attachFiles.entrySet()) {
                    String planet = entry.getKey();
                    BufferedImage bufferedImage = ImageHelper.read(entry.getValue());
                    bufferedImage = ImageHelper.scale(bufferedImage, (float) Math.sqrt(
                            24_000.0f / offBoardHighlighting / bufferedImage.getWidth() / bufferedImage.getHeight()));
                    graphics.drawImage(
                            bufferedImage,
                            miscTile.x + (345 - bufferedImage.getWidth()) / 2 - 30,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                    - 30
                                    + (player.isSpeaker() ? 30 : 0),
                            null);
                    if (attachCount.get(planet) > 1) {
                        graphics.setColor(Color.WHITE);
                        graphics.fillOval(
                                miscTile.x + (345 - 80) / 2 - 30,
                                miscTile.y
                                        + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                        - 30
                                        + (player.isSpeaker() ? 30 : 0),
                                80,
                                80);
                        graphics.setColor(Color.BLACK);
                        graphics.fillOval(
                                miscTile.x + (345 - 72) / 2 - 30,
                                miscTile.y
                                        + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                        - 30
                                        + (player.isSpeaker() ? 30 : 0)
                                        + 4,
                                72,
                                72);
                        graphics.setColor(Color.WHITE);
                        DrawingUtil.drawCenteredString(
                                graphics,
                                "" + attachCount.get(planet),
                                new Rectangle(
                                        miscTile.x + (345 - 80) / 2 - 30,
                                        miscTile.y
                                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                                - 30
                                                + (player.isSpeaker() ? 30 : 0),
                                        80,
                                        80),
                                Storage.getFont48());
                    }
                }
            } else if (offBoardHighlighting == 1) {
                String planet = attachFiles.keySet().iterator().next();

                BufferedImage bufferedImage = ImageHelper.read(attachFiles.get(planet));
                bufferedImage = ImageHelper.scale(bufferedImage, (float)
                        Math.sqrt(24_000.0f / bufferedImage.getWidth() / bufferedImage.getHeight()));
                graphics.drawImage(
                        bufferedImage,
                        miscTile.x + (345 - bufferedImage.getWidth()) / 2,
                        miscTile.y
                                + (BoardLayout.SPACE_FOR_TILE_HEIGHT - bufferedImage.getHeight()) / 2
                                + (player.isSpeaker() ? 30 : 0),
                        null);
                if (attachCount.get(planet) > 1) {
                    graphics.setColor(Color.WHITE);
                    graphics.fillOval(
                            miscTile.x + (345 - 80) / 2,
                            miscTile.y + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2 + (player.isSpeaker() ? 30 : 0),
                            80,
                            80);
                    graphics.setColor(Color.BLACK);
                    graphics.fillOval(
                            miscTile.x + (345 - 72) / 2,
                            miscTile.y
                                    + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                    + (player.isSpeaker() ? 30 : 0)
                                    + 4,
                            72,
                            72);
                    graphics.setColor(Color.WHITE);
                    DrawingUtil.drawCenteredString(
                            graphics,
                            "" + attachCount.get(planet),
                            new Rectangle(
                                    miscTile.x + (345 - 80) / 2,
                                    miscTile.y
                                            + (BoardLayout.SPACE_FOR_TILE_HEIGHT - 16) / 2
                                            + (player.isSpeaker() ? 30 : 0),
                                    80,
                                    80),
                            Storage.getFont48());
                }
            }
        }
        return offBoardHighlighting;
    }

    private Point drawCommandCounters(Player player, int dir1, Point statTile1, Point statTile2, int dir2) {
        Point point;
        Point miscTile;
        graphics.setFont(Storage.getFont32());
        String ccID = Mapper.getCCID(player.getColor());
        String fleetCCID = Mapper.getFleetCCID(player.getColor());
        point = PositionMapper.getPlayerStats("newcc");
        int dir = dir1;
        if (dir1 == 1 || dir1 == 2 || dir1 == 4 || dir1 == 5) {
            point.translate(statTile1.x, statTile1.y);
            miscTile = statTile2;
        } else {
            dir = dir2;
            point.translate(statTile2.x, statTile2.y);
            miscTile = statTile1;
        }
        boolean rightAlign = false;
        switch (dir) {
            case 0, 1, 2, 3 -> point.translate(60, 45); // centered vertically, coming from the left side
            case 4, 5 -> {
                point.translate(210, 45); // centered vertically
                rightAlign = true; // coming from the left side
            }
        }

        DrawingUtil.drawCCOfPlayer(graphics, ccID, point.x, point.y, player.getTacticalCC(), player, false, rightAlign);
        drawFleetCCOfPlayer(graphics, fleetCCID, point.x, point.y + 65, player, rightAlign);
        DrawingUtil.drawCCOfPlayer(
                graphics, ccID, point.x, point.y + 130, player.getStrategicCC(), player, false, rightAlign);

        // Additional FS
        int additionalFleetSupply = 0;
        String addFS = "";

        if (player.hasAbility("edict") || player.hasAbility("edict_y")) {
            additionalFleetSupply += player.getMahactCC().size();
        }
        if (player.hasAbility("armada")) additionalFleetSupply += 2;
        if (additionalFleetSupply > 0) addFS = "*";

        // Draw Numbers
        HorizontalAlign align = HorizontalAlign.Left;
        List<String> reps = Arrays.asList(player.getCCRepresentation().split("/"));
        graphics.setFont(Storage.getFont28());
        point.translate(rightAlign ? 58 : -3, 32);
        String fleetCCs = player.getFleetCC() + additionalFleetSupply + addFS;
        DrawingUtil.superDrawString(
                graphics, reps.get(0), point.x, point.y, Color.WHITE, align, null, stroke4, Color.BLACK);
        DrawingUtil.superDrawString(
                graphics, fleetCCs, point.x, point.y + 65, Color.WHITE, align, null, stroke4, Color.BLACK);
        DrawingUtil.superDrawString(
                graphics, reps.get(2), point.x, point.y + 130, Color.WHITE, align, null, stroke4, Color.BLACK);
        return miscTile;
    }

    private void drawStrategyCardNumbers(
            Game game, Player player, Point statTileMid, HorizontalAlign center, VerticalAlign bottom) {
        Point point;
        graphics.setFont(Storage.getFont80());
        int scsize = 96;
        List<Integer> playerSCs = new ArrayList<>(player.getSCs());
        if (player.hasTheZeroToken()) playerSCs.add(0);
        if (player.hasAbility("patience")) {
            playerSCs.add(9);
        }
        Collections.sort(playerSCs);

        point = PositionMapper.getPlayerStats("newsc");
        point.translate(statTileMid.x, statTileMid.y);
        point.translate(-1 * (scsize / 2) * (playerSCs.size() - 1), -1 * (scsize / 2));

        for (int sc : playerSCs) {
            StrategyCardModel scModel =
                    game.getStrategyCardModelByInitiative(sc).orElse(null);
            if (sc == 0) {
                drawPAImageScaled(point.x, point.y, "pa_telepathic.png", scsize);
                point.translate(scsize, 0);
            } else {
                int fontYoffset = (scsize / 2) + 25;
                DrawingUtil.superDrawString(
                        graphics,
                        Integer.toString(sc),
                        point.x,
                        point.y + fontYoffset,
                        ColorUtil.getSCColor(sc, game),
                        center,
                        bottom,
                        stroke6,
                        Color.BLACK);
                if (scModel != null) {
                    addWebsiteOverlay(scModel, point.x - 20, point.y + 20, 40, 50);
                    // graphics.drawRect(point.x - 20, point.y + 20, 40, 50); //debug
                }
                point.translate(scsize, 0);
            }
        }
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont32());
    }

    private void drawPassedActiveOrAfk(Game game, Player player, Point miscTile, HorizontalAlign center) {
        Point point;
        String activePlayerID = game.getActivePlayerID();
        String phase = game.getPhaseOfGame();
        if (player.isPassed()) {
            point = PositionMapper.getPlayerStats("newpassed");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    graphics, "PASSED", point.x, point.y, ColorUtil.PassedColor, center, null, stroke4, Color.BLACK);
        } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
            point = PositionMapper.getPlayerStats("newpassed");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    graphics, "ACTIVE", point.x, point.y, ColorUtil.ActiveColor, center, null, stroke4, Color.BLACK);
        }
        if (player.isAFK()) {
            point = PositionMapper.getPlayerStats("newafk");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    graphics, "AFK", point.x, point.y, Color.gray, center, null, stroke4, Color.BLACK);
        }
        graphics.setColor(Color.WHITE);
    }

    private void drawSpeaker(Player player, Point miscTile, int offBoardHighlighting) {
        Point point;
        if (player.isSpeaker()) {
            String speakerID = Mapper.getTokenID(Constants.SPEAKER);
            String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
            BufferedImage img = ImageHelper.read(speakerFile);
            if (img != null) {
                point = PositionMapper.getPlayerStats("newspeaker");
                point.translate(miscTile.x - (img.getWidth() / 2), miscTile.y - (img.getHeight() / 2));
                graphics.drawImage(img, point.x, point.y - (offBoardHighlighting > 0 ? 30 : 0), null);
            }
        }
        if (player.isTyrant()) {
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID("tyrant"));
            BufferedImage img = ImageHelper.read(speakerFile);
            if (img != null) {
                point = PositionMapper.getPlayerStats("newspeaker");
                point.translate(miscTile.x - (img.getWidth() / 2), miscTile.y - (img.getHeight() / 2));
                graphics.drawImage(img, point.x, point.y - (offBoardHighlighting > 0 ? 30 : 0), null);
            }
        }
    }

    private void paintPlayerInfoOld(Game game, Player player, int ringCount) {
        int deltaX, deltaSplitX = 0;
        int deltaY, deltaSplitY = 0;

        String playerStatsAnchor = player.getPlayerStatsAnchorPosition();
        if (playerStatsAnchor != null) {
            Point anchorProjectedPoint = PositionMapper.getTilePosition(playerStatsAnchor);
            if (anchorProjectedPoint != null) {
                Point playerStatsAnchorPoint = PositionMapper.getScaledTilePosition(
                        game, playerStatsAnchor, anchorProjectedPoint.x, anchorProjectedPoint.y, board.fractureYbump());
                playerStatsAnchorPoint.translate(-board.frameOffsetX(), -board.frameOffsetY());
                Integer anchorLocationIndex =
                        PositionMapper.getRingSideNumberOfTileID(player.getPlayerStatsAnchorPosition());
                anchorLocationIndex = anchorLocationIndex == null ? 0 : anchorLocationIndex - 1;
                boolean isCorner = playerStatsAnchor.equals(
                        PositionMapper.getTileIDAtCornerPositionOfRing(ringCount, anchorLocationIndex + 1));
                if (anchorLocationIndex == 0 && isCorner) { // North Corner
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X + 80;
                    deltaY = playerStatsAnchorPoint.y - 80;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 0) { // North East
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X + BoardLayout.SPACE_FOR_TILE_HEIGHT;
                    deltaY = playerStatsAnchorPoint.y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 1) { // East
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 2 && isCorner) { // South East Corner
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 2) { // South East
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y + 100;
                } else if (anchorLocationIndex == 3 && isCorner) { // South Corner
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + 360 + BoardLayout.EXTRA_Y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 3) { // South West
                    deltaX = playerStatsAnchorPoint.x;
                    deltaY = playerStatsAnchorPoint.y + 250 + BoardLayout.EXTRA_Y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 4) { // West
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 5 && isCorner) { // North West Corner
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 5) { // North West
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y - 100;
                    deltaSplitX = 200;
                } else return;
            } else return;
        } else return;

        String userName = player.getUserName();

        boolean convertToGeneric = fog.shouldConvertToGeneric(player);
        if (convertToGeneric) {
            return;
        }

        // PAINT USERNAME
        Point point = PositionMapper.getPlayerStats(Constants.STATS_USERNAME);
        if (!game.hideUserNames()) {
            graphics.drawString(
                    userName.substring(0, Math.min(userName.length(), 11)), point.x + deltaX, point.y + deltaY);
        }

        // PAINT FACTION
        point = PositionMapper.getPlayerStats(Constants.STATS_FACTION);
        String factionText = player.getFaction();
        if (player.getDisplayName() != null && !"null".equals(player.getDisplayName())) {
            factionText = player.getDisplayName();
        }
        graphics.drawString(StringUtils.capitalize(factionText), point.x + deltaX, point.y + deltaY);

        // PAINT COLOR
        point = PositionMapper.getPlayerStats(Constants.STATS_COLOR);
        graphics.drawString(player.getColor(), point.x + deltaX, point.y + deltaY);

        // PAIN VICTORY POINTS
        int vpCount = player.getTotalVictoryPoints();
        point = PositionMapper.getPlayerStats(Constants.STATS_VP);
        graphics.drawString("VP: " + vpCount, point.x + deltaX, point.y + deltaY);

        // PAINT SO ICONS
        int totalSecrets = player.getSecrets().size();
        Set<String> soSet = player.getSecretsScored().keySet();
        int soOffset = 0;
        String soHand = "pa_so-icon_hand.png";
        String soScored = "pa_so-icon_scored.png";
        point = PositionMapper.getPlayerStats(Constants.STATS_SO);
        for (int i = 0; i < totalSecrets; i++) {
            drawPAImage((point.x + deltaX + soOffset), point.y + deltaY, soHand);
            soOffset += 25;
        }
        List<String> soToPoList = game.getSoToPoList();
        for (String soID : soSet) {
            if (!soToPoList.contains(soID)) {
                drawPAImage((point.x + deltaX + soOffset), point.y + deltaY, soScored);
                soOffset += 25;
            }
        }

        // PAINT SC#
        List<Integer> playerSCs = new ArrayList<>(player.getSCs());
        Collections.sort(playerSCs);
        int count = 0;
        for (int sc : playerSCs) {
            String scText = sc == 0 ? " " : Integer.toString(sc);
            scText = Game.getSCNumberIfNaaluInPlay(player, scText);
            graphics.setColor(ColorUtil.getSCColor(sc, game));
            graphics.setFont(Storage.getFont64());
            point = PositionMapper.getPlayerStats(Constants.STATS_SC);
            if (sc != 0) {
                graphics.drawString(scText, point.x + deltaX + 64 * count, point.y + deltaY);
            }
            count++;
        }

        // PAINT CCs
        graphics.setColor(Color.WHITE);
        graphics.setFont(Storage.getFont32());
        String ccID = Mapper.getCCID(player.getColor());
        String fleetCCID = Mapper.getFleetCCID(player.getColor());
        point = PositionMapper.getPlayerStats(Constants.STATS_CC);
        int x = point.x + deltaX;
        int y = point.y + deltaY;
        if (deltaSplitX != 0) {
            deltaSplitY = point.y;
        }

        DrawingUtil.drawCCOfPlayer(
                graphics, ccID, x + deltaSplitX, y - deltaSplitY, player.getTacticalCC(), player, false, false);
        drawFleetCCOfPlayer(graphics, fleetCCID, x + deltaSplitX, y + 65 - deltaSplitY, player, false);
        DrawingUtil.drawCCOfPlayer(
                graphics, ccID, x + deltaSplitX, y + 130 - deltaSplitY, player.getStrategicCC(), player, false, false);

        // PAINT SPEAKER
        if (player.isSpeaker()) {
            String speakerID = Mapper.getTokenID(Constants.SPEAKER);
            String speakerFile = ResourceHelper.getInstance().getTokenFile(speakerID);
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                point = PositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                int negativeDelta = 0;
                graphics.drawImage(
                        bufferedImage,
                        point.x + deltaX + deltaSplitX + negativeDelta,
                        point.y + deltaY - deltaSplitY,
                        null);
                graphics.setColor(Color.WHITE);
            }
        }
        if (player.isTyrant()) {
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID("tyrant"));
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                point = PositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                int negativeDelta = 0;
                graphics.drawImage(
                        bufferedImage,
                        point.x + deltaX + deltaSplitX + negativeDelta,
                        point.y + deltaY - deltaSplitY,
                        null);
                graphics.setColor(Color.WHITE);
            }
        }
        String activePlayerID = game.getActivePlayerID();
        String phase = game.getPhaseOfGame();
        if (player.isPassed()) {
            point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
            graphics.setColor(ColorUtil.PassedColor);
            graphics.drawString("PASSED", point.x + deltaX, point.y + deltaY);
            graphics.setColor(Color.WHITE);
        } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
            point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
            graphics.setColor(ColorUtil.ActiveColor);
            graphics.drawString("ACTIVE", point.x + deltaX + 4, point.y + deltaY);

            if (player.isAFK()) {
                graphics.setColor(Color.GRAY);
                graphics.drawString("(AFK)", point.x + deltaX + 124, point.y + deltaY);
            }
            graphics.setColor(Color.WHITE);
        } else if (player.isAFK()) {
            point = PositionMapper.getPlayerStats(Constants.STATS_PASSED);
            graphics.setColor(Color.GRAY);
            graphics.drawString("AFK", point.x + deltaX + 4, point.y + deltaY);
            graphics.setColor(Color.WHITE);
        }
    }

    private static void drawFleetCCOfPlayer(Graphics graphics, String ccID, int x, int y, Player player) {
        drawFleetCCOfPlayer(graphics, ccID, x, y, player, true);
    }

    private static void drawFleetCCOfPlayer(
            Graphics graphics, String ccID, int x, int y, Player player, boolean rightAlign) {
        String ccPath = Mapper.getCCPath(ccID);
        int ccCount = player.getFleetCC();
        boolean hasArmada = player.hasAbility("armada");
        List<String> mahactCC = player.getMahactCC();
        boolean hasMahactCCs =
                !player.getMahactCC().isEmpty() && (player.hasAbility("edict") || player.hasAbility("edict_y"));

        try {
            BufferedImage ccImage = ImageHelper.read(ccPath);
            BufferedImage blankCC = ImageHelper.read(Mapper.getCCPath("command_blank.png"));
            int delta = rightAlign ? -20 : 20;

            // DRAW TWO ARMADA TOKENS
            if (hasArmada) {
                BufferedImage armadaLowerCCImage =
                        ImageHelper.read(Mapper.getCCPath(Mapper.getCCID(player.getColor())));
                BufferedImage armadaCCImage = ImageHelper.read(Mapper.getCCPath("fleet_armada.png"));
                for (int i = 0; i < 2; i++) {
                    graphics.drawImage(armadaLowerCCImage, x, y, null);
                    graphics.drawImage(armadaCCImage, x, y, null);
                    x += delta;
                }
                x += delta;
            }

            if (ccCount == 0 && !hasArmada && !hasMahactCCs) {
                ccCount = 1;
                ccImage = blankCC;
            }

            // DRAW FLEET TOKENS
            for (int i = 0; i < ccCount; i++) {
                graphics.drawImage(ccImage, x, y, null);
                x += delta;
            }

            if (hasMahactCCs) {
                if (hasArmada || ccCount >= 1) {
                    x += delta;
                }
                for (String ccColor : mahactCC) {
                    String fleetCCID = Mapper.getCCPath(Mapper.getFleetCCID(ccColor));
                    BufferedImage ccImageExtra = ImageHelper.readScaled(fleetCCID, 1.0f);
                    graphics.drawImage(ccImageExtra, x, y, null);
                    x += delta;
                }
            }
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(player), "Ignored exception during map generation", e);
        }
    }

    private void addWebsiteOverlay(String overlayTitle, String overlayText, int x, int y, int width, int height) {
        websiteOverlays.add(overlayTitle, overlayText, x, y, width, height);
    }

    private void addWebsiteOverlay(ModelInterface dataModel, int x, int y, int width, int height) {
        websiteOverlays.add(dataModel, x, y, width, height);
    }

    String getGameName() {
        return game.getName();
    }
}
