package ti4.service.testbed;

import java.lang.reflect.InvocationTargetException;
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
import ti4.discord.interactions.routing.AnnotationHandler;

final class TestBedSyncMessage {

    private final Message real;
    private final AtomicBoolean handlerFailed = new AtomicBoolean();
    private final List<CompletableFuture<?>> pending = new ArrayList<>();
    private final Message proxy;

    TestBedSyncMessage(Message real) {
        this.real = real;
        proxy = (Message)
                Proxy.newProxyInstance(Message.class.getClassLoader(), new Class<?>[] {Message.class}, this::answer);
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
        } catch (ExecutionException e) {
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    private Object answer(Object self, Method method, @Nullable Object[] args) throws Throwable {
        String name = method.getName();
        if ("hashCode".equals(name)) return System.identityHashCode(self);
        if ("equals".equals(name)) return args != null && args[0] == self;
        if ("toString".equals(name)) return "TestBedSyncMessage[" + real.getId() + "]";
        if (isFailureReply(name, args)) handlerFailed.set(true);
        Object result = invoke(method, real, args);
        if (result instanceof RestAction<?> action && changesMessage(name)) {
            return tracked(method.getReturnType(), action);
        }
        return result;
    }

    static boolean isFailureReply(String name, @Nullable Object[] args) {
        return "reply".equals(name)
                && args != null
                && args.length == 1
                && args[0] instanceof CharSequence text
                && text.toString().startsWith(AnnotationHandler.BUTTON_FAILURE_PREFIX);
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
                    Object result = invoke(method, action, args);
                    return result == action ? self : result;
                });
    }

    @SuppressWarnings("unchecked")
    private void queueTracked(RestAction<?> action, @Nullable Object[] args) {
        Consumer<Object> success = args != null && args.length > 0 ? (Consumer<Object>) args[0] : null;
        Consumer<Throwable> failure = args != null && args.length > 1 ? (Consumer<Throwable>) args[1] : null;
        track(action.submit()).whenComplete((value, error) -> {
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

    private static Object invoke(Method method, Object receiver, @Nullable Object[] args) throws Throwable {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
