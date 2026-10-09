package ti4.testUtils.discord;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.tree.ComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.IThreadContainerUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

/** A fake text channel or thread. Messages are kept in posting order; the newest is last. */
public final class FakeChannel {

    private final FakeDiscord discord;
    private final long id;
    private final String name;
    private final FakeChannel parent;
    private final boolean privateThread;
    private final List<FakeMessage> messages = new ArrayList<>();
    private final Object proxy;
    private boolean archived;

    FakeChannel(FakeDiscord discord, long id, String name, FakeChannel parent, boolean privateThread) {
        this.discord = discord;
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.privateThread = privateThread;
        this.proxy = parent == null
                ? ProxySupport.proxy(
                        discord,
                        "FakeTextChannel[" + name + "]",
                        this::handle,
                        TextChannel.class,
                        MessageChannelUnion.class,
                        GuildMessageChannelUnion.class,
                        IThreadContainerUnion.class,
                        GuildChannelUnion.class)
                : ProxySupport.proxy(
                        discord,
                        "FakeThread[" + name + "]",
                        this::handle,
                        ThreadChannel.class,
                        MessageChannelUnion.class,
                        GuildMessageChannelUnion.class,
                        GuildChannelUnion.class);
    }

    public long id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean isThread() {
        return parent != null;
    }

    public FakeChannel parent() {
        return parent;
    }

    public boolean isArchived() {
        return archived;
    }

    public Object proxy() {
        return proxy;
    }

    TextChannel asText() {
        return (TextChannel) proxy;
    }

    public synchronized List<FakeMessage> messages() {
        return List.copyOf(messages);
    }

    public synchronized List<FakeMessage> liveMessages() {
        return messages.stream().filter(message -> !message.isDeleted()).toList();
    }

    public List<FakeChannel> threads() {
        return discord.channels().stream()
                .filter(channel -> channel.parent() == this)
                .toList();
    }

    synchronized FakeMessage post(MessageCreateData data) {
        FakeMessage message = new FakeMessage(discord, this, discord.nextSnowflake(), data);
        messages.add(message);
        discord.register(message);
        if (archived) archived = false;
        return message;
    }

    synchronized FakeMessage find(long messageId) {
        return messages.stream()
                .filter(message -> message.id() == messageId && !message.isDeleted())
                .findFirst()
                .orElse(null);
    }

    public synchronized long latestMessageId() {
        return messages.isEmpty() ? 0L : messages.getLast().id();
    }

    private synchronized List<Message> newest(int count) {
        List<Message> newest = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0 && newest.size() < count; i--) {
            FakeMessage message = messages.get(i);
            if (!message.isDeleted()) newest.add(message.proxy());
        }
        return newest;
    }

    private Object handle(Object self, Method method, Object[] args) {
        String methodName = method.getName();
        if (methodName.startsWith("sendMessage") || "sendFiles".equals(methodName)) return send(methodName, args);
        if (methodName.startsWith("editMessage") && methodName.endsWith("ById")) return editById(methodName, args);
        return switch (methodName) {
            case "getIdLong" -> id;
            case "getId" -> String.valueOf(id);
            case "getName" -> name;
            case "getAsMention" -> "<#" + id + ">";
            case "getJumpUrl" -> "https://discord.com/channels/" + FakeDiscord.GUILD_ID + "/" + id;
            case "getGuild" -> discord.guild();
            case "getJDA" -> discord.jda();
            case "getType" ->
                parent == null
                        ? ChannelType.TEXT
                        : privateThread ? ChannelType.GUILD_PRIVATE_THREAD : ChannelType.GUILD_PUBLIC_THREAD;
            case "canTalk" -> true;
            case "isArchived" -> archived;
            case "isLocked", "isNSFW", "isNews" -> false;
            case "isPublic" -> !privateThread;
            case "isInvitable" -> false;
            case "getLatestMessageIdLong" -> latestMessageId();
            case "getParentChannel", "getParentMessageChannel" -> parent == null ? null : parent.proxy();
            case "getParentCategory", "getParentCategoryId", "getTopic" -> null;
            case "getOwnerIdLong" -> FakeDiscord.BOT_ID;
            case "getOwnerId" -> String.valueOf(FakeDiscord.BOT_ID);
            case "getMessageCount", "getTotalMessageCount", "getMemberCount", "getPositionRaw", "getSlowmode" -> 0;
            case "getMembers",
                    "getThreadMembers",
                    "getPermissionOverrides",
                    "getMemberPermissionOverrides",
                    "getRolePermissionOverrides" -> List.of();
            case "getThreadChannels" ->
                threads().stream().map(FakeChannel::proxy).toList();
            case "asTextChannel", "asStandardGuildMessageChannel", "asIThreadContainer" -> {
                if (parent != null) throw new IllegalStateException("Cannot convert a thread to " + methodName);
                yield proxy;
            }
            case "asThreadChannel" -> {
                if (parent == null) throw new IllegalStateException("Cannot convert a text channel to a thread");
                yield proxy;
            }
            case "asGuildMessageChannel", "asGuildChannel", "asMessageChannel" -> proxy;
            case "retrieveMessageById" ->
                action(method, "retrieveMessageById", state -> {
                    FakeMessage message = find(ProxySupport.idOf(args[0]));
                    if (message == null) throw FakeDiscord.unknownMessage();
                    return message.proxy();
                });
            case "deleteMessageById" ->
                action(method, "deleteMessageById", state -> {
                    FakeMessage message = find(ProxySupport.idOf(args[0]));
                    if (message == null) throw FakeDiscord.unknownMessage();
                    message.markDeleted();
                    return null;
                });
            case "addReactionById",
                    "removeReactionById",
                    "clearReactionsById",
                    "pinMessageById",
                    "unpinMessageById",
                    "deleteMessages",
                    "deleteMessagesByIds",
                    "purgeMessagesById",
                    "join",
                    "leave",
                    "addThreadMember",
                    "addThreadMemberById",
                    "removeThreadMember",
                    "removeThreadMemberById",
                    "sendTyping",
                    "delete" -> action(method, methodName, state -> null);
            case "getHistory" -> history();
            case "createThreadChannel" -> createThread(method, args);
            case "retrieveArchivedPublicThreadChannels",
                    "retrieveArchivedPrivateThreadChannels",
                    "retrieveArchivedPrivateJoinedThreadChannels" -> archivedThreads(method);
            case "retrieveThreadMembers", "retrievePinnedMessages" -> action(method, methodName, state -> List.of());
            case "getManager" ->
                FakeRestAction.of(
                        discord,
                        method.getReturnType(),
                        "manager " + name,
                        state -> {
                            if (state.called("setArchived")) archived = (Boolean) state.lastArg("setArchived");
                            return null;
                        },
                        null);
            default -> ProxySupport.UNHANDLED;
        };
    }

    private Object action(Method method, String description, FakeRestAction.Body body) {
        return FakeRestAction.of(discord, method.getReturnType(), description + " in " + name, body, null);
    }

    private Object send(String methodName, Object[] args) {
        MessageCreateBuilder builder = new MessageCreateBuilder();
        switch (methodName) {
            case "sendMessage" -> {
                if (args[0] instanceof MessageCreateData data) builder.applyData(data);
                else builder.setContent(String.valueOf(args[0]));
            }
            case "sendMessageFormat" ->
                builder.setContent(String.format((String) args[0], ProxySupport.varargs(args, 1)));
            case "sendMessageEmbeds" -> builder.setEmbeds(embeds(args));
            case "sendMessageComponents" -> builder.setComponents(components(args));
            case "sendFiles" -> builder.setFiles(files(args));
            case "sendMessagePoll" -> builder.setPoll((net.dv8tion.jda.api.utils.messages.MessagePollData) args[0]);
            default -> discord.recordUnsupported(name + "." + methodName);
        }
        return FakeRestAction.of(
                discord,
                MessageCreateAction.class,
                methodName + " in " + name,
                state -> post(((MessageCreateBuilder) state.builder).build()).proxy(),
                builder);
    }

    private Object editById(String methodName, Object[] args) {
        long messageId = ProxySupport.idOf(args[0]);
        MessageEditBuilder builder =
                FakeMessage.editBuilder(methodName.replace("ById", ""), ProxySupport.varargs(args, 1));
        return FakeRestAction.of(
                discord,
                MessageEditAction.class,
                methodName + " in " + name,
                state -> {
                    FakeMessage message = find(messageId);
                    if (message == null) throw FakeDiscord.unknownMessage();
                    message.edit(((MessageEditBuilder) state.builder).build());
                    return message.proxy();
                },
                builder);
    }

    static List<MessageEmbed> embeds(Object[] args) {
        List<MessageEmbed> embeds = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof MessageEmbed embed) embeds.add(embed);
            else if (arg instanceof MessageEmbed[] array) Collections.addAll(embeds, array);
            else if (arg instanceof Collection<?> collection)
                collection.forEach(item -> embeds.add((MessageEmbed) item));
        }
        return embeds;
    }

    @SuppressWarnings("unchecked")
    static List<MessageTopLevelComponent> components(Object[] args) {
        List<MessageTopLevelComponent> components = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof MessageTopLevelComponent component) components.add(component);
            else if (arg instanceof MessageTopLevelComponent[] array) Collections.addAll(components, array);
            else if (arg instanceof ComponentTree<?> tree)
                components.addAll((List<MessageTopLevelComponent>) tree.getComponents());
            else if (arg instanceof Collection<?> collection)
                collection.forEach(item -> components.add((MessageTopLevelComponent) item));
        }
        return components;
    }

    static List<FileUpload> files(Object[] args) {
        List<FileUpload> files = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof FileUpload file) files.add(file);
            else if (arg instanceof FileUpload[] array) Collections.addAll(files, array);
            else if (arg instanceof Collection<?> collection) collection.forEach(item -> files.add((FileUpload) item));
        }
        return files;
    }

    private MessageHistory history() {
        MessageHistory history = mock(MessageHistory.class);
        when(history.retrievePast(anyInt())).thenAnswer(invocation -> {
            int count = invocation.getArgument(0);
            if (count < 1 || count > 100)
                throw new IllegalArgumentException("Message retrieval limit is between 1 and 100");
            return FakeRestAction.of(
                    discord,
                    net.dv8tion.jda.api.requests.RestAction.class,
                    "retrievePast in " + name,
                    state -> newest(count),
                    null);
        });
        when(history.getChannel()).thenReturn((MessageChannelUnion) proxy);
        return history;
    }

    private Object createThread(Method method, Object[] args) {
        String threadName = (String) args[0];
        Long threadId = null;
        boolean isPrivate = false;
        if (args.length > 1) {
            if (args[1] instanceof Boolean flag) isPrivate = flag;
            else threadId = ProxySupport.idOf(args[1]);
        }
        Long requestedId = threadId;
        boolean privateFlag = isPrivate;
        return FakeRestAction.of(
                discord,
                method.getReturnType(),
                "createThreadChannel " + threadName,
                state -> {
                    FakeChannel existing = requestedId == null ? null : discord.channel(requestedId);
                    if (existing != null) return existing.proxy();
                    return discord.createThread(this, threadName, privateFlag, requestedId)
                            .proxy();
                },
                null);
    }

    private Object archivedThreads(Method method) {
        return ProxySupport.proxy(
                discord,
                "archivedThreads[" + name + "]",
                (self, m, args) -> switch (m.getName()) {
                    case "iterator" -> Collections.emptyIterator();
                    case "complete", "completeAfter" -> List.of();
                    case "queue", "queueAfter" -> {
                        for (int i = 0; i < args.length; i++) {
                            if (args[i] instanceof java.util.function.Consumer<?> consumer) {
                                @SuppressWarnings("unchecked")
                                java.util.function.Consumer<Object> success =
                                        (java.util.function.Consumer<Object>) consumer;
                                success.accept(List.of());
                                break;
                            }
                        }
                        yield null;
                    }
                    case "submit" -> java.util.concurrent.CompletableFuture.completedFuture(List.of());
                    case "getJDA" -> discord.jda();
                    case "isEmpty" -> true;
                    case "getCached" -> List.of();
                    case "map", "flatMap", "onSuccess", "onErrorMap", "onErrorFlatMap", "and", "zip", "mapToResult" ->
                        ProxySupport.UNHANDLED;
                    default -> m.getReturnType().isInstance(self) ? self : ProxySupport.UNHANDLED;
                },
                method.getReturnType());
    }
}
