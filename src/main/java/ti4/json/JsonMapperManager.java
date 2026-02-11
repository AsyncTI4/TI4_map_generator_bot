package ti4.json;

import static tools.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS;
import static tools.jackson.databind.cfg.EnumFeature.READ_ENUMS_USING_TO_STRING;

import lombok.experimental.UtilityClass;
import tools.jackson.databind.json.JsonMapper;

@UtilityClass
public class JsonMapperManager {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .disable(READ_ENUMS_USING_TO_STRING)
            .enable(ACCEPT_CASE_INSENSITIVE_ENUMS)
            .findAndAddModules()
            .build();

    public static JsonMapper basic() {
        return JSON_MAPPER;
    }
}
