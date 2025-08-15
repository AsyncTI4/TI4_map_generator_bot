package ti4.buttons.handlers.relics;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Helper;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.objectives.RevealPublicObjectiveService;
import ti4.service.tactical.TacticalActionService;

@UtilityClass
class RelicButtonHandler {

    @ButtonHandler("useRelic_")
    static void useRelic(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String relic = buttonID.replace("useRelic_", "");
        ButtonHelper.deleteTheOneButton(event);
        if ("boon".equals(relic)) { // Sarween Tools
            player.addSpentThing("boon");
            String exhaustedMessage = Helper.buildSpentThingsMessage(player, game, "res");
            event.getMessage().editMessage(exhaustedMessage).queue();
        }
    }

    @ButtonHandler("exhaustRelic_")
    static void exhaustRelic(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String relic = buttonID.replace("exhaustRelic_", "");
        if (!player.hasRelicReady(relic)) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), player.getFactionEmoji() + " doesn't have an unexhausted " + relic + ".");
            return;
        }
        player.addExhaustedRelic(relic);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmoji() + " exhausted "
                        + Mapper.getRelic(relic).getName());
        ButtonHelper.deleteTheOneButton(event);
        if ("absol_luxarchtreatise".equalsIgnoreCase(relic)) {
            game.setStoredValue("absolLux", "true");
        }
    }

    @ButtonHandler("relic_look_top")
    static void relicLookTop(ButtonInteractionEvent event, Game game, Player player) {
        List<String> deck = game.getAllRelics();
        if (deck.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "The " + ExploreEmojis.Relic + " relic deck & discard is empty - nothing to look at.");
            return;
        }
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "The top card of the " + ExploreEmojis.Relic + " relic deck has been sent to "
                            + player.getFactionEmojiOrColor() + " `#cards-info` thread.");
        } else {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation(true, false) + " looked at top card of the " + ExploreEmojis.Relic
                            + " relic deck. The card has been sent to their `#cards-info` thread.");
        }

        String topCard = deck.getFirst();
        RelicModel relic = Mapper.getRelic(topCard);
        String message = "You looked at the top of the " + ExploreEmojis.Relic + " relic deck and saw _"
                + relic.getName() + "_.";
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCardsInfoThread(), message, relic.getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawRelicFromFrag")
    static void drawRelicFromFrag(ButtonInteractionEvent event, Player player, Game game) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("neuraloopPart1")
    static void neuraloopPart1(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String poID = buttonID.split(";")[1];
        String type = buttonID.split(";")[2];
        String msg = player.getRepresentation()
                + ", please choose the relic you wish to purge in order to replace the objective with a " + type + ".";
        List<Button> buttons = RelicHelper.getNeuraLoopButton(player, poID, type, game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("neuraloopPart2")
    static void neuraloopPart2(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String poID = buttonID.split(";")[1];
        String type = buttonID.split(";")[2];
        String relic = buttonID.split(";")[3];
        player.removeRelic(relic);
        player.removeExhaustedRelic(relic);
        game.removeRevealedObjective(poID);
        String msg = player.getRepresentation() + " is using _Neuraloop_, purge "
                + (relic.equals("neuraloop") ? "itself" : Mapper.getRelic(relic).getName())
                + ", to replace the recently revealed objective with a random " + type + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (type.equalsIgnoreCase("stage1")) {
            RevealPublicObjectiveService.revealS1(game, event, true);
        } else if (type.equalsIgnoreCase("stage2")) {
            RevealPublicObjectiveService.revealS2(game, event, true);
        } else {
            RevealPublicObjectiveService.revealSO(game, event.getMessageChannel());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawRelic")
    static void drawRelic(ButtonInteractionEvent event, Player player, Game game) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dominusOrb")
    static void dominusOrb(ButtonInteractionEvent event, Player player, Game game) {
        game.setDominusOrb(true);
        String relicId = "dominusorb";
        player.removeRelic(relicId);
        player.removeExhaustedRelic(relicId);
        String relicName = Mapper.getRelic(relicId).getName();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Purged " + ExploreEmojis.Relic + " relic: " + relicName);
        ButtonHelper.deleteMessage(event);
        String message = "Please choose a system to move from.";
        List<Button> systemButtons = TacticalActionService.getTilesToMoveFrom(player, game, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
    }

    @ButtonHandler("eyeOfVogul")
    static void eyeOfVogul(ButtonInteractionEvent event, Player player, Game game) {
        String relicId = "eye_of_vogul";
        player.removeRelic(relicId);
        player.removeExhaustedRelic(relicId);
        String relicName = Mapper.getRelic(relicId).getName();
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getRepresentationNoPing() + " has purged the _Eye of Vogul_.");
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("exhauste6g0network")
    static void exhaustE6G0Network(ButtonInteractionEvent event, Player player, Game game) {
        player.addExhaustedRelic("e6-g0_network");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getFactionEmoji() + " chose to exhaust _E6-G0 Network_.");
        String message;
        if (player.hasAbility("scheming")) {
            game.drawActionCard(player.getUserID());
            game.drawActionCard(player.getUserID());
            message =
                    player.getFactionEmoji() + " drew 2 action cards with **Scheming**. Please discard 1 action card.";
            ActionCardHelper.sendActionCardInfo(game, player, event);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " use buttons to discard",
                    ActionCardHelper.getDiscardActionCardButtons(player, false));
        } else if (player.hasAbility("autonetic_memory")) {
            ButtonHelperAbilities.autoneticMemoryStep1(game, player, 1);
            message = player.getFactionEmoji() + " triggered **Autonetic Memory** option.";
        } else {
            game.drawActionCard(player.getUserID());
            ActionCardHelper.sendActionCardInfo(game, player, event);
            message = player.getFactionEmoji() + " drew 1 action card.";
        }
        CommanderUnlockCheckService.checkPlayer(player, "yssaril");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.checkACLimit(game, player);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("crownofemphidiaexplore")
    static void crownOfEmphidiaExplore(ButtonInteractionEvent event, Player player, Game game) {
        player.addExhaustedRelic("emphidia");
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getFactionEmojiOrColor() + " Exhausted _The Crown of Emphidia_.");
        List<Button> buttons = ButtonHelper.getButtonsToExploreAllPlanets(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Use buttons to explore", buttons);
        ButtonHelper.deleteMessage(event);
    }
}
