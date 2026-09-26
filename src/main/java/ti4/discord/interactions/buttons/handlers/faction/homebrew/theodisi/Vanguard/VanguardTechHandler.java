package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Vanguard;

import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.combat.StartCombatService;
import ti4.service.tactical.TacticalActionService;

@UtilityClass
public class VanguardTechHandler {
    public static void resolveEnhancedPlating(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, UnitHolder holder, UnitType type) {
        if (!player.hasTech("thvanguardr") || holder == null) {
            return;
        }

        StartCombatService.CurrentCombat combat = StartCombatService.getCurrentCombat(game);
        if (combat == null
                || !combat.factions().contains(player.getFaction())
                || !tile.getPosition().equals(combat.tilePosition())
                || !holder.getName().equals(combat.unitHolderName())) {
            return;
        }

        int round = combat.factions().stream()
                .mapToInt(faction -> NumberUtils.toInt(game.getStoredValue(
                        "combatRoundTracker" + faction + combat.tilePosition() + combat.unitHolderName())))
                .max()
                .orElse(0);
        String key = "enhancedPlating" + player.getFaction() + combat.tilePosition() + combat.unitHolderName() + round;
        if (!game.getStoredValue(key).isEmpty()) {
            return;
        }

        UnitModel unit = player.getPriorityUnitByAsyncID(type.value, holder);
        if (unit == null || unit.getCombatHitsOn() < 1) {
            return;
        }

        game.setStoredValue(key, "used");
        List<Die> dice = DiceHelper.rollDice(unit.getCombatHitsOn(), 1);
        boolean hit = DiceHelper.countSuccesses(dice) > 0;

        String message = player.getRepresentationNoPing() + " resolved _Enhanced Plating_:\n"
                + unit.getUnitEmoji() + " 1 roll, hits on **" + unit.getCombatHitsOn() + "** "
                + DiceHelper.formatDiceOutput(dice) + " - " + (hit ? "1 hit" : "0 hits");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);

        if (!hit) {
            return;
        }

        Player opponent = combat.factions().stream()
                .filter(faction -> !faction.equals(player.getFaction()))
                .map(game::getPlayerFromColorOrFaction)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (opponent == null) {
            return;
        }

        boolean groundCombat = !Constants.SPACE.equals(holder.getName());
        List<Button> buttons = groundCombat
                ? List.of(
                        Buttons.green(
                                opponent.factionButtonChecker() + "autoAssignGroundHits_" + holder.getName() + "_1",
                                "Auto-Assign Hit"),
                        Buttons.red(
                                opponent.factionButtonChecker()
                                        + "getDamageButtons_"
                                        + tile.getPosition()
                                        + "_groundcombat",
                                "Manually Assign Hit"))
                : List.of(
                        Buttons.green(
                                opponent.factionButtonChecker() + "autoAssignSpaceHits_" + tile.getPosition() + "_1",
                                "Auto-Assign Hit"),
                        Buttons.red(
                                opponent.factionButtonChecker()
                                        + "getDamageButtons_"
                                        + tile.getPosition()
                                        + "_spacecombat",
                                "Manually Assign Hit"));

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                opponent.getRepresentationNoPing() + ", _Enhanced Plating_ produced 1 hit against your units.",
                buttons);
    }

    @ButtonHandler("useVanguardReinforce")
    public static void useReinforce(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasTech("thvanguardy")
                || game.getActiveSystem().isEmpty()
                || !game.getStoredValue("vanguardReinforce" + player.getFaction())
                        .isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        game.setStoredValue("vanguardReinforce" + player.getFaction(), game.getActiveSystem());
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing()
                        + " used _Reinforce_. The active system has PRODUCTION 3 until the end of this action.",
                TacticalActionService.getBuildButtons(
                        event, game, player, game.getTileByPosition(game.getActiveSystem())));
    }
}
