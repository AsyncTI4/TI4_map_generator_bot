package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.unit.UnitModelValueInjectionService;

@UtilityClass
public class MassHypnosisLLButtonHandler {
    private static final String MASS_HYPNOSIS = "massHypnosis";
    private static final String RESOLVE_MASS_HYPNOSIS = "resolveMassHypnosis";
    private static final String SELECT_MASS_HYPNOSIS_TARGET = "selectMassHypnosisTarget_";
    private static final String SELECT_MASS_HYPNOSIS_SHIP = "selectMassHypnosisShip_";
    private static final String HYPNOTIZED_ASYNC_SUFFIX = "_massHypnosis";

    @ButtonHandler(RESOLVE_MASS_HYPNOSIS)
    public static void resolveMassHypnosis(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (target != player) {
                buttons.add(FoWHelper.fogSafeTargetButton(
                        player.factionButtonChecker() + SELECT_MASS_HYPNOSIS_TARGET + target.getFaction(),
                        "green",
                        target));
            }
        }
        Player neutral = game.getNeutral();
        if (neutral != null && neutral != player) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + SELECT_MASS_HYPNOSIS_TARGET + neutral.getFaction(), "Neutral"));
        }
        ti4.message.MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationNoPing()
                        + ", choose the player whose ship you are selecting for _Mass Hypnosis_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_MASS_HYPNOSIS_TARGET)
    public static void selectMassHypnosisTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_MASS_HYPNOSIS_TARGET.length()));
        if (target == null
                && game.getNeutral() != null
                && game.getNeutral().getFaction().equals(buttonID.substring(SELECT_MASS_HYPNOSIS_TARGET.length()))) {
            target = game.getNeutral();
        }
        if (target == null || target == player) return;
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        Tile combatTile = combat == null || combat.tilePosition() == null
                ? game.getTileByPosition(game.getActiveSystem())
                : game.getTileByPosition(combat.tilePosition());
        if (combatTile == null) return;

        List<Button> buttons = getShipButtons(player, target, combatTile);
        if (buttons.isEmpty()) {
            ti4.message.MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    target.getRepresentationNoPing() + " has no ships in the combat system.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String message = player.getRepresentationNoPing()
                + ", choose a ship controlled by " + target.getRepresentationNoPing()
                + " for _Mass Hypnosis_.";
        ti4.message.MessageHelper.editMessageWithButtons(
                event,
                message,
                NewStuffHelper.buttonPagination(
                        buttons,
                        player.factionButtonChecker() + SELECT_MASS_HYPNOSIS_SHIP + target.getFaction() + "|",
                        0));
    }

    @ButtonHandler(SELECT_MASS_HYPNOSIS_SHIP)
    public static void selectMassHypnosisShip(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_MASS_HYPNOSIS_SHIP.length()).split("\\|", 4);
        Player target = values.length >= 1 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (target == null
                && values.length >= 1
                && game.getNeutral().getFaction().equals(values[0])) {
            target = game.getNeutral();
        }
        if (target != null) {
            StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
            Tile combatTile = combat == null || combat.tilePosition() == null
                    ? game.getTileByPosition(game.getActiveSystem())
                    : game.getTileByPosition(combat.tilePosition());
            List<Button> buttons = combatTile == null ? List.of() : getShipButtons(player, target, combatTile);
            String message = player.getRepresentationNoPing()
                    + ", choose a ship controlled by " + target.getRepresentationNoPing()
                    + " for _Mass Hypnosis_.";
            if (NewStuffHelper.checkAndHandlePaginationChange(
                    event,
                    event.getMessageChannel(),
                    buttons,
                    message,
                    player.factionButtonChecker() + SELECT_MASS_HYPNOSIS_SHIP + target.getFaction() + "|",
                    buttonID)) return;
        }
        Tile tile = values.length == 4 ? game.getTileByPosition(values[1]) : null;
        UnitHolder space = tile == null ? null : tile.getSpaceUnitHolder();
        UnitKey unitKey = space == null || target == null
                ? null
                : space.getUnitKeysForPlayer(target).stream()
                        .filter(key -> key.asyncID().equals(values[2]))
                        .findFirst()
                        .orElse(null);
        UnitState state = values.length == 4 ? UnitState.valueOf(values[3]) : null;

        UnitModel unit = unitKey == null ? null : target.getPriorityUnitByAsyncID(unitKey.asyncID(), space);

        if (target == null
                || tile == null
                || unitKey == null
                || unit == null
                || state == null
                || space.getUnitCountForState(unitKey, state) < 1
                || !unit.getIsShip()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue(
                MASS_HYPNOSIS,
                String.join("|", player.getFaction(), target.getFaction(), tile.getPosition(), unitKey.asyncID()));

        ti4.message.MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationNoPing() + " selected 1 "
                        + (state == UnitState.none ? "" : state.humanDescr() + " ")
                        + unitKey.humanReadableName() + " for _Mass Hypnosis_.");

        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getShipButtons(Player player, Player target, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        UnitHolder space = tile.getSpaceUnitHolder();
        for (UnitKey unitKey : space.getUnitKeysForPlayer(target)) {
            UnitModel unit = target.getPriorityUnitByAsyncID(unitKey.asyncID(), space);
            if (unit == null || !unit.getIsShip()) continue;
            for (UnitState state : space.getNonZeroUnitStates(unitKey)) {
                if (space.getUnitCountForState(unitKey, state) < 1) continue;
                String stateText = state == UnitState.none ? "" : state.humanDescr() + " ";
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SELECT_MASS_HYPNOSIS_SHIP + target.getFaction() + "|"
                                + tile.getPosition() + "|" + unitKey.asyncID() + "|" + state.name(),
                        "Select " + stateText + unitKey.humanReadableName(),
                        unit.getUnitEmoji()));
            }
        }
        return buttons;
    }

    public static Map<Pair<UnitModel, UnitHolder>, Integer> splitHypnotizedShipForRoll(
            Game game,
            Player rollingPlayer,
            Tile tile,
            UnitHolder combatHolder,
            CombatRollType rollType,
            Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        String[] state = game.getStoredValue(MASS_HYPNOSIS).split("\\|", 4);
        if (state.length != 4
                || rollType != CombatRollType.combatround
                || !Constants.SPACE.equals(combatHolder.getName())
                || !state[1].equals(rollingPlayer.getFaction())
                || !state[2].equals(tile.getPosition())) {
            return units;
        }

        Map<Pair<UnitModel, UnitHolder>, Integer> adjustedUnits = new HashMap<>(units);
        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : units.entrySet()) {
            UnitModel unit = entry.getKey().getLeft();
            UnitHolder holder = entry.getKey().getRight();

            if (holder != combatHolder || unit == null || !unit.getAsyncId().equals(state[3]) || entry.getValue() < 1) {
                continue;
            }

            if (entry.getValue() == 1) {
                adjustedUnits.remove(entry.getKey());
            } else {
                adjustedUnits.put(entry.getKey(), entry.getValue() - 1);
            }

            UnitModel hypnotizedUnit = UnitModelValueInjectionService.injectTemporaryValues(
                    unit, UnitModelValueInjectionService.UnitValueInjection.empty());
            hypnotizedUnit.setAsyncId(unit.getAsyncId() + HYPNOTIZED_ASYNC_SUFFIX);
            adjustedUnits.put(new ImmutablePair<>(hypnotizedUnit, holder), 1);
            return adjustedUnits;
        }

        game.removeStoredValue(MASS_HYPNOSIS);
        return units;
    }

    public static int getRedirectedHits(
            Game game,
            Player rollingPlayer,
            Tile tile,
            UnitHolder combatHolder,
            CombatRollType rollType,
            CombatRollPayload payload) {
        String[] state = game.getStoredValue(MASS_HYPNOSIS).split("\\|", 4);
        if (state.length != 4
                || rollType != CombatRollType.combatround
                || !Constants.SPACE.equals(combatHolder.getName())
                || !state[1].equals(rollingPlayer.getFaction())
                || !state[2].equals(tile.getPosition())) {
            return 0;
        }

        int redirectedHits = payload.unitRolls().stream()
                .filter(unitRoll -> unitRoll.asyncId().equals(state[3] + HYPNOTIZED_ASYNC_SUFFIX))
                .mapToInt(CombatRollPayload.UnitRoll::hits)
                .sum();

        game.removeStoredValue(MASS_HYPNOSIS);
        return redirectedHits;
    }

    public static boolean isHypnotizedRollModel(UnitModel unit) {
        return unit != null && unit.getAsyncId().endsWith(HYPNOTIZED_ASYNC_SUFFIX);
    }

    public static void clearMassHypnosis(Game game) {
        game.removeStoredValue(MASS_HYPNOSIS);
    }
}
