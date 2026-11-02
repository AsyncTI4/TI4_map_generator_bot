package ti4.image.tile;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.commons.lang3.Strings;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;
import ti4.model.PlanetLayoutModel;
import ti4.model.PlanetModel;
import ti4.model.TokenModel;

final class TokenPlanetPlacement {

    private static final int TRIPLE_SYSTEM_TOKEN_PLANET_GAP_OFFSET = 65;

    private static final List<Point> TOKEN_PLANET_POSITIONS = List.of(
            new Point(87, 79),
            new Point(258, 79),
            new Point(87, 221),
            new Point(258, 221),
            new Point(172, 79),
            new Point(172, 221));

    private static final List<Point> TRIPLE_SYSTEM_TOKEN_PLANET_POSITIONS = List.of(
            new Point(87, 221),
            new Point(258, 221),
            new Point(87, 79),
            new Point(258, 79),
            new Point(172, 221),
            new Point(172, 79));

    private static final List<Point> TWO_PLANET_OR_WORMHOLE_TOKEN_PLANET_POSITIONS = List.of(
            new Point(Constants.TOKEN_PLANET_POSITION),
            new Point(78, 178),
            new Point(258, 221),
            new Point(87, 221),
            new Point(172, 79),
            new Point(172, 221));

    private TokenPlanetPlacement() {}

    static Point holderCenter(UnitHolder unitHolder, Tile tile) {
        if (Constants.TOKEN_PLANETS.contains(unitHolder.getName())) {
            return getTokenPlanetCenterPosition(tile, unitHolder.getName());
        }
        return unitHolder.getHolderCenterPosition();
    }

    private static Point getTokenPlanetPosition(Tile tile, String tokenOrPlanetID) {
        Point defaultPosition = getDefaultTokenPlanetPosition(tile, tokenOrPlanetID);
        List<String> tokenPlanets = tile.getPlanetUnitHolders().stream()
                .map(UnitHolder::getName)
                .filter(Constants.TOKEN_PLANETS::contains)
                .sorted()
                .toList();

        if (tokenPlanets.size() <= 1) {
            return defaultPosition;
        }

        String tokenPlanetID = getTokenPlanetID(tokenOrPlanetID);
        if (tokenPlanetID == null) {
            return defaultPosition;
        }

        if (Constants.THUNDERSEDGE.equalsIgnoreCase(tokenPlanetID)) {
            return new Point(Constants.SPACE_CENTER_POSITION);
        }

        List<String> slottedTokenPlanets = tokenPlanets.stream()
                .filter(tokenPlanet -> !Constants.THUNDERSEDGE.equalsIgnoreCase(tokenPlanet))
                .toList();

        int index = slottedTokenPlanets.indexOf(tokenPlanetID);
        if (index < 0) {
            return defaultPosition;
        }

        int printedPlanetCount = tile.getTileModel().getNumPlanets();
        boolean usesTwoPlanetOrWormholeSlots = printedPlanetCount != 3
                && (printedPlanetCount == 2
                        || printedPlanetCount > 0 && tile.getTileModel().hasWormhole());
        List<Point> positions = printedPlanetCount == 3
                ? TRIPLE_SYSTEM_TOKEN_PLANET_POSITIONS
                : usesTwoPlanetOrWormholeSlots ? TWO_PLANET_OR_WORMHOLE_TOKEN_PLANET_POSITIONS : TOKEN_PLANET_POSITIONS;
        Point position = printedPlanetCount == 3 && index < 3 ? getTripleSystemTokenPlanetPosition(tile, index) : null;
        if (position == null) {
            if (index < positions.size()) {
                position = positions.get(index);
            } else {
                double theta = (2 * Math.PI * index) / slottedTokenPlanets.size();
                int x = Constants.SPACE_CENTER_POSITION.x + (int) Math.round(85 * Math.cos(theta));
                int y = Constants.SPACE_CENTER_POSITION.y + (int) Math.round(70 * Math.sin(theta));
                position = new Point(x, y);
            }
        }

        return new Point(position);
    }

    @Nullable
    private static Point getTripleSystemTokenPlanetPosition(Tile tile, int index) {
        List<Point> planetPositions = tile.getTileModel().getPlanets().stream()
                .map(Mapper::getPlanet)
                .filter(Objects::nonNull)
                .map(PlanetModel::getPlanetLayout)
                .filter(Objects::nonNull)
                .map(PlanetLayoutModel::getCenterPosition)
                .filter(Objects::nonNull)
                .toList();
        if (planetPositions.size() != 3) {
            return null;
        }

        double centerX =
                planetPositions.stream().mapToInt(point -> point.x).average().orElseThrow();
        double centerY =
                planetPositions.stream().mapToInt(point -> point.y).average().orElseThrow();
        Point firstPlanet = planetPositions.get(index);
        Point secondPlanet = planetPositions.get((index + 1) % planetPositions.size());
        double midpointX = (firstPlanet.x + secondPlanet.x) / 2.0;
        double midpointY = (firstPlanet.y + secondPlanet.y) / 2.0;
        double deltaX = midpointX - centerX;
        double deltaY = midpointY - centerY;
        double distance = Math.hypot(deltaX, deltaY);
        if (distance == 0) {
            return new Point((int) Math.round(midpointX), (int) Math.round(midpointY));
        }
        return new Point((int) Math.round(midpointX + deltaX * TRIPLE_SYSTEM_TOKEN_PLANET_GAP_OFFSET / distance), (int)
                Math.round(midpointY + deltaY * TRIPLE_SYSTEM_TOKEN_PLANET_GAP_OFFSET / distance));
    }

    private static Point getDefaultTokenPlanetPosition(Tile tile, String tokenOrPlanetID) {
        if (Strings.CI.contains(tokenOrPlanetID, Constants.THUNDERSEDGE)) {
            return new Point(Constants.SPACE_CENTER_POSITION);
        }

        Point position = new Point(Constants.TOKEN_PLANET_POSITION);
        if (tile.getTileModel().getNumPlanets() == 3) {
            position = new Point(Constants.MIRAGE_TRIPLE_POSITION);
        }
        return position;
    }

    private static String getTokenPlanetID(String tokenOrPlanetID) {
        if (tokenOrPlanetID == null) {
            return null;
        }

        TokenModel token = Mapper.getToken(tokenOrPlanetID);
        if (token != null && token.getTokenPlanetName() != null) {
            return token.getTokenPlanetName();
        }
        return Mapper.getTokenKey(tokenOrPlanetID);
    }

    static Point getTokenPlanetCenterPosition(Tile tile, String tokenID) {
        TokenModel token = Mapper.getToken(tokenID);
        PlanetModel planet = token == null ? Mapper.getPlanet(tokenID) : Mapper.getPlanet(token.getTokenPlanetName());
        Integer planetX = null;
        Integer planetY = null;
        if (planet != null
                && planet.getPlanetLayout() != null
                && planet.getPlanetLayout().getCenterPosition() != null) {
            planetX = planet.getPlanetLayout().getCenterPosition().x;
            planetY = planet.getPlanetLayout().getCenterPosition().y;
        }

        String tokenPath = token == null ? Mapper.getTokenID(tokenID) : token.getImagePath();
        BufferedImage tokenImage = ImageHelper.read(tokenPath == null ? null : ImageResources.attachment(tokenPath));
        Integer imageX = null;
        Integer imageY = null;
        if (tokenImage != null) {
            imageX = tokenImage.getWidth() / 2;
            imageY = tokenImage.getHeight() / 2;
        }

        Point tokenPlanetPosition = getTokenPlanetPosition(tile, tokenID);
        if (imageX != null && imageY != null && planetX != null && planetY != null) {
            return new Point(tokenPlanetPosition.x - imageX + planetX, tokenPlanetPosition.y - imageY + planetY);
        }

        return tokenPlanetPosition;
    }

    static Point getTokenPlanetCenterOfImage(Tile tile, String tokenID) {
        return getTokenPlanetPosition(tile, tokenID);
    }
}
