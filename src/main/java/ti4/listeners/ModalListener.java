package ti4.listeners;

import java.util.Date;

import lombok.NonNull;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.listeners.context.ModalContext;
import ti4.map.Game;
import ti4.message.BotLogger;

public class ModalListener extends ListenerAdapter {

    @Override
    public void onModalInteraction(@NonNull ModalInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to handle button presses.").setEphemeral(true).queue();
            return;
        }
        event.deferEdit().queue();
        long startTime = new Date().getTime();
        try {
            ModalContext context = new ModalContext(event);
            if (context.isValid()) {
                resolveModalInteractionEvent(context);
            }
        } catch (Exception e) {
            BotLogger.log(event, "Something went wrong with button interaction", e);
        }
        long endTime = new Date().getTime();
        if (endTime - startTime > 3000) {
            BotLogger.log(event, "This button command took longer than 3000 ms (" + (endTime - startTime) + ")");
        }
    }

    private static void resolveModalInteractionEvent(@NonNull ModalContext context) {
        String modalID = context.getModalID();
        Game game = context.getGame();

        if (modalID.startsWith("jmfA_")) {
            game.initializeMiltySettings().parseInput(context);
        }
    }
}
