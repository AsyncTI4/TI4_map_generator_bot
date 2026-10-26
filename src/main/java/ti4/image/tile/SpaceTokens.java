package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_PADDING;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.oblivion.OblivionAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisAbilityHandler;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.draw.HorizontalAlign;
import ti4.image.draw.OutlinedText;
import ti4.image.draw.Strokes;
import ti4.image.draw.VerticalAlign;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.WormholeModel.Wormhole;

final class SpaceTokens {

    // top-left of the fog-vision token, tucked inside the hex's bottom-left edge
    private static final Point FOW_VISION_TOKEN_POINT = new Point(75, 236);
    private static final float MIRAGE_DRAG_RATIO = 2.0f / 3;
    private static final int MIRAGE_DRAG_X =
            Math.round(((float) TILE_WIDTH / 8 + TILE_PADDING) * (1 - MIRAGE_DRAG_RATIO));
    private static final int MIRAGE_DRAG_Y =
            Math.round(((float) (3 * TILE_HEIGHT) / 4 + TILE_PADDING) * (1 - MIRAGE_DRAG_RATIO));
    private static final BasicStroke COUNT_OUTLINE = Strokes.STROKE_4;

    private SpaceTokens() {}

    static void draw(Tile tile, Graphics2D g, UnitHolder unitHolder, Game game) {
        List<String> tokenList = new ArrayList<>(unitHolder.getTokenList());
        tokenList.sort(Comparator.comparing(token -> !Constants.TOKEN_PLANETS.contains(Mapper.getTokenKey(token))));

        Point centerPosition = TokenPlanetPlacement.holderCenter(unitHolder, tile);
        int x = 0;
        int y = 0;
        int deltaX = 80;
        int deltaY = 0;

        boolean hasTripleTokenPlanet =
                tokenList.stream().anyMatch(tok -> Constants.TOKEN_PLANETS.contains(Mapper.getTokenKey(tok)))
                        && (tile.getPlanetUnitHolders().size() >= 4);
        List<Point> spaceTokenPositions = MapPositionMapper.getSpaceTokenPositions(tile.getTileID());
        if (spaceTokenPositions.isEmpty()) {
            x = centerPosition.x;
            y = centerPosition.y;
        }
        WormholeTokenMerge.DrawOrder drawOrder = game.isWormholeTokenMerge()
                ? WormholeTokenMerge.apply(tokenList)
                : new WormholeTokenMerge.DrawOrder(tokenList, Map.of());
        int index = 0;
        for (String tokenID : drawOrder.tokens()) {
            WormholeTokenMerge.Merged merged = drawOrder.merged().get(tokenID);
            String tokenPath = merged != null ? tokenID : ImageResources.tokenOrAttachment(tokenID);
            if (game.isLiberationC4Mode()) {
                tokenPath = tokenPath.replace("token_creuss", "token_crimsoncreuss");
                tokenPath = tokenPath.replace("token_crimsoncreussepsilon", "token_creussepsilon");
            }
            String tokenName = Mapper.getTokenKey(tokenID);

            if (tokenPath == null) {
                BotLogger.warning(new LogOrigin(game), "Could not parse token file for: " + tokenID);
                continue;
            }
            if (game.isCptiExploreMode() && tokenPath.toLowerCase().contains("token_frontier")) {
                tokenPath = tokenPath.replace("token_frontier", "token_frontier_cpti");
            }

            BufferedImage tokenImage = merged != null ? merged.image() : ImageHelper.read(tokenPath);
            if (tokenImage == null) return;

            if (Constants.TOKEN_PLANETS.contains(tokenName)) {
                Point tokenCenter = TokenPlanetPlacement.getTokenPlanetCenterOfImage(tile, tokenName);
                int tokenX = tokenCenter.x - (tokenImage.getWidth() / 2);
                int tokenY = tokenCenter.y - (tokenImage.getHeight() / 2);
                g.drawImage(tokenImage, TILE_PADDING + tokenX, TILE_PADDING + tokenY, null);
            } else if (tokenPath.contains(Constants.SLEEPER)) {
                int sleeperX = TILE_PADDING + centerPosition.x - (tokenImage.getWidth() / 2);
                int sleeperY = TILE_PADDING + centerPosition.y - (tokenImage.getHeight() / 2);
                g.drawImage(tokenImage, sleeperX, sleeperY, null);
            } else if (Mapper.isFowVisionToken(tokenID)) {
                g.drawImage(
                        tokenImage,
                        TILE_PADDING + FOW_VISION_TOKEN_POINT.x,
                        TILE_PADDING + FOW_VISION_TOKEN_POINT.y,
                        null);
            } else {
                int drawX = TILE_PADDING + x;
                if (tokenPath.contains("mustache")) {
                    drawX -= 120;
                }
                int drawY = TILE_PADDING + y;
                if (spaceTokenPositions.size() > index) {
                    Point point = spaceTokenPositions.get(index);
                    drawX += point.x;
                    drawY += point.y;
                    index++;
                } else {
                    drawX += deltaX;
                    drawY += deltaY;
                    deltaX += 30;
                    deltaY += 30;
                }
                if (hasTripleTokenPlanet) {
                    drawX += (tokenImage.getWidth() / 2);
                    drawY += (tokenImage.getHeight() / 2);
                    drawX = Math.round(MIRAGE_DRAG_RATIO * drawX) + MIRAGE_DRAG_X;
                    drawY = Math.round(MIRAGE_DRAG_RATIO * drawY) + MIRAGE_DRAG_Y;
                    drawX -= (tokenImage.getWidth() / 2);
                    drawY -= (tokenImage.getHeight() / 2);
                }
                g.drawImage(tokenImage, drawX, drawY, null);
                drawTheodisiTokenCount(g, tokenPath, game, tile, drawX, drawY, tokenImage);
                List<Wormhole> wormholes = merged != null ? merged.wormholes() : Mapper.getTokenWormholes(tokenID);
                drawLawMarks(g, wormholes, game, tile, drawX, drawY, tokenImage);
            }
        }
    }

    private static void drawLawMarks(
            Graphics2D g,
            List<Wormhole> wormholes,
            Game game,
            Tile tile,
            int drawX,
            int drawY,
            BufferedImage tokenImage) {
        boolean reconstruction = LawWormholeMarks.isReconstructionInPlay(game);
        int offsetX = (tokenImage.getWidth() - 80) / 2;
        int offsetY = (tokenImage.getWidth() - 80) / 2;
        boolean alpha = wormholes.contains(Wormhole.ALPHA);
        boolean beta = wormholes.contains(Wormhole.BETA);
        boolean alphaOrBeta = alpha || beta;
        if (LawWormholeMarks.isTravelBanInPlay(game) && alphaOrBeta) {
            g.drawImage(
                    LawWormholeMarks.blockedWormholeImage(reconstruction),
                    drawX + offsetX + 40,
                    drawY + offsetY + 40,
                    null);
        } else if (tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_SEVERED) && !wormholes.isEmpty()) {
            g.drawImage(
                    LawWormholeMarks.blockedWormholeImage(reconstruction && alphaOrBeta),
                    drawX + offsetX + 40,
                    drawY + offsetY + 40,
                    null);
        }
        if (reconstruction && alpha) {
            g.drawImage(
                    LawWormholeMarks.reconstructionPartnerImage(Wormhole.BETA), drawX + offsetX, drawY + offsetY, null);
        }
        if (reconstruction && beta) {
            g.drawImage(
                    LawWormholeMarks.reconstructionPartnerImage(Wormhole.ALPHA),
                    drawX + offsetX + (alpha ? 40 : 0),
                    drawY + offsetY,
                    null);
        }
        if (LawWormholeMarks.isNexusLawInPlay(game)
                && "82b".equals(tile.getTileID())
                && !LawWormholeMarks.isTravelBanInPlay(game)
                && alphaOrBeta) {
            g.drawImage(
                    LawWormholeMarks.blockedWormholeImage(reconstruction),
                    drawX + offsetX + 40,
                    drawY + offsetY + 40,
                    null);
        }
    }

    private static void drawTheodisiTokenCount(
            Graphics2D g, String tokenPath, Game game, Tile tile, int drawX, int drawY, BufferedImage tokenImage) {
        int count = tokenPath.contains("token_theodisi_mine")
                ? XytherisAbilityHandler.getStingOfTheHiveMineCount(game, tile)
                : tokenPath.contains("token_theodisi_oblivionreflection")
                        ? OblivionAbilityHandler.getReflectionCount(game, tile)
                        : 0;
        if (count < 2) {
            return;
        }
        g.setFont(Fonts.ti(24));
        OutlinedText.draw(
                g,
                Integer.toString(count),
                drawX + tokenImage.getWidth() / 2,
                drawY + tokenImage.getHeight() / 2,
                Color.WHITE,
                HorizontalAlign.Center,
                VerticalAlign.Center,
                COUNT_OUTLINE,
                Color.BLACK);
    }
}
