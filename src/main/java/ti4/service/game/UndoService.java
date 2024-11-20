package ti4.service.game;

import java.io.File;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;

@UtilityClass
public class UndoService {

    public static Map<String, Game> getAllUndoSavedGames(Game game) {
        File mapUndoDirectory = Storage.getGameUndoDirectory();
        String gameName = game.getName();
        String gameNameForUndoStart = gameName + "_";
        String[] mapUndoFiles = mapUndoDirectory.list((dir, name) -> name.startsWith(gameNameForUndoStart));
        return Arrays.stream(mapUndoFiles).map(Storage::getGameUndoStorage)
            .collect(Collectors.toMap(File::getName, GameSaveLoadManager::loadGame));
    }
}
