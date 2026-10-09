package ti4.testUtils.discord;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.managers.Presence;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.Response;
import net.dv8tion.jda.api.utils.TimeUtil;

/**
 * An in-memory Discord for in-process tests. It answers the JDA calls the bot makes while a game is played:
 * channels, threads and messages are kept in memory, every RestAction runs inline on the calling thread, message
 * ids are snowflakes stamped by the supplied clock, and button components get the unique ids Discord would assign.
 *
 * <p>Install it with {@code JdaService.jda = discord.jda(); JdaService.guildPrimary = discord.guild();}.
 */
public final class FakeDiscord {

    public static final long BOT_ID = 400_000_000_000_000_001L;
    public static final long GUILD_ID = 400_000_000_000_000_002L;
    private static final int MAX_SEQUENCE = (1 << 22) - 1;

    private final LongSupplier clock;
    private final Map<Long, FakeChannel> channels = new LinkedHashMap<>();
    private final Map<Long, FakeMessage> messages = new LinkedHashMap<>();
    private final List<String> unsupported = new ArrayList<>();
    private final List<String> actionFailures = new ArrayList<>();
    private final List<String> callbackFailures = new ArrayList<>();
    private final Presence presence = mock(Presence.class);
    private final JDA jda;
    private final Guild guild;
    private final SelfUser selfUser;
    private final Member selfMember;
    private long lastMillis;
    private int sequence;
    private int nextComponentId = 1;

    public FakeDiscord(LongSupplier clock) {
        this.clock = clock;
        this.selfUser = createSelfUser();
        this.jda = createJda();
        this.guild = createGuild();
        this.selfMember = createSelfMember();
    }

    public JDA jda() {
        return jda;
    }

    public Guild guild() {
        return guild;
    }

    public SelfUser selfUser() {
        return selfUser;
    }

    public Member selfMember() {
        return selfMember;
    }

    public long now() {
        return clock.getAsLong();
    }

    public synchronized TextChannel createTextChannel(String name) {
        FakeChannel channel = new FakeChannel(this, nextSnowflake(), name, null, false);
        channels.put(channel.id(), channel);
        return channel.asText();
    }

    synchronized FakeChannel createThread(FakeChannel parent, String name, boolean privateThread, Long id) {
        long threadId = id == null ? nextSnowflake() : id;
        FakeChannel thread = new FakeChannel(this, threadId, name, parent, privateThread);
        channels.put(threadId, thread);
        return thread;
    }

    public synchronized FakeChannel channel(long id) {
        return channels.get(id);
    }

    public synchronized FakeChannel channel(String id) {
        try {
            return channels.get(Long.parseLong(id));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public synchronized List<FakeChannel> channels() {
        return List.copyOf(channels.values());
    }

    public synchronized List<FakeChannel> channelsNamed(String name, boolean ignoreCase) {
        return channels.values().stream()
                .filter(channel -> ignoreCase
                        ? channel.name().equalsIgnoreCase(name)
                        : channel.name().equals(name))
                .toList();
    }

    public synchronized List<FakeMessage> allMessages() {
        return List.copyOf(messages.values());
    }

    synchronized void register(FakeMessage message) {
        messages.put(message.id(), message);
    }

    synchronized FakeMessage message(long id) {
        return messages.get(id);
    }

    synchronized long nextSnowflake() {
        long millis = Math.max(clock.getAsLong(), lastMillis);
        if (millis == lastMillis) {
            sequence++;
            if (sequence > MAX_SEQUENCE) {
                millis++;
                sequence = 0;
            }
        } else {
            sequence = 0;
        }
        lastMillis = millis;
        return TimeUtil.getDiscordTimestamp(millis) | sequence;
    }

    synchronized int nextComponentId() {
        return nextComponentId++;
    }

    public synchronized List<String> unsupportedCalls() {
        return List.copyOf(unsupported);
    }

    public synchronized List<String> actionFailures() {
        return List.copyOf(actionFailures);
    }

    public synchronized List<String> callbackFailures() {
        return List.copyOf(callbackFailures);
    }

    synchronized void recordUnsupported(String call) {
        if (unsupported.size() < 10_000) unsupported.add(call);
    }

    synchronized void recordActionFailure(String description, Throwable failure) {
        if (actionFailures.size() < 10_000) actionFailures.add(description + ": " + failure);
    }

    synchronized void recordCallbackFailure(String description, Throwable failure) {
        if (callbackFailures.size() < 10_000) callbackFailures.add(description + ": " + failure);
    }

    static ErrorResponseException unknownMessage() {
        ErrorResponseException exception = mock(ErrorResponseException.class);
        when(exception.getErrorCode()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE.getCode());
        when(exception.getErrorResponse()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE);
        when(exception.getMeaning()).thenReturn(ErrorResponse.UNKNOWN_MESSAGE.getMeaning());
        when(exception.getMessage()).thenReturn("10008: Unknown Message");
        when(exception.getResponse()).thenReturn(notFoundResponse());
        return exception;
    }

    private static Response notFoundResponse() {
        Response response = mock(Response.class);
        try {
            java.lang.reflect.Field code = Response.class.getField("code");
            code.setAccessible(true);
            code.setInt(response, 404);
            java.lang.reflect.Field message = Response.class.getField("message");
            message.setAccessible(true);
            message.set(response, "Not Found");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not build a fake Discord response", e);
        }
        return response;
    }

    private Object channelLookup(Class<?> type, Object id) {
        FakeChannel channel = channel(ProxySupport.idOf(id));
        if (channel == null) return null;
        Object proxy = channel.proxy();
        return type == null || type.isInstance(proxy) ? proxy : null;
    }

    private List<Object> threads() {
        return channels().stream()
                .filter(FakeChannel::isThread)
                .map(FakeChannel::proxy)
                .toList();
    }

    private List<Object> textChannels() {
        return channels().stream()
                .filter(channel -> !channel.isThread())
                .map(FakeChannel::proxy)
                .toList();
    }

    private List<Object> named(String name, boolean ignoreCase, boolean threads) {
        return channelsNamed(name, ignoreCase).stream()
                .filter(channel -> channel.isThread() == threads)
                .map(FakeChannel::proxy)
                .toList();
    }

    private Object channelQueries(java.lang.reflect.Method method, Object[] args) {
        String name = method.getName();
        return switch (name) {
            case "getTextChannelById" -> channelLookup(TextChannel.class, args[0]);
            case "getThreadChannelById" -> channelLookup(ThreadChannel.class, args[0]);
            case "getGuildChannelById", "getChannelById" -> {
                if (args.length == 2 && args[0] instanceof Class<?> type) yield channelLookup(type, args[1]);
                if (args.length == 2 && args[0] instanceof ChannelType) yield channelLookup(null, args[1]);
                yield channelLookup(GuildChannel.class, args[0]);
            }
            case "getTextChannels" -> textChannels();
            case "getThreadChannels" -> threads();
            case "getTextChannelsByName" -> named((String) args[0], (Boolean) args[1], false);
            case "getThreadChannelsByName" -> named((String) args[0], (Boolean) args[1], true);
            default -> ProxySupport.UNHANDLED;
        };
    }

    private JDA createJda() {
        when(presence.getStatus()).thenReturn(net.dv8tion.jda.api.OnlineStatus.ONLINE);
        return ProxySupport.proxy(
                this,
                "FakeJDA",
                (proxy, method, args) -> {
                    Object channel = channelQueries(method, args);
                    if (channel != ProxySupport.UNHANDLED) return channel;
                    return switch (method.getName()) {
                        case "getSelfUser" -> selfUser;
                        case "getUserById", "getUserByTag" -> null;
                        case "retrieveUserById" ->
                            FakeRestAction.of(this, method.getReturnType(), "retrieveUserById", state -> null, null);
                        case "getGuildById" -> ProxySupport.idOf(args[0]) == GUILD_ID ? guild : null;
                        case "getGuilds" -> List.of(guild);
                        case "getEmojiById", "getRoleById" -> null;
                        case "getEmojis", "getRoles", "getUsers" -> List.of();
                        case "getPresence" -> presence;
                        case "getStatus" -> JDA.Status.CONNECTED;
                        case "getGatewayPing" -> 1L;
                        case "getResponseTotal" -> 0L;
                        default -> ProxySupport.UNHANDLED;
                    };
                },
                JDA.class);
    }

    private Guild createGuild() {
        return ProxySupport.proxy(
                this,
                "FakeGuild",
                (proxy, method, args) -> {
                    Object channel = channelQueries(method, args);
                    if (channel != ProxySupport.UNHANDLED) return channel;
                    return switch (method.getName()) {
                        case "getIdLong" -> GUILD_ID;
                        case "getId" -> String.valueOf(GUILD_ID);
                        case "getName" -> "Fake Guild";
                        case "getJDA" -> jda;
                        case "getSelfMember" -> selfMember;
                        case "getMemberById", "getMember", "getRoleById", "getEmojiById", "getPublicRole" -> null;
                        case "getMembers",
                                "getRoles",
                                "getRolesByName",
                                "getMembersByName",
                                "getEmojis",
                                "getMembersWithRoles" -> List.of();
                        case "isMember" -> false;
                        case "retrieveActiveThreads" ->
                            FakeRestAction.of(
                                    this, method.getReturnType(), "retrieveActiveThreads", state -> threads(), null);
                        case "retrieveMemberById", "retrieveMember" ->
                            FakeRestAction.of(this, method.getReturnType(), "retrieveMemberById", state -> null, null);
                        default -> ProxySupport.UNHANDLED;
                    };
                },
                Guild.class);
    }

    private SelfUser createSelfUser() {
        return ProxySupport.proxy(
                this,
                "FakeSelfUser",
                (proxy, method, args) -> switch (method.getName()) {
                    case "getIdLong", "getApplicationIdLong" -> BOT_ID;
                    case "getId", "getApplicationId" -> String.valueOf(BOT_ID);
                    case "getName", "getEffectiveName", "getGlobalName" -> "AsyncTI4";
                    case "getAsMention" -> "<@" + BOT_ID + ">";
                    case "isBot" -> true;
                    case "getJDA" -> jda;
                    case "getEffectiveAvatarUrl", "getDefaultAvatarUrl" -> "https://example.invalid/avatar.png";
                    case "getAvatarUrl", "getAvatarId" -> null;
                    default -> ProxySupport.UNHANDLED;
                },
                SelfUser.class);
    }

    private Member createSelfMember() {
        return ProxySupport.proxy(
                this,
                "FakeSelfMember",
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUser" -> selfUser;
                    case "getIdLong" -> BOT_ID;
                    case "getId" -> String.valueOf(BOT_ID);
                    case "getEffectiveName" -> "AsyncTI4";
                    case "getNickname" -> null;
                    case "getAsMention" -> "<@" + BOT_ID + ">";
                    case "getGuild" -> guild;
                    case "getJDA" -> jda;
                    case "hasPermission", "hasAccess", "canInteract", "canSync" -> true;
                    case "isOwner", "isTimedOut", "isPending", "isBoosting" -> false;
                    case "getRoles" -> List.of();
                    case "getPermissions" -> EnumSet.allOf(Permission.class);
                    default -> ProxySupport.UNHANDLED;
                },
                SelfMember.class);
    }
}
