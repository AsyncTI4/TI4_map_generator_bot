package ti4.spring.service.gameevent;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;

@RequiredArgsConstructor
@Service
public class GameEventArchiveService {

    private final GameEventRepository gameEventRepository;
    private final GameEventService gameEventService;

    public void writeEventsSinceAsZip(OutputStream out, long sinceEpochMillis) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            if (DatabasePersistenceGate.isDisabled()) return;
            for (String gameName : gameEventRepository.findGameNamesWithEventsAfter(sinceEpochMillis)) {
                writeGameEntry(zip, gameName, sinceEpochMillis);
            }
        }
    }

    private void writeGameEntry(ZipOutputStream zip, String gameName, long sinceEpochMillis) throws IOException {
        List<GameEventDto> events = publicEventsSince(gameName, sinceEpochMillis);
        if (events.isEmpty()) return;
        zip.putNextEntry(new ZipEntry(gameName + ".json"));
        zip.write(JsonMapperManager.basic().writeValueAsBytes(events));
        zip.closeEntry();
    }

    private List<GameEventDto> publicEventsSince(String gameName, long sinceEpochMillis) {
        try {
            ManagedGame managedGame = GameManager.getManagedGame(gameName);
            if (managedGame == null || managedGame.isFowMode()) return List.of();
            Game game = managedGame.getGame();
            return game == null ? List.of() : gameEventService.getEventsSince(game, sinceEpochMillis);
        } catch (Exception e) {
            BotLogger.error("Failed to export game events for " + gameName + ".", e);
            return List.of();
        }
    }
}
