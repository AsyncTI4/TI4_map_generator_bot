package ti4.image.tile;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;

final class UnitsStep implements TileStepPainter {

    private static final int SPIRAL_START_DEGREE = 180;
    private static final int SPIRAL_DEGREE_STEP = 5;

    @Override
    public void paint(TileScene scene, TileLayer layer) {
        Game game = scene.game();
        Tile tile = layer.tile();
        Graphics2D g = layer.g();
        if (scene.hidesFogged(tile)) {
            return;
        }

        List<Rectangle> occupied = new ArrayList<>();
        Collection<UnitHolder> unitHolders =
                new ArrayList<>(tile.getUnitHolders().values());
        UnitHolder spaceUnitHolder = tile.getSpaceUnitHolder();
        if (spaceUnitHolder != null) {
            HolderTokens.draw(tile, g, spaceUnitHolder, HolderTokens::isCustodianToken, game);
            SpaceTokens.draw(tile, g, spaceUnitHolder, game);
            unitHolders.remove(spaceUnitHolder);
            unitHolders.add(spaceUnitHolder);
        }

        ProductionBadge badge = ProductionBadge.measure(game, tile);
        for (UnitHolder unitHolder : unitHolders) {
            HolderTokens.draw(tile, g, unitHolder, HolderTokens::isGroundToken, game);
            ControlMarkers.draw(scene, tile, g, unitHolder, occupied);
        }
        if (game.isShowGears() && !game.isFowMode()) {
            badge.draw(tile, g);
        }

        if (spaceUnitHolder != null) {
            CommandTokens.draw(scene, tile, g, spaceUnitHolder);
        }
        for (UnitHolder unitHolder : unitHolders) {
            int radius = Constants.SPACE.equals(unitHolder.getName()) ? Constants.SPACE_RADIUS : Constants.RADIUS;
            if (unitHolder != spaceUnitHolder) {
                PlanetTokens.draw(tile, g, unitHolder, occupied);
            }
            new UnitHolderRenderer(
                            game,
                            scene.displayType(),
                            tile,
                            g,
                            occupied,
                            SPIRAL_START_DEGREE,
                            SPIRAL_DEGREE_STEP,
                            unitHolder,
                            radius,
                            scene.viewer(),
                            scene.warnings())
                    .render();
        }
    }
}
