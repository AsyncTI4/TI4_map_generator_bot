package ti4.listeners.annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.listeners.context.ButtonContext;
import ti4.listeners.context.ListenerContext;
import ti4.listeners.context.ModalContext;
import ti4.listeners.context.SelectionMenuContext;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;

public class AnnotationHandler {

    private static <C extends ListenerContext> boolean validateParams(Method method, Class<C> contextClass) {
        boolean hasComponentID = false;
        List<Parameter> badParams = new ArrayList<>();
        for (Parameter param : method.getParameters()) {
            // easy parameters
            if (param.getType().equals(contextClass)) continue;
            if (param.getType().equals(Game.class)) continue;
            if (param.getType().equals(Player.class)) continue;
            if (param.getType().equals(GenericInteractionCreateEvent.class)) continue;
            if (param.getType().equals(MessageChannel.class)) continue;

            // other event parameters
            if (param.getType().equals(ButtonInteractionEvent.class) && contextClass.equals(ButtonContext.class)) continue;
            if (param.getType().equals(ModalInteractionEvent.class) && contextClass.equals(ModalContext.class)) continue;
            if (param.getType().equals(StringSelectInteractionEvent.class) && contextClass.equals(SelectionMenuContext.class)) continue;

            // string parameters
            if (param.getType().equals(String.class)) {
                NamedParam nameAnnotation = param.getAnnotation(NamedParam.class);
                String name = param.isNamePresent() ? param.getName() : (nameAnnotation == null ? null : nameAnnotation.value());

                if (name == null) {
                    if (!hasComponentID) {
                        hasComponentID = true;
                    } else {
                        badParams.add(param);
                    }
                    continue;
                }
                switch (name.toLowerCase()) {
                    case "componentid", "component" -> {
                        continue;
                    }
                    case "buttonid", "button" -> {
                        if (contextClass.equals(ButtonContext.class)) continue;
                    }
                    case "modalid", "modal" -> {
                        if (contextClass.equals(ModalContext.class)) continue;
                    }
                    case "menuid", "menu" -> {
                        if (contextClass.equals(SelectionMenuContext.class)) continue;
                    }
                    case "messageid", "message" -> {
                        if (contextClass.equals(ButtonContext.class)) continue;
                        if (contextClass.equals(SelectionMenuContext.class)) continue;
                    }
                }
            }
            badParams.add(param);
        }
        if (!badParams.isEmpty()) {
            String er = "Bad parameters detected in method `" + method.getClass().getName() + "." + method.getName() + "`. Please fix:\n> - ";
            er += String.join("\n> - ", badParams.stream().map(param -> param.getType().getSimpleName() + " " + param.getName()).toList());

            // This error can only be logged to the console because JDA isn't ready yet.
            // As such, in an effort to be notified if something goes horribly wrong, still add the handler
            // so that when it gets called it will generate an error for bot-log and ping Jazz.
            System.out.println(er);
            // BotLogger.log(er);
        }
        return true;
    }

    private static <C extends ListenerContext> Function<C, List<Object>> getArgs(Method method, Class<C> contextClass) {
        if (!validateParams(method, contextClass)) return null;

        return ctx -> {
            boolean hasComponentID = false;
            List<Object> args = new ArrayList<>();
            for (Parameter param : method.getParameters()) {
                if (param.getType().equals(contextClass)) args.add(ctx);
                if (param.getType().equals(Game.class)) args.add(ctx.getGame());
                if (param.getType().equals(Player.class)) args.add(ctx.getPlayer());
                if (param.getType().equals(GenericInteractionCreateEvent.class)) args.add(ctx.getEvent());
                if (param.getType().equals(ButtonInteractionEvent.class) && contextClass.equals(ButtonContext.class)) args.add(ctx.getEvent());
                if (param.getType().equals(ModalInteractionEvent.class) && contextClass.equals(ModalContext.class)) args.add(ctx.getEvent());
                if (param.getType().equals(StringSelectInteractionEvent.class) && contextClass.equals(SelectionMenuContext.class)) args.add(ctx.getEvent());
                if (param.getType().equals(MessageChannel.class)) args.add(ctx.getEvent().getMessageChannel());

                // string parameters
                // if the string is unnamed, assume it is the componentID
                if (param.getType().equals(String.class)) {
                    NamedParam name = param.getAnnotation(NamedParam.class);
                    if (name == null) {
                        if (!hasComponentID) {
                            hasComponentID = true;
                            args.add(ctx.getComponentID());
                        }
                    } else {
                        switch (name.value().toLowerCase()) {
                            case "componentid", "component" -> args.add(ctx.getComponentID());
                            case "buttonid", "button" -> {
                                if (ctx instanceof ButtonContext bctx) args.add(bctx.getButtonID());
                            }
                            case "modalid", "modal" -> {
                                if (ctx instanceof ModalContext mctx) args.add(mctx.getModalID());
                            }
                            case "menuid", "menu" -> {
                                if (ctx instanceof SelectionMenuContext sctx) args.add(sctx.getMenuID());
                            }
                            case "messageid", "message" -> {
                                if (ctx instanceof ButtonContext bctx) args.add(bctx.getMessageID());
                                if (ctx instanceof SelectionMenuContext sctx) args.add(sctx.getMessageID());
                            }
                        }
                    }
                }
            }
            return args;
        };
    }

    private static <T extends ListenerContext> Consumer<T> buildConsumer(Method method, Function<T, List<Object>> getArgs, boolean save) {
        return context -> {
            List<Object> args = getArgs.apply(context);
            try {
                method.setAccessible(true);
                context.setShouldSave(save);
                method.invoke(null, args.toArray());
            } catch (InvocationTargetException e) {
                BotLogger.error("Error within handler \"" + method.getDeclaringClass().getSimpleName() + "#" + method.getName() + "\":", e.getCause());
                for (Object arg : args) {
                    if (arg instanceof ButtonInteractionEvent buttonInteractionEvent) {
                        buttonInteractionEvent.getInteraction().getMessage()
                            .reply("The button failed. An exception has been logged for the developers.")
                            .queue();
                    }
                    if (arg instanceof StringSelectInteractionEvent selectInteractionEvent) {
                        selectInteractionEvent.getInteraction().getMessage()
                            .reply("The selection failed. An exception has been logged for the developers.")
                            .queue();
                    }
                }
            } catch (Exception e) {
                List<String> paramTypes = Arrays.stream(method.getParameters()).map(param -> param.getType().getSimpleName()).toList();
                List<String> argTypes = args.stream().map(obj -> obj.getClass().getSimpleName()).toList();

                String methodName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
                String paramString = "(" + String.join(", ", paramTypes) + ")";
                String argsString = "(" + String.join(", ", argTypes) + ")";

                String error = Constants.jazzPing() + " button handler failed. Please fix the configuration.\n";
                error += "`Expected: " + methodName + paramString + "`\n";
                error += "`Received: " + methodName + argsString + "`";
                BotLogger.error(error, e);
            }
        };
    }

    private static List<Class<?>> contexts() {
        return List.of(ButtonContext.class, ModalContext.class, SelectionMenuContext.class);
    }

    private static List<Class<?>> handlers() {
        return List.of(ButtonHandler.class, ModalHandler.class, SelectionHandler.class);
    }

    /**
     * <p>
     * Find all functions that are tagged with `@handlerClass`, and which take parameters based on `contextClass`.
     * <p>
     * Untagged String parameters are assumed to be `componentID`. Use {@link NamedParam} to tag string parameters for now
     * 
     * @param <C> {@link AnnotationHandler#contexts}
     * @param <H> {@link AnnotationHandler#handlers}
     * @param contextClass Which context type to accept parameters based upon
     * @param handlerClass Which handler annotation to look for
     * @return A map of prefix -> consumer which will
     */
    public static <C extends ListenerContext, H extends Annotation> Map<String, Consumer<C>> findKnownHandlers(Class<C> contextClass, Class<H> handlerClass) {
        Map<String, Consumer<C>> consumers = new HashMap<>();
        try {
            if (!handlers().contains(handlerClass)) {
                BotLogger.warning("Unknown handler class `" + handlerClass.getName() + "`. Please fix " + Constants.jazzPing());
                return consumers;
            }
            if (!contexts().contains(contextClass)) {
                BotLogger.warning("Unknown context class `" + contextClass.getName() + "`. Please fix " + Constants.jazzPing());
                return consumers;
            }
            for (Class<?> klass : AsyncTI4DiscordBot.getAllClasses()) {
                for (Method method : klass.getDeclaredMethods()) {
                    method.setAccessible(true);
                    List<H> handlers = Arrays.asList(method.getAnnotationsByType(handlerClass));
                    if (handlers.isEmpty()) continue;

                    String methodName = klass.getName() + "." + method.getName();
                    if (!Modifier.isStatic(method.getModifiers())) {
                        BotLogger.warning("Method `" + methodName + "` is not static. Please fix it " + Constants.jazzPing());
                        continue;
                    }

                    Function<C, List<Object>> argGetter = getArgs(method, contextClass);
                    if (argGetter == null) {
                        continue;
                    }
                    
                    for (H handler : handlers) {
                        String val = null;
                        Boolean save = true;
                        if (handler instanceof ButtonHandler bh) { val = bh.value(); save = bh.save(); };
                        if (handler instanceof SelectionHandler sh) val = sh.value();
                        if (handler instanceof ModalHandler mh) val = mh.value();
                        if (val == null) continue;
                        Consumer<C> consumer = buildConsumer(method, argGetter, save);
                        consumers.put(val, consumer);
                    }
                }
            }
        } catch (SecurityException e) {
            BotLogger.error(Constants.jazzPing() + " bot cannot read methods in the file.", e);
        } catch (Exception e) {
            BotLogger.error(Constants.jazzPing() + " some other issue registering buttons.", e);
        }

        BotLogger.info("Registered " + consumers.size() + " handlers of type " + handlerClass.getName());
        return consumers;
    }

}
