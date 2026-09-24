package ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.FoWHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.button.ReactionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.strategycard.StrategyCardMessageService;
import ti4.service.strategycard.StrategyCardSecondaryButtonService;
import ti4.service.unit.DestroyUnitService;
import ti4.service.unit.ParsedUnit;

@UtilityClass
public class OnyxxaTechHandler {

    public static final String SACRIFICIAL_COMMAND = "baconcy";

    public static void offerStrategicInversion(Game game, Player player) {
        if (!player.hasTech("baconcg")) return;
        List<Button> scButtons = new ArrayList<>();
        for (int scNum : player.getSCs()) {
            if (StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, scNum)
                    .isEmpty()) continue;
            scButtons.add(Buttons.gray(
                    player.factionButtonChecker() + "strategicInversionCard_" + scNum,
                    "Secondary of " + game.getSCName(scNum)));
        }
        if (scButtons.isEmpty()) return;
        scButtons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose the strategy card whose secondary ability you will"
                        + " resolve with _Strategic Inversion_. This spends 1 command token from your strategy pool"
                        + " (except for **Leadership**).",
                scButtons);
    }

    @ButtonHandler("strategicInversionCard_")
    public static void resolveStrategicInversionCard(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        int scNum = Integer.parseInt(buttonID.replace("strategicInversionCard_", ""));
        String payment = "";
        if (!isLeadership(game, scNum)) {
            if (player.getStrategicCC() < 1) {
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentation() + ", you have no command tokens in your strategy pool to resolve"
                                + " the secondary ability of **" + game.getSCName(scNum) + "**.");
                return;
            }
            player.setStrategicCC(player.getStrategicCC() - 1);
            payment = " spent 1 command token from their strategy pool and";
        }
        ButtonHelper.deleteMessage(event);
        List<Button> scButtons =
                new ArrayList<>(StrategyCardSecondaryButtonService.getSecondaryAbilityButtons(game, scNum));
        scButtons.add(Buttons.red("deleteButtons", "Done resolving"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + payment + " is using _Strategic Inversion_ to resolve the secondary"
                        + " ability of **" + game.getSCName(scNum) + "**.",
                scButtons);
    }

    private static boolean isTrade(Game game, int scNum) {
        return game.getStrategyCardModelByInitiative(scNum)
                .map(scModel -> scModel.usesAutomationForSCID("pok5trade"))
                .orElse(false);
    }

    private static boolean isLeadership(Game game, int scNum) {
        return game.getStrategyCardModelByInitiative(scNum)
                .map(scModel -> scModel.usesAutomationForSCID("pok1leadership"))
                .orElse(false);
    }

    public static void serveSacrificialCommandButtons(Game game, Player player, int scNum) {
        if (!player.hasTech(SACRIFICIAL_COMMAND)) return;
        List<Button> buttons = new ArrayList<>();
        for (String position : getPositionsInOrAdjacentToActivePlayerUnits(game, player)) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) continue;
            for (UnitKey unitKey : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
                if (unitKey.unitType() == UnitType.Fighter) continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.getIsShip()) continue;
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + "sacrificialShip_" + scNum + "_" + position + "_"
                                + unitKey.asyncID(),
                        "Destroy " + unitKey.humanReadableName() + " in "
                                + tile.getRepresentationForButtons(game, player)));
            }
        }
        if (buttons.isEmpty()) return;
        buttons.add(Buttons.DONE_DELETE_BUTTONS
                .withLabel("Decline Sacrificial Command")
                .withEmoji(FactionEmojis.onyxxa.asEmoji()));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation() + " since you have _Sacrificial Command_ you may destroy 1 of your"
                        + " non-fighter ships in or adjacent to a system that contains the active player's units"
                        + " instead of spending a command token from your strategy pool on **"
                        + game.getSCName(scNum) + "**. If you wish to do so, please choose which ship to destroy.",
                buttons);
    }

    @ButtonHandler("sacrificialShip_")
    public static void resolveSacrificialCommand(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] parts = buttonID.replace("sacrificialShip_", "").split("_");
        int scNum = Integer.parseInt(parts[0]);
        Tile tile = game.getTileByPosition(parts[1]);
        UnitKey unitKey = Units.getUnitKey(parts[2], player.getColorID());
        if (tile == null || unitKey == null || tile.getSpaceUnitHolder().getUnitCount(unitKey) < 1) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentation() + ", that ship is no longer there.");
            return;
        }
        DestroyUnitService.destroyUnit(event, tile, game, new ParsedUnit(unitKey), false);
        String scMessageId = StrategyCardMessageService.getStrategyCardMessage(game.getName(), game.getRound(), scNum)
                .map(GameMessage::messageId)
                .orElse(null);
        String result;
        if (!OnyxxaAbilityHandler.hasPaidFollowToken(game, player, scNum)) {
            if (!player.getFollowedSCs().contains(scNum)) {
                ButtonHelperFactionSpecific.resolveVadenSCDebt(player, scNum, game, event);
                player.addFollowedSC(scNum, event);
            }
            OnyxxaAbilityHandler.markFollowTokenPaid(game, player, scNum);
            if (scMessageId != null) {
                ButtonHelperSCs.addUsedSCPlayer(scMessageId, game, player);
            }
            if (isTrade(game, scNum)) {
                ButtonHelperStats.replenishComms(event, game, player, false);
            }
            result = " to perform the secondary ability of **" + game.getSCName(scNum) + "**";
        } else if (OnyxxaAbilityHandler.canUseStrategicFluidity(game, player, scNum)) {
            result = OnyxxaAbilityHandler.applyStrategicFluidity(event, game, player, scNum);
        } else {
            result = " on **" + game.getSCName(scNum) + "**";
        }
        if (scMessageId != null) {
            ReactionService.addReaction(player, false, null, null, scMessageId, game);
        }
        String msg = player.getRepresentationUnfogged() + " destroyed 1 " + unitKey.humanReadableName() + " in "
                + tile.getRepresentationForButtons(game, player) + " with _Sacrificial Command_ instead of spending"
                + " a command token from their strategy pool" + result + ".";
        MessageChannel channel =
                game.isFowMode() ? player.getCorrectChannel() : ButtonHelper.getSCFollowChannel(game, player, scNum);
        MessageHelper.sendMessageToChannel(channel, msg.replace("..", "."));
        ButtonHelper.deleteMessage(event);
    }

    private static Set<String> getPositionsInOrAdjacentToActivePlayerUnits(Game game, Player player) {
        Set<String> positions = new HashSet<>();
        Player activePlayer = game.getActivePlayer();
        if (activePlayer == null) return positions;
        for (Tile tile : game.getTileMap().values()) {
            if (FoWHelper.playerHasUnitsInSystem(activePlayer, tile)) {
                positions.addAll(FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false, true));
            }
        }
        return positions;
    }
}
