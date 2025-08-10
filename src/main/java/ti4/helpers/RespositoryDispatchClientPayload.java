package ti4.helpers;

import java.util.HashMap;
import java.util.Map;

public class RespositoryDispatchClientPayload {
    private final Map<String, String> records;

    /**
     * https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#create-a-repository-dispatch-event
     */
    public RespositoryDispatchClientPayload(Map<String, String> records) {
        this.records = records;
    }

    public RespositoryDispatchClientPayload() {
        this.records = new HashMap<>(1);
    }

    public void addRecord(String key, String value) {
        records.put(key, value);
    }

    public Map<String, String> getRecords() {
        return records;
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
