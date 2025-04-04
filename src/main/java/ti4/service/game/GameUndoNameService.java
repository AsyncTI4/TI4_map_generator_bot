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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Constants;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.message.BotLogger;

@UtilityClass
public class GameUndoNameService {

    private static final Pattern lastestCommandPattern = Pattern.compile("^(?>latest_command ).*$");
    private static final Pattern lastModifiedPattern = Pattern.compile("^(?>last_modified_date ).*$");
    private static final Comparator<File> fileComparator = Comparator.comparingInt(file -> getUndoNumberFromFileName(file.getName()));

    public static Map<String, String> getUndoNamesToCommandText(Game game, int limit) {
        Path undoPath = Storage.getGameUndoDirectory(game.getName());
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(undoPath, game.getName() + "_*")) {
            return StreamSupport.stream(directoryStream.spliterator(), false)
                .map(Path::toFile)
                .sorted(fileComparator.reversed())
                .limit(limit)
                .collect(Collectors.toMap(
                    File::getName,
                    GameUndoNameService::getLastModifiedDateAndLastCommandTextFromFile));
        } catch (IOException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(game), "Error listing files in directory: " + undoPath, e);
            return Collections.emptyMap();
        }
    }

    private static String getLastModifiedDateAndLastCommandTextFromFile(File file) {
        long dateTime = System.currentTimeMillis();
        long fileLastModifiedDate = file.lastModified();

        String latestCommand = "";
        String lastModifiedDateString = "";
        try {
            List<String> fileLines = Files.readAllLines(file.toPath());
            latestCommand = fileLines.stream()
                .filter(line -> lastestCommandPattern.matcher(line).matches())
                .findFirst()
                .map(s -> StringUtils.substringAfter(s, " "))
                .orElse("Latest Command not Found");

            lastModifiedDateString = fileLines.stream()
                .filter(line -> lastModifiedPattern.matcher(line).matches())
                .findFirst()
                .map(s -> StringUtils.substringAfter(s, " "))
                .map(Long::parseLong)
                .map(lastModifiedDate -> DateTimeHelper.getTimeRepresentationToSeconds(dateTime - lastModifiedDate))
                .orElse(DateTimeHelper.getTimestampFromMillisecondsEpoch(fileLastModifiedDate));
        } catch (Exception e) {
            BotLogger.error("Could not get AutoComplete data from undo file: " + file.getName(), e);
        }

        return file.getName() + " (" + lastModifiedDateString + " ago):  " + latestCommand;
    }

    public static int getUndoNumberFromFileName(String name) {
        String number = StringUtils.substringAfterLast(name, "_");
        number = StringUtils.substringBefore(number, ".txt");
        return StringUtils.isEmpty(number) ? 0 : Integer.parseInt(number);
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
