package ti4.spring.service.persistence;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GameEntityPersistenceServiceTest {

    private GameEntityRepository gameEntityRepository;
    private PlayerEntityRepository playerEntityRepository;
    private GameParticipantEntityRepository gameParticipantEntityRepository;
    private TitleEntityRepository titleEntityRepository;
    private UserEntityRepository userEntityRepository;
    private GameEntityPersistenceService service;

    @BeforeEach
    void setUp() {
        gameEntityRepository = mock(GameEntityRepository.class);
        playerEntityRepository = mock(PlayerEntityRepository.class);
        gameParticipantEntityRepository = mock(GameParticipantEntityRepository.class);
        titleEntityRepository = mock(TitleEntityRepository.class);
        userEntityRepository = mock(UserEntityRepository.class);
        service = new GameEntityPersistenceService(
                gameEntityRepository,
                playerEntityRepository,
                gameParticipantEntityRepository,
                titleEntityRepository,
                userEntityRepository);
    }

    @Test
    void deleteRemovesRowsInForeignKeyOrder() {
        service.delete("pbd1");

        InOrder deletionOrder = inOrder(
                titleEntityRepository, playerEntityRepository, gameParticipantEntityRepository, gameEntityRepository);
        deletionOrder.verify(titleEntityRepository).deleteByGameName("pbd1");
        deletionOrder.verify(playerEntityRepository).deleteByGameName("pbd1");
        deletionOrder.verify(gameParticipantEntityRepository).deleteByGameName("pbd1");
        deletionOrder.verify(gameEntityRepository).deleteByGameName("pbd1");
    }

    @Test
    void replaceUpsertsUsersThenReplacesExistingGameRows() {
        GameEntity game = new GameEntity();
        game.setGameName("pbd1");
        List<UserEntity> users = List.of(new UserEntity("1", "alice"));
        List<TitleEntity> titles = List.of(new TitleEntity());

        service.replace(new GameEntitySnapshot(game, users, titles));

        InOrder order = inOrder(
                userEntityRepository,
                titleEntityRepository,
                playerEntityRepository,
                gameParticipantEntityRepository,
                gameEntityRepository);
        order.verify(userEntityRepository).saveAll(users);
        order.verify(titleEntityRepository).deleteByGameName("pbd1");
        order.verify(playerEntityRepository).deleteByGameName("pbd1");
        order.verify(gameParticipantEntityRepository).deleteByGameName("pbd1");
        order.verify(gameEntityRepository).deleteByGameName("pbd1");
        order.verify(gameEntityRepository).save(game);
        order.verify(titleEntityRepository).saveAll(titles);
    }

    @Test
    void replaceDoesNotOverwriteKnownUserNameWithUnknownPlaceholder() {
        GameEntity game = new GameEntity();
        game.setGameName("pbd1");
        UserEntity known = new UserEntity("1", "alice");
        UserEntity unknownExisting = new UserEntity("2", GameEntityMapper.UNKNOWN_USER_PREFIX + "2");
        UserEntity unknownNew = new UserEntity("3", GameEntityMapper.UNKNOWN_USER_PREFIX + "3");
        when(userEntityRepository.existsById(anyString())).thenReturn(false);
        when(userEntityRepository.existsById("2")).thenReturn(true);

        service.replace(new GameEntitySnapshot(game, List.of(known, unknownExisting, unknownNew), List.of()));

        // An existing user keeps its real name; a brand new unknown user still gets a row so its players can
        // reference it.
        verify(userEntityRepository).saveAll(List.of(known, unknownNew));
    }
}
