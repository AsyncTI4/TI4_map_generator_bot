package ti4.discord.interactions.buttons.handlers.strategycard;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.EventHelper;
import ti4.helpers.RelicHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
class IgnisStrategyCardButtonHandler {

    @ButtonHandler("ignisAuroraSC8Primary")
    public static void resolveIgnisAuroraSC8Primary(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasStrategyCard(2)) {
            MessageHelper.sendMessageToEventChannel(event, "You don't have the Antiquities strategy card.");
            return;
        }
        event.editButton(event.getButton().asDisabled()).queue(Consumers.nop(), BotLogger::catchRestError);
        RelicHelper.drawRelicAndNotify(player, event, game);
        EventHelper.revealEvent(event, game, game.getMainGameChannel());
    }

    @ButtonHandler("ignisAuroraSC8Secondary")
    public static void resolveIgnisAuroraSC8Secondary(Player player) {
        player.addFragment("urf1");
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.toString() + " gained an " + ExploreEmojis.UFrag + " Unknown Relic Fragment");
    }
}
