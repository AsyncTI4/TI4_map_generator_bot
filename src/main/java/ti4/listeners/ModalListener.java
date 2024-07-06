package ti4.listeners;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import lombok.NonNull;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.async.RoundSummaryHelper;
import ti4.listeners.annotations.ModalHandler;
import ti4.listeners.context.ModalContext;
import ti4.map.Game;
import ti4.message.BotLogger;

public class ModalListener extends ListenerAdapter {
    public static ModalListener instance = null;

    private final Map<String, Consumer<ModalContext>> knownModals = new HashMap<>();

    public static ModalListener getInstance() {
        if (instance == null)
            instance = new ModalListener();
        return instance;
    }

    private ModalListener() {
        try {
            findKnownModals();
        } catch (SecurityException e) {
            BotLogger.log(Constants.jazzPing() + " bot cannot read methods in the file.", e);
        } catch (Exception e) {
            BotLogger.log(Constants.jazzPing() + " some other issue registering buttons.", e);
        }
    }

    private List<Class<?>> knownModalClasses() {
        List<Class<?>> classesWithButtons = new ArrayList<>();
        // Async
        classesWithButtons.addAll(List.of(RoundSummaryHelper.class));// , WhisperHelper.class, NotepadHelper.class));

        return classesWithButtons;
    }

    private void findKnownModals() {
        List<Class<?>> classesWithModals = knownModalClasses();

        Function<Method, Consumer<ModalContext>> transformer = method -> ctx -> {
            String errorString = Constants.jazzPing() + " button handler failed. Please fix the configuration.";
            try {
                method.invoke(null, ctx);
            } catch (IllegalAccessException e) {
                BotLogger.log(errorString, e);
            } catch (IllegalArgumentException e) {
                BotLogger.log(errorString, e);
            } catch (InvocationTargetException e) {
                BotLogger.log(errorString, e);
            }
        };

        // Find all the buttons
        for (Class<?> c : classesWithModals) {
            List<Method> methods = Arrays.asList(c.getMethods());
            if (methods == null) continue;

            for (Method m : methods) {
                List<ModalHandler> handlers = Arrays.asList(m.getAnnotationsByType(ModalHandler.class));
                if (handlers == null || handlers.isEmpty()) continue;

                List<Class<?>> methodParams = Arrays.asList(m.getParameterTypes());
                if (methodParams.size() != 1 || !methodParams.get(0).equals(ModalContext.class)) {
                    BotLogger.log("Method " + m.getName() + " in class " + c.getName() + " is not a properly formatted button handler.");
                    continue;
                }

                Consumer<ModalContext> handler = transformer.apply(m);
                for (ModalHandler handleString : handlers) {
                    knownModals.put(handleString.value(), handler);
                }
            }
        }
    }

    private boolean handleKnownModals(ModalContext context) {
        String modalID = context.getModalID();
        // Check for exact match first
        if (knownModals.containsKey(modalID)) {
            System.out.println("Found modal " + modalID);
            knownModals.get(modalID).accept(context);
            return true;
        }

        // Then check for prefix match
        for (String key : knownModals.keySet()) {
            if (modalID.startsWith(key)) {
                System.out.println("Found modal " + modalID);
                knownModals.get(key).accept(context);
                return true;
            }
        }
        return false;
    }

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

    private void resolveModalInteractionEvent(@NonNull ModalContext context) {
        String modalID = context.getModalID();
        Game game = context.getGame();

        if (handleKnownModals(context)) return;

        if (modalID.startsWith("jmfA_")) {
            game.initializeMiltySettings().parseInput(context);
        }
    }
}
