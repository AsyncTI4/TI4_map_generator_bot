package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.contest.replay.core.CombatRollPayload;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.unit.RemoveUnitService;
import ti4.service.unit.UnitModelValueInjectionService;
import ti4.service.unit.UnitModelValueInjectionService.IntegerValueInjection;
import ti4.service.unit.UnitModelValueInjectionService.UnitValueInjection;

@UtilityClass
public class RiggedExplosivesLLButtonHandler {
    private static final String RIGGED_EXPLOSIVES = "riggedExplosives_";
    private static final String RESOLVE_RIGGED_EXPLOSIVES = "resolveRiggedExplosives";
    private static final String SELECT_RIGGED_EXPLOSIVES = "selectRiggedExplosives_";
    private static final String DONE_RIGGED_EXPLOSIVES = "doneRiggedExplosives";

    @ButtonHandler(RESOLVE_RIGGED_EXPLOSIVES)
    public static void resolveRiggedExplosives(ButtonInteractionEvent event, Game game, Player player) {
        for (int selection = 1; selection <= 5; selection++) {
            game.removeStoredValue(getStateKey(player, selection));
        }
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile == null) {
            return;
        }

        List<Button> buttons = getGroundForceButtons(game, player, tile);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", you have no ground forces in the active system for _Rigged Explosives_.");
            return;
        }

        sendGroundForceButtons(event, game, player, tile);
    }

    @ButtonHandler(SELECT_RIGGED_EXPLOSIVES)
    public static void selectRiggedExplosivesGroundForce(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_RIGGED_EXPLOSIVES.length()).split("\\|", 3);
        if (values.length != 3) {
            return;
        }

        Tile tile = game.getTileByPosition(values[0]);
        Tile activeSystem = game.getTileByPosition(game.getActiveSystem());
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitKeysForPlayer(player).stream()
                        .filter(key -> key.asyncID().equals(values[2]))
                        .findFirst()
                        .orElse(null);
        UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);

        if (tile == null
                || tile != activeSystem
                || holder == null
                || unitKey == null
                || unit == null
                || !unit.getIsGroundForce()
                || getSelectionCount(game, player) >= 5
                || holder.getUnitCount(unitKey) <= getSelectedUnitCount(game, player, tile, holder, unitKey)) {
            return;
        }

        int selection = getSelectionCount(game, player) + 1;
        game.setStoredValue(
                getStateKey(player, selection),
                String.join("|", tile.getPosition(), holder.getName(), unitKey.asyncID()));

        sendGroundForceButtons(event, game, player, tile);
    }

    @ButtonHandler(DONE_RIGGED_EXPLOSIVES)
    public static void doneRiggedExplosives(ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
    }

    public static List<UnitModel> getRiggedExplosivesCannons(Game game, Player player, Tile tile) {
        List<UnitModel> cannons = new ArrayList<>();

        for (int selection = 1; selection <= 5; selection++) {
            String[] values =
                    game.getStoredValue(getStateKey(player, selection)).split("\\|", 3);
            if (values.length != 3 || tile == null || !tile.getPosition().equals(values[0])) {
                continue;
            }

            UnitHolder holder = tile.getUnitHolders().get(values[1]);
            UnitKey unitKey = holder == null
                    ? null
                    : holder.getUnitKeysForPlayer(player).stream()
                            .filter(key -> key.asyncID().equals(values[2]))
                            .findFirst()
                            .orElse(null);
            UnitModel unit = unitKey == null ? null : player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);

            if (unit == null || !unit.getIsGroundForce() || holder.getUnitCount(unitKey) < 1) {
                continue;
            }

            UnitModel cannon = UnitModelValueInjectionService.injectTemporaryValues(
                    unit,
                    UnitValueInjection.of(IntegerValueInjection.create()
                            .spaceCannonHitsOn(unit.getCombatHitsOn())
                            .spaceCannonDieCount(unit.getCombatDieCount())));
            cannon.setAsyncId(unit.getAsyncId() + "_riggedExplosives_" + selection);
            cannon.setName(unit.getName() + " (_Rigged Explosives_)");
            cannons.add(cannon);
        }

        return cannons;
    }

    public static void destroyFailedRiggedExplosives(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            CombatRollType rollType,
            CombatRollPayload payload) {
        if (rollType != CombatRollType.SpaceCannonOffence || payload == null) {
            return;
        }

        List<String> destroyed = new ArrayList<>();
        for (int selection = 1; selection <= 5; selection++) {
            String stateKey = getStateKey(player, selection);
            String[] values = game.getStoredValue(stateKey).split("\\|", 3);
            if (values.length != 3 || tile == null || !tile.getPosition().equals(values[0])) {
                continue;
            }

            String riggedAsyncId = values[2] + "_riggedExplosives_" + selection;
            int hits = payload.unitRolls().stream()
                    .filter(roll -> riggedAsyncId.equals(roll.asyncId()))
                    .mapToInt(CombatRollPayload.UnitRoll::hits)
                    .sum();

            UnitHolder holder = tile.getUnitHolders().get(values[1]);
            UnitKey unitKey = holder == null
                    ? null
                    : holder.getUnitKeysForPlayer(player).stream()
                            .filter(key -> key.asyncID().equals(values[2]))
                            .findFirst()
                            .orElse(null);

            if (hits < 1 && holder != null && unitKey != null && holder.getUnitCount(unitKey) > 0) {
                RemoveUnitService.removeUnit(event, tile, game, player, holder, unitKey.unitType(), 1);
                destroyed.add(unitKey.humanReadableName());
            }
            game.removeStoredValue(stateKey);
        }

        if (!destroyed.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + " destroyed "
                            + String.join(", ", destroyed)
                            + " because they missed with _Rigged Explosives_.");
        }
    }

    public static void clearRiggedExplosives(Game game) {
        for (Player player : game.getRealPlayers()) {
            for (int selection = 1; selection <= 5; selection++) {
                game.removeStoredValue(getStateKey(player, selection));
            }
        }
    }

    private static void sendGroundForceButtons(ButtonInteractionEvent event, Game game, Player player, Tile tile) {
        List<Button> buttons = getGroundForceButtons(game, player, tile);
        buttons.add(Buttons.red(player.factionButtonChecker() + DONE_RIGGED_EXPLOSIVES, "Done Selecting"));

        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", select up to "
                        + (5 - getSelectionCount(game, player))
                        + " ground forces for _Rigged Explosives_.",
                buttons);
    }

    private static List<Button> getGroundForceButtons(Game game, Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();

        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                UnitModel unit = player.getPriorityUnitByAsyncID(unitKey.asyncID(), holder);
                if (unit == null
                        || !unit.getIsGroundForce()
                        || holder.getUnitCount(unitKey) <= getSelectedUnitCount(game, player, tile, holder, unitKey)) {
                    continue;
                }

                buttons.add(Buttons.green(
                        player.factionButtonChecker()
                                + SELECT_RIGGED_EXPLOSIVES
                                + tile.getPosition()
                                + "|"
                                + holder.getName()
                                + "|"
                                + unitKey.asyncID(),
                        "Select " + unit.getName() + " on " + Helper.getPlanetRepresentation(holder.getName(), game),
                        unitKey.unitEmoji()));
            }
        }
        return buttons;
    }

    private static int getSelectionCount(Game game, Player player) {
        int count = 0;
        for (int selection = 1; selection <= 5; selection++) {
            if (!game.getStoredValue(getStateKey(player, selection)).isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static int getSelectedUnitCount(Game game, Player player, Tile tile, UnitHolder holder, UnitKey unitKey) {
        int count = 0;
        for (int selection = 1; selection <= 5; selection++) {
            String[] values =
                    game.getStoredValue(getStateKey(player, selection)).split("\\|", 3);
            if (values.length == 3
                    && tile.getPosition().equals(values[0])
                    && holder.getName().equals(values[1])
                    && unitKey.asyncID().equals(values[2])) {
                count++;
            }
        }
        return count;
    }

    private static String getStateKey(Player player, int selection) {
        return RIGGED_EXPLOSIVES + player.getFaction() + "_" + selection;
    }
}
