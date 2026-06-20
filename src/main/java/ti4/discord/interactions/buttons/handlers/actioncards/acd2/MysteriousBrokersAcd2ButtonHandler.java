package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

@UtilityClass
class MysteriousBrokersAcd2ButtonHandler {

    @ButtonHandler("resolveMysteriousBrokers")
    public static void resolveMysteriousBrokers(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);

        List<Button> buttons = new ArrayList<>();
        for (String relicId : player.getActualRelics()) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + "mysteriousBrokersPurge_" + relicId,
                    "Purge " + Mapper.getRelic(relicId).getName() + " & Gain a Relic"));
        }
        int tgGain = neighborsNotPassed(player);
        buttons.add(Buttons.blue(
                player.factionButtonChecker() + "mysteriousBrokersTg",
                "Gain " + tgGain + " Trade Good" + (tgGain == 1 ? "" : "s"),
                MiscEmojis.tg));

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose how to resolve _Mysterious Brokers_. You may purge a"
                        + " relic to gain a relic, or instead gain trade goods equal to your neighbors that have not"
                        + " passed.",
                buttons);
    }

    @ButtonHandler("mysteriousBrokersPurge_")
    public static void mysteriousBrokersPurge(Player player, ButtonInteractionEvent event, String buttonID) {
        String relicId = buttonID.replace("mysteriousBrokersPurge_", "");
        if (!player.hasRelic(relicId)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Could not resolve _Mysterious Brokers_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        String relicName = Mapper.getRelic(relicId).getName();
        player.removeRelic(relicId);
        player.removeExhaustedRelic(relicId);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " purged _" + relicName + "_ for _Mysterious Brokers_.");
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", use the button to draw your relic.",
                Buttons.green(player.factionButtonChecker() + "drawRelic", "Draw Relic"));
    }

    @ButtonHandler("mysteriousBrokersTg")
    public static void mysteriousBrokersTg(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        int tgGain = neighborsNotPassed(player);
        if (tgGain == 0) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " has no neighbors that have not passed, so _Mysterious Brokers_ grants no trade goods.");
            return;
        }
        ButtonHelperStats.gainTGs(event, game, player, tgGain, false);
    }

    private static int neighborsNotPassed(Player player) {
        return (int) player.getNeighbouringPlayers(true).stream()
                .filter(p2 -> !p2.isPassed())
                .count();
    }
}
