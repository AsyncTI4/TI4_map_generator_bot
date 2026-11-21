package ti4.ai.actuation;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.requests.RestAction;

final class SyncMessage {

    static final String HANDLER_FAILURE_PREFIX = "The button failed";

    private final Message real;
    private final AtomicBoolean handlerFailed = new AtomicBoolean();
    private final List<CompletableFuture<?>> pending = new ArrayList<>();
    private final Message proxy;

    SyncMessage(Message real) {
        this.real = real;
        proxy = (Message) Proxy.newProxyInstance(
                Message.class.getClassLoader(),
                new Class<?>[] {Message.class},
                (self, method, args) -> answer(self, method, args));
    }

    Message proxy() {
        return proxy;
    }

    boolean handlerFailed() {
        return handlerFailed.get();
    }

    boolean awaitPendingEdits(Duration timeout) {
        CompletableFuture<?>[] edits;
        synchronized (pending) {
            edits = pending.toArray(CompletableFuture<?>[]::new);
        }
        try {
            CompletableFuture.allOf(edits).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException e) {
            return false;
        }
    }

    private Object answer(Object self, Method method, @Nullable Object[] args) throws Throwable {
        String name = method.getName();
        if ("hashCode".equals(name)) return System.identityHashCode(self);
        if ("equals".equals(name)) return args != null && args[0] == self;
        if ("toString".equals(name)) return "AiMessage[" + real.getId() + "]";
        if (isFailureReply(name, args)) {
            handlerFailed.set(true);
            return silentAction(method.getReturnType());
        }
        Object result = Proxies.invoke(method, real, args);
        if (result instanceof RestAction<?> action && changesMessage(name)) {
            return tracked(method.getReturnType(), action);
        }
        return result;
    }

    private static boolean isFailureReply(String name, @Nullable Object[] args) {
        return "reply".equals(name)
                && args != null
                && args.length == 1
                && args[0] instanceof CharSequence text
                && text.toString().startsWith(HANDLER_FAILURE_PREFIX);
    }

    private static boolean changesMessage(String name) {
        return name.startsWith("edit") || "delete".equals(name);
    }

    private Object tracked(Class<?> actionType, RestAction<?> action) {
        return Proxy.newProxyInstance(
                actionType.getClassLoader(), new Class<?>[] {actionType}, (self, method, args) -> {
                    String name = method.getName();
                    if ("queue".equals(name)) {
                        queueTracked(action, args);
                        return null;
                    }
                    if ("submit".equals(name) && method.getParameterCount() == 0) return track(action.submit());
                    if ("hashCode".equals(name)) return System.identityHashCode(self);
                    if ("equals".equals(name)) return args != null && args[0] == self;
                    Object result = Proxies.invoke(method, action, args);
                    return result == action ? self : result;
                });
    }

    @SuppressWarnings("unchecked")
    private void queueTracked(RestAction<?> action, @Nullable Object[] args) {
        Consumer<Object> success = args != null && args.length > 0 ? (Consumer<Object>) args[0] : null;
        Consumer<Throwable> failure = args != null && args.length > 1 ? (Consumer<Throwable>) args[1] : null;
        CompletableFuture<?> future = track(action.submit());
        future.whenComplete((value, error) -> {
            if (error == null && success != null) success.accept(value);
            if (error != null && failure != null) failure.accept(error);
        });
    }

    private CompletableFuture<?> track(CompletableFuture<?> future) {
        synchronized (pending) {
            pending.add(future);
        }
        return future;
    }

    private static Object silentAction(Class<?> actionType) {
        return Proxy.newProxyInstance(
                actionType.getClassLoader(), new Class<?>[] {actionType}, (self, method, args) -> {
                    String name = method.getName();
                    if ("submit".equals(name)) return CompletableFuture.completedFuture(null);
                    if ("hashCode".equals(name)) return System.identityHashCode(self);
                    if ("equals".equals(name)) return args != null && args[0] == self;
                    Class<?> type = method.getReturnType();
                    if (type != Object.class && type.isInstance(self)) return self;
                    if (type == boolean.class) return false;
                    return null;
                });
    }
}
