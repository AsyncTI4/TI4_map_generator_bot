package ti4.service.fow;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.logging.BotLogger;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;

@UtilityClass
public class FowScoringStatusService {

    private static final String REFRESH_BUTTON = "fowScoringStatusRefresh";

    public static void postForAllPlayers(Game game) {
        if (!FoWHelper.isFogQol01(game)) return;
        for (Player player : game.getRealPlayers()) {
            TextChannel channel = player.getPrivateChannel();
            if (channel == null) continue;
            MessageHelper.splitAndSentWithAction(
                    statusText(game, player),
                    channel,
                    List.of(Buttons.gray(REFRESH_BUTTON, "Refresh My Status")),
                    message -> trackMessage(game, player, channel, message.getId()));
        }
    }

    public static void refresh(Game game, Player player) {
        if (!FoWHelper.isFogQol01(game)) return;
        TextChannel channel = player.getPrivateChannel();
        if (channel == null) return;
        GameMessageManager.getOne(game.getName(), GameMessageType.FOW_SCORING_STATUS, player.getFaction())
                .ifPresent(message -> channel.editMessageById(message.messageId(), statusText(game, player))
                        .queue(Consumers.nop(), BotLogger::catchRestError));
    }

    @ButtonHandler(value = REFRESH_BUTTON, save = false)
    public static void refreshFromButton(ButtonInteractionEvent event, Game game, Player player) {
        event.getMessage().editMessage(statusText(game, player)).queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static void trackMessage(Game game, Player player, TextChannel channel, String messageId) {
        GameMessage status = new GameMessage(
                messageId, GameMessageType.FOW_SCORING_STATUS, game.getLastModifiedDate(), player.getFaction());
        String replaced = GameMessageManager.replace(game.getName(), status);
        if (replaced != null) {
            channel.deleteMessageById(replaced).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static String statusText(Game game, Player player) {
        return "## Your Scoring Status (Round " + game.getRound() + ")\n" + Helper.getPlayerScoringStatus(game, player);
    }
}
