package ti4.discord.interactions.buttons.handlers.faction.homebrew.lunarium;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.leader.ExhaustLeaderService;

@UtilityClass
public class LunariumAgentButtonHandler {

    @ButtonHandler("exhaustAgent_lunariumagent")
    public static void exhaustLunariumAgent(ButtonInteractionEvent event, Game game, Player player) {
        Leader playerLeader = player.getLeader("lunariumagent").orElse(null);
        if (playerLeader == null) return;
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), player.getRepresentation() + " has exhausted Gu'la Ma, the Lunarium agent.");
        ExhaustLeaderService.exhaustLeader(game, player, playerLeader);
        player.addSpentThing("lunariumagent");
        event.getMessage()
                .editMessage(Helper.buildSpentThingsMessage(player, game, "res"))
                .queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
