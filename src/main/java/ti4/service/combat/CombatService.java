package ti4.service.combat;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CombatTempModHelper;
import ti4.service.combat.v2.CombatV2Config;
import ti4.service.combat.v2.CombatV2Modifiers;
import ti4.service.combat.v2.CombatV2RollData.Request;
import ti4.service.combat.v2.CombatV2RollService;
import ti4.service.combat.v2.CombatV2UnitService;

/** Routes combat detection, startup, and rolls through the configured combat implementation. */
@UtilityClass
public class CombatService {

    public static boolean checkIfUnitsOfType(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        if (!CombatV2Config.isEnabled(game)) {
            return CombatRollService.checkIfUnitsOfType(player, game, event, tile, unitHolderName, rollType);
        }
        return CombatV2UnitService.checkIfUnitsOfType(new Request(player, game, event, tile, unitHolderName), rollType);
    }

    public static boolean canActivateModifier(Game game, String sourceType, String sourceId) {
        return CombatV2Config.isEnabled(game)
                ? CombatV2Modifiers.canActivate(sourceType, sourceId)
                : CombatTempModHelper.getPossibleTempModifier(sourceType, sourceId, 0) != null;
    }

    public static boolean activateModifier(Game game, Player player, String sourceType, String sourceId) {
        if (CombatV2Config.isEnabled(game)) {
            return CombatV2Modifiers.activate(player, sourceType, sourceId);
        }
        var modifier = CombatTempModHelper.getPossibleTempModifier(sourceType, sourceId, player.getNumberOfTurns());
        if (modifier == null) return false;
        player.addNewTempCombatMod(modifier);
        return true;
    }

    public static void combatCheck(Game game, GenericInteractionCreateEvent event, Tile tile) {
        StartCombatService.combatCheck(game, event, tile);
    }

    public static void startSpaceCombat(
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            GenericInteractionCreateEvent event,
            String specialCombatTitle) {
        StartCombatService.startSpaceCombat(game, attacker, defender, tile, event, specialCombatTitle);
    }

    public static void startGroundCombat(
            Player attacker,
            Player defender,
            Game game,
            GenericInteractionCreateEvent event,
            UnitHolder holder,
            Tile tile) {
        StartCombatService.startGroundCombat(attacker, defender, game, event, holder, tile);
    }

    public static int roll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        if (!CombatV2Config.isEnabled(game)) {
            return CombatRollService.secondHalfOfCombatRoll(player, game, event, tile, unitHolderName, rollType);
        }
        Request request = new Request(player, game, event, tile, unitHolderName);
        return switch (rollType) {
            case combatround -> CombatV2RollService.combatRound(request);
            case AFB -> CombatV2RollService.antiFighterBarrage(request);
            case bombardment -> CombatV2RollService.bombardment(request);
            case SpaceCannonOffence -> CombatV2RollService.spaceCannonOffense(request);
            case SpaceCannonDefence -> CombatV2RollService.spaceCannonDefense(request);
        };
    }

    public static int automatedCombatRound(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName) {
        if (!CombatV2Config.isEnabled(game)) {
            return CombatRollService.secondHalfOfCombatRoll(
                    player, game, event, tile, unitHolderName, CombatRollType.combatround, true);
        }
        return CombatV2RollService.automatedCombatRound(new Request(player, game, event, tile, unitHolderName));
    }

    public static int bombardmentTarget(
            Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String unitHolderName) {
        if (!CombatV2Config.isEnabled(game)) {
            return CombatRollService.secondHalfOfCombatRoll(
                    player, game, event, tile, unitHolderName, CombatRollType.bombardment, false);
        }
        return CombatV2RollService.bombardmentTarget(new Request(player, game, event, tile, unitHolderName));
    }
}
