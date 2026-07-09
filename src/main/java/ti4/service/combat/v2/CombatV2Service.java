package ti4.service.combat.v2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.CombatV2Messages;
import ti4.service.combat.CombatV2RollData.Request;
import ti4.service.combat.CombatV2RollService;
import ti4.service.combat.CombatV2StartService;

/** Detects combats, starts the established Discord interface, and routes combat rolls. */
@UtilityClass
public class CombatV2Service {

    public static void combatCheck(Game game, GenericInteractionCreateEvent event, Tile tile) {
        detectAndStartSpaceCombat(game, event, tile);
        detectGroundCombats(game, event, tile);
    }

    private static void detectAndStartSpaceCombat(Game game, GenericInteractionCreateEvent event, Tile tile) {
        List<Player> players = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        if (players.size() <= 1) return;

        Player attacker = players.contains(game.getActivePlayer()) ? game.getActivePlayer() : players.getFirst();
        players.stream()
                .filter(player -> player != attacker && !attacker.isPlayerMemberOfAlliance(player))
                .findFirst()
                .ifPresent(defender -> startSpaceCombat(game, attacker, defender, tile, event, null));
    }

    private static void detectGroundCombats(Game game, GenericInteractionCreateEvent event, Tile tile) {
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            if (!Constants.SPACE.equals(holder.getName())) detectGroundCombat(game, event, holder);
        }
    }

    private static void detectGroundCombat(Game game, GenericInteractionCreateEvent event, UnitHolder holder) {
        List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, holder);
        if (players.size() <= 1) return;
        if (game.getActivePlayer() != null
                && !players.contains(game.getActivePlayer())
                && event instanceof ButtonInteractionEvent) return;

        Player attacker = players.contains(game.getActivePlayer()) ? game.getActivePlayer() : players.getFirst();
        players.stream()
                .filter(player -> player != attacker && !attacker.isPlayerMemberOfAlliance(player))
                .findFirst()
                .ifPresent(defender -> MessageHelper.sendMessageToChannel(
                        attacker.getCorrectChannel(),
                        CombatV2Messages.coexistencePrompt(attacker, holder.getName(), game),
                        List.of(
                                Buttons.red(
                                        "startCombatOn_" + holder.getName(), CombatV2Messages.engageInCombatLabel()),
                                Buttons.green("deleteButtons", CombatV2Messages.coexistLabel()))));
    }

    public static void startSpaceCombat(
            Game game,
            Player attacker,
            Player defender,
            Tile tile,
            GenericInteractionCreateEvent event,
            String specialCombatTitle) {
        CombatV2StartService.startSpaceCombat(game, attacker, defender, tile, event, specialCombatTitle);
    }

    public static void startGroundCombat(
            Player attacker,
            Player defender,
            Game game,
            GenericInteractionCreateEvent event,
            UnitHolder holder,
            Tile tile) {
        CombatV2StartService.startGroundCombat(attacker, defender, game, event, holder, tile);
    }

    public static int roll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        return interactiveRoll(new Request(player, game, event, tile, unitHolderName), rollType);
    }

    public static int roll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType,
            boolean automated) {
        return directRoll(new Request(player, game, event, tile, unitHolderName), rollType, automated);
    }

    private static int interactiveRoll(Request request, CombatRollType rollType) {
        return switch (rollType) {
            case combatround -> CombatV2RollService.combatRound(request);
            case AFB -> CombatV2RollService.antiFighterBarrage(request);
            case bombardment -> CombatV2RollService.bombardment(request);
            case SpaceCannonOffence -> CombatV2RollService.spaceCannonOffense(request);
            case SpaceCannonDefence -> CombatV2RollService.spaceCannonDefense(request);
        };
    }

    private static int directRoll(Request request, CombatRollType rollType, boolean automated) {
        return switch (rollType) {
            case combatround ->
                automated
                        ? CombatV2RollService.automatedCombatRound(request)
                        : CombatV2RollService.combatRound(request);
            case AFB -> CombatV2RollService.antiFighterBarrage(request);
            case bombardment -> CombatV2RollService.bombardmentTarget(request);
            case SpaceCannonOffence -> CombatV2RollService.spaceCannonOffense(request);
            case SpaceCannonDefence -> CombatV2RollService.spaceCannonDefense(request);
        };
    }
}
