package ti4.spring.service.gameevent;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.Storage;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.service.persistence.DatabasePersistenceGate;

@RequiredArgsConstructor
@Service
public class GameEventArchiveService {

    public static final int MAX_PAGE_SIZE = 500;

    private final GameEventRepository gameEventRepository;
    private final GameEventService gameEventService;

    public void writeCompletedGamesAsZip(OutputStream out, int page, int limit) throws IOException {
        PageRequest pagination = PageRequest.of(page, Math.min(limit, MAX_PAGE_SIZE));
        Set<String> gamesWithEvents = DatabasePersistenceGate.isDisabled()
                ? Set.of()
                : new HashSet<>(gameEventRepository.findGameNamesWithEvents());
        List<ManagedGame> games = GameManager.getManagedGames().stream()
                .filter(ManagedGame::isHasEnded)
                .filter(game -> !game.isFowMode())
                .filter(ManagedGame::isCompetitiveExportEligible)
                .filter(game -> gamesWithEvents.contains(game.getName()))
                .sorted(Comparator.comparing(ManagedGame::getName))
                .toList();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeEntry(
                    zip,
                    "pagination.json",
                    JsonMapperManager.basic()
                            .writeValueAsBytes(new Pagination(
                                    page,
                                    pagination.getPageSize(),
                                    games.size(),
                                    pagination.getOffset() + pagination.getPageSize() < games.size())));
            for (ManagedGame game : games.stream()
                    .skip(pagination.getOffset())
                    .limit(pagination.getPageSize())
                    .toList()) {
                writeGameEntries(zip, game);
            }
        }
    }

    private void writeGameEntries(ZipOutputStream zip, ManagedGame managedGame) throws IOException {
        String gameName = managedGame.getName();
        byte[] save;
        byte[] events;
        try {
            Game game = managedGame.getGame();
            if (game == null || game.isFowMode() || !game.isHasEnded()) return;
            if (!GameStatisticsFilterer.getCompetitiveExportGamesFilter().test(game)) return;
            List<GameEventDto> gameEvents = gameEventService.getEvents(game);
            if (gameEvents.isEmpty()) return;
            save = Files.readAllBytes(Storage.getGamePath(gameName + ".txt"));
            events = JsonMapperManager.basic().writeValueAsBytes(gameEvents);
        } catch (Exception e) {
            BotLogger.error("Failed to export completed game " + gameName + ".", e);
            return;
        }
        writeEntry(zip, gameName + ".txt", save);
        writeEntry(zip, gameName + ".json", events);
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private record Pagination(int page, int limit, int totalGames, boolean hasNext) {}
}
