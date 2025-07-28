package ti4.buttons.handlers.actioncards;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.button.ReactionService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.StartTurnService;

@UtilityClass
class ActionCardHandButtonHandler {

    @ButtonHandler("getACFrom_")
    static void getACFrom(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String faction = buttonID.replace("getACFrom_", "");
        Player victim = game.getPlayerFromColorOrFaction(faction);
        List<Button> buttons = ButtonHelperFactionSpecific.getButtonsToTakeSomeonesAC(player, victim);
        ActionCardHelper.showAll(victim, player, game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + ", please choose which action card you wish to steal.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(value = "checkCombatACs", save = false)
    static void checkCombatACs(ButtonInteractionEvent event, Player player) {
        String secretScoreMsg = "Please choose the action card you wish to play.";
        List<Button> acButtons = ActionCardHelper.getCombatActionCardButtons(player);
        if (!acButtons.isEmpty()) {
            MessageHelper.sendMessageToEventChannelWithEphemeralButtons(event, secretScoreMsg, acButtons);
        } else {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You have no combat action cards.");
        }
    }

    @ButtonHandler(value = "ac_discard_from_hand_")
    static void acDiscardFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player,
        MessageChannel actionsChannel) {
        String acIndex = buttonID.replace("ac_discard_from_hand_", "");
        boolean stalling = false;
        boolean drawReplacement = false;
        boolean retainButtons = false;
        if (acIndex.contains("stall")) {
            acIndex = acIndex.replace("stall", "");
            stalling = true;
        }
        if (acIndex.endsWith("redraw")) {
            acIndex = acIndex.replace("redraw", "");
            drawReplacement = true;
        }
        if (acIndex.endsWith("retain")) {
            acIndex = acIndex.replace("retain", "");
            retainButtons = true;
        }

        MessageChannel channel = game.getMainGameChannel() != null ? game.getMainGameChannel() : actionsChannel;

        if (channel == null) {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
            return;
        }

        try {
            String acID = null;
            for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                if (so.getValue().equals(Integer.parseInt(acIndex))) {
                    acID = so.getKey();
                }
            }

            boolean removed = game.discardActionCard(player.getUserID(), Integer.parseInt(acIndex));
            if (!removed) {
                MessageHelper.sendMessageToChannel(event.getChannel(),
                    "No such Action Card ID found, please retry");
                return;
            }
            String sb = player.getRepresentation() + " discarded the action card _"
                + Mapper.getActionCard(acID).getName() + "_.\n" +
                Mapper.getActionCard(acID).getRepresentation();
            MessageChannel channel2 = game.isFowMode() ? player.getPrivateChannel() : game.getMainGameChannel();
            MessageHelper.sendMessageToChannel(channel2, sb);
            ActionCardHelper.sendActionCardInfo(game, player);
            String message = "Use buttons to end turn or do another action.";
            if (stalling) {
                if (player.hasUnit("yssaril_mech") && !ButtonHelper.isLawInPlay(game, "articles_war")
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4) {
                    String message3 = "Use buttons to drop 1 mech on a planet or decline";
                    List<Button> buttons = new ArrayList<>(Helper.getPlanetPlaceUnitButtons(player, game,
                        "mech", "placeOneNDone_skipbuild"));
                    buttons.add(Buttons.red("deleteButtons", "Decline to Drop Mech"));
                    MessageHelper.sendMessageToChannelWithButtons(channel2, message3, buttons);
                }
                List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
                MessageHelper.sendMessageToChannelWithButtons(channel2, message, systemButtons);
            }
            if (drawReplacement) {
                ActionCardHelper.drawActionCards(game, player, 1, true);
            }
            ButtonHelper.checkACLimit(game, player);
            if (!retainButtons) {
                ButtonHelper.deleteMessage(event);
            } else {
                ButtonHelper.deleteTheOneButton(event, buttonID, false);
            }
            if (player.hasUnexhaustedLeader("cymiaeagent")) {
                List<Button> buttons2 = new ArrayList<>();
                Button hacanButton = Buttons.gray("exhaustAgent_cymiaeagent_" + player.getFaction(),
                    "Use Cymiae Agent", FactionEmojis.cymiae);
                buttons2.add(hacanButton);
                MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                        + " you may use "
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                        + "Skhot Unit X-12, the Cymiae"
                        + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                        + " agent, to make yourself draw 1 action card.",
                    buttons2);
            }
            ActionCardHelper.serveReverseEngineerButtons(game, player, List.of(acID));
        } catch (Exception e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Something went wrong discarding", e);
        }
    }

    @ButtonHandler(Constants.AC_PLAY_FROM_HAND)
    static void acPlayFromHand(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String acID = buttonID.replace(Constants.AC_PLAY_FROM_HAND, "");
        MessageChannel channel = game.getMainGameChannel();
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation()
                + ", please play _Sabotage_ by clicking the Sabo button on the action card you wish to Sabo.");
            return;
        }

        if (acID.contains("reverse_")) {
            String actionCardTitle = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolveReverse_" + actionCardTitle,
                "Pick Up " + actionCardTitle + " From The Discard"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation()
                    + ", after checking for Sabos, use buttons to resolve _Reverse Engineer_.",
                scButtons);
        }
        if (acID.contains("counterstroke_")) {
            String tilePos = acID.split("_")[2];
            acID = acID.split("_")[0];
            List<Button> scButtons = new ArrayList<>();
            scButtons.add(Buttons.green("resolveCounterStroke_" + tilePos,
                "Counterstroke in " + tilePos));
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                player.getRepresentation() + ", after checking for Sabos, use buttons to resolve _Counterstroke_.",
                scButtons);
        }
        if (channel != null) {
            try {
                String error = ActionCardHelper.playAC(event, game, player, acID, channel);
                if (error != null) {
                    event.getChannel().sendMessage(error).queue();
                }
            } catch (Exception e) {
                BotLogger.error(new BotLogger.LogMessageOrigin(event, player), "Could not parse AC ID: " + acID, e);
                event.getChannel().asThreadChannel()
                    .sendMessage("Could not parse action card ID: " + acID + ". Please play manually.").queue();
            }
        } else {
            event.getChannel().sendMessage("Could not find channel to play card. Please ping Bothelper.").queue();
        }
    }

    @ButtonHandler("getDiscardButtonsACs")
    static void getDiscardButtonsACs(Player player) {
        String msg = player.getRepresentationUnfogged() + ", use buttons to discard an action card.";
        List<Button> buttons = ActionCardHelper.getDiscardActionCardButtons(player, false);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("draw_1_ACDelete")
    static void draw1ACDelete(ButtonInteractionEvent event, Player player, Game game) {
        draw1Ac(event, player, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("draw_1_AC")
    static void draw1AC(ButtonInteractionEvent event, Player player, Game game) {
        draw1Ac(event, player, game);
    }

    private static void draw1Ac(ButtonInteractionEvent event, Player player, Game game) {
        String message;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = " drew 1 action card.";
        }
        ReactionService.addReaction(event, game, player, true, false, message);
        ButtonHelper.checkACLimit(game, player);
    }

    @ButtonHandler("draw_2_ACDelete")
    static void draw2ACDelete(ButtonInteractionEvent event, Player player, Game game) {
        String message;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 2);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            CommanderUnlockCheckService.checkPlayer(player, "yssaril");
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = "drew 2 action cards with **Scheming**. Please discard 1 action card.";
        }
        ReactionService.addReaction(event, game, player, true, false, message);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + ", use buttons to discard an action card.",
            ActionCardHelper.getDiscardActionCardButtons(player, false));
        ButtonHelper.deleteMessage(event);
        ButtonHelper.checkACLimit(game, player);
    }

    @ButtonHandler("draw2 AC")
    static void draw2AC(ButtonInteractionEvent event, Player player, Game game) {
        boolean hasSchemingAbility = player.hasAbility("scheming");
        String message = hasSchemingAbility
            ? "drew 3 Action Cards (**Scheming**) - please discard 1 action card from your hand"
            : "drew 2 Action cards";
        int count = hasSchemingAbility ? 3 : 2;
        if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, count);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            for (int i = 0; i < count; i++) {
                game.drawActionCard(player.getUserID());
            }
            ActionCardHelper.sendActionCardInfo(game, player, event);
            ButtonHelper.checkACLimit(game, player);
        }
        ReactionService.addReaction(event, game, player, message);
        if (hasSchemingAbility) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", please discard an action card due to **Scheming**.",
                ActionCardHelper.getDiscardActionCardButtons(player, false));
        }
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("drawActionCards_")
    static void drawActionCards(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        try {
            int count = Integer.parseInt(buttonID.replace("drawActionCards_", ""));
            ActionCardHelper.drawActionCards(game, player, count, true);
            ButtonHelper.deleteTheOneButton(event);
        } catch (Exception ignored) {}
    }
}
