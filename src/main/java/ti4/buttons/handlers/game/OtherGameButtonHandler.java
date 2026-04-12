package ti4.buttons.handlers.game;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.actionrow.ActionRowChildComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DisplayType;
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.game.SwapFactionService;

@UtilityClass
class OtherGameButtonHandler {

    @ButtonHandler("reduceComm_")
    public static void reduceComm(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        player.getFactionEmojiOrColor();
        String message;

        if (tgLoss > player.getCommodities()) {
            message = "You don't have " + tgLoss + " commodit" + (tgLoss == 1 ? "y" : "ies") + ". No change made.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            player.setCommodities(player.getCommodities() - tgLoss);
            player.addSpentThing("comm_" + tgLoss);
        }
        String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        Leader playerLeader = player.getLeader("keleresagent").orElse(null);
        if (playerLeader != null && !playerLeader.isExhausted()) {
            playerLeader.setExhausted(true);
            String messageText =
                    player.getRepresentation() + " exhausted " + Helper.getLeaderFullRepresentation(playerLeader) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), messageText);
        }
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("reduceTG_")
    public static void reduceTG(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        int tgLoss = Integer.parseInt(buttonID.split("_")[1]);

        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        if (tgLoss > player.getTg()) {
            String message =
                    "You don't have " + tgLoss + " trade good" + (tgLoss == 1 ? "" : "s") + ". No change made.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        } else {
            player.setTg(player.getTg() - tgLoss);
            player.increaseTgsSpentThisWindow(tgLoss);
        }
        if (tgLoss > player.getTg()) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
        String editedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage().editMessage(editedMessage).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("getAgentSelection_")
    public static void getAgentSelection(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        List<Button> buttons = ButtonHelper.getButtonsForAgentSelection(game, buttonID.split("_")[1]);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", please choose the target of your agent.",
                buttons);
    }

    @ButtonHandler("chooseMapView")
    public static void chooseMapView(ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("checkWHView", "Find Wormholes"));
        buttons.add(Buttons.red("checkAnomView", "Find Anomalies"));
        buttons.add(Buttons.green("checkLegendView", "Find Legendaries"));
        buttons.add(Buttons.gray("checkEmptyView", "Find Empties"));
        buttons.add(Buttons.red("checkExileView", "Determine Exile Breachable Systems"));
        buttons.add(Buttons.blue("checkAetherView", "Determine Aetherstreamable Systems"));
        buttons.add(Buttons.red("checkCannonView", "Calculate Space Cannon Offense Shots"));
        buttons.add(Buttons.green("checkTraitView", "Find Traits"));
        buttons.add(Buttons.green("checkTechSkipView", "Find Technology Specialties"));
        buttons.add(Buttons.blue("checkAttachmView", "Find Attachments"));
        buttons.add(Buttons.gray("checkShiplessView", "Show Map Without Ships"));
        buttons.add(Buttons.gray("checkUnlocked", "Show Only Unlocked Units"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "", buttons);
    }

    @ButtonHandler("checkExileView")
    public static void calculateExileView(ButtonInteractionEvent event, Game game) {
        ButtonHelper.showFeatureType(event, game, DisplayType.exile);
    }

    @ButtonHandler("resetSpend_")
    public static void resetSpend_(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Helper.refreshPlanetsOnTheRespend(player, game);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 1) {
            whatIsItFor = buttonID.split("_")[1];
        }

        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor);
        for (ActionRow row : event.getMessage().getComponentTree().findAll(ActionRow.class)) {
            List<ActionRowChildComponentUnion> buttonRow = row.getComponents();
            for (ActionRowChildComponentUnion but : buttonRow) {
                if (but instanceof Button butt) {
                    if (!Helper.doesListContainButtonID(buttons, butt.getCustomId())) {
                        buttons.add(butt);
                    }
                }
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage()
                .editMessage(exhaustedMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resetSpend")
    public static void resetSpend(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        Helper.refreshPlanetsOnTheRevote(player, game);
        String whatIsItFor = "both";
        if (buttonID.split("_").length > 2) {
            whatIsItFor = buttonID.split("_")[2];
        }
        player.resetSpentThings();
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, whatIsItFor);
        for (ActionRow row : event.getMessage().getComponentTree().findAll(ActionRow.class)) {
            List<ActionRowChildComponentUnion> buttonRow = row.getComponents();
            for (ActionRowChildComponentUnion but : buttonRow) {
                if (but instanceof Button butt) {
                    if (!buttons.contains(butt)) {
                        buttons.add(butt);
                    }
                }
            }
        }
        String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, whatIsItFor);
        event.getMessage()
                .editMessage(exhaustedMessage)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
                .queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("setOrder")
    public static void setOrder(ButtonInteractionEvent event, Game game) {
        Helper.setOrder(game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("yssarilMinisterOfPolicy")
    public static void yssarilMinisterOfPolicy(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " is drawing their _Minister of Policy_ action card.");
        ActionCardHelper.drawActionCards(player, 1);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("deployTyrant")
    public static void deployTyrant(ButtonInteractionEvent event, Player player, Game game) {
        String message = "Use buttons to place the _Tyrant's Lament_ with your ships.";
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                message,
                Helper.getTileWithShipsPlaceUnitButtons(player, game, "tyrantslament", "placeOneNDone_skipbuild"));
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " is deploying the _Tyrant's Lament_.");
        player.addOwnedUnitByID("tyrantslament");
    }

    @ButtonHandler("useTA_")
    public static void useTA(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String ta = buttonID.replace("useTA_", "") + "_ta";
        PromissoryNoteHelper.resolvePNPlay(ta, player, game, event);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dsdihmy_")
    public static void dsDihmhonYellowTech(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperFactionSpecific.resolveImpressmentPrograms(buttonID, event, game, player);
    }

    @ButtonHandler("swapToFaction_")
    public static void swapToFaction(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("swapToFaction_", "");
        SwapFactionService.secondHalfOfSwap(
                game, player, game.getPlayerFromColorOrFaction(faction), event.getUser(), event);
    }
}
