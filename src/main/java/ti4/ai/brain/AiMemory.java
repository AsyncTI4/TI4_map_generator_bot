package ti4.ai.brain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AiMemory {

    private static final int MAX_ENTRIES = 500;

    private final Map<String, String> values = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    public synchronized Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public synchronized void put(String key, String value) {
        values.put(key, value);
    }

    public synchronized void refresh(String key, String value) {
        values.remove(key);
        values.put(key, value);
    }

    public synchronized boolean has(String key) {
        return values.containsKey(key);
    }

    public synchronized void remove(String key) {
        values.remove(key);
    }

    public synchronized Map<String, String> snapshot() {
        return new LinkedHashMap<>(values);
    }

    public synchronized void restore(Map<String, String> snapshot) {
        values.clear();
        values.putAll(snapshot);
    }

    public synchronized List<String> keysStartingWith(String prefix) {
        return values.keySet().stream().filter(key -> key.startsWith(prefix)).toList();
    }
}
