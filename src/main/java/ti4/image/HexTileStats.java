package ti4.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.image.MapGenerator.VerticalAlign;
import ti4.model.ColorModel;
import ti4.model.StrategyCardModel;

final class HexTileStats {

    private final RenderContext ctx;
    private final Graphics2D g2;
    private final FogView fog;
    private final BoardLayout board;
    private final OffBoardHighlights highlights;

    HexTileStats(RenderContext ctx, Graphics2D g2) {
        this.ctx = ctx;
        this.g2 = g2;
        this.fog = ctx.fog();
        this.board = ctx.board();
        this.highlights = OffBoardHighlights.forDisplayType(ctx.displayType());
    }

    void paintPlayerInfo(Game game, Player player, List<String> statTiles) {
        boolean convertToGeneric = fog.shouldConvertToGeneric(player);
        if (convertToGeneric) {
            return;
        }

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
            g2.drawImage(tint, p.x, p.y, null);
            g2.drawImage(hex, p.x, p.y, null);
        }

        drawFactionIcon(player, statTileMid);
        HorizontalAlign center = HorizontalAlign.Center;
        drawUsername(game, player, statTileMid, center);
        drawFactionNameAndColorStroters(player, statTileMid, center);
        drawVictoryPoints(game, player, statTileMid, center);
        drawSecretObjectiveIcons(game, player, statTileMid);
        VerticalAlign bottom = VerticalAlign.Bottom;
        drawStrategyCardNumbers(game, player, statTileMid, center, bottom);

        Point miscTile = drawCommandCounters(player, dir1, statTile1, statTile2, dir2);
        int offBoardHighlighting = highlights.draw(g2, game, player, miscTile);
        drawSpeaker(player, miscTile, offBoardHighlighting);
        drawPassedActiveOrAfk(game, player, miscTile, center);
    }

    private void drawFactionIcon(Player player, Point statTileMid) {
        Point point;
        point = PositionMapper.getPlayerStats("factionicon");
        int size = 275;
        point.translate(statTileMid.x - (size / 2), statTileMid.y - (size / 2));
        DrawingUtil.drawPlayerFactionIconImageUnderlay(g2, player, point.x, point.y, size, size);
        DrawingUtil.drawPlayerFactionIconImageOpaque(g2, player, point.x, point.y, size, size, 0.40f);
    }

    private void drawUsername(Game game, Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        g2.setFont(Storage.getFont32());
        String userName = player.getUserName();
        point = PositionMapper.getPlayerStats("newuserName");
        if (!game.hideUserNames()) {
            String name = userName.substring(0, Math.min(userName.length(), 15));
            DrawingUtil.superDrawString(
                    g2,
                    name,
                    statTileMid.x + point.x,
                    statTileMid.y + point.y,
                    Color.WHITE,
                    center,
                    null,
                    Strokes.STROKE_5,
                    Color.BLACK);
        }
    }

    private void drawFactionNameAndColorStroters(Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        g2.setFont(Storage.getFont32());
        point = PositionMapper.getPlayerStats("newfaction");
        point.translate(statTileMid.x, statTileMid.y);
        String factionText = player.getFactionModel() == null
                ? StringUtils.capitalize(player.getFaction())
                : player.getFactionModel().getShortName();
        if (player.getDisplayName() != null && !"null".equals(player.getDisplayName())) {
            factionText = player.getDisplayName();
        }
        DrawingUtil.superDrawString(
                g2, factionText, point.x, point.y, Color.WHITE, center, null, Strokes.STROKE_5, Color.BLACK);
    }

    private void drawVictoryPoints(Game game, Player player, Point statTileMid, HorizontalAlign center) {
        Point point;
        g2.setFont(Storage.getFont32());
        String vpCount = "VP: " + player.getTotalVictoryPoints() + " / " + game.getVp();
        point = PositionMapper.getPlayerStats("newvp");
        point.translate(statTileMid.x, statTileMid.y);
        DrawingUtil.superDrawString(
                g2, vpCount, point.x, point.y, Color.WHITE, center, null, Strokes.STROKE_5, Color.BLACK);
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

    private Point drawCommandCounters(Player player, int dir1, Point statTile1, Point statTile2, int dir2) {
        Point point;
        Point miscTile;
        g2.setFont(Storage.getFont32());
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
            case 0, 1, 2, 3 -> point.translate(60, 45);
            case 4, 5 -> {
                point.translate(210, 45);
                rightAlign = true;
            }
        }

        DrawingUtil.drawCCOfPlayer(g2, ccID, point.x, point.y, player.getTacticalCC(), player, false, rightAlign);
        FleetCommandCounters.draw(g2, fleetCCID, point.x, point.y + 65, player, rightAlign);
        DrawingUtil.drawCCOfPlayer(
                g2, ccID, point.x, point.y + 130, player.getStrategicCC(), player, false, rightAlign);

        int additionalFleetSupply = 0;
        String addFS = "";

        if (player.hasAbility("edict") || player.hasAbility("edict_y")) {
            additionalFleetSupply += player.getMahactCC().size();
        }
        if (player.hasAbility("armada")) additionalFleetSupply += 2;
        if (additionalFleetSupply > 0) addFS = "*";

        HorizontalAlign align = HorizontalAlign.Left;
        List<String> reps = Arrays.asList(player.getCCRepresentation().split("/"));
        g2.setFont(Storage.getFont28());
        point.translate(rightAlign ? 58 : -3, 32);
        String fleetCCs = player.getFleetCC() + additionalFleetSupply + addFS;
        DrawingUtil.superDrawString(
                g2, reps.get(0), point.x, point.y, Color.WHITE, align, null, Strokes.STROKE_4, Color.BLACK);
        DrawingUtil.superDrawString(
                g2, fleetCCs, point.x, point.y + 65, Color.WHITE, align, null, Strokes.STROKE_4, Color.BLACK);
        DrawingUtil.superDrawString(
                g2, reps.get(2), point.x, point.y + 130, Color.WHITE, align, null, Strokes.STROKE_4, Color.BLACK);
        return miscTile;
    }

    private void drawStrategyCardNumbers(
            Game game, Player player, Point statTileMid, HorizontalAlign center, VerticalAlign bottom) {
        Point point;
        g2.setFont(Storage.getFont80());
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
                        g2,
                        Integer.toString(sc),
                        point.x,
                        point.y + fontYoffset,
                        ColorUtil.getSCColor(sc, game),
                        center,
                        bottom,
                        Strokes.STROKE_6,
                        Color.BLACK);
                if (scModel != null) {
                    ctx.overlays().add(scModel, point.x - 20, point.y + 20, 40, 50);
                }
                point.translate(scsize, 0);
            }
        }
        g2.setColor(Color.WHITE);
        g2.setFont(Storage.getFont32());
    }

    private void drawPassedActiveOrAfk(Game game, Player player, Point miscTile, HorizontalAlign center) {
        Point point;
        String activePlayerID = game.getActivePlayerID();
        String phase = game.getPhaseOfGame();
        if (player.isPassed()) {
            point = PositionMapper.getPlayerStats("newpassed");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    g2, "PASSED", point.x, point.y, ColorUtil.PassedColor, center, null, Strokes.STROKE_4, Color.BLACK);
        } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
            point = PositionMapper.getPlayerStats("newpassed");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    g2, "ACTIVE", point.x, point.y, ColorUtil.ActiveColor, center, null, Strokes.STROKE_4, Color.BLACK);
        }
        if (player.isAFK()) {
            point = PositionMapper.getPlayerStats("newafk");
            point.translate(miscTile.x, miscTile.y);
            DrawingUtil.superDrawString(
                    g2, "AFK", point.x, point.y, Color.gray, center, null, Strokes.STROKE_4, Color.BLACK);
        }
        g2.setColor(Color.WHITE);
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
                g2.drawImage(img, point.x, point.y - (offBoardHighlighting > 0 ? 30 : 0), null);
            }
        }
        if (player.isTyrant()) {
            String speakerFile = ResourceHelper.getInstance().getTokenFile(Mapper.getTokenID("tyrant"));
            BufferedImage img = ImageHelper.read(speakerFile);
            if (img != null) {
                point = PositionMapper.getPlayerStats("newspeaker");
                point.translate(miscTile.x - (img.getWidth() / 2), miscTile.y - (img.getHeight() / 2));
                g2.drawImage(img, point.x, point.y - (offBoardHighlighting > 0 ? 30 : 0), null);
            }
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        PlayerAreaGenerator.drawPAImage(g2, x, y, resourceName);
    }

    private void drawPAImageScaled(int x, int y, String resourceName, int size) {
        PlayerAreaGenerator.drawPAImageScaled(g2, x, y, resourceName, size, size);
    }
}
