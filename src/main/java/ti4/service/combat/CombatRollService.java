package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.contest.replay.core.CombatRollPayloadBuilder;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraUnitHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.fow.FOWCombatThreadMirroring;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@UtilityClass
public class CombatRollService {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public boolean checkIfUnitsOfType(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        UnitHolder combatOnHolder = tile.getUnitHolders().get(unitHolderName);
        Map<UnitModel, Integer> playerUnitsByQuantity =
                CombatUnitResolver.getUnitsInCombat(tile, combatOnHolder, player, event, rollType, game);
        return !playerUnitsByQuantity.isEmpty();
    }

    public static CombatRollResult secondHalfOfCombatRoll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType) {
        if (rollType == CombatRollType.bombardment) {
            AshenUnitHandler.clearFlagshipBombardmentContexts(game);
            if (game.getStoredValue("assignedBombardment" + player.getFaction()).isEmpty()) {
                BombardmentService.autoAssignAllBombardmentToAPlanet(player, game, tile);
            }
            List<BombardmentAssignment> assignedUnits = MAPPER.readValue(
                    game.getStoredValue("assignedBombardment" + player.getFaction()),
                    new TypeReference<List<BombardmentAssignment>>() {});

            CombatRollResult result = CombatRollResult.stopped(CombatRollStatus.NO_ELIGIBLE_UNITS);
            List<String> bombardedPlanets = new ArrayList<>();
            for (String planet : BombardmentService.getBombardablePlanets(player, game, tile)) {
                if (assignedUnits.stream().anyMatch(a -> a.planet().equals(planet))) {
                    game.setStoredValue("bombardmentTarget" + player.getFaction(), planet);
                    result =
                            runCombatRoll(player, game, event, tile, unitHolderName, CombatRollType.bombardment, false);
                    bombardedPlanets.add(planet);
                }
            }
            if (bombardedPlanets.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        "No valid bombardment target found. Please assign bombardment to a planet using the buttons and try again.");
            } else if (ButtonHelper.doesPlayerHaveFSHere("kalora_flagship", player, tile)) {
                KaloraUnitHandler.flagshipBombardmentReroll(
                        player, event.getMessageChannel(), tile.getPosition(), bombardedPlanets);
            }
            return result;
        }
        return runCombatRoll(player, game, event, tile, unitHolderName, rollType, false);
    }

    public static CombatRollResult runCombatRoll(
            Player player,
            Game game,
            GenericInteractionCreateEvent event,
            Tile tile,
            String unitHolderName,
            CombatRollType rollType,
            boolean automated) {
        CombatContext state = new CombatContext(player, game, event, tile, unitHolderName, rollType, automated);
        CombatRollPreparation.validateCombatRollLocation(state);
        if (state.isStopped()) return CombatRollResult.stopped(state.stoppedStatus);
        CombatRollPreparation.prepareCombatRoll(state);
        if (state.isStopped()) return CombatRollResult.stopped(state.stoppedStatus);
        executeCombatRoll(state);
        loadCombatRounds(state);
        announceCombatRound(state);
        CombatRollPublication.publish(state);
        return state.rawRollResult.withPublishedResult(
                state.publishedMessage, state.publishedHits, state.publishedPayload);
    }

    private static void executeCombatRoll(CombatContext state) {
        CombatRollResult result = UnitRollExecution.rollForUnitsWithResult(state);
        String summary = CombatMessageHelper.displayCombatSummary(
                state.player, state.tile, state.combatOnHolder, state.rollType);
        String message = summary + result.message();
        var payload = CombatRollPayloadBuilder.attachHeader(
                result.payload(),
                state.player,
                state.opponent,
                state.game,
                state.tile,
                state.combatOnHolder,
                state.rollType,
                summary);
        FOWCombatThreadMirroring.mirrorCombatMessage(state.event, state.player, state.game, message);
        state.rawRollResult = result;
        state.publishedMessage = message;
        state.publishedPayload = payload;
    }

    private static void loadCombatRounds(CombatContext state) {
        state.opponentRound = getStoredCombatRound(state, state.opponent, 0);
        state.playerRound = getStoredCombatRound(state, state.player, 1);
    }

    private static int getStoredCombatRound(CombatContext state, Player player, int defaultRound) {
        String key =
                "combatRoundTracker" + player.getFaction() + state.tile.getPosition() + state.combatOnHolder.getName();
        String storedRound = state.game.getStoredValue(key);
        return storedRound.isEmpty() ? defaultRound : Integer.parseInt(storedRound);
    }

    private static void announceCombatRound(CombatContext state) {
        if (state.playerRound > state.opponentRound && state.rollType == CombatRollType.combatround) {
            MessageHelper.sendMessageToChannel(
                    state.event.getMessageChannel(), "## __Start of Combat Round #" + state.playerRound + "__");
        }
    }
}
