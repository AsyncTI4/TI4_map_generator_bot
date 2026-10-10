package ti4.testUtils.discord;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.lang3.reflect.MethodUtils;

/**
 * A RestAction of any JDA action interface that runs its body exactly once, inline on the calling thread, the
 * first time it is queued, completed or submitted. Delays are ignored. Fluent setters are applied to an optional
 * builder (a MessageCreateBuilder or MessageEditBuilder) and recorded, so the body can read them.
 */
final class FakeRestAction {

    interface Body {
        Object run(State state) throws Throwable;
    }

    static final class State {
        final Object builder;
        final List<Call> calls = new ArrayList<>();
        private boolean done;
        private Object value;
        private Throwable failure;

        State(Object builder) {
            this.builder = builder;
        }

        boolean called(String name) {
            return calls.stream().anyMatch(call -> call.name().equals(name));
        }

        Object lastArg(String name) {
            for (int i = calls.size() - 1; i >= 0; i--) {
                Call call = calls.get(i);
                if (call.name().equals(name) && call.args().length > 0) return call.args()[0];
            }
            return null;
        }
    }

    record Call(String name, Object[] args) {}

    private static final Set<String> OPERATORS =
            Set.of("map", "flatMap", "onSuccess", "onErrorMap", "onErrorFlatMap", "and", "zip", "mapToResult");

    private FakeRestAction() {}

    static <T> T of(FakeDiscord discord, Class<?> type, String description, Body body, Object builder) {
        State state = new State(builder);
        return ProxySupport.proxy(
                discord,
                "action[" + description + "]",
                (proxy, method, args) -> handle(discord, description, body, state, proxy, method, args),
                type);
    }

    private static Object handle(
            FakeDiscord discord, String description, Body body, State state, Object proxy, Method method, Object[] args)
            throws Throwable {
        String name = method.getName();
        switch (name) {
            case "queue", "queueAfter" -> {
                run(discord, description, body, state);
                List<Consumer<Object>> callbacks = consumers(method, args);
                deliver(discord, description, state, callbacks);
                return "queueAfter".equals(name) ? new DoneFuture() : null;
            }
            case "complete", "completeAfter" -> {
                run(discord, description, body, state);
                if (state.failure != null) throw state.failure;
                return state.value;
            }
            case "submit" -> {
                run(discord, description, body, state);
                return state.failure != null
                        ? CompletableFuture.failedFuture(state.failure)
                        : CompletableFuture.completedFuture(state.value);
            }
            case "getJDA" -> {
                return discord.jda();
            }
            case "delay", "timeout", "deadline" -> {
                return proxy;
            }
            case "getCheck" -> {
                return null;
            }
            default -> {}
        }
        if (OPERATORS.contains(name)) return ProxySupport.UNHANDLED;
        if (method.getReturnType().isInstance(proxy)) {
            state.calls.add(new Call(name, args));
            applyToBuilder(state.builder, name, args);
            return proxy;
        }
        return ProxySupport.UNHANDLED;
    }

    private static void run(FakeDiscord discord, String description, Body body, State state) {
        if (state.done) return;
        state.done = true;
        try {
            state.value = body.run(state);
        } catch (Throwable t) {
            state.failure = t;
            discord.recordActionFailure(description, t);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Consumer<Object>> consumers(Method method, Object[] args) {
        List<Consumer<Object>> consumers = new ArrayList<>();
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length && i < args.length; i++) {
            if (Consumer.class.isAssignableFrom(types[i])) consumers.add((Consumer<Object>) args[i]);
        }
        return consumers;
    }

    private static void deliver(
            FakeDiscord discord, String description, State state, List<Consumer<Object>> callbacks) {
        Consumer<Object> success = callbacks.isEmpty() ? null : callbacks.get(0);
        Consumer<Object> failure = callbacks.size() > 1 ? callbacks.get(1) : null;
        try {
            if (state.failure == null) {
                if (success != null) success.accept(state.value);
            } else if (failure != null) {
                failure.accept(state.failure);
            }
        } catch (Throwable t) {
            discord.recordCallbackFailure(description, t);
        }
    }

    private static void applyToBuilder(Object builder, String name, Object[] args) {
        if (builder == null) return;
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = args[i] == null ? Object.class : args[i].getClass();
        Method target = MethodUtils.getMatchingAccessibleMethod(builder.getClass(), name, types);
        if (target == null) return;
        try {
            target.invoke(builder, args);
        } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
            // The builder has no equivalent setter (for example message references); the call is still recorded.
        }
    }

    private static final class DoneFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
