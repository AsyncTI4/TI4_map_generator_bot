package ti4.service.combat;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.service.combat.v2.CombatV2Config;
import ti4.service.combat.v2.CombatV2Service;

/** Routes combat detection, startup, and rolls through the configured combat implementation. */
@UtilityClass
public class CombatService {

    public static void combatCheck(Game game, GenericInteractionCreateEvent event, Tile tile) {
        if (CombatV2Config.isEnabled(game)) {
            CombatV2Service.combatCheck(game, event, tile);
        } else {
            StartCombatService.combatCheck(game, event, tile);
        }
    }

    public static void startSpaceCombat(
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            GenericInteractionCreateEvent event,
            String specialCombatTitle) {
        if (CombatV2Config.isEnabled(game)) {
            CombatV2Service.startSpaceCombat(game, attacker, defender, tile, event, specialCombatTitle);
        } else {
            StartCombatService.startSpaceCombat(game, attacker, defender, tile, event, specialCombatTitle);
        }
    }

    public static void startGroundCombat(
            Player attacker,
            Player defender,
            Game game,
            GenericInteractionCreateEvent event,
            UnitHolder holder,
            Tile tile) {
        if (CombatV2Config.isEnabled(game)) {
            CombatV2Service.startGroundCombat(attacker, defender, game, event, holder, tile);
        } else {
            StartCombatService.startGroundCombat(attacker, defender, game, event, holder, tile);
        }
    }

    public static int roll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        return CombatV2Config.isEnabled(game)
                ? CombatV2Service.roll(player, game, event, tile, unitHolderName, rollType)
                : CombatRollService.secondHalfOfCombatRoll(player, game, event, tile, unitHolderName, rollType);
    }

    public static int roll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType,
            boolean automated) {
        return CombatV2Config.isEnabled(game)
                ? CombatV2Service.roll(player, game, event, tile, unitHolderName, rollType, automated)
                : CombatRollService.secondHalfOfCombatRoll(
                        player, game, event, tile, unitHolderName, rollType, automated);
    }
}
