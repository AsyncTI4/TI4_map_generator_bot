package ti4.json;

import lombok.experimental.UtilityClass;
import tools.jackson.databind.json.JsonMapper;

@UtilityClass
public class JsonMapperManager {

    private static final JsonMapper JSON_MAPPER =
            JsonMapper.builder().findAndAddModules().build();

    public static JsonMapper basic() {
        return JSON_MAPPER;
    }
}
