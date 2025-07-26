package ti4.buttons.handlers.other;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;

import java.util.Arrays;
import java.util.List;

@UtilityClass
class CommandCounterButtonHandler {

    @ButtonHandler("gain_CC")
    public static void gainCC(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + "! Your current command tokens are "
            + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());

        String finChecker = player.finChecker();
        Button getTactic = Buttons.green(finChecker + "increase_tactic_cc", "Gain 1 Tactic Token");
        Button getFleet = Buttons.green(finChecker + "increase_fleet_cc", "Gain 1 Fleet Token");
        Button getStrat = Buttons.green(finChecker + "increase_strategy_cc", "Gain 1 Strategy Token");
        Button doneGainingCC = Buttons.blue(finChecker + "deleteButtons", "Done Gaining Command Tokens");
        Button resetCC = Buttons.gray(finChecker + "resetCCs", "Reset Command Tokens");

        List<Button> buttons = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("confirm_cc")
    public static void confirmCC(ButtonInteractionEvent event, Game game, Player player) {
        String message = "Command tokens confirmed for " + player.getRepresentation();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ReactionService.addReaction(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("lose1CC")
    public static void lose1CC(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + "! Your current command tokens are "
            + player.getCCRepresentation() + ".";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());

        String finChecker = player.finChecker();
        Button loseTactic = Buttons.red(finChecker + "decrease_tactic_cc", "Lose 1 Tactic Token");
        Button loseFleet = Buttons.red(finChecker + "decrease_fleet_cc", "Lose 1 Fleet Token");
        Button loseStrat = Buttons.red(finChecker + "decrease_strategy_cc", "Lose 1 Strategy Token");
        Button doneGainingCC = Buttons.blue(finChecker + "deleteButtons_spitItOut", "Done Losing 1 Command Token");
        Button resetCC = Buttons.gray(finChecker + "resetCCs", "Reset Command Tokens");

        List<Button> buttons = Arrays.asList(loseTactic, loseFleet, loseStrat,
            doneGainingCC, resetCC);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation() + " has chosen to lose 1 command token.");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("redistributeCCButtons") // Buttons.REDISTRIBUTE_CCs
    public static void redistributeCCButtons(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + "! Your current command tokens are "
            + player.getCCRepresentation() + ". Use buttons to gain command tokens.";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());

        String finChecker = player.finChecker();
        Button getTactic = Buttons.green(finChecker + "increase_tactic_cc", "Gain 1 Tactic Token");
        Button getFleet = Buttons.green(finChecker + "increase_fleet_cc", "Gain 1 Fleet Token");
        Button getStrat = Buttons.green(finChecker + "increase_strategy_cc", "Gain 1 Strategy Token");
        Button loseTactic = Buttons.red(finChecker + "decrease_tactic_cc", "Lose 1 Tactic Token");
        Button loseFleet = Buttons.red(finChecker + "decrease_fleet_cc", "Lose 1 Fleet Token");
        Button loseStrat = Buttons.red(finChecker + "decrease_strategy_cc", "Lose 1 Strategy Token");
        Button doneGainingCC = Buttons.blue(finChecker + "deleteButtons", "Done Redistributing Command Tokens");
        Button resetCC = Buttons.gray(finChecker + "resetCCs", "Reset Command Tokens");

        List<Button> buttons = Arrays.asList(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat,
            doneGainingCC, resetCC);
        if (player.hasAbility("deliberate_action") && game.getPhaseOfGame().contains("status")) {
            buttons = Arrays.asList(getTactic, getFleet, getStrat, doneGainingCC, resetCC);
        }
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), message, buttons);
        }

        if (!game.isFowMode() && "statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
            ReactionService.addReaction(event, game, player);
        }

        if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
            boolean cyber = false;
            for (String pn : player.getPromissoryNotes().keySet()) {
                if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                    cyber = true;
                }
            }
            if (player.hasAbility("versatile") || player.hasTech("hm") || cyber) {
                int properGain = 2;
                String reasons = "";
                if (player.hasAbility("versatile")) {
                    properGain += 1;
                    reasons = "**Versatile** ";
                }
                if (player.hasTech("hm")) {
                    properGain += 1;
                    reasons += "_Hypermetabolism_ ";
                }
                if (cyber) {
                    properGain += 1;
                    reasons += "_Cybernetic Enhancements_ ";
                }
                if (properGain > 2) {
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                        "Heads up " + player.getRepresentationUnfogged()
                            + ", the bot thinks you should gain " + properGain + " command tokens now due to: " + reasons + ".");
                }
            }
            if (game.isCcNPlasticLimit()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    "Your highest fleet count in a system is currently "
                        + ButtonHelper.checkFleetInEveryTile(player, game)
                        + ". That's how many command tokens you'll need to retain in your fleet pool to avoid removing ships.");
            }
        }
    }

    @ButtonHandler("decrease_fleet_cc")
    public static void decreaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() - 1);
        String originalCCs = game
            .getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
            + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("decrease_tactic_cc")
    public static void decreaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() - 1);
        String originalCCs = game
            .getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
            + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("decrease_strategy_cc")
    public static void decreaseStrategyCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setStrategicCC(player.getStrategicCC() - 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs
            + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_fleet_cc")
    public static void increaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from "
            + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
        if (ButtonHelper.isLawInPlay(game, "regulations") && player.getFleetCC() > 4) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation()
                + " reminder that under the _Fleet Regulations_ law, fleet pools are limited to 4 command tokens.");
        }
    }

    @ButtonHandler("resetCCs")
    public static void resetCCs(ButtonInteractionEvent event, Player player, Game game) {
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        ButtonHelper.resetCCs(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from "
            + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: 0.";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_tactic_cc")
    public static void increaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from "
            + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("increase_strategy_cc")
    public static void increaseStrategyCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setStrategicCC(player.getStrategicCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from "
            + originalCCs + " -> " + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue();
    }

    @ButtonHandler("reinforcements_cc_placement_")
    public static void reinforcementsCCPlacement(
        GenericInteractionCreateEvent event, Game game, Player player,
        String buttonID
    ) {
        String tilePos = buttonID.split("_")[3];
        Tile tile = game.getTileByPosition(tilePos);
        CommandCounterHelper.addCC(event, player, tile);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " placed a command token in " + tile.getRepresentationForButtons(game, player));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("removeCCFromBoard_")
    public static void removeCCFromBoard(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.resolveRemovingYourCC(player, game, event, buttonID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("placeCCBack_")
    public static void placeCCBack(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String position = buttonID.split("_")[1];
        String message = player.getRepresentationUnfogged() + " has chosen to not pay the 1 command token required to remove a command token from the Errant (Toldar flagship) system,"
            + " and so their command token has been placed back in tile " + position + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
        CommandCounterHelper.addCC(event, player, game.getTileByPosition(position));
    }

    @ButtonHandler("spendAStratCC")
    public static void spendAStratCC(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentation() + " spent 1 strategy command token.";
        player.setStrategicCC(player.getStrategicCC() - 1);
        ButtonHelper.addReaction(event, false, false, message, "");
        ButtonHelper.deleteMessage(event);
    }
}