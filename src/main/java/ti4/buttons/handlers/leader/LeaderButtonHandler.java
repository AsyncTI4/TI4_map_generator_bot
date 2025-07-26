package ti4.buttons.handlers.leader;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.game.SwapFactionService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.option.GameOptionService;

import java.util.List;

@UtilityClass
class LeaderButtonHandler {

    @ButtonHandler("unlockCommander_")
    public static void unlockCommander(ButtonInteractionEvent event, Player player, String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        CommanderUnlockCheckService.checkPlayer(player, buttonID.split("_")[1]);
    }

    @ButtonHandler("declareUse_")
    public static void declareUse(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String msg = player.getFactionEmojiOrColor() + " is using " + buttonID.split("_")[1];
        if (msg.contains("Vaylerian")) {
            msg = player.getFactionEmojiOrColor()
                + " is using Pyndil Gonsuul, the Vaylerian commander, to add +2 capacity to a ship with capacity.";
        }
        if (msg.contains("Tnelis")) {
            msg = player.getFactionEmojiOrColor() + " is using Fillipo Rois, the Tnelis commander,"
                + " producing a hit against 1 of their __non-fighter__ ships in the system to give __one__ of their ships a +1 move boost."
                + "\n-# This ability may only be used once per activation.";
            String pos = buttonID.split("_")[2];
            List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game,
                game.getTileByPosition(pos));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
                player.getRepresentationUnfogged() + ", use buttons to assign 1 hit.", buttons);
            game.setStoredValue("tnelisCommanderTracker", player.getFaction());
        }
        if (msg.contains("Ghemina")) {
            msg = player.getFactionEmojiOrColor()
                + " is using Jarl Vel & Jarl Jotrun, the Ghemina commanders, to gain 1 trade good after winning the space combat.";
            player.setTg(player.getTg() + 1);
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
            ButtonHelperAbilities.pillageCheck(player, game);
        }
        if (msg.contains("Lightning")) {
            msg = player.getFactionEmojiOrColor()
                + " is using _Lightning Drives_ to give each ship not transporting fighters or infantry a +1 move boost."
                + "\n-# A ship transporting just mechs gets this boost.";
        }
        if (msg.contains("Impactor")) {
            msg = player.getFactionEmojiOrColor()
                + " is using _Reality Field Impactor_ to nullify the effects of one anomaly for this tactical action.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("fogAllianceAgentStep3_")
    public static void fogAllianceAgentStep3(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        ButtonHelper.deleteMessage(event);
        ButtonHelperHeroes.argentHeroStep3(game, player, event, buttonID);
    }

    @ButtonHandler("enableDaneMode_")
    public static void enableDaneMode(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String mode = buttonID.split("_")[1];
        boolean enable = buttonID.split("_")[2].equalsIgnoreCase("enable");
        String message = "Successfully " + buttonID.split("_")[2] + "d the ";
        if (mode.equalsIgnoreCase("hiddenagenda")) {
            game.setHiddenAgendaMode(enable);
            message += "Hidden Agenda Mode. Nothing more needs to be done.";
        }
        if (mode.equalsIgnoreCase("minorFactions")) {
            game.setMinorFactionsMode(enable);
            message += "Minor Factions Mode. ";
            if (enable) {
                message += "You will need to decide how you wish to draft the minor factions. This site has a decent setup for it, "
                    + "and you can important the map using buttons above: https://tidraft.com/draft/prechoice. Note that you need to set up a neutral player "
                    + "after the draft finishes with `/special2 setup_neutral_player`, and you can add 3 infantry to the minor faction planets pretty easily with `/add_units`.";

            }
        }
        if (mode.equalsIgnoreCase("ageOfExploration")) {
            game.setAgeOfExplorationMode(enable);
            message += "Age of Exploration Mode. Nothing more needs to be done.";
        }
        if (mode.equalsIgnoreCase("ageOfCommerce")) {
            game.setAgeOfCommerceMode(enable);
            message += "Age of Commerce Mode. Nothing more needs to be done.";
        }
        if (mode.equalsIgnoreCase("totalWar")) {
            game.setTotalWarMode(enable);
            message += "Total War Mode. Nothing more needs to be done.";
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        List<Button> buttons = GameOptionService.getDaneLeakModeButtons(game);
        event.getMessage().editMessage(event.getMessage().getContentRaw()).setComponents(ButtonHelper.turnButtonListIntoActionRowList(buttons))
            .queue();
    }

    @ButtonHandler("arboAgentOn_")
    public static void arboAgentOn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = ButtonHelperAgents.getArboAgentReplacementOptions(player, game, event,
            game.getTileByPosition(pos), unit);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentationUnfogged() + ", please choose which unit you wish to place down.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("arboAgentIn_")
    public static void arboAgentIn(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String pos = buttonID.substring(buttonID.indexOf("_") + 1);
        List<Button> buttons = ButtonHelperAgents.getUnitsToArboAgent(player, game.getTileByPosition(pos));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentationUnfogged() + ", please choose which unit you'd like to replace.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getReleaseButtons")
    public static void getReleaseButtons(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
            player.getRepresentationUnfogged()
                + ", you may release units one at a time with the buttons. Reminder that captured units may only be released as part of an ability or a transaction.",
            ButtonHelperFactionSpecific.getReleaseButtons(player, game));
    }

    @ButtonHandler("shroudOfLithStart")
    public static void shroudOfLithStart(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteTheOneButton(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(),
            "Select up to 2 ships and 2 ground forces to place in the space area.",
            ButtonHelperFactionSpecific.getKolleccReleaseButtons(player, game));
    }

    @ButtonHandler("assimilate_")
    public static void assimilate(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.split("_")[1];
        ButtonHelperFactionSpecific.resolveAssimilate(event, game, player, planet);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("getAgentSelection_")
    public static void getAgentSelection(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String faction = buttonID.replace("getAgentSelection_", "");
        List<Button> buttons = ButtonHelperAgents.getAgentButtons(game, player, faction);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Choose agent to use", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("getPsychoButtons")
    public static void offerPsychoButtons(Player player, Game game) {
        List<Button> buttons = ButtonHelperFactionSpecific.getPsychoButtons(game, player);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            "Use buttons to resolve Psychoarchaeology", buttons);
    }

    @ButtonHandler("mahactCommander")
    public static void mahactCommander(ButtonInteractionEvent event, Player player, Game game) {
        List<Button> buttons = ButtonHelper.getButtonsToRemoveYourCC(player, game, event, "mahactCommander");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to remove token.",
            buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("yssarilMinisterOfPolicy")
    public static void yssarilMinisterOfPolicy(ButtonInteractionEvent event, Player player, Game game) {
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getFactionEmoji() + " is drawing their _Minister of Policy_ action card.");
        String message;
        if (player.hasAbility("scheming")) {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            message = player.getFactionEmoji()
                + " drew 2 action cards with **Scheming**. Please discard 1 action card.";
            ActionCardHelper.sendActionCardInfo(game, player, event);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", please use buttons to discard.",
                ActionCardHelper.getDiscardActionCardButtons(player, false));

        } else if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = player.getFactionEmoji() + " drew 1 action card.";
        }
        player.checkCommanderUnlock("yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.checkACLimit(game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("drawAgenda_2")
    public static void drawAgenda2(ButtonInteractionEvent event, Game game, Player player) {
        if (!game.getStoredValue("hasntSetSpeaker").isEmpty() && !game.isHomebrewSCMode()) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged()
                + ", you need to assign speaker first before drawing agendas. You can override this restriction with `/agenda draw`.");
            return;
        }
        AgendaHelper.drawAgenda(2, game, player);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, false) + " drew 2 agendas");
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
        SwapFactionService.secondHalfOfSwap(game, player, game.getPlayerFromColorOrFaction(faction), event.getUser(),
            event);
    }
}