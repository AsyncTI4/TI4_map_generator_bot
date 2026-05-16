package ti4.spring.service.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

@ExtendWith(MockitoExtension.class)
class GameEntitySyncServiceTest extends BaseTi4Test {

    @Mock
    private GameEntityRepository gameEntityRepository;

    @Mock
    private PlayerEntityRepository playerEntityRepository;

    @Mock
    private TitleEntityRepository titleEntityRepository;

    @Mock
    private UserEntityRepository userEntityRepository;

    @InjectMocks
    private GameEntitySyncService gameEntitySyncService;

    @Test
    void syncPersistsGamePlayersTitlesAndPrunesRemovedUsers() {
        Game game = new Game();
        game.setName("sync-persisted-game");
        Player playerOne = addRealPlayer(game, "user-1", "User One", "faction1", "red");
        addRealPlayer(game, "user-2", "User Two", "faction2", "blue");
        addRealPlayer(game, "user-3", "User Three", "faction3", "green");
        game.setStoredValue("TitlesFor" + playerOne.getUserID(), "Champion");

        when(playerEntityRepository.findDistinctUserIdsByGameName(game.getName())).thenReturn(List.of("removed-user"));
        when(titleEntityRepository.findDistinctUserIdsByGameName(game.getName())).thenReturn(List.of());
        when(playerEntityRepository.existsByUser_Id("removed-user")).thenReturn(false);
        when(titleEntityRepository.existsByUser_Id("removed-user")).thenReturn(false);
        when(userEntityRepository.findById(anyString())).thenReturn(Optional.empty());
        when(userEntityRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameEntityRepository.save(any(GameEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(titleEntityRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        gameEntitySyncService.sync(game);

        ArgumentCaptor<GameEntity> gameCaptor = ArgumentCaptor.forClass(GameEntity.class);
        verify(gameEntityRepository).save(gameCaptor.capture());
        GameEntity savedGame = gameCaptor.getValue();
        assertEquals(game.getName(), savedGame.getGameName());
        assertEquals(3, savedGame.getPlayerCount());
        assertEquals(3, savedGame.getPlayers().size());
        assertNull(savedGame.getEndedEpochMilliseconds());

        ArgumentCaptor<List<TitleEntity>> titleCaptor = ArgumentCaptor.forClass(List.class);
        verify(titleEntityRepository).saveAll(titleCaptor.capture());
        assertEquals(1, titleCaptor.getValue().size());
        assertEquals("Champion", titleCaptor.getValue().getFirst().getTitle());

        verify(userEntityRepository).deleteAllByIdInBatch(List.of("removed-user"));
    }

    @Test
    void syncDeletesPersistedRowsForGamesThatShouldNotBeTracked() {
        Game game = new Game();
        game.setName("sync-transient-game");
        addRealPlayer(game, "user-1", "User One", "faction1", "red");
        addRealPlayer(game, "user-2", "User Two", "faction2", "blue");

        when(playerEntityRepository.findDistinctUserIdsByGameName(game.getName())).thenReturn(List.of("user-1"));
        when(titleEntityRepository.findDistinctUserIdsByGameName(game.getName())).thenReturn(List.of());
        when(playerEntityRepository.existsByUser_Id("user-1")).thenReturn(false);
        when(titleEntityRepository.existsByUser_Id("user-1")).thenReturn(false);

        gameEntitySyncService.sync(game);

        verify(titleEntityRepository).deleteByGame_GameName(game.getName());
        verify(playerEntityRepository).deleteByGame_GameName(game.getName());
        verify(gameEntityRepository).deleteAllByIdInBatch(List.of(game.getName()));
        verify(gameEntityRepository, never()).save(any(GameEntity.class));
        verify(titleEntityRepository, never()).saveAll(any());
        verify(userEntityRepository).deleteAllByIdInBatch(List.of("user-1"));
    }

    private static Player addRealPlayer(Game game, String userId, String userName, String faction, String color) {
        Player player = game.addPlayer(userId, userName);
        player.setFaction(faction);
        player.setColor(color);
        return player;
    }
}
