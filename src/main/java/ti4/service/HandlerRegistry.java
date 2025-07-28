package ti4.service;

import java.util.HashMap;
import java.util.Map;


public class HandlerRegistry {
    private final Map<Class<?>, Object> handlers = new HashMap<>();

    public <T> void registerHandler(Class<T> handlerClass, T instance) {
        handlers.put(handlerClass, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T getHandler(Class<T> handlerClass) {
        Object handler = handlers.get(handlerClass);
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + handlerClass.getName() +
                ". Make sure to register it in ServiceRegistry.initialize()");
        }
        return (T) handler;
    }
}