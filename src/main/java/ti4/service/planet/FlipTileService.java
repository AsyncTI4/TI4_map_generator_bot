package ti4.service.planet;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.CommandCounterHelper;
import ti4.image.io.ImageResources;
import ti4.message.MessageHelper;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;

@UtilityClass
public class FlipTileService {

    public static String unlockMalliceIfNeeded(ButtonInteractionEvent event, String planet, Game game) {
        String unlockedPlanet = unlockedMalliceName(planet);
        if (unlockedPlanet.equals(planet)) {
            return planet;
        }
        Tile lockedTile = game.getTileFromPlanet(planet.toLowerCase());
        if (lockedTile != null) {
            flipTileIfNeeded(event, lockedTile, game);
        }
        return unlockedPlanet;
    }

    private static String unlockedMalliceName(String planet) {
        return switch (planet.toLowerCase()) {
            case "lockedmallice" -> "mallice";
            case "hexlockedmallice" -> "hexmallice";
            default -> planet;
        };
    }

    @NotNull
    public static Tile flipTileIfNeeded(Tile tile, Game game) {
        return flipTileIfNeeded(null, tile, game);
    }

    public static Tile flipTileIfNeeded(ButtonInteractionEvent event, Tile tile, Game game) {
        List<Player> players = new ArrayList<>();
        for (Player player : game.getRealPlayers()) {
            if (CommandCounterHelper.hasCC(player, tile)) {
                players.add(player);
            }
        }
        boolean flipped = false;
        if ("82a".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);
            String planetTileName = AliasHandler.resolveTile("82b");
            if (!MapPositionMapper.isTilePositionValid(position)) {
                if (event == null) throw new IllegalStateException("Position tile not allowed");
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return null;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ImageResources.tile(tileName);
            if (tilePath == null) {
                if (event == null) throw new IllegalStateException("Could not find tile: " + planetTileName);
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return null;
            }
            tile = new Tile(planetTileName, position, tile.getSpaceUnitHolder());
            game.setTile(tile);
            flipped = true;
        } else if ("82ah".equals(tile.getTileID())) {
            String position = tile.getPosition();
            game.removeTile(position);
            String planetTileName = AliasHandler.resolveTile("82bh");
            if (!MapPositionMapper.isTilePositionValid(position)) {
                if (event == null) throw new IllegalStateException("Position tile not allowed");
                MessageHelper.replyToMessage(event, "Position tile not allowed");
                return null;
            }

            String tileName = Mapper.getTileID(planetTileName);
            String tilePath = ImageResources.tile(tileName);
            if (tilePath == null) {
                if (event == null) throw new IllegalStateException("Could not find tile: " + planetTileName);
                MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
                return null;
            }
            tile = new Tile(planetTileName, position, tile.getSpaceUnitHolder());
            game.setTile(tile);
            flipped = true;
        } else if ("2025scptFinals".equals(game.getMapTemplateID())
                && List.of("528", "529", "530", "501", "502", "503", "504").contains(tile.getPosition())) {
            boolean anything = false;
            for (String pos : List.of("528", "529", "530", "501", "502", "503", "504")) {
                for (UnitHolder uh :
                        game.getTileByPosition(pos).getUnitHolders().values()) {
                    if (uh.hasUnits()) {
                        anything = true;
                        break;
                    }
                }
                if (anything) break;
            }
            if (anything) {
                Tile left = game.getTileByPosition("529");
                Tile right = game.getTileByPosition("503");
                if (!left.getSpaceUnitHolder().getTokenList().contains("token_whalpha.png")) {
                    left.addToken("token_whalpha.png", "space");
                    left.addToken("token_whbeta.png", "space");
                }
                if (!right.getSpaceUnitHolder().getTokenList().contains("token_whalpha.png")) {
                    right.addToken("token_whalpha.png", "space");
                    right.addToken("token_whbeta.png", "space");
                }
            }
        }
        if (flipped) {
            for (Player player : players) {
                if (!CommandCounterHelper.hasCC(player, tile)) {
                    CommandCounterHelper.addCC(event, player, tile);
                }
            }
        }
        return tile;
    }
}
