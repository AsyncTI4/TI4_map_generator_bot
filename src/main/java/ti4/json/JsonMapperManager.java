package ti4.json;

import static tools.jackson.databind.cfg.EnumFeature.READ_ENUMS_USING_TO_STRING;
import static tools.jackson.databind.cfg.EnumFeature.WRITE_ENUMS_USING_TO_STRING;

import lombok.experimental.UtilityClass;
import tools.jackson.databind.json.JsonMapper;

@UtilityClass
public class JsonMapperManager {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .disable(READ_ENUMS_USING_TO_STRING, WRITE_ENUMS_USING_TO_STRING)
            .findAndAddModules()
            .build();

    public static JsonMapper basic() {
        return JSON_MAPPER;
    }
}
