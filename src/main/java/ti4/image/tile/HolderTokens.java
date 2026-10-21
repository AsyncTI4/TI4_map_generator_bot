package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;

final class HolderTokens {

    private static final Point CUSTODIAN_POSITION = new Point(125, 115);
    private static final String RINGWORLD_TILE_ID = "99";

    private HolderTokens() {}

    static boolean isGroundToken(String tokenID) {
        return tokenID.contains(Constants.SLEEPER)
                || tokenID.contains(Constants.DMZ_LARGE)
                || tokenID.contains(Constants.WORLD_DESTROYED)
                || tokenID.contains(Constants.GLEDGE_CORE)
                || tokenID.contains(Constants.CUSTODIAN_TOKEN)
                || tokenID.contains(Constants.CONSULATE_TOKEN);
    }

    static boolean isCustodianToken(String tokenID) {
        return tokenID.contains(Constants.CUSTODIAN_TOKEN);
    }

    static void draw(Tile tile, Graphics2D g, UnitHolder unitHolder, Predicate<String> isDrawn, Game game) {
        Point centerPosition = TokenPlanetPlacement.holderCenter(unitHolder, tile);
        List<String> tokenList = sortedTokens(unitHolder);
        if (game.isShowBubbles()
                && unitHolder instanceof Planet planetHolder
                && PlanetShields.shouldHaveShield(unitHolder, game)) {
            drawShield(tile, g, planetHolder, centerPosition);
        }
        boolean containsDMZ = tokenList.stream().anyMatch(token -> token.contains(Constants.DMZ_LARGE));
        for (String tokenID : tokenList) {
            if (isDrawn.test(tokenID)) {
                drawToken(tile, g, unitHolder, game, centerPosition, tokenID, containsDMZ);
            }
        }
    }

    private static List<String> sortedTokens(UnitHolder unitHolder) {
        List<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.remove(null);
        tokenList.sort((o1, o2) -> {
            if (o1.contains(Constants.SLEEPER) || o2.contains(Constants.SLEEPER)) {
                return -1;
            } else if (o1.contains(Constants.DMZ_LARGE) || o2.contains(Constants.DMZ_LARGE)) {
                return 1;
            }
            return o1.compareTo(o2);
        });
        return tokenList;
    }

    private static void drawShield(Tile tile, Graphics2D g, Planet planetHolder, Point centerPosition) {
        String tokenPath =
                switch (StringUtils.defaultString(planetHolder.getContrastColor())) {
                    case "orange" -> ImageResources.token("token_planetaryShield_orange.png");
                    case "test" -> ImageResources.token("token_planetaryShield_test.png");
                    default -> ImageResources.token("token_planetaryShield.png");
                };
        float scale = planetHolder.getRadius() / 58.0f;
        BufferedImage shield = ImageHelper.readScaled(tokenPath, scale);
        Point position =
                new Point(centerPosition.x - (shield.getWidth() / 2), centerPosition.y - (shield.getHeight() / 2));
        g.drawImage(shield, TILE_PADDING + position.x, TILE_PADDING + position.y, null);
        if (RINGWORLD_TILE_ID.equals(tile.getTileID())) {
            drawRingworldMoonShields(g, tokenPath, scale, position);
        }
    }

    private static void drawRingworldMoonShields(Graphics2D g, String tokenPath, float scale, Point position) {
        BufferedImage shield = ImageHelper.readScaled(tokenPath, scale * 0.2526f);
        g.drawImage(shield, TILE_PADDING + position.x + 172, TILE_PADDING + position.y + 148, null);
        shield = ImageHelper.readScaled(tokenPath, scale * 0.1421f);
        g.drawImage(shield, TILE_PADDING + position.x - 19, TILE_PADDING + position.y + 13, null);
        shield = ImageHelper.readScaled(tokenPath, scale * 0.0842f);
        g.drawImage(shield, TILE_PADDING + position.x - 7, TILE_PADDING + position.y + 195, null);
    }

    private static void drawToken(
            Tile tile,
            Graphics2D g,
            UnitHolder unitHolder,
            Game game,
            Point centerPosition,
            String tokenID,
            boolean containsDMZ) {
        String tokenPath = ImageResources.tokenOrAttachment(tokenID);
        if (tokenPath == null) {
            BotLogger.warning(new LogOrigin(game), "Could not find token file for: " + tokenID);
            return;
        }
        float scale = 0.85f;
        if (tokenID.contains(Constants.DMZ_LARGE)) {
            if (unitHolder instanceof Planet planetHolder) {
                scale = planetHolder.getRadius() / 65.0f;
            }
            scale *= 0.35f;
        } else if (tokenPath.contains(Constants.WORLD_DESTROYED)) {
            if (unitHolder instanceof Planet planetHolder) {
                scale = planetHolder.getRadius() / 65.0f;
            }
            if (tile.isFracture()) {
                tokenPath = ImageResources.tokenOrAttachment("token_worlddestroyed_frac.png");
            }
            scale *= 1.55f;
        } else if (tokenPath.contains(Constants.CUSTODIAN_TOKEN)) {
            scale = 0.5f;
        }

        BufferedImage tokenImage = ImageHelper.readScaled(tokenPath, scale);
        if (tokenImage == null) return;
        Point position = new Point(
                centerPosition.x - (tokenImage.getWidth() / 2), centerPosition.y - (tokenImage.getHeight() / 2));
        if (tokenID.contains(Constants.CUSTODIAN_TOKEN)) {
            position = new Point(CUSTODIAN_POSITION);
        } else if (tokenID.contains(Constants.SLEEPER) && containsDMZ) {
            position = new Point(position.x + 10, position.y + 10);
        } else if (tokenID.contains(Constants.WORLD_DESTROYED)) {
            position = new Point(position.x + 4, position.y + 13);
        } else if (tokenID.contains(Constants.DMZ_LARGE)) {
            position = new Point(position.x, position.y + 10);
        }
        g.drawImage(tokenImage, TILE_PADDING + position.x, TILE_PADDING + position.y - 10, null);
    }
}
