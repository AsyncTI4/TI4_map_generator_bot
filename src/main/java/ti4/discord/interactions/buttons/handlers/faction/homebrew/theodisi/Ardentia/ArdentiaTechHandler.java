package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Ardentia;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;

@UtilityClass
public class ArdentiaTechHandler {
    private static final String OVERLORD = "thardentiar";
    private static final String USE_OVERLORD = "useOverlordMatrix";
    private static final String OVERLORD_UNIT = "overlordMatrixUnit_";

    public static void offerOverlordMatrixButton(Game game, Tile tile) {
        for (Player player : game.getRealPlayers()) {
            boolean hasUnitsInActivatedSystem = FoWHelper.playerHasUnitsInSystem(player, tile);
            if (!player.hasTechReady("thardentiar") || player.getTacticalCC() == 0 || !hasUnitsInActivatedSystem) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", you may exhaust _Overlord Matrix_ to galvanize units for this tactical action.",
                    List.of(
                            Buttons.green(player.factionButtonChecker() + USE_OVERLORD, "Exhaust Overlord Matrix"),
                            Buttons.red(player.factionButtonChecker() + "deleteButtons", "Decline")));
        }
    }

    @ButtonHandler(USE_OVERLORD)
    public static void useOverlordMatrix(ButtonInteractionEvent event, Game game, Player player) {
        if (player == null || game == null || !player.hasTechReady(OVERLORD)) {
            return;
        }

        player.exhaustTech(OVERLORD);
        game.setStoredValue(OVERLORD + "Remaining" + player.getFaction(), Integer.toString(player.getTacticalCC()));
        game.removeStoredValue(OVERLORD + "Tracked" + player.getFaction());
        sendOverlordMatrixButtons(event.getMessageChannel(), game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(OVERLORD_UNIT)
    public static void resolveOverlordMatrix(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int remaining = Integer.parseInt(game.getStoredValue(OVERLORD + "Remaining" + player.getFaction()));
        if (remaining < 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String[] parts = buttonID.replace(OVERLORD_UNIT, "").split("\\|", 3);
        Tile tile = game.getTileByPosition(parts[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(parts[1]);
        UnitKey unitKey = holder == null
                ? null
                : holder.getUnitsByState().keySet().stream()
                        .filter(unit -> unit.asyncID().equals(parts[2]))
                        .findFirst()
                        .orElse(null);
        if (unitKey == null
                || !player.unitBelongsToPlayer(unitKey)
                || holder.getUnitCount(unitKey) <= holder.getGalvanizedUnitCount(unitKey)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trackedKey = OVERLORD + "Tracked" + player.getFaction();
        String baselineKey = OVERLORD + "Baseline" + player.getFaction() + unitKey.asyncID();
        if (game.getStoredValue(baselineKey).isEmpty()) {
            int baseline = 0;
            for (Tile tile_ : game.getTileMap().values()) {
                for (UnitHolder holder_ : tile_.getUnitHolders().values()) {
                    for (UnitKey unit_ : holder_.getUnitsByState().keySet()) {
                        if (unit_.asyncID().equals(unitKey.asyncID())) {
                            baseline += holder_.getGalvanizedUnitCount(unit_);
                        }
                    }
                }
            }
            game.setStoredValue(baselineKey, Integer.toString(baseline));
            game.setStoredValue(trackedKey, game.getStoredValue(trackedKey) + unitKey.asyncID() + ",");
        }

        holder.addGalvanizedUnit(unitKey, 1);
        game.setStoredValue(OVERLORD + "Remaining" + player.getFaction(), Integer.toString(remaining - 1));
        if (remaining == 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationUnfogged() + " finished resolving _Overlord Matrix_.");
        } else {
            sendOverlordMatrixButtons(event.getMessageChannel(), game, player);
        }
        ButtonHelper.deleteMessage(event);
    }

    private static void sendOverlordMatrixButtons(MessageChannel channel, Game game, Player player) {
        int remaining = Integer.parseInt(game.getStoredValue(OVERLORD + "Remaining" + player.getFaction()));
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile != null) {
            for (UnitHolder holder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : holder.getUnitsByState().keySet()) {
                    if (player.unitBelongsToPlayer(unitKey)
                            && holder.getUnitCount(unitKey) > holder.getGalvanizedUnitCount(unitKey)) {
                        buttons.add(Buttons.green(
                                player.factionButtonChecker() + OVERLORD_UNIT + tile.getPosition() + "|"
                                        + holder.getName() + "|" + unitKey.asyncID(),
                                "Galvanize 1 " + unitKey.humanReadableName() + " in " + holder.getRepresentation(game),
                                unitKey.unitEmoji()));
                    }
                }
            }
        }

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    channel,
                    player.getRepresentationUnfogged() + " has no eligible units to galvanize for _Overlord Matrix_.");
            return;
        }

        String prompt = player.getRepresentationUnfogged() + ", choose a unit in the active system to galvanize ("
                + remaining + " remaining).";
        for (int index = 0; index < buttons.size(); index += 25) {
            MessageHelper.sendMessageToChannelWithButtons(
                    channel, prompt, buttons.subList(index, Math.min(index + 25, buttons.size())));
        }
    }

    public static void clearOverlordMatrixGalvanization(Game game) {
        for (Player player : game.getRealPlayers()) {
            String trackedKey = OVERLORD + "Tracked" + player.getFaction();
            for (String unitID : game.getStoredValue(trackedKey).split(",")) {
                if (unitID.isEmpty()) {
                    continue;
                }
                String baselineKey = OVERLORD + "Baseline" + player.getFaction() + unitID;
                int baseline = Integer.parseInt(game.getStoredValue(baselineKey));
                int current = 0;
                for (Tile tile : game.getTileMap().values()) {
                    for (UnitHolder holder : tile.getUnitHolders().values()) {
                        for (UnitKey unitKey : holder.getUnitsByState().keySet()) {
                            if (unitKey.asyncID().equals(unitID)) {
                                current += holder.getGalvanizedUnitCount(unitKey);
                            }
                        }
                    }
                }
                for (Tile tile : game.getTileMap().values()) {
                    for (UnitHolder holder : tile.getUnitHolders().values()) {
                        for (UnitKey unitKey : holder.getUnitsByState().keySet()) {
                            if (current <= baseline || !unitKey.asyncID().equals(unitID)) {
                                continue;
                            }
                            int toRemove = Math.min(current - baseline, holder.getGalvanizedUnitCount(unitKey));
                            holder.removeGalvanizedUnit(unitKey, toRemove);
                            current -= toRemove;
                        }
                    }
                }
                game.removeStoredValue(baselineKey);
            }
            game.removeStoredValue(trackedKey);
            game.removeStoredValue(OVERLORD + "Remaining" + player.getFaction());
        }
    }
}
