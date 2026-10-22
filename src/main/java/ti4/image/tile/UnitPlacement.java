package ti4.image.tile;

import static ti4.image.tile.TileGeometry.TILE_HEIGHT;
import static ti4.image.tile.TileGeometry.TILE_PADDING;
import static ti4.image.tile.TileGeometry.TILE_WIDTH;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
import ti4.model.UnitTokenPosition;

final class UnitPlacement {

    record ImagePosition(int originalX, int originalY, int x, int y) {}

    record UnitShape(Point centerPosition, String unitId, BufferedImage unitImage, boolean fighterOrInfantry) {}

    private static final float MIRAGE_DRAG_RATIO = 2.0f / 3;
    private static final int MIRAGE_DRAG_X =
            Math.round(((float) TILE_WIDTH / 8 + TILE_PADDING) * (1 - MIRAGE_DRAG_RATIO));
    private static final int MIRAGE_DRAG_Y =
            Math.round(((float) (3 * TILE_HEIGHT) / 4 + TILE_PADDING) * (1 - MIRAGE_DRAG_RATIO));
    private static final int MIRAGE_TOKEN_UNIT_SHIFT_X = 60;
    private static final int DEFAULT_SPACE_STEP_X = 10;
    private static final int DEFAULT_SPACE_STEP_Y = -7;

    private final Tile tile;
    private final UnitHolder unitHolder;
    private final List<Rectangle> occupied;
    private final int radius;
    private final int degree;
    private final int degreeChange;
    private final boolean isSpace;
    private final boolean isTokenPlanet;
    private final boolean hasTokenPlanet;
    private final int spaceUnitXOffset;
    private final int spaceUnitYOffset;
    private final UnitTokenPosition unitTokenPosition;

    UnitPlacement(
            Game game,
            Tile tile,
            UnitHolder unitHolder,
            List<Rectangle> occupied,
            int radius,
            int degree,
            int degreeChange) {
        this.tile = tile;
        this.unitHolder = unitHolder;
        this.occupied = occupied;
        this.radius = radius;
        this.degree = degree;
        this.degreeChange = degreeChange;
        isSpace = Constants.SPACE.equals(unitHolder.getName());
        isTokenPlanet = Constants.TOKEN_PLANETS.contains(unitHolder.getName());
        hasTokenPlanet =
                unitHolder.getTokenList().stream().map(Mapper::getTokenKey).anyMatch(Constants.TOKEN_PLANETS::contains);
        Point unitOffset =
                game.isAllianceMode() ? MapPositionMapper.getAllianceUnitOffset() : MapPositionMapper.getUnitOffset();
        spaceUnitXOffset = unitOffset != null ? unitOffset.x : DEFAULT_SPACE_STEP_X;
        spaceUnitYOffset = unitOffset != null ? unitOffset.y : DEFAULT_SPACE_STEP_Y;
        UnitTokenPosition planetPositions = MapPositionMapper.getPlanetTokenPosition(unitHolder.getName());
        unitTokenPosition = planetPositions != null
                ? planetPositions
                : MapPositionMapper.getSpaceUnitPosition(unitHolder.getName(), tile.getTileID());
    }

    boolean isSpace() {
        return isSpace;
    }

    boolean isTokenPlanet() {
        return isTokenPlanet;
    }

    Point unitPosition(UnitShape shape, UnitKey unitKey, int count) {
        Point predefinedPosition = tokenPosition(unitKey, shape);

        if (isSpace && predefinedPosition != null && !shape.fighterOrInfantry()) {
            predefinedPosition.translate(spaceUnitXOffset * count, spaceUnitYOffset * count);
            return predefinedPosition;
        }
        if (predefinedPosition != null) {
            return predefinedPosition;
        }

        Point position = spiralPosition(shape, radius, degree, degreeChange, occupied);
        Point centerPosition = shape.centerPosition();
        int unitWidth = shape.unitImage().getWidth();
        int unitHeight = shape.unitImage().getHeight();
        occupied.add(new Rectangle(
                centerPosition.x + position.x - (unitWidth / 2),
                centerPosition.y + position.y - (unitHeight / 2),
                unitWidth,
                unitHeight));
        return position;
    }

    private Point tokenPosition(UnitKey unitKey, UnitShape shape) {
        boolean fighterOrInfantry = unitKey.unitType() == UnitType.Infantry || unitKey.unitType() == UnitType.Fighter;
        String positionKey = fighterOrInfantry ? "tkn_" + shape.unitId() : shape.unitId();
        return unitTokenPosition.getPosition(positionKey);
    }

    private static Point spiralPosition(
            UnitShape shape, int radius, int degree, int degreeChange, List<Rectangle> occupied) {
        Point centerPosition = shape.centerPosition();
        int unitWidth = shape.unitImage().getWidth();
        int unitHeight = shape.unitImage().getHeight();

        int foundX = 0;
        int foundY = 0;
        boolean searchPosition = true;
        while (searchPosition) {
            int x = (int) (radius * Math.sin(degree));
            int y = (int) (radius * Math.cos(degree));
            int candidateX = centerPosition.x + x - (unitWidth / 2);
            int candidateY = centerPosition.y + y - (unitHeight / 2);
            if (occupied.stream()
                    .noneMatch(rectangle -> rectangle.intersects(candidateX, candidateY, unitWidth, unitHeight))) {
                searchPosition = false;
            } else if (degree > 360) {
                searchPosition = false;
            }

            degree += degreeChange;

            if (!searchPosition) {
                foundX = candidateX;
                foundY = candidateY;
            }
        }

        occupied.add(new Rectangle(foundX, foundY, unitWidth, unitHeight));
        return new Point(foundX, foundY);
    }

    ImagePosition imagePosition(UnitShape shape, Point unitPosition) {
        Point position = TokenPlanets.offset(unitPosition, unitHolder, tile);
        int xOriginal = shape.centerPosition().x + position.x;
        int yOriginal = shape.centerPosition().y + position.y;

        int imageX = position.x + TILE_PADDING;
        int imageY = position.y + TILE_PADDING;

        if (!isTokenPlanet && hasTokenPlanet) {
            imageX += (shape.unitImage().getWidth() / 2);
            imageY += (shape.unitImage().getHeight() / 2);
            imageX = Math.round(MIRAGE_DRAG_RATIO * imageX)
                    + MIRAGE_DRAG_X
                    + (shape.fighterOrInfantry() ? MIRAGE_TOKEN_UNIT_SHIFT_X : 0);
            imageY = Math.round(MIRAGE_DRAG_RATIO * imageY) + MIRAGE_DRAG_Y;
            imageX -= (shape.unitImage().getWidth() / 2);
            imageY -= (shape.unitImage().getHeight() / 2);
        }

        return new ImagePosition(xOriginal, yOriginal, imageX, imageY);
    }
}
