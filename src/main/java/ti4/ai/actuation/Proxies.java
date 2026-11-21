package ti4.ai.actuation;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;

@UtilityClass
class Proxies {

    interface Answer {
        @Nullable
        Object answer(Object proxy, Method method, @Nullable Object[] args) throws Throwable;
    }

    @SuppressWarnings("unchecked")
    static <T> T delegating(Class<T> type, Object delegate, Map<String, Answer> overrides) {
        InvocationHandler handler = (proxy, method, args) -> {
            Answer override = overrides.get(method.getName());
            if (override != null && method.getParameterCount() == argCount(args)) {
                return override.answer(proxy, method, args);
            }
            return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> args != null && args[0] == proxy;
                default -> invoke(method, delegate, args);
            };
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }

    static Answer constant(@Nullable Object value) {
        return (proxy, method, args) -> value;
    }

    static Answer computed(Function<Object[], Object> function) {
        return (proxy, method, args) -> function.apply(args);
    }

    static Object invoke(Method method, Object receiver, @Nullable Object[] args) throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static int argCount(@Nullable Object[] args) {
        return args == null ? 0 : args.length;
    }
}
