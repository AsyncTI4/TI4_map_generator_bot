package ti4.service.map;

import java.awt.Point;
import java.util.Map;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.TokenModel;

public class TokenPlanetService {
    public enum TokenPlanets {
        mirage,

        cradle,
        oasis,

        illusion,
        phantasm,
    }

    public static String adsf() {
        return TokenPlanets.mirage.name();
    }

    public static boolean isTokenPlanet(String tokenOrPlanetName) {
        if (tokenOrPlanetName == null || tokenOrPlanetName.isBlank()) return false;
        if (Constants.TOKEN_PLANETS.contains(tokenOrPlanetName)) return true;

        TokenModel token = Mapper.getToken(tokenOrPlanetName);
        if (token.getTokenPlanetName() != null && Constants.TOKEN_PLANETS.contains(token.getTokenPlanetName()))
            return true;
        return false;
    }

    public static void moveTokenPlanet(Game game, Player player, Tile destination, String planetName) {
        Tile oldTile = game.getTileFromPlanet(planetName);
        if (oldTile == null) return;

        Planet oldPlanet = oldTile.getUnitHolderFromPlanet(planetName);
        if (oldPlanet == null) return;
        PlanetModel model = oldPlanet.getPlanetModel();

        // Move the UnitHolder over
        oldTile.getUnitHolders().remove(planetName);
        destination.getUnitHolders().put(planetName, oldPlanet);
        game.clearPlanetsCache();

        // Move the token over
        addTokenPlanetToTile(game, destination, planetName);
        String tokenID = Mapper.getTokenID(planetName);
        oldTile.getSpaceUnitHolder().removeToken(tokenID);
        destination.getSpaceUnitHolder().addToken(tokenID);

        // Inform the player
        if (player != null) {
            String message = player.getRepresentation() + " moved " + model.getName() + " from "
                    + oldTile.getRepresentationForButtons(game, player) + " to "
                    + destination.getRepresentationForButtons(game, player) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        }
    }

    public static void addTokenPlanetToTile(Game game, Tile tile, String planetName) {
        if (!isTokenPlanet(planetName) || tile.getUnitHolderFromPlanet(planetName) != null) return;

        Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
        Point tokenPlanetPosition = Constants.TOKEN_PLANET_POSITION;
        if (tile.getTileModel().getNumPlanets() == 3) {
            tokenPlanetPosition = Constants.MIRAGE_TRIPLE_POSITION;
        }

        Point tokenPlanetCenter = Constants.TOKEN_PLANET_CENTER_OFFSET;
        Point planetCenter =
                new Point(tokenPlanetPosition.x + tokenPlanetCenter.x, tokenPlanetPosition.y + tokenPlanetCenter.y);
        Planet planetObject = new Planet(planetName, planetCenter);
        unitHolders.put(planetName, planetObject);
    }

    public static void removeTokenPlanetFromTile(Game game, String planetName) {
        Tile tile = game.getTileFromPlanet(planetName);
        if (!isTokenPlanet(planetName) || tile == null) return;

        String tokenPath = Mapper.getTokenPath(planetName);
        tile.getUnitHolders().remove(planetName);
        tile.getSpaceUnitHolder().removeToken(tokenPath);
    }
}
