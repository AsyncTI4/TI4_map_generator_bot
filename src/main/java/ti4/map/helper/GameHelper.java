package ti4.map.helper;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.experimental.UtilityClass;
import ti4.map.Game;

@UtilityClass
public class GameHelper {

    public static final DateTimeFormatter CREATION_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.ROOT);

    public static long getCreationDateAsEpochMillis(Game game) {
        return LocalDate.parse(game.getCreationDate(), CREATION_DATE_FORMATTER)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    public LocalDate getCreationDateAsLocalDate(Game game) {
        return LocalDate.parse(game.getCreationDate(), CREATION_DATE_FORMATTER);
    }
}
