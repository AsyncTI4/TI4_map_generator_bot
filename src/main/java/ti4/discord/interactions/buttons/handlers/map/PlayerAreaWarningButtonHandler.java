package ti4.discord.interactions.buttons.handlers.map;

import java.io.IOException;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.tile.SystemContextImage;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.service.map.PlayerAreaWarningService;
import ti4.service.map.PlayerAreaWarningService.AffectedSystems;

@UtilityClass
class PlayerAreaWarningButtonHandler {

    private static final int MAX_SYSTEMS = 10;

    @ButtonHandler(value = PlayerAreaWarningService.SHOW_SYSTEMS_PREFIX, save = false)
    public static void showSystems(Game game, ButtonInteractionEvent event, String buttonID) {
        AffectedSystems affected = PlayerAreaWarningService.affectedSystems(game, buttonID);
        if (affected == null || affected.positions().isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "No systems to show anymore.");
            return;
        }
        MessageChannel channel =
                game.getBotMapUpdatesThread() != null ? game.getBotMapUpdatesThread() : event.getMessageChannel();
        List<String> positions = affected.positions();
        List<String> shown = positions.subList(0, Math.min(MAX_SYSTEMS, positions.size()));
        MessageHelper.sendMessageToChannel(channel, header(game, event, affected, shown.size(), positions.size()));
        for (String position : shown) {
            sendSystem(game, event, channel, position);
        }
        if (channel != event.getMessageChannel()) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "Systems sent to " + channel.getAsMention() + ".");
        }
    }

    private static String header(
            Game game, ButtonInteractionEvent event, AffectedSystems affected, int shown, int total) {
        Player clicker = game.getPlayer(event.getUser().getId());
        String ping = clicker == null ? event.getUser().getAsMention() : clicker.getRepresentationUnfogged();
        String more = shown < total ? " (first " + shown + " of " + total + ")" : "";
        return ping + ", here are the systems with " + affected.description() + more + ".";
    }

    private static void sendSystem(Game game, ButtonInteractionEvent event, MessageChannel channel, String position) {
        try (FileUpload picture = SystemContextImage.upload(game, event, 0, position)) {
            MessageHelper.sendMessageWithFile(channel, picture, "System " + position, false);
        } catch (IOException e) {
            BotLogger.error(new LogOrigin(event, game), "Failed to close FileUpload", e);
        }
    }
}
