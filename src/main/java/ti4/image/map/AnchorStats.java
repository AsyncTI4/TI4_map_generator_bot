package ti4.image.map;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.draw.ColorUtil;
import ti4.image.draw.PaImage;
import ti4.image.draw.PlayerTokens;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.tile.TileGeometry;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;

final class AnchorStats {

    private final Game game;
    private final Graphics2D g2;
    private final FogView fog;
    private final BoardLayout board;

    AnchorStats(RenderContext ctx, Graphics2D g2) {
        this.game = ctx.game();
        this.g2 = g2;
        this.fog = ctx.fog();
        this.board = ctx.board();
    }

    void paintPlayerInfoOld(Player player, int ringCount) {
        int deltaX, deltaSplitX = 0;
        int deltaY, deltaSplitY = 0;

        String playerStatsAnchor = player.getPlayerStatsAnchorPosition();
        if (playerStatsAnchor != null) {
            Point anchorProjectedPoint = MapPositionMapper.getTilePosition(playerStatsAnchor);
            if (anchorProjectedPoint != null) {
                Point playerStatsAnchorPoint = MapPositionMapper.getScaledTilePosition(
                        game, playerStatsAnchor, anchorProjectedPoint.x, anchorProjectedPoint.y, board.fractureYbump());
                playerStatsAnchorPoint.translate(-board.frameOffsetX(), -board.frameOffsetY());
                Integer anchorLocationIndex =
                        MapPositionMapper.getRingSideNumberOfTileID(player.getPlayerStatsAnchorPosition());
                anchorLocationIndex = anchorLocationIndex == null ? 0 : anchorLocationIndex - 1;
                boolean isCorner = playerStatsAnchor.equals(
                        MapPositionMapper.getTileIDAtCornerPositionOfRing(ringCount, anchorLocationIndex + 1));
                if (anchorLocationIndex == 0 && isCorner) {
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X + 80;
                    deltaY = playerStatsAnchorPoint.y - 80;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 0) {
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X + TileGeometry.TILE_HEIGHT;
                    deltaY = playerStatsAnchorPoint.y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 1) {
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 2 && isCorner) {
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 2) {
                    deltaX = playerStatsAnchorPoint.x + 360 + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y + 100;
                } else if (anchorLocationIndex == 3 && isCorner) {
                    deltaX = playerStatsAnchorPoint.x + BoardLayout.EXTRA_X;
                    deltaY = playerStatsAnchorPoint.y + 360 + BoardLayout.EXTRA_Y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 3) {
                    deltaX = playerStatsAnchorPoint.x;
                    deltaY = playerStatsAnchorPoint.y + 250 + BoardLayout.EXTRA_Y;
                    deltaSplitX = 200;
                } else if (anchorLocationIndex == 4) {
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 5 && isCorner) {
                    deltaX = playerStatsAnchorPoint.x + 10;
                    deltaY = playerStatsAnchorPoint.y + BoardLayout.EXTRA_Y;
                } else if (anchorLocationIndex == 5) {
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

        Point point = MapPositionMapper.getPlayerStats(Constants.STATS_USERNAME);
        if (!game.hideUserNames()) {
            g2.drawString(userName.substring(0, Math.min(userName.length(), 11)), point.x + deltaX, point.y + deltaY);
        }

        point = MapPositionMapper.getPlayerStats(Constants.STATS_FACTION);
        String factionText = player.getFaction();
        if (player.getDisplayName() != null && !"null".equals(player.getDisplayName())) {
            factionText = player.getDisplayName();
        }
        g2.drawString(StringUtils.capitalize(factionText), point.x + deltaX, point.y + deltaY);

        point = MapPositionMapper.getPlayerStats(Constants.STATS_COLOR);
        g2.drawString(player.getColor(), point.x + deltaX, point.y + deltaY);

        int vpCount = player.getTotalVictoryPoints();
        point = MapPositionMapper.getPlayerStats(Constants.STATS_VP);
        g2.drawString("VP: " + vpCount, point.x + deltaX, point.y + deltaY);

        int totalSecrets = player.getSecrets().size();
        Set<String> soSet = player.getSecretsScored().keySet();
        int soOffset = 0;
        String soHand = "pa_so-icon_hand.png";
        String soScored = "pa_so-icon_scored.png";
        point = MapPositionMapper.getPlayerStats(Constants.STATS_SO);
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

        List<Integer> playerSCs = new ArrayList<>(player.getSCs());
        Collections.sort(playerSCs);
        int count = 0;
        for (int sc : playerSCs) {
            String scText = sc == 0 ? " " : Integer.toString(sc);
            scText = Game.getSCNumberIfNaaluInPlay(player, scText);
            g2.setColor(ColorUtil.getSCColor(sc, game));
            g2.setFont(Fonts.ti(64));
            point = MapPositionMapper.getPlayerStats(Constants.STATS_SC);
            if (sc != 0) {
                g2.drawString(scText, point.x + deltaX + 64 * count, point.y + deltaY);
            }
            count++;
        }

        g2.setColor(Color.WHITE);
        g2.setFont(Fonts.ti(32));
        String ccID = Mapper.getCCID(player.getColor());
        String fleetCCID = Mapper.getFleetCCID(player.getColor());
        point = MapPositionMapper.getPlayerStats(Constants.STATS_CC);
        int x = point.x + deltaX;
        int y = point.y + deltaY;
        if (deltaSplitX != 0) {
            deltaSplitY = point.y;
        }

        PlayerTokens.drawCommandCounters(
                g2, ccID, x + deltaSplitX, y - deltaSplitY, player.getTacticalCC(), player, false, false);
        FleetCommandCounters.draw(g2, fleetCCID, x + deltaSplitX, y + 65 - deltaSplitY, player, false);
        PlayerTokens.drawCommandCounters(
                g2, ccID, x + deltaSplitX, y + 130 - deltaSplitY, player.getStrategicCC(), player, false, false);

        if (player.isSpeaker()) {
            String speakerID = Mapper.getTokenID(Constants.SPEAKER);
            String speakerFile = ImageResources.token(speakerID);
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                point = MapPositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                int negativeDelta = 0;
                g2.drawImage(
                        bufferedImage,
                        point.x + deltaX + deltaSplitX + negativeDelta,
                        point.y + deltaY - deltaSplitY,
                        null);
                g2.setColor(Color.WHITE);
            }
        }
        if (player.isTyrant()) {
            String speakerFile = ImageResources.token(Mapper.getTokenID("tyrant"));
            if (speakerFile != null) {
                BufferedImage bufferedImage = ImageHelper.read(speakerFile);
                point = MapPositionMapper.getPlayerStats(Constants.STATS_SPEAKER);
                int negativeDelta = 0;
                g2.drawImage(
                        bufferedImage,
                        point.x + deltaX + deltaSplitX + negativeDelta,
                        point.y + deltaY - deltaSplitY,
                        null);
                g2.setColor(Color.WHITE);
            }
        }
        String activePlayerID = game.getActivePlayerID();
        String phase = game.getPhaseOfGame();
        if (player.isPassed()) {
            point = MapPositionMapper.getPlayerStats(Constants.STATS_PASSED);
            g2.setColor(ColorUtil.PassedColor);
            g2.drawString("PASSED", point.x + deltaX, point.y + deltaY);
            g2.setColor(Color.WHITE);
        } else if (player.getUserID().equals(activePlayerID) && "action".equals(phase)) {
            point = MapPositionMapper.getPlayerStats(Constants.STATS_PASSED);
            g2.setColor(ColorUtil.ActiveColor);
            g2.drawString("ACTIVE", point.x + deltaX + 4, point.y + deltaY);

            if (player.isAFK()) {
                g2.setColor(Color.GRAY);
                g2.drawString("(AFK)", point.x + deltaX + 124, point.y + deltaY);
            }
            g2.setColor(Color.WHITE);
        } else if (player.isAFK()) {
            point = MapPositionMapper.getPlayerStats(Constants.STATS_PASSED);
            g2.setColor(Color.GRAY);
            g2.drawString("AFK", point.x + deltaX + 4, point.y + deltaY);
            g2.setColor(Color.WHITE);
        }
    }

    private void drawPAImage(int x, int y, String resourceName) {
        PaImage.draw(g2, x, y, resourceName);
    }
}
