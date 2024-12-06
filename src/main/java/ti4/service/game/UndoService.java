package ti4.service.game;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.message.BotLogger;

@UtilityClass
public class UndoService {

    private static final Pattern lastestCommandPattern = Pattern.compile("^(?>latest_command ).*$");
    private static final Pattern lastModifiedPattern = Pattern.compile("^(?>last_modified_date ).*$");

    public static Set<String> getUndoGameNames(Game game) {
        Path undoPath = Storage.getGameUndoDirectory().toPath();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(undoPath, game.getName() + "_*")) {
            return StreamSupport.stream(directoryStream.spliterator(), false)
                .map(Path::toFile)
                .sorted(Comparator.comparing(File::getName).reversed())
                .map(File::getName)
                .collect(Collectors.toSet());
        } catch (IOException e) {
            BotLogger.log("Error listing files in directory: " + undoPath, e);
            return Collections.emptySet();
        }
    }

    public static Map<String, String> get25UndoNamesToCommandText(Game game) {
        Path undoPath = Storage.getGameUndoDirectory().toPath();
        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(undoPath, game.getName() + "_*")) {
            return StreamSupport.stream(directoryStream.spliterator(), false)
                .map(Path::toFile)
                .sorted(Comparator.comparing(File::getName).reversed())
                .limit(25)
                .collect(Collectors.toMap(
                    File::getName,
                    UndoService::getLastModifiedDateAndLastCommandTextFromFile
                ));
        } catch (IOException e) {
            BotLogger.log("Error listing files in directory: " + undoPath, e);
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
                .orElse(DateTimeHelper.getTimestampFromMillesecondsEpoch(fileLastModifiedDate));
        } catch (Exception e) {
            BotLogger.log("Could not get AutoComplete data from undo file: " + file.getName());
        }

        return file.getName() + " (" + lastModifiedDateString + " ago):  " + latestCommand;
    }
}
