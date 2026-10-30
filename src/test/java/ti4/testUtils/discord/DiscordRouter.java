package ti4.testUtils.discord;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import ti4.discord.JdaService;

/**
 * Lets several fake Discords run at once in one JVM, one per thread. The bot reads Discord through the static
 * {@code JdaService.jda} and {@code JdaService.guildPrimary}; while any fake is bound, those hold proxies that forward
 * every call to the fake bound to the calling thread. A thread with no binding (work the bot hands to its own
 * executors) asks every bound fake and takes the first one that knows the channel, message or user it is after.
 *
 * <p>With a single binding this behaves exactly like installing that fake directly.
 */
public final class DiscordRouter {

    private static final ThreadLocal<FakeDiscord> CURRENT = new ThreadLocal<>();
    private static final List<FakeDiscord> BOUND = new CopyOnWriteArrayList<>();
    private static final AtomicLong UNBOUND_CALLS = new AtomicLong();
    private static final JDA JDA_PROXY = routing(JDA.class, FakeDiscord::jda, "routed JDA");
    private static final Guild GUILD_PROXY = routing(Guild.class, FakeDiscord::guild, "routed guild");

    private static JDA previousJda;
    private static Guild previousGuild;

    private DiscordRouter() {}

    /** Binds the fake to the calling thread, installing the routing proxies when it is the first binding. */
    public static synchronized void bind(FakeDiscord discord) {
        if (BOUND.isEmpty()) {
            previousJda = JdaService.jda;
            previousGuild = JdaService.guildPrimary;
            JdaService.jda = JDA_PROXY;
            JdaService.guildPrimary = GUILD_PROXY;
        }
        BOUND.add(discord);
        CURRENT.set(discord);
    }

    /** Releases the fake; the last release puts back whatever Discord was installed before the first binding. */
    public static synchronized void unbind(FakeDiscord discord) {
        if (CURRENT.get() == discord) CURRENT.remove();
        if (!BOUND.remove(discord)) return;
        if (BOUND.isEmpty()) {
            JdaService.jda = previousJda;
            JdaService.guildPrimary = previousGuild;
            previousJda = null;
            previousGuild = null;
        }
    }

    /** How many Discord calls came from threads with no fake bound, which had to be routed by lookup. */
    public static long unboundCalls() {
        return UNBOUND_CALLS.get();
    }

    @SuppressWarnings("unchecked")
    private static <T> T routing(Class<T> type, Function<FakeDiscord, Object> target, String description) {
        return (T) Proxy.newProxyInstance(
                DiscordRouter.class.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals" -> {
                            if (args != null && args.length == 1) return proxy == args[0];
                        }
                        case "hashCode" -> {
                            if (args == null || args.length == 0) return System.identityHashCode(proxy);
                        }
                        case "toString" -> {
                            if (args == null || args.length == 0) return description;
                        }
                        default -> {}
                    }
                    FakeDiscord current = CURRENT.get();
                    if (current != null) return call(target.apply(current), method, args);
                    return routeUnbound(target, method, args);
                });
    }

    private static Object routeUnbound(Function<FakeDiscord, Object> target, Method method, Object[] args)
            throws Throwable {
        UNBOUND_CALLS.incrementAndGet();
        List<FakeDiscord> bound = List.copyOf(BOUND);
        if (bound.isEmpty()) throw new IllegalStateException("No fake Discord is bound");
        if (bound.size() == 1 || args == null || args.length == 0) {
            return call(target.apply(bound.getFirst()), method, args);
        }
        Object fallback = null;
        for (FakeDiscord discord : bound) {
            Object result = call(target.apply(discord), method, args);
            if (found(result)) return result;
            if (fallback == null) fallback = result;
        }
        return fallback;
    }

    private static boolean found(Object result) {
        if (result == null) return false;
        if (result instanceof Collection<?> collection) return !collection.isEmpty();
        if (result instanceof Optional<?> optional) return optional.isPresent();
        return true;
    }

    private static Object call(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
