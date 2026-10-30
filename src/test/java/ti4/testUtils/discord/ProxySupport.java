package ti4.testUtils.discord;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.requests.RestAction;

/**
 * Shared plumbing for the fake Discord proxies: a method table keyed by method name, falling back to the
 * interface's default method, and finally to a harmless default value. Every fallback to a default value is
 * recorded so a test can see which JDA calls the fake does not model yet.
 */
final class ProxySupport {

    interface Handler {
        Object handle(Object proxy, Method method, Object[] args) throws Throwable;
    }

    /** Thrown by a handler to say "not modelled here, fall back to the default method or a default value". */
    static final Object UNHANDLED = new Object();

    private ProxySupport() {}

    @SuppressWarnings("unchecked")
    static <T> T proxy(FakeDiscord discord, String description, Handler handler, Class<?>... interfaces) {
        InvocationHandler invocation = (proxy, method, args) -> {
            Object[] safeArgs = args == null ? new Object[0] : args;
            switch (method.getName()) {
                case "equals" -> {
                    if (safeArgs.length == 1) return proxy == safeArgs[0];
                }
                case "hashCode" -> {
                    if (safeArgs.length == 0) return System.identityHashCode(proxy);
                }
                case "toString" -> {
                    if (safeArgs.length == 0) return description;
                }
                default -> {}
            }
            Object result = handler.handle(proxy, method, safeArgs);
            if (result != UNHANDLED) return result;
            if (method.isDefault()) {
                try {
                    return InvocationHandler.invokeDefault(proxy, method, args);
                } catch (ClassCastException e) {
                    discord.recordUnsupported(
                            description + "." + method.getName() + " (default method needs JDA internals)");
                    return defaultValue(discord, method);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            discord.recordUnsupported(description + "." + method.getName());
            return defaultValue(discord, method);
        };
        return (T) Proxy.newProxyInstance(ProxySupport.class.getClassLoader(), interfaces, invocation);
    }

    static Object defaultValue(FakeDiscord discord, Method method) {
        Class<?> type = method.getReturnType();
        if (type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == int.class || type == short.class || type == byte.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0d;
        if (type == float.class) return 0f;
        if (type == char.class) return '\0';
        if (type == String.class || type == CharSequence.class) return "";
        if (List.class.isAssignableFrom(type) || type == Collection.class) return Collections.emptyList();
        if (Set.class.isAssignableFrom(type)) return type == EnumSet.class ? null : Collections.emptySet();
        if (Map.class.isAssignableFrom(type)) return Collections.emptyMap();
        if (type == Optional.class) return Optional.empty();
        if (RestAction.class.isAssignableFrom(type) && type.isInterface()) {
            return FakeRestAction.of(discord, type, "noop " + method.getName(), builder -> null, null);
        }
        return null;
    }

    static Object[] varargs(Object[] args, int from) {
        if (args.length <= from) return new Object[0];
        Object last = args[args.length - 1];
        if (args.length == from + 1 && last instanceof Object[] array) return array;
        Object[] rest = new Object[args.length - from];
        System.arraycopy(args, from, rest, 0, rest.length);
        return rest;
    }

    static long idOf(Object id) {
        if (id instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(id));
    }
}
