package ti4.game.helper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import ti4.game.Game;

@UtilityClass
public class GameHelper {

    public static final DateTimeFormatter CREATION_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.ROOT);

    public static LocalDate getCreationDateAsLocalDate(Game game) {
        return Instant.ofEpochMilli(game.getCreationDateTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    public static long getCreationDateTimeFromLegacyDate(String creationDate) {
        return LocalDate.parse(creationDate, CREATION_DATE_FORMATTER)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }
}
