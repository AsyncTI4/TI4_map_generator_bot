package ti4.service.game;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Storage;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;

@UtilityClass
public class GameUndoNameService {

    private static final Pattern lastestCommandPattern = Pattern.compile("^(?>latest_command ).*$");
    private static final Pattern lastModifiedPattern = Pattern.compile("^(?>last_modified_date ).*$");
    private static final Pattern undoFileNamePattern = Pattern.compile("([A-Za-z0-9_-]+_\\d+\\.txt)");
    private static final Pattern selectionLastModifiedDatePattern = Pattern.compile("\\.txt@(\\d{1,18})\\b");
    private static final Comparator<File> fileComparator =
            Comparator.comparingInt(file -> getUndoNumberFromFileName(file.getName()));

    public record UndoSave(
            String fileName, String label, @Nullable Long lastModifiedDate) {
        public String selectionValue() {
            return lastModifiedDate == null ? fileName : fileName + "@" + lastModifiedDate;
        }
    }

    public static Map<String, String> getUndoNamesToCommandText(Game game, int limit) {
        return getLatestUndoSaves(game, limit).stream().collect(Collectors.toMap(UndoSave::fileName, UndoSave::label));
    }

    public static List<UndoSave> getLatestUndoSaves(Game game, int limit) {
        Path undoPath = Storage.getGameUndoDirectory(game.getName());
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(undoPath, game.getName() + "_*")) {
            return StreamSupport.stream(directoryStream.spliterator(), false)
                    .map(Path::toFile)
                    .sorted(fileComparator.reversed())
                    .limit(limit)
                    .map(GameUndoNameService::readUndoSave)
                    .toList();
        } catch (IOException e) {
            BotLogger.error(new LogOrigin(game), "Error listing files in directory: " + undoPath, e);
            return Collections.emptyList();
        }
    }

    private static UndoSave readUndoSave(File file) {
        long dateTime = System.currentTimeMillis();
        long fileLastModifiedDate = file.lastModified();

        String latestCommand = "";
        String lastModifiedDateString = "";
        Long lastModifiedDate = null;
        try {
            List<String> fileLines = Files.readAllLines(file.toPath());
            latestCommand = fileLines.stream()
                    .filter(line -> lastestCommandPattern.matcher(line).matches())
                    .findFirst()
                    .map(s -> StringUtils.substringAfter(s, " "))
                    .orElse("Latest Command not Found");

            lastModifiedDate = findLastModifiedDate(fileLines.stream()).orElse(null);
            lastModifiedDateString = lastModifiedDate == null
                    ? DateTimeHelper.getTimestampFromMillisecondsEpoch(fileLastModifiedDate)
                    : DateTimeHelper.getTimeRepresentationToSeconds(dateTime - lastModifiedDate);
        } catch (Exception e) {
            BotLogger.error("Could not get AutoComplete data from undo file: " + file.getName(), e);
        }

        String label = file.getName() + " (" + lastModifiedDateString + " ago):  " + latestCommand;
        return new UndoSave(file.getName(), label, lastModifiedDate);
    }

    @Nullable
    public static Long readLastModifiedDate(Path undoFile) {
        try (Stream<String> lines = Files.lines(undoFile)) {
            return findLastModifiedDate(lines).orElse(null);
        } catch (Exception e) {
            BotLogger.error("Could not read last modified date from undo file: " + undoFile, e);
            return null;
        }
    }

    private static Optional<Long> findLastModifiedDate(Stream<String> fileLines) {
        return fileLines
                .filter(line -> lastModifiedPattern.matcher(line).matches())
                .findFirst()
                .map(s -> StringUtils.substringAfter(s, " "))
                .map(Long::parseLong);
    }

    public static int getUndoNumberFromFileName(String name) {
        String number = StringUtils.substringAfterLast(name, "_");
        number = StringUtils.substringBefore(number, ".txt");
        return StringUtils.isEmpty(number) ? 0 : Integer.parseInt(number);
    }

    @Nullable
    public static Long getLastModifiedDateFromSelection(String selection) {
        if (StringUtils.isBlank(selection)) return null;
        var matcher = selectionLastModifiedDatePattern.matcher(selection);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    public static Integer getUndoNumberFromSelection(String gameName, String selection) {
        if (StringUtils.isBlank(selection)) {
            return null;
        }

        // Accept either the raw filename or the human-readable autocomplete label.
        var matcher = undoFileNamePattern.matcher(selection);
        while (matcher.find()) {
            String fileName = matcher.group(1);
            if (StringUtils.startsWith(fileName, gameName + "_")) {
                return getUndoNumberFromFileName(fileName);
            }
        }

        return null;
    }

    public static List<Integer> getSortedUndoNumbers(String gameName) {
        String gameNameFileNamePrefix = gameName + "_";
        var gameUndoPath = Storage.getGameUndoDirectory(gameName);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gameUndoPath, gameNameFileNamePrefix + "*")) {
            List<Integer> undoNumbers = new ArrayList<>();
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                String undoNumberStr = StringUtils.substringBetween(fileName, gameNameFileNamePrefix, Constants.TXT);
                if (undoNumberStr != null) {
                    try {
                        undoNumbers.add(Integer.parseInt(undoNumberStr));
                    } catch (NumberFormatException e) {
                        BotLogger.error("Could not parse undo number '" + undoNumberStr + "' for game " + gameName, e);
                    }
                }
            }
            undoNumbers.sort(Integer::compareTo);
            return undoNumbers;
        } catch (Exception e) {
            BotLogger.error("Failed to get undo numbers for game " + gameName, e);
            return Collections.emptyList();
        }
    }

    public static int getLatestUndoNumber(String gameName) {
        var sortedUndoNumbers = getSortedUndoNumbers(gameName);
        if (sortedUndoNumbers.isEmpty()) {
            return -1;
        }
        return sortedUndoNumbers.getLast();
    }
}
