package ti4.spring.service.persistence;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GameEntityPersistenceServiceTest {

    private GameEntityRepository gameEntityRepository;
    private PlayerEntityRepository playerEntityRepository;
    private TitleEntityRepository titleEntityRepository;
    private UserEntityRepository userEntityRepository;
    private GameEntityPersistenceService service;

    @BeforeEach
    void setUp() {
        gameEntityRepository = mock(GameEntityRepository.class);
        playerEntityRepository = mock(PlayerEntityRepository.class);
        titleEntityRepository = mock(TitleEntityRepository.class);
        userEntityRepository = mock(UserEntityRepository.class);
        service = new GameEntityPersistenceService(
                gameEntityRepository, playerEntityRepository, titleEntityRepository, userEntityRepository);
    }

    @Test
    void deleteRemovesRowsInForeignKeyOrder() {
        service.delete("pbd1");

        InOrder deletionOrder = inOrder(titleEntityRepository, playerEntityRepository, gameEntityRepository);
        deletionOrder.verify(titleEntityRepository).deleteByGameName("pbd1");
        deletionOrder.verify(playerEntityRepository).deleteByGameName("pbd1");
        deletionOrder.verify(gameEntityRepository).deleteByGameName("pbd1");
    }
}
