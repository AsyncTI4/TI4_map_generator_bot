package ti4.discord.interactions.buttons.handlers.actioncards.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;

@UtilityClass
public class RaisedMoraleLLButtonHandler {
    private static final String RESOLVE_RAISED_MORALE = "resolveRaisedMorale";
    private static final String RAISED_MORALE_STATE = "raisedMorale_";

    @ButtonHandler(RESOLVE_RAISED_MORALE)
    public static void resolveRaisedMorale(ButtonInteractionEvent event, Game game, Player player) {
        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        Tile tile =
                combat == null || combat.tilePosition() == null ? null : game.getTileByPosition(combat.tilePosition());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " could not find a combat system for _Raised Morale_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> changes = new ArrayList<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                int damaged = holder.getUnitCountForState(unitKey, UnitState.dmg);
                if (damaged < 1) continue;

                int baseline = holder.getUnitCountForState(unitKey, UnitState.dmg_glv);
                holder.removeUnit(unitKey, damaged, UnitState.dmg);
                List<Integer> states = UnitState.emptyList();
                states.set(UnitState.dmg_glv.ordinal(), damaged);
                holder.addUnitsWithStates(unitKey, states);
                changes.add(String.join(
                        "|",
                        tile.getPosition(),
                        holder.getName(),
                        unitKey.asyncID(),
                        Integer.toString(baseline),
                        Integer.toString(damaged)));
            }
        }

        if (changes.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " has no damaged units in " + tile.getRepresentation()
                            + " to galvanize for _Raised Morale_.");
        } else {
            String key = RAISED_MORALE_STATE + player.getFaction();
            String previous = game.getStoredValue(key);
            game.setStoredValue(
                    key, previous.isBlank() ? String.join(";", changes) : previous + ";" + String.join(";", changes));
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + " used _Raised Morale_. Their damaged units in "
                            + tile.getRepresentation() + " are galvanized until this combat ends.");
        }
        ButtonHelper.deleteMessage(event);
    }

    public static void clearRaisedMorale(Game game) {
        for (Player player : game.getRealPlayers()) {
            String key = RAISED_MORALE_STATE + player.getFaction();
            for (String change : game.getStoredValue(key).split(";")) {
                String[] values = change.split("\\|", 5);
                if (values.length != 5) continue;

                Tile tile = game.getTileByPosition(values[0]);
                UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
                UnitKey unitKey = holder == null
                        ? null
                        : holder.getUnitKeysForPlayer(player).stream()
                                .filter(unit -> unit.asyncID().equals(values[2]))
                                .findFirst()
                                .orElse(null);
                if (unitKey == null) continue;

                int baseline = NumberUtils.toInt(values[3]);
                int added = NumberUtils.toInt(values[4]);
                int remove = Math.min(
                        added, Math.max(0, holder.getUnitCountForState(unitKey, UnitState.dmg_glv) - baseline));
                if (remove > 0) {
                    holder.removeUnit(unitKey, remove, UnitState.dmg_glv);
                    List<Integer> states = UnitState.emptyList();
                    states.set(UnitState.dmg.ordinal(), remove);
                    holder.addUnitsWithStates(unitKey, states);
                }
            }
            game.removeStoredValue(key);
        }
    }
}
