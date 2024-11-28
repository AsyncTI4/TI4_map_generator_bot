package ti4.service.game;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import ti4.helpers.DateTimeHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.message.BotLogger;

@UtilityClass
public class UndoService {

    private static final Pattern descrRegex = Pattern.compile("^(?<=latest_command ).*$");

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
        long lastModifiedDate = file.lastModified();
        
        String latestCommand = "Latest Command not Found";
        try {
            latestCommand = Files.readAllLines(file.toPath()).stream()
                .filter(line -> descrRegex.matcher(line).find())
                .findFirst()
                .orElse("Latest Command not Found");
        } catch (Exception e) {
           BotLogger.log("Could not find latest command from undo file: " + file.getName());
        }

        return "(" + DateTimeHelper.getTimeRepresentationToSeconds(dateTime - lastModifiedDate) + " ago):  " + latestCommand;
    }
}
