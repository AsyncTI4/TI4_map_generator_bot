package ti4.spring.service.gameevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.json.JsonMapperManager;
import tools.jackson.databind.JsonNode;

class GameEventArchiveServiceTest {

    private static final long SINCE = 1_000L;

    private final GameEventRepository repository = mock(GameEventRepository.class);
    private final GameEventService eventService = mock(GameEventService.class);
    private final GameEventArchiveService archiveService = new GameEventArchiveService(repository, eventService);

    @Test
    void zipsOneJsonEntryPerPublicGameWithEventsSinceTheTimestamp() throws IOException {
        when(repository.findGameNamesWithEventsAfter(SINCE)).thenReturn(List.of("pbd1", "pbd2"));
        Game first = game("pbd1");
        Game second = game("pbd2");
        when(eventService.getEventsSince(first, SINCE)).thenReturn(List.of(event(3), event(4)));
        when(eventService.getEventsSince(second, SINCE)).thenReturn(List.of(event(9)));

        ManagedGame firstManaged = managed(first, false);
        ManagedGame secondManaged = managed(second, false);

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("pbd1")).thenReturn(firstManaged);
            gameManager.when(() -> GameManager.getManagedGame("pbd2")).thenReturn(secondManaged);

            Map<String, String> entries = unzip(archive());

            assertThat(entries).containsOnlyKeys("pbd1.json", "pbd2.json");
            assertThat(seqs(entries.get("pbd1.json"))).containsExactly(3L, 4L);
            assertThat(seqs(entries.get("pbd2.json"))).containsExactly(9L);
        }
    }

    @Test
    void omitsFogOfWarGamesUnknownGamesAndGamesWithoutNewEvents() throws IOException {
        when(repository.findGameNamesWithEventsAfter(SINCE)).thenReturn(List.of("fow1", "gone", "quiet", "pbd1"));
        Game fog = game("fow1");
        Game quiet = game("quiet");
        Game open = game("pbd1");
        when(eventService.getEventsSince(fog, SINCE)).thenReturn(List.of(event(1)));
        when(eventService.getEventsSince(quiet, SINCE)).thenReturn(List.of());
        when(eventService.getEventsSince(open, SINCE)).thenReturn(List.of(event(2)));

        ManagedGame fogManaged = managed(fog, true);
        ManagedGame quietManaged = managed(quiet, false);
        ManagedGame openManaged = managed(open, false);

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager.when(() -> GameManager.getManagedGame("fow1")).thenReturn(fogManaged);
            gameManager.when(() -> GameManager.getManagedGame("gone")).thenReturn(null);
            gameManager.when(() -> GameManager.getManagedGame("quiet")).thenReturn(quietManaged);
            gameManager.when(() -> GameManager.getManagedGame("pbd1")).thenReturn(openManaged);

            assertThat(unzip(archive())).containsOnlyKeys("pbd1.json");
        }
    }

    @Test
    void skipsAGameThatFailsToLoadAndKeepsTheRest() throws IOException {
        when(repository.findGameNamesWithEventsAfter(SINCE)).thenReturn(List.of("broken", "pbd1"));
        Game open = game("pbd1");
        when(eventService.getEventsSince(open, SINCE)).thenReturn(List.of(event(2)));

        ManagedGame openManaged = managed(open, false);

        try (MockedStatic<GameManager> gameManager = mockStatic(GameManager.class)) {
            gameManager
                    .when(() -> GameManager.getManagedGame("broken"))
                    .thenThrow(new IllegalStateException("Failed to load"));
            gameManager.when(() -> GameManager.getManagedGame("pbd1")).thenReturn(openManaged);

            assertThat(unzip(archive())).containsOnlyKeys("pbd1.json");
        }
    }

    @Test
    void producesAValidEmptyZipWhenNothingChanged() throws IOException {
        when(repository.findGameNamesWithEventsAfter(SINCE)).thenReturn(List.of());

        assertThat(unzip(archive())).isEmpty();
    }

    private byte[] archive() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        archiveService.writeEventsSinceAsZip(out, SINCE);
        return out.toByteArray();
    }

    private static Game game(String name) {
        Game game = mock(Game.class);
        when(game.getName()).thenReturn(name);
        return game;
    }

    private static ManagedGame managed(Game game, boolean fow) {
        ManagedGame managedGame = mock(ManagedGame.class);
        when(managedGame.isFowMode()).thenReturn(fow);
        when(managedGame.getGame()).thenReturn(game);
        return managedGame;
    }

    private static GameEventDto event(long seq) {
        return new GameEventDto(
                seq,
                "TURN",
                1,
                "action",
                "sol",
                SINCE + seq,
                JsonMapperManager.basic().readTree("{}"),
                null,
                null,
                null);
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

    private static List<Long> seqs(String json) {
        List<Long> seqs = new ArrayList<>();
        for (JsonNode event : JsonMapperManager.basic().readTree(json)) {
            seqs.add(event.get("seq").asLong());
        }
        return seqs;
    }
}
