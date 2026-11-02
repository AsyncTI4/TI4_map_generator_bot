package ti4.spring.service.gameevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.helpers.Storage;
import ti4.json.JsonMapperManager;
import tools.jackson.databind.JsonNode;

class GameEventArchiveServiceTest {

    @TempDir
    Path directory;

    private final GameEventRepository repository = mock(GameEventRepository.class);
    private final GameEventService eventService = mock(GameEventService.class);
    private final GameEventArchiveService archiveService = new GameEventArchiveService(repository, eventService);
    private final List<String> gamesWithEvents = new ArrayList<>();

    @Test
    void exportsTheSaveAndAllEventsAndExcludesGamesWithoutRecordedEvents() throws IOException {
        ManagedGame first = managed("pbd1", true, false);
        ManagedGame second = managed("pbd2", true, false);
        when(eventService.getEvents(first.getGame())).thenReturn(List.of(event(3), event(4)));
        gamesWithEvents.remove("pbd2");
        org.mockito.Mockito.clearInvocations(second);

        Map<String, String> entries = archive(List.of(second, first), 0, 100);

        assertThat(entries.keySet()).containsExactly("pagination.json", "pbd1.txt", "pbd1.json");
        assertThat(entries.get("pbd1.txt")).isEqualTo("save for pbd1");
        JsonNode events = JsonMapperManager.basic().readTree(entries.get("pbd1.json"));
        assertThat(events.get(0).get("seq").asLong()).isEqualTo(3);
        assertThat(events.get(1).get("seq").asLong()).isEqualTo(4);
        verify(second, never()).getGame();
    }

    @Test
    void filtersBeforePaginatingAndOnlyLoadsGamesOnThePageInNameOrder() throws IOException {
        ManagedGame first = managed("pbd1", true, false);
        ManagedGame second = managed("pbd2", true, false);
        ManagedGame third = managed("pbd3", true, false);
        ManagedGame active = managed("active", false, false);
        ManagedGame fog = managed("fog", true, true);
        ManagedGame noncompetitive = managed("homebrew", true, false);
        when(noncompetitive.isCompetitiveExportEligible()).thenReturn(false);
        Game firstGame = first.getGame();
        Game thirdGame = third.getGame();
        org.mockito.Mockito.clearInvocations(first, third, active, fog, noncompetitive);

        Map<String, String> entries = archive(List.of(third, active, second, fog, first, noncompetitive), 1, 1);

        assertThat(entries).containsOnlyKeys("pagination.json", "pbd2.txt", "pbd2.json");
        JsonNode pagination = JsonMapperManager.basic().readTree(entries.get("pagination.json"));
        assertThat(pagination.get("page").asInt()).isEqualTo(1);
        assertThat(pagination.get("limit").asInt()).isEqualTo(1);
        assertThat(pagination.get("totalGames").asInt()).isEqualTo(3);
        assertThat(pagination.get("hasNext").asBoolean()).isTrue();
        for (ManagedGame excluded : List.of(first, third, active, fog, noncompetitive)) {
            verify(excluded, never()).getGame();
        }
        verify(eventService, never()).getEvents(firstGame);
        verify(eventService, never()).getEvents(thirdGame);
    }

    @Test
    void skipsUnavailableGamesAndRechecksLoadedGameVisibilityAndCompletion() throws IOException {
        ManagedGame broken = managed("broken", true, false);
        when(broken.getGame()).thenThrow(new IllegalStateException("Failed to load"));
        ManagedGame missing = managed("missing", true, false);
        when(missing.getGame()).thenReturn(null);
        ManagedGame fog = managed("fog", true, false);
        when(fog.getGame().isFowMode()).thenReturn(true);
        ManagedGame reopened = managed("reopened", true, false);
        when(reopened.getGame().isHasEnded()).thenReturn(false);
        ManagedGame missingSave = managed("missingSave", true, false);
        Files.delete(directory.resolve("missingSave.txt"));
        ManagedGame valid = managed("valid", true, false);
        ManagedGame homebrew = managed("homebrew", true, false);
        when(homebrew.getGame().hasHomebrew()).thenReturn(true);
        ManagedGame undone = managed("undone", true, false);
        when(eventService.getEvents(undone.getGame())).thenReturn(List.of());

        assertThat(archive(List.of(broken, missing, fog, reopened, missingSave, valid, homebrew, undone), 0, 100))
                .containsOnlyKeys("pagination.json", "valid.txt", "valid.json");
    }

    @Test
    void returnsPaginationForEmptyAndOutOfRangePages() throws IOException {
        Map<String, String> empty = archive(List.of(), 0, 100);
        assertThat(empty).containsOnlyKeys("pagination.json");
        assertThat(JsonMapperManager.basic()
                        .readTree(empty.get("pagination.json"))
                        .get("hasNext")
                        .asBoolean())
                .isFalse();

        ManagedGame game = managed("pbd1", true, false);
        Map<String, String> beyond = archive(List.of(game), Integer.MAX_VALUE, 500);
        assertThat(beyond).containsOnlyKeys("pagination.json");
        assertThat(JsonMapperManager.basic()
                        .readTree(beyond.get("pagination.json"))
                        .get("hasNext")
                        .asBoolean())
                .isFalse();
    }

    @Test
    void capsPageSizeAtFiveHundred() throws IOException {
        List<ManagedGame> games = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            games.add(managed("pbd%04d".formatted(i), true, false));
        }
        Map<String, String> entries = archive(games, 0, 1000);

        assertThat(entries).hasSize(1001).containsKeys("pbd0499.txt", "pbd0499.json");
        assertThat(entries).doesNotContainKeys("pbd0500.txt", "pbd0500.json");
        verify(games.getLast(), never()).getGame();
        assertThat(JsonMapperManager.basic()
                        .readTree(entries.get("pagination.json"))
                        .get("limit")
                        .asInt())
                .isEqualTo(500);
    }

    private Map<String, String> archive(List<ManagedGame> games, int page, int limit) throws IOException {
        try (MockedStatic<GameManager> manager = mockStatic(GameManager.class);
                MockedStatic<Storage> storage = mockStatic(Storage.class)) {
            manager.when(GameManager::getManagedGames).thenReturn(games);
            when(repository.findGameNamesWithEvents()).thenReturn(gamesWithEvents);
            storage.when(() -> Storage.getGamePath(org.mockito.ArgumentMatchers.anyString()))
                    .thenAnswer(invocation -> directory.resolve((String) invocation.getArgument(0)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            archiveService.writeCompletedGamesAsZip(out, page, limit);
            return unzip(out.toByteArray());
        }
    }

    private ManagedGame managed(String name, boolean ended, boolean fog) throws IOException {
        Game game = mock(Game.class);
        when(game.isHasEnded()).thenReturn(ended);
        when(game.isFowMode()).thenReturn(fog);
        when(game.getRealAndEliminatedPlayers()).thenReturn(Collections.nCopies(6, mock(Player.class)));
        when(game.getVp()).thenReturn(10);
        when(game.getRound()).thenReturn(4);
        when(game.isThundersEdge()).thenReturn(true);
        when(game.isProphecyOfKings()).thenReturn(true);
        when(game.getWinner()).thenReturn(Optional.of(mock(Player.class)));
        when(eventService.getEvents(game)).thenReturn(List.of(event(1)));
        gamesWithEvents.add(name);
        boolean eligible = ended
                && GameStatisticsFilterer.getCompetitiveExportGamesFilter().test(game);
        ManagedGame managed = mock(ManagedGame.class);
        when(managed.getName()).thenReturn(name);
        when(managed.isHasEnded()).thenReturn(ended);
        when(managed.isFowMode()).thenReturn(fog);
        when(managed.isCompetitiveExportEligible()).thenReturn(eligible);
        when(managed.getGame()).thenReturn(game);
        Files.writeString(directory.resolve(name + ".txt"), "save for " + name);
        return managed;
    }

    private static GameEventDto event(long seq) {
        return new GameEventDto(
                seq, "TURN", 1, "action", "sol", seq, JsonMapperManager.basic().readTree("{}"), null, null, null);
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
