package ti4.service.testbed;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.InteractionType;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import ti4.discord.interactions.buttons.ButtonProcessor;
import ti4.discord.interactions.buttons.Buttons;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;

@UtilityClass
public class TestBedPress {

    static final int HISTORY_SIZE = 25;
    private static final long SEARCH_RETRY_MILLIS = 2000;
    private static final String FACTION_CHECK_PREFIX = "FFCC_";

    public static final class Recorder {
        private final List<String> replies = new ArrayList<>();
        private final List<String> unsupportedCalls = new ArrayList<>();
        private String modalId;

        synchronized void reply(String text) {
            if (text != null && !text.isBlank()) replies.add(text);
        }

        synchronized void unsupported(String call) {
            if (!unsupportedCalls.contains(call)) unsupportedCalls.add(call);
        }

        synchronized void modal(String id) {
            modalId = id;
        }

        public synchronized List<String> replies() {
            return List.copyOf(replies);
        }

        public synchronized List<String> unsupportedCalls() {
            return List.copyOf(unsupportedCalls);
        }

        @Nullable
        public synchronized String modalId() {
            return modalId;
        }
    }

    public record PressResult(boolean pressed, String detail, Recorder recorder) {}

    private record Found(Message message, Button button) {}

    public static PressResult pressVisible(
            Game game,
            Member developer,
            Player seat,
            Supplier<List<MessageChannel>> channels,
            String labelOrId,
            long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        List<String> seen = new ArrayList<>();
        Found found = find(channels.get(), labelOrId, seen);
        while (found == null && System.currentTimeMillis() < deadline) {
            sleep(SEARCH_RETRY_MILLIS);
            seen.clear();
            found = find(channels.get(), labelOrId, seen);
        }
        if (found != null) return press(game, developer, seat, found.message(), found.button());
        String visible = seen.isEmpty()
                ? "no buttons"
                : String.join(", ", seen.stream().distinct().toList());
        return new PressResult(
                false,
                "no button `" + labelOrId + "` after " + timeoutMillis / 1000 + "s; visible: " + visible,
                new Recorder());
    }

    @Nullable
    private static Found find(List<MessageChannel> channels, String labelOrId, List<String> seen) {
        Found prefixMatch = null;
        for (MessageChannel channel : channels) {
            if (channel == null) continue;
            for (Message message :
                    channel.getHistory().retrievePast(HISTORY_SIZE).complete()) {
                for (Button button : message.getComponentTree().findAll(Button.class)) {
                    if (button.getCustomId() == null) continue;
                    Match match = match(button, labelOrId);
                    if (match == Match.EXACT) return new Found(message, button);
                    if (match == Match.PREFIX && prefixMatch == null) prefixMatch = new Found(message, button);
                    seen.add(button.getLabel() + " (`" + button.getCustomId() + "`)");
                }
            }
        }
        return prefixMatch;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static PressResult pressById(
            Game game, Member developer, Player seat, MessageChannel channel, String buttonId) {
        Button button = Buttons.gray(buttonId, "test bed: " + buttonId);
        Message carrier = channel.sendMessage("[test bed] press `" + buttonId + "` as " + seat.getFaction())
                .setComponents(ActionRow.of(button))
                .complete();
        return press(game, developer, seat, carrier, button);
    }

    enum Match {
        EXACT,
        PREFIX,
        NONE
    }

    static Match match(Button button, String labelOrId) {
        String id = button.getCustomId();
        String withoutFaction = withoutFactionCheck(id);
        if (labelOrId.equalsIgnoreCase(button.getLabel()) || labelOrId.equals(id) || labelOrId.equals(withoutFaction)) {
            return Match.EXACT;
        }
        if (id.startsWith(labelOrId) || withoutFaction.startsWith(labelOrId)) return Match.PREFIX;
        return Match.NONE;
    }

    private static String withoutFactionCheck(String id) {
        if (!id.startsWith(FACTION_CHECK_PREFIX)) return id;
        String withoutCheck = id.substring(FACTION_CHECK_PREFIX.length());
        return withoutCheck.substring(withoutCheck.indexOf('_') + 1);
    }

    private static PressResult press(Game game, Member developer, Player seat, Message message, Button button) {
        String developerId = developer.getId();
        Player previous = TestBedService.getActingAs(game, developerId);
        String previousFaction = previous == null ? null : previous.getFaction();
        runLocked(game, locked -> TestBedService.setActingAs(locked, developerId, seat));
        Recorder recorder = new Recorder();
        try {
            ButtonProcessor.processNow(standInEvent(message, button, developer, recorder));
        } finally {
            runLocked(
                    game,
                    locked -> TestBedService.setActingAs(
                            locked,
                            developerId,
                            previousFaction == null ? null : locked.getPlayerFromColorOrFaction(previousFaction)));
        }
        return new PressResult(true, "pressed `" + button.getLabel() + "` (`" + button.getCustomId() + "`)", recorder);
    }

    public static boolean runLocked(Game game, Consumer<Game> action) {
        String gameName = game.getName();
        ExecutionLockManager.lock(gameName, ExecutionLockType.WRITE);
        try {
            ManagedGame managed = GameManager.getManagedGame(gameName);
            if (managed == null) return false;
            Game current = managed.getGame();
            action.accept(current);
            GameManager.save(current, "Test bed script step");
            return true;
        } finally {
            ExecutionLockManager.unlock(gameName, ExecutionLockType.WRITE);
        }
    }

    static ButtonInteractionEvent standInEvent(Message message, Button button, Member developer, Recorder recorder) {
        JDA jda = message.getJDA();
        ButtonInteraction[] self = new ButtonInteraction[1];
        InvocationHandler handler =
                (proxy, method, args) -> answerInteraction(self[0], method, args, message, button, developer, recorder);
        self[0] = (ButtonInteraction) Proxy.newProxyInstance(
                ButtonInteraction.class.getClassLoader(), new Class<?>[] {ButtonInteraction.class}, handler);
        return new ButtonInteractionEvent(jda, 0, self[0]);
    }

    private static Object answerInteraction(
            ButtonInteraction self,
            Method method,
            Object[] args,
            Message message,
            Button button,
            Member developer,
            Recorder recorder)
            throws Throwable {
        String name = method.getName();
        return switch (name) {
            case "getButton", "getComponent" -> button;
            case "getComponentId", "getCustomId" -> button.getCustomId();
            case "getUniqueId" -> button.getUniqueId();
            case "getComponentType" -> button.getType();
            case "getMessage" -> message;
            case "getMessageId" -> message.getId();
            case "getMessageIdLong" -> message.getIdLong();
            case "getChannel", "getMessageChannel" -> message.getChannel();
            case "getGuildChannel" -> message.getGuildChannel();
            case "getChannelId" -> message.getChannel().getId();
            case "getChannelIdLong" -> message.getChannel().getIdLong();
            case "getChannelType" -> message.getChannelType();
            case "getUser" -> developer.getUser();
            case "getMember" -> developer;
            case "getGuild" -> message.getGuild();
            case "getJDA" -> message.getJDA();
            case "isFromGuild", "isFromAttachedGuild", "isAcknowledged" -> true;
            case "getTypeRaw" -> InteractionType.COMPONENT.getKey();
            case "getType" -> InteractionType.COMPONENT;
            case "getToken" -> "test-bed";
            case "getIdLong" -> message.getIdLong();
            case "getId" -> message.getId();
            case "getUserLocale", "getGuildLocale" -> DiscordLocale.ENGLISH_US;
            case "getEntitlements" -> List.of();
            case "getContext" -> InteractionContextType.GUILD;
            case "getIntegrationOwners" ->
                message.getInteractionMetadata() == null
                        ? null
                        : message.getInteractionMetadata().getIntegrationOwners();
            case "getHook" -> hook(self, message.getJDA(), recorder);
            case "replyModal" -> {
                recorder.modal(((Modal) args[0]).getId());
                yield noOpAction(method.getReturnType(), message.getJDA(), recorder);
            }
            case "toString" -> "TestBedButtonInteraction[" + button.getCustomId() + "]";
            case "hashCode" -> System.identityHashCode(self);
            case "equals" -> args[0] == self;
            default -> {
                if (method.isDefault()) yield InvocationHandler.invokeDefault(self, method, args);
                recordText(recorder, args);
                if (!name.startsWith("reply") && !name.startsWith("defer") && !name.startsWith("edit")) {
                    recorder.unsupported(name);
                }
                yield defaultValue(method, message.getJDA(), recorder);
            }
        };
    }

    private static InteractionHook hook(ButtonInteraction interaction, JDA jda, Recorder recorder) {
        InteractionHook[] self = new InteractionHook[1];
        self[0] = (InteractionHook) Proxy.newProxyInstance(
                InteractionHook.class.getClassLoader(),
                new Class<?>[] {InteractionHook.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getInteraction".equals(name)) return interaction;
                    if ("getJDA".equals(name)) return jda;
                    if ("isExpired".equals(name)) return false;
                    if ("toString".equals(name)) return "TestBedInteractionHook";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return args[0] == proxy;
                    recordText(recorder, args);
                    if (returnsSelf(method, proxy)) return proxy;
                    return defaultValue(method, jda, recorder);
                });
        return self[0];
    }

    private static Object defaultValue(Method method, JDA jda, Recorder recorder) {
        Class<?> type = method.getReturnType();
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class || type == long.class || type == short.class || type == byte.class) {
            return primitiveZero(type);
        }
        if (Collection.class.isAssignableFrom(type)) return List.of();
        if (type.isInterface() && type.getName().startsWith("net.dv8tion.jda.api.requests")) {
            return noOpAction(type, jda, recorder);
        }
        return null;
    }

    private static Object primitiveZero(Class<?> type) {
        if (type == long.class) return 0L;
        if (type == short.class) return (short) 0;
        if (type == byte.class) return (byte) 0;
        return 0;
    }

    private static Object noOpAction(Class<?> actionType, JDA jda, Recorder recorder) {
        return Proxy.newProxyInstance(
                actionType.getClassLoader(), new Class<?>[] {actionType}, (proxy, method, args) -> {
                    String name = method.getName();
                    if ("submit".equals(name)) return CompletableFuture.completedFuture(null);
                    if ("getJDA".equals(name)) return jda;
                    if ("toString".equals(name)) return "TestBedNoOpAction[" + actionType.getSimpleName() + "]";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return args[0] == proxy;
                    recordText(recorder, args);
                    if (returnsSelf(method, proxy)) return proxy;
                    return defaultValue(method, jda, recorder);
                });
    }

    private static boolean returnsSelf(Method method, Object proxy) {
        Class<?> type = method.getReturnType();
        return type != Object.class && type.isInstance(proxy);
    }

    private static void recordText(Recorder recorder, @Nullable Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (arg instanceof CharSequence text) recorder.reply(text.toString());
            if (arg instanceof MessageCreateData data) recorder.reply(data.getContent());
            if (arg instanceof MessageEditData data) recorder.reply(data.getContent());
        }
    }
}
