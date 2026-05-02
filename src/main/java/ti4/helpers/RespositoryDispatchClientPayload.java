package ti4.helpers;

import java.util.HashMap;
import java.util.Map;

record RespositoryDispatchClientPayload(Map<String, String> records) {
    /**
     * <a href="https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#create-a-repository-dispatch-event">...</a>
     */
    RespositoryDispatchClientPayload {}

    public RespositoryDispatchClientPayload() {
        this(new HashMap<>(1));
    }

    public void addRecord(String key, String value) {
        records.put(key, value);
    }

    public boolean isValid() {
        return !records.isEmpty() && records.size() < 10;
    }

    public String toJson() {
        return "\"client_payload\":{"
                + records.entrySet().stream()
                        .map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
                        .reduce((a, b) -> a + "," + b)
                        .orElse("")
                + "}";
    }
}
