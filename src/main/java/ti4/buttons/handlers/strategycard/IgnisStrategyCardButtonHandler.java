package ti4.buttons.handlers.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.EventHelper;
import ti4.helpers.RelicHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
class IgnisStrategyCardButtonHandler {

    @ButtonHandler("ignisAuroraSC8Primary")
    public static void resolveIgnisAuroraSC8Primary(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.getSCs().contains(8)) {
            MessageHelper.sendMessageToEventChannel(event, "You don't have the Antiquities strategy card.");
            return;
        }
        event.editButton(event.getButton().asDisabled()).queue();
        RelicHelper.drawRelicAndNotify(player, event, game);
        EventHelper.revealEvent(event, game, game.getMainGameChannel());
    }

    @ButtonHandler("ignisAuroraSC8Secondary")
    public static void resolveIgnisAuroraSC8Secondary(ButtonInteractionEvent event, Game game, Player player) {
        player.addFragment("urf1");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " gained an " + ExploreEmojis.UFrag + " Unknown Relic Fragment");
    }
}
