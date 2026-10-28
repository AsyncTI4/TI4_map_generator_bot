package ti4.service.storage;

import java.util.Arrays;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChannelExportFormat {
    JSON("json", "Json"),
    HTML("html", "HtmlDark");

    private final String optionValue;
    private final String exporterName;

    public static ChannelExportFormat fromOption(@Nullable String optionValue) {
        return Arrays.stream(values())
                .filter(format -> format.optionValue.equalsIgnoreCase(optionValue))
                .findFirst()
                .orElse(JSON);
    }
}
