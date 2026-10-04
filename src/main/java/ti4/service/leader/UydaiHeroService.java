package ti4.service.leader;

import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.message.MessageHelper;

@UtilityClass
public class UydaiHeroService {

    private static final String HERO_PLAYER = "uydaiHeroPlayer";
    private static final String HERO_SYSTEM = "uydaiHeroSystem";

    public static void purgeHero(ButtonInteractionEvent event, Player player, Game game) {
        Tile activeTile = game.getTileByPosition(game.getCurrentActiveSystem());
        if (activeTile == null || activeTile.isHomeSystem(game) || !player.hasLeaderUnlocked("uydaihero")) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Use your unlocked Uydai hero after activating a non-home system.");
            return;
        }
        PurgeHeroService.purgeHeroPreamble(event, player, game, "uydaihero", "Londor II, the Uydai hero");
        game.setStoredValue(HERO_PLAYER, player.getUserID());
        game.setStoredValue(HERO_SYSTEM, activeTile.getPosition());
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + ", all of your units are treated as adjacent to "
                        + activeTile.getRepresentationForButtons(game, player)
                        + " until the end of this tactical action. Continue using the movement buttons.");
    }

    public static void addAdjacencies(Game game, Player player, String position, Set<String> adjacentPositions) {
        String activeSystem = game.getStoredValue(HERO_SYSTEM);
        if (player == null
                || !player.getUserID().equals(game.getStoredValue(HERO_PLAYER))
                || activeSystem.isEmpty()
                || !activeSystem.equals(game.getCurrentActiveSystem())) {
            return;
        }
        if (activeSystem.equals(position)) {
            for (Tile tile : game.getTileMap().values()) {
                if (hasUnitsOrMovedUnits(game, player, tile)) {
                    adjacentPositions.add(tile.getPosition());
                }
            }
        } else if (hasUnitsOrMovedUnits(game, player, game.getTileByPosition(position))) {
            adjacentPositions.add(activeSystem);
        }
    }

    private static boolean hasUnitsOrMovedUnits(Game game, Player player, Tile tile) {
        return tile != null
                && (tile.containsPlayersUnits(player)
                        || game.getTacticalActionDisplacement().entrySet().stream()
                                .filter(entry -> entry.getKey().startsWith(tile.getPosition() + "-"))
                                .anyMatch(entry -> entry.getValue().entrySet().stream()
                                        .anyMatch(
                                                unit -> unit.getKey().colorID().equals(player.getColorID())
                                                        && unit.getValue().stream()
                                                                .anyMatch(count -> count > 0))));
    }

    public static void clear(Game game) {
        game.removeStoredValue(HERO_PLAYER);
        game.removeStoredValue(HERO_SYSTEM);
    }
}
