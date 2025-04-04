package ti4.listeners;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import ti4.AsyncTI4DiscordBot;
import ti4.executors.ExecutorManager;
import ti4.listeners.annotations.AnnotationHandler;
import ti4.listeners.annotations.ModalHandler;
import ti4.listeners.context.ModalContext;
import ti4.map.Game;
import ti4.message.BotLogger;
import ti4.service.game.GameNameService;

public class ModalListener extends ListenerAdapter {

    public static ModalListener instance = null;

    private final Map<String, Consumer<ModalContext>> knownModals = new HashMap<>();

    public static ModalListener getInstance() {
        if (instance == null)
            instance = new ModalListener();
        return instance;
    }

    private ModalListener() {
        knownModals.putAll(AnnotationHandler.findKnownHandlers(ModalContext.class, ModalHandler.class));
    }

    @Override
    public void onModalInteraction(@Nonnull ModalInteractionEvent event) {
        if (!AsyncTI4DiscordBot.isReadyToReceiveCommands()) {
            event.reply("Please try again in a moment. The bot is not ready to handle button presses.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        String gameName = GameNameService.getGameNameFromChannel(event);
        ExecutorManager.runAsync("ModalListener task", gameName, event.getMessageChannel(), () -> handleModal(event));
    }

    private void handleModal(@Nonnull ModalInteractionEvent event) {
        try {
            ModalContext context = new ModalContext(event);
            if (context.isValid()) {
                resolveModalInteractionEvent(context);
                context.save();
            }
        } catch (Exception e) {
            String message = "Modal issue in event: " + event.getModalId() + "\n> Channel: " + event.getChannel().getAsMention() + "\n> Command: " + event.getValues();
            BotLogger.error(new BotLogger.LogMessageOrigin(event), message, e);
        }
    }

    private boolean handleKnownModals(ModalContext context) {
        String modalID = context.getModalID();
        // Check for exact match first
        if (knownModals.containsKey(modalID)) {
            knownModals.get(modalID).accept(context);
            return true;
        }

        // Then check for prefix match
        for (String key : knownModals.keySet()) {
            if (modalID.startsWith(key)) {
                knownModals.get(key).accept(context);
                return true;
            }
        }
        return false;
    }

    private void resolveModalInteractionEvent(@Nonnull ModalContext context) {
        String modalID = context.getModalID();
        Game game = context.getGame();

        if (handleKnownModals(context)) return;

        if (modalID.startsWith("jmfA_")) {
            game.initializeMiltySettings().parseInput(context);
        }
    }

    public static String getModalDebugText(ModalInteractionEvent event) {
        StringBuilder output = new StringBuilder("INPUT:\n```\n" +
            "MenuID: " + event.getModalId());
        for (ModalMapping field : event.getValues()) {
            output.append("\n> Field: ").append(field.getId()).append(" => ").append(field.getAsString());
        }
        output.append("\n```");
        return output.toString();
    }
}
