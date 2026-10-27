package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_PADDING;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.ResourceHelper;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.draw.PlayerTokens;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.MapPositionMapper;
import ti4.model.UnitTokenPosition;

final class ControlMarkers {

    private static final float SCALE = 1.0f;
    private static final int UNPLACED_STEP_X = 10;

    private ControlMarkers() {}

    static void draw(TileScene scene, Tile tile, Graphics2D g, UnitHolder unitHolder, List<Rectangle> occupied) {
        List<String> controlList = new ArrayList<>(unitHolder.getControlList());
        UnitTokenPosition unitTokenPosition = MapPositionMapper.getPlanetTokenPosition(unitHolder.getName());
        if (unitTokenPosition == null) {
            LegacyTokenStack.draw(tile, g, unitHolder, controlList);
            return;
        }
        Point centerPosition = TokenPlanetPlacement.holderCenter(unitHolder, tile);
        int xDelta = 0;
        for (String controlID : controlList) {
            if (controlID.contains(Constants.SLEEPER)) {
                continue;
            }

            Player player = PlayerTokens.ownerOfToken(scene.game().getPlayers().values(), controlID);
            boolean convertToGeneric = scene.showsGeneric(player);

            Point position = unitTokenPosition.getPosition(controlID);
            position = TokenPlanets.offset(position, unitHolder, tile);

            BufferedImage controlTokenImage = ImageHelper.readScaled(ImageResources.commandToken(controlID), SCALE);
            if (controlTokenImage == null) continue;

            int imgX;
            int imgY;
            String exhaustedFile;
            if (position != null) {
                imgX = TILE_PADDING + position.x;
                imgY = TILE_PADDING + position.y;
                exhaustedFile = "exhaustedControl.png";
            } else {
                imgX = TILE_PADDING + centerPosition.x + xDelta;
                imgY = TILE_PADDING + centerPosition.y;
                exhaustedFile = "exhaustedControl";
            }
            PlayerTokens.drawControlToken(g, controlTokenImage, player, imgX, imgY, convertToGeneric, SCALE);
            occupied.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
            if (player != null
                    && player.isRealPlayer()
                    && player.getExhaustedPlanets().contains(unitHolder.getName())) {
                BufferedImage exhaustedTokenImage = ImageHelper.readScaled(
                        ResourceHelper.getResourceFromFolder("command_token/", exhaustedFile), SCALE);
                PlayerTokens.drawControlToken(g, exhaustedTokenImage, player, imgX, imgY, convertToGeneric, SCALE);
                occupied.add(new Rectangle(imgX, imgY, controlTokenImage.getWidth(), controlTokenImage.getHeight()));
            }
            if (position == null) {
                xDelta += UNPLACED_STEP_X;
            }
        }
    }
}
