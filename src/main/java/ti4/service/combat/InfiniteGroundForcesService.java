package ti4.service.combat;

import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

@UtilityClass
public class InfiniteGroundForcesService {

    public static boolean rollsInfinitely(Player player, UnitHolder combatOnHolder, CombatRollType rollType) {
        return rollType == CombatRollType.combatround
                && combatOnHolder instanceof Planet
                && combatOnHolder.hasInfiniteUnits(player.getColorID());
    }

    public static int roll(
            Game game,
            Player player,
            Player opponent,
            Tile tile,
            Planet planet,
            Map<UnitModel, Integer> opponentUnits,
            GenericInteractionCreateEvent event) {
        MessageHelper.sendMessageToEventChannel(
                event,
                player.getRepresentation() + " fights with an infinite stack of infantry on "
                        + planet.getRepresentation(game) + ". No dice are rolled: it produces ∞ hits.");
        int hits = hitsToDestroyEverything(opponentUnits);
        if (opponent == player || hits == 0) return hits;
        CombatRollService.sendGroundCombatHitAssignment(
                game, player, opponent, tile, planet, hits, event, "an infinite stack of infantry (∞ hits).");
        return hits;
    }

    private static int hitsToDestroyEverything(Map<UnitModel, Integer> opponentUnits) {
        return opponentUnits.entrySet().stream()
                .mapToInt(entry -> entry.getValue() * (entry.getKey().getSustainDamage() ? 2 : 1))
                .sum();
    }
}
