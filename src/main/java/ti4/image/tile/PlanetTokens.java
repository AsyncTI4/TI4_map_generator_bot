package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.model.MapPositionMapper;
import ti4.model.UnitTokenPosition;

final class PlanetTokens {

    private static final Point CUSTODIAN_POSITION = new Point(70, 45);
    private static final Point CUSTODIA_VIGILIA_POSITION = new Point(140, 185);
    private static final int UNPLACED_STEP_X = 10;

    private PlanetTokens() {}

    static void draw(Tile tile, Graphics2D g, UnitHolder unitHolder, List<Rectangle> occupied) {
        List<String> tokenList = sortedTokens(unitHolder);
        UnitTokenPosition unitTokenPosition = MapPositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition == null) {
            LegacyTokenStack.draw(tile, g, unitHolder, tokenList);
            return;
        }
        Point centerPosition = TokenPlanetPlacement.holderCenter(unitHolder, tile);
        int xDelta = 0;
        for (String tokenID : tokenList) {
            if (HolderTokens.isGroundToken(tokenID) || HolderTokens.isCustodianToken(tokenID)) {
                continue;
            }
            String tokenPath = ImageResources.tokenOrAttachment(tokenID);
            if (tokenPath == null) {
                BotLogger.warning(
                        "Could not parse token file for: " + tokenID + " on tile: " + tile.getAutoCompleteName());
                continue;
            }
            BufferedImage tokenImage = ImageHelper.read(tokenPath);
            if (tokenImage == null) continue;

            if (isCentredToken(tokenPath)) {
                drawCentred(g, tokenImage, centerPosition, 0);
            } else if (tokenPath.contains("token_theodisi_kairnexpedition")) {
                tokenImage = ImageHelper.readScaled(tokenPath, 0.5f);
                Rectangle bounds = drawCentred(g, tokenImage, centerPosition, 0);
                occupied.add(bounds);
            } else if (tokenPath.contains(Constants.DMZ_LARGE)) {
                float scale = 0.3f;
                if (unitHolder instanceof Planet planetHolder) {
                    scale = planetHolder.getRadius() / 184.0f;
                }
                drawCentred(g, ImageHelper.readScaled(tokenPath, scale), centerPosition, 10);
            } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
                g.drawImage(tokenImage, TILE_PADDING + CUSTODIAN_POSITION.x, TILE_PADDING + CUSTODIAN_POSITION.y, null);
            } else if (tokenPath.contains("custodiavigilia")) {
                g.drawImage(
                        tokenImage,
                        TILE_PADDING + CUSTODIA_VIGILIA_POSITION.x,
                        TILE_PADDING + CUSTODIA_VIGILIA_POSITION.y,
                        null);
            } else {
                Point position = TokenPlanets.offset(unitTokenPosition.getPosition(tokenID), unitHolder, tile);
                int x;
                int y;
                if (position != null) {
                    x = TILE_PADDING + position.x;
                    y = TILE_PADDING + position.y;
                } else {
                    x = TILE_PADDING + centerPosition.x + xDelta;
                    y = TILE_PADDING + centerPosition.y;
                    xDelta += UNPLACED_STEP_X;
                }
                g.drawImage(tokenImage, x, y, null);
                occupied.add(new Rectangle(x, y, tokenImage.getWidth(), tokenImage.getHeight()));
            }
        }
    }

    private static List<String> sortedTokens(UnitHolder unitHolder) {
        List<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.sort((o1, o2) -> {
            if ((o1.contains("nanoforge") || o1.contains("titanspn"))) {
                return -1;
            } else if ((o2.contains("nanoforge") || o2.contains("titanspn"))) {
                return -1;
            } else if (o1.contains(Constants.DMZ_LARGE) || o2.contains(Constants.DMZ_LARGE)) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        return tokenList;
    }

    private static boolean isCentredToken(String tokenPath) {
        return tokenPath.contains(Constants.WORLD_DESTROYED)
                || tokenPath.contains(Constants.CONSULATE_TOKEN)
                || tokenPath.contains(Constants.GLEDGE_CORE)
                || tokenPath.contains("freepeople");
    }

    private static Rectangle drawCentred(Graphics2D g, BufferedImage image, Point centerPosition, int dropY) {
        int x = TILE_PADDING + centerPosition.x - (image.getWidth() / 2);
        int y = TILE_PADDING + centerPosition.y - (image.getHeight() / 2) + dropY;
        g.drawImage(image, x, y, null);
        return new Rectangle(x, y, image.getWidth(), image.getHeight());
    }
}
