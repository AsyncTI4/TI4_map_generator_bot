package ti4.buttons.handlers.relics;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.service.explore.ExploreService;

import java.util.List;

@UtilityClass
class RelicButtonHandler {

    @ButtonHandler("useRelic_")
    public static void useRelic(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String relic = buttonID.replace("useRelic_", "");
        RelicModel relicModel = Mapper.getRelic(relic);
        String message = player.getRepresentation() + " exhausted relic: " + relicModel.getRepresentation();
        player.addExhaustedRelic(relic);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("exhaustRelic_")
    public static void exhaustRelic(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String relic = buttonID.replace("exhaustRelic_", "");
        if (relic.contains("extra")) {
            String factionForExtra = relic.split("extra")[1];
            Player p2 = game.getPlayerFromColorOrFaction(factionForExtra);
            p2.addExhaustedRelic(relic.split("extra")[0]);
            MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getFactionEmoji() + " exhausted "
                + Mapper.getRelic(relic.split("extra")[0]).getRepresentation() + " due to " + player.getFactionEmojiOrColor() + " Stellar Converter");
        } else {
            player.addExhaustedRelic(relic);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                player.getFactionEmoji() + " exhausted " + Mapper.getRelic(relic).getRepresentation());
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawRelic")
    public static void drawRelic(ButtonInteractionEvent event, Player player, Game game) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawRelicFromFrag")
    public static void drawRelicFromFrag(ButtonInteractionEvent event, Player player, Game game) {
        RelicHelper.drawRelicAndNotify(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("relic_look_top")
    public static void relicLookTop(ButtonInteractionEvent event, Game game, Player player) {
        String message = "Use buttons to decide what to do with the relic you drew.";
        String relicID = game.drawRelic();
        if (relicID == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Relic deck is empty");
            return;
        }
        RelicModel relic = Mapper.getRelic(relicID);

        Button takeRelic = Buttons.green("gain_relic_" + relicID, "Gain relic");
        Button returnRelic = Buttons.red("return_relic_" + relicID, "Return relic to deck");
        List<Button> buttons = List.of(takeRelic, returnRelic);

        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            player.getRepresentationUnfogged() + " drew " + relic.getRepresentation() + "\n" + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("neuraloopPart1")
    public static void neuraloopPart1(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String cardChosen = buttonID.replace("neuraloopPart1_", "");
        String message = player.getFactionEmoji() + " is choosing to resolve **Neural Motivator**";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);

        if ("AC".equals(cardChosen)) {
            String message2 = "Please choose an Action Card to discard";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2);
        } else {
            String message2 = "Please choose a Secret Objective to discard";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message2);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("neuraloopPart2")
    public static void neuraloopPart2(ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        String cardID = buttonID.replace("neuraloopPart2_", "");
        String message = player.getFactionEmoji() + " discarded a card and gained 2 trade goods via **Neural Motivator**.";
        player.setTg(player.getTg() + 2);

        if (cardID.contains("ac_")) {
            String acID = cardID.replace("ac_", "");
            player.removeActionCard(acID);
        } else {
            String soID = cardID.replace("so_", "");
            player.removeSecretObjective(soID);
        }

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("dominusOrb")
    public static void dominusOrb(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getRepresentationUnfogged() + ", choose a player to give a command token to.";
        List<Button> buttons = ButtonHelper.getGainCCButtons(player, game);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("thronePoint")
    public static void thronePoint(ButtonInteractionEvent event, Player player, Game game) {
        if (player.getTotalVictoryPoints() >= 1) {
            player.setTotalVictoryPoints(player.getTotalVictoryPoints() - 1);
            String message = player.getRepresentation() + " used **The Crown of Thalnos** to spend 1 victory point and draw a relic.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            RelicHelper.drawRelicAndNotify(player, event, game);
        } else {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You do not have any victory points to spend.");
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("crownofemphidiaexplore")
    public static void crownOfEmphidiaExplore(ButtonInteractionEvent event, Player player, Game game) {
        String message = player.getFactionEmoji() + " explored a planet using **Crown of Emphidia**.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        String planet = "mirage";
        ExploreService.explorePlanet(event, game.getTileFromPlanet(planet), planet, "CULTURAL", player, true, game, 1, false);
        ButtonHelper.deleteMessage(event);
    }
}