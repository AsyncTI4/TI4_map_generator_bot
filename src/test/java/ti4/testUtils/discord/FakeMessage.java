package ti4.testUtils.discord;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.Component;
import net.dv8tion.jda.api.components.MessageTopLevelComponentUnion;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.replacer.ComponentReplacer;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageType;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.AttachedFile;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

/** A message in the fake Discord. Edits change it in place; every read through its proxy sees the latest state. */
public final class FakeMessage {

    private static final int CONTENT = 1;
    private static final int EMBEDS = 2;
    private static final int COMPONENTS = 4;
    private static final int ATTACHMENTS = 8;

    private final FakeDiscord discord;
    private final FakeChannel channel;
    private final long id;
    private final Message proxy;
    private String content;
    private List<MessageEmbed> embeds;
    private List<MessageTopLevelComponentUnion> components;
    private List<String> attachments;
    private final List<String> reactions = new ArrayList<>();
    private boolean pinned;
    private boolean deleted;
    private int edits;

    FakeMessage(FakeDiscord discord, FakeChannel channel, long id, MessageCreateData data) {
        this.discord = discord;
        this.channel = channel;
        this.id = id;
        this.content = data.getContent() == null ? "" : data.getContent();
        this.embeds = List.copyOf(data.getEmbeds());
        this.components = withUniqueIds(discord, data.getComponents());
        this.attachments = data.getFiles().stream().map(FileUpload::getName).toList();
        data.getFiles().forEach(FakeMessage::closeQuietly);
        this.proxy = ProxySupport.proxy(discord, "FakeMessage[" + id + "]", this::handle, Message.class);
    }

    public long id() {
        return id;
    }

    public FakeChannel channel() {
        return channel;
    }

    public synchronized String content() {
        return content;
    }

    public synchronized List<MessageEmbed> embeds() {
        return embeds;
    }

    public synchronized List<MessageTopLevelComponentUnion> components() {
        return components;
    }

    public synchronized List<Button> buttons() {
        return components.isEmpty()
                ? List.of()
                : MessageComponentTree.of(components).findAll(Button.class);
    }

    public synchronized List<String> attachments() {
        return attachments;
    }

    public synchronized boolean isDeleted() {
        return deleted;
    }

    public synchronized int edits() {
        return edits;
    }

    public Message proxy() {
        return proxy;
    }

    synchronized void markDeleted() {
        deleted = true;
    }

    synchronized void edit(MessageEditData data) {
        boolean replace = isReplace(data);
        if (replace || isSet(data, CONTENT)) content = data.getContent() == null ? "" : data.getContent();
        if (replace || isSet(data, EMBEDS)) embeds = List.copyOf(data.getEmbeds());
        if (replace || isSet(data, COMPONENTS)) components = withUniqueIds(discord, data.getComponents());
        if (replace || isSet(data, ATTACHMENTS)) {
            attachments =
                    data.getAttachments().stream().map(FakeMessage::nameOf).toList();
            data.getFiles().forEach(FakeMessage::closeQuietly);
        }
        edits++;
    }

    @Override
    public synchronized String toString() {
        String buttonIds = buttons().stream().map(Button::getCustomId).collect(Collectors.joining(", "));
        String text = content.length() > 160 ? content.substring(0, 157) + "..." : content;
        return "#" + channel.name() + " " + id + (deleted ? " (deleted)" : "") + ": " + text.replace("\n", " ")
                + (buttonIds.isEmpty() ? "" : " [" + buttonIds + "]");
    }

    static MessageEditBuilder editBuilder(String methodName, Object[] args) {
        MessageEditBuilder builder = new MessageEditBuilder();
        switch (methodName) {
            case "editMessage" -> {
                if (args.length > 0 && args[0] instanceof MessageEditData data) builder.applyData(data);
                else if (args.length > 0) builder.setContent(String.valueOf(args[0]));
            }
            case "editMessageFormat" ->
                builder.setContent(String.format((String) args[0], ProxySupport.varargs(args, 1)));
            case "editMessageEmbeds" -> builder.setEmbeds(FakeChannel.embeds(args));
            case "editMessageComponents" -> builder.setComponents(FakeChannel.components(args));
            case "editMessageAttachments" -> builder.setAttachments(attachedFiles(args));
            default -> {}
        }
        return builder;
    }

    private Object handle(Object self, Method method, Object[] args) {
        String name = method.getName();
        if (name.startsWith("editMessage")) return editAction(name, args);
        return switch (name) {
            case "getIdLong" -> id;
            case "getId" -> String.valueOf(id);
            case "getContentRaw", "getContentDisplay", "getContentStripped" -> content();
            case "getEmbeds" -> embeds();
            case "getComponents" -> components();
            case "getAttachments" -> attachmentMocks();
            case "getAuthor" -> discord.selfUser();
            case "getMember" -> discord.selfMember();
            case "getChannel", "getGuildChannel" -> channel.proxy();
            case "getChannelType" -> channel.isThread() ? ChannelType.GUILD_PUBLIC_THREAD : ChannelType.TEXT;
            case "getChannelIdLong" -> channel.id();
            case "getGuild" -> discord.guild();
            case "getGuildIdLong" -> FakeDiscord.GUILD_ID;
            case "isFromGuild" -> true;
            case "getJDA" -> discord.jda();
            case "getJumpUrl" -> "https://discord.com/channels/" + FakeDiscord.GUILD_ID + "/" + channel.id() + "/" + id;
            case "isEphemeral", "isWebhookMessage", "isTTS", "isSuppressedEmbeds", "isUsingComponentsV2" -> false;
            case "isPinned" -> pinned;
            case "isEdited" -> edits > 0;
            case "getFlags" -> EnumSet.noneOf(Message.MessageFlag.class);
            case "getFlagsRaw" -> 0L;
            case "getType" -> MessageType.DEFAULT;
            case "getReactions", "getStickers" -> List.of();
            case "getMentions" -> mock(net.dv8tion.jda.api.entities.Mentions.class);
            case "getStartedThread" -> {
                FakeChannel thread = discord.channel(id);
                yield thread == null ? null : thread.proxy();
            }
            case "getMessageReference",
                    "getReferencedMessage",
                    "getInteraction",
                    "getInteractionMetadata",
                    "getActivity",
                    "getPoll",
                    "getNonce",
                    "getTimeEdited",
                    "getApplicationId" -> null;
            case "delete" ->
                action(name, state -> {
                    markDeleted();
                    return null;
                });
            case "pin", "unpin" ->
                action(name, state -> {
                    synchronized (this) {
                        pinned = "pin".equals(name);
                    }
                    return null;
                });
            case "addReaction" ->
                action(name, state -> {
                    synchronized (this) {
                        reactions.add(String.valueOf(args[0]));
                    }
                    return null;
                });
            case "removeReaction", "clearReactions", "suppressEmbeds", "crosspost" -> action(name, state -> null);
            case "createThreadChannel" ->
                FakeRestAction.of(
                        discord,
                        method.getReturnType(),
                        "createThreadChannel from message " + id,
                        state -> {
                            FakeChannel existing = discord.channel(id);
                            if (existing != null) return existing.proxy();
                            return discord.createThread(channel, (String) args[0], false, id)
                                    .proxy();
                        },
                        null);
            default -> ProxySupport.UNHANDLED;
        };
    }

    private Object action(String name, FakeRestAction.Body body) {
        Class<?> type = "delete".equals(name) || name.startsWith("pin") || name.startsWith("unpin")
                ? net.dv8tion.jda.api.requests.restaction.AuditableRestAction.class
                : net.dv8tion.jda.api.requests.RestAction.class;
        return FakeRestAction.of(discord, type, name + " message " + id, body, null);
    }

    private Object editAction(String name, Object[] args) {
        MessageEditBuilder builder = editBuilder(name, args);
        return FakeRestAction.of(
                discord,
                MessageEditAction.class,
                name + " message " + id,
                state -> {
                    if (isDeleted()) throw FakeDiscord.unknownMessage();
                    edit(((MessageEditBuilder) state.builder).build());
                    return proxy;
                },
                builder);
    }

    private synchronized List<Message.Attachment> attachmentMocks() {
        List<Message.Attachment> mocks = new ArrayList<>();
        for (String file : attachments) {
            Message.Attachment attachment = mock(Message.Attachment.class);
            when(attachment.getFileName()).thenReturn(file);
            when(attachment.getUrl()).thenReturn("https://example.invalid/" + file);
            when(attachment.getProxyUrl()).thenReturn("https://example.invalid/" + file);
            mocks.add(attachment);
        }
        return mocks;
    }

    static List<MessageTopLevelComponentUnion> withUniqueIds(
            FakeDiscord discord, List<? extends MessageTopLevelComponentUnion> components) {
        if (components == null || components.isEmpty()) return List.of();
        ComponentReplacer assignIds = ComponentReplacer.of(
                Component.class,
                component -> component.getUniqueId() < 0,
                component -> component.withUniqueId(discord.nextComponentId()));
        MessageComponentTree tree = MessageComponentTree.of(components);
        for (int depth = 0; depth < 3; depth++) tree = tree.replace(assignIds);
        rejectDuplicateCustomIds(tree);
        return List.copyOf(tree.getComponents());
    }

    private static void rejectDuplicateCustomIds(MessageComponentTree tree) {
        Set<String> seen = new HashSet<>();
        for (Button button : tree.findAll(Button.class)) {
            String customId = button.getCustomId();
            if (customId != null && !seen.add(customId)) {
                throw new IllegalArgumentException("Discord rejects a message with duplicate custom_id " + customId);
            }
        }
    }

    private static boolean isReplace(MessageEditData data) {
        return (Boolean) invokeProtected(data, "isReplace", new Class<?>[0]);
    }

    private static boolean isSet(MessageEditData data, int field) {
        return (Boolean) invokeProtected(data, "isSet", new Class<?>[] {int.class}, field);
    }

    private static Object invokeProtected(MessageEditData data, String name, Class<?>[] types, Object... args) {
        try {
            Method method = MessageEditData.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(data, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("MessageEditData." + name + " is not available", e);
        }
    }

    private static String nameOf(AttachedFile file) {
        return file instanceof FileUpload upload ? upload.getName() : String.valueOf(file);
    }

    private static List<AttachedFile> attachedFiles(Object[] args) {
        List<AttachedFile> files = new ArrayList<>();
        for (Object arg : args) {
            if (arg instanceof AttachedFile file) files.add(file);
            else if (arg instanceof AttachedFile[] array) files.addAll(List.of(array));
            else if (arg instanceof java.util.Collection<?> collection)
                collection.forEach(item -> files.add((AttachedFile) item));
        }
        return files;
    }

    private static void closeQuietly(FileUpload upload) {
        try {
            upload.close();
        } catch (Exception ignored) {
            // The fake keeps only file names.
        }
    }
}
