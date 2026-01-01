package ti4.map.helper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Random;
import lombok.experimental.UtilityClass;
import ti4.map.Game;
import ti4.message.logging.BotLogger;

@UtilityClass
public class GameHelper {

    public static final DateTimeFormatter CREATION_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.ROOT);

    public static LocalDate getCreationDateAsLocalDate(Game game) {
        return LocalDate.parse(game.getCreationDate(), CREATION_DATE_FORMATTER);
    }

    public static boolean updateCreationDateTimeIfNotSameDateAsCreationDateField(Game game) {
        long creationTimeMillis = game.getCreationDateTime();
        LocalDate creationDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(creationTimeMillis), ZoneOffset.UTC)
                .toLocalDate();
        LocalDate creationDate = getCreationDateAsLocalDate(game);
        if (creationDateTime.equals(creationDate)) {
            return false;
        }
        Random random = new Random();
        int hours = random.nextInt(24);
        int minutes = random.nextInt(60);
        int seconds = random.nextInt(60);
        int nanoseconds = random.nextInt(1000000000);
        LocalDateTime newCreationTime = creationDate.atTime(hours, minutes, seconds, nanoseconds);
        game.setCreationDateTime(newCreationTime.toInstant(ZoneOffset.UTC).toEpochMilli());
        BotLogger.error("Had to update a " + game.getName() + " creationDateTime field from " + creationDateTime
                + " to: " + newCreationTime + ". It's creation date was: " + creationDate);
        return true;
    }
}
