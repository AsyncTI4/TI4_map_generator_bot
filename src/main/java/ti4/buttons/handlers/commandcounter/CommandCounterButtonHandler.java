package ti4.buttons.handlers.commandcounter;

import java.util.Arrays;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.ButtonHelperSCs;
import ti4.listeners.annotations.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.logging.BotLogger;
import ti4.service.button.ReactionService;
import ti4.service.game.StartPhaseService;

@UtilityClass
public class CommandCounterButtonHandler {

    @ButtonHandler("decrease_fleet_cc")
    public static void decreaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() - 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.checkFleetInEveryTile(player, game);
    }

    @ButtonHandler("decrease_tactic_cc")
    public static void decreaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() - 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("decrease_strategy_cc")
    public static void decreaseStrategyCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setStrategicCC(player.getStrategicCC() - 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("increase_fleet_cc")
    public static void increaseFleetCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setFleetCC(player.getFleetCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
        if (ButtonHelper.isLawInPlay(game, "regulations") && player.getEffectiveFleetCC() > 4) {
            String msg = player.getRepresentation() + ", reminder that _Fleet Regulations_ is a";
            msg += " law in play, which is limiting fleet pool to 4 tokens.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        }
    }

    @ButtonHandler("increase_strategy_cc")
    public static void increaseStrategyCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setStrategicCC(player.getStrategicCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("increase_tactic_cc")
    public static void increaseTacticCC(ButtonInteractionEvent event, Player player, Game game) {
        player.setTacticalCC(player.getTacticalCC() + 1);
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        int netGain = ButtonHelper.checkNetGain(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: " + netGain + ".";
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resetCCs")
    public static void resetCCs(ButtonInteractionEvent event, Player player, Game game) {
        String originalCCs = game.getStoredValue("originalCCsFor" + player.getFaction());
        ButtonHelper.resetCCs(player, originalCCs);
        String editedMessage = player.getRepresentation() + " command tokens have gone from " + originalCCs + " -> "
                + player.getCCRepresentation() + ". Net gain of: 0.";
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("lose1CC")
    public static void lose1CC(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + "! Your current command tokens are "
                + player.getCCRepresentation() + ".";
        game.setStoredValue("originalCCsFor" + player.getFaction(), player.getCCRepresentation());

        List<Button> buttons = ButtonHelper.getLoseCCButtons(player);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentation() + " has chosen to lose 1 command token.");
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

        List<Button> buttons =
                Arrays.asList(getTactic, getFleet, getStrat, loseTactic, loseFleet, loseStrat, doneGainingCC, resetCC);
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
            for (Player p2 : game.getRealPlayers()) {
                if (p2.isNpc()
                        && game.getStoredValue(
                                        "statusHomeworkReactionFor" + p2.getFaction() + "Round" + game.getRound())
                                .isEmpty()) {
                    ReactionService.addReaction(event, game, p2);
                    game.setStoredValue(
                            "statusHomeworkReactionFor" + p2.getFaction() + "Round" + game.getRound(), "added");
                }
            }
        }

        if ("statusHomework".equalsIgnoreCase(game.getPhaseOfGame())) {
            boolean cyber = false;
            boolean malevolency = false;
            boolean mahactMalev = false;
            for (String pn : player.getPromissoryNotes().keySet()) {
                if (!player.ownsPromissoryNote("ce") && "ce".equalsIgnoreCase(pn)) {
                    cyber = true;
                }
                if (!player.ownsPromissoryNote("malevolency") && "malevolency".equalsIgnoreCase(pn)) {
                    malevolency = true;
                }
            }
            if (malevolency && !player.getMahactCC().isEmpty()) {
                malevolency = false;
                mahactMalev = true;
            }
            if (player.hasAbility("versatile")
                    || player.hasTech("hm")
                    || cyber
                    || malevolency
                    || player.hasTech("tf-inheritancesystems")) {
                int properGain = 2;
                String reasons = "";
                if (player.hasAbility("versatile")) {
                    properGain += 1;
                    reasons = "**Versatile** ";
                }
                if (player.hasTech("hm")) {
                    properGain += 1;
                    reasons += (properGain == 3 ? "" : ", ") + "_Hyper Metabolism_";
                }
                if (player.hasTech("tf-inheritancesystems")) {
                    properGain += 1;
                    reasons += (properGain == 3 ? "" : ", ") + "_Inheritance Systems_";
                }
                if (cyber) {
                    properGain += 1;
                    reasons += (properGain == 3 ? "" : ", ") + "_Cybernetic Enhancements_";
                }
                if (malevolency) {
                    properGain -= 1;
                    reasons += (properGain == 1 ? "" : ", ") + "_Malevolency_";
                }
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        "## " + player.getRepresentationUnfogged()
                                + ", heads up, the bot thinks you should gain " + (properGain == 1 ? "only " : "")
                                + properGain + " command token"
                                + (properGain == 1 ? "" : "s") + " now due to: " + reasons + ".");
                if (!player.getMahactCC().isEmpty() && mahactMalev) {
                    String malevMsg = "## " + player.getRepresentationUnfogged() + " you should gain your normal";
                    malevMsg += " amount of tokens now, and then you will have the option to lose your own or another";
                    malevMsg += " player's command token from your fleet pool due to _Malevolency_. Plan accordingly.";
                    MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), malevMsg);
                }
            }
            if (game.isCcNPlasticLimit()) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        "Your highest fleet count in a system is currently "
                                + ButtonHelper.checkFleetInEveryTile(player, game)
                                + ". That's how many command tokens you'll need to retain in your fleet pool to avoid removing ships.");
            }
            StartPhaseService.sendStatusReminders(event, game, player);
        }
    }

    @ButtonHandler("spendAStratCC")
    public static void spendAStratCC(ButtonInteractionEvent event, Player player, Game game) {
        if (player.getStrategicCC() > 0) {
            ButtonHelperCommanders.resolveMuaatCommanderCheck(player, game, event);
        }
        String message = ButtonHelperSCs.deductCC(game, player, -1);
        ReactionService.addReaction(event, game, player, message);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
