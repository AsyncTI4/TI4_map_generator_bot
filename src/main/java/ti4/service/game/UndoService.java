package ti4.service.game;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.message.BotLogger;

@UtilityClass
public class UndoService {

    private static final Pattern lastestCommandPattern = Pattern.compile("^(?>latest_command ).*$");
    private static final Pattern lastModifiedPattern = Pattern.compile("^(?>last_modified_date ).*$");

    public static Map<String, String> getAllUndoSavedGamesForAutoComplete(Game game) {
        File mapUndoDirectory = Storage.getGameUndoDirectory();
        String gameName = game.getName();
        String gameNameForUndoStart = gameName + "_";
        String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(gameNameForUndoStart));
        return Arrays.stream(mapUndoFiles).map(Storage::getGameUndoStorage)
            .collect(Collectors.toMap(File::getName, UndoService::getLastModifiedDateAndLastCommandTextFromFile));
    }

    public static String getLastModifiedDateAndLastCommandTextFromFile(File file) {
        long dateTime = System.currentTimeMillis();
        long fileLastModifiedDate = file.lastModified();
        System.out.println(DateTimeHelper.getTimeRepresentationToSeconds(fileLastModifiedDate));

        String latestCommand = "Latest Command not Found";
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
                .orElse(DateTimeHelper.getTimeRepresentationToSeconds(fileLastModifiedDate));
        } catch (Exception e) {
            BotLogger.log("Could not get AutoComplete data from undo file: " + file.getName());
        }

        return "(" + lastModifiedDateString + " ago):  " + latestCommand;
    }
}
