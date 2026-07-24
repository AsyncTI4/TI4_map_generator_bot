package ti4.spring.service.persistence;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PersistAllEntitiesServiceTest {

    @Test
    void deletesPersistedEntitiesInForeignKeyOrder() {
        GameEntityRepository gameEntityRepository = mock(GameEntityRepository.class);
        PlayerEntityRepository playerEntityRepository = mock(PlayerEntityRepository.class);
        TitleEntityRepository titleEntityRepository = mock(TitleEntityRepository.class);
        UserEntityRepository userEntityRepository = mock(UserEntityRepository.class);
        PersistAllEntitiesService service = new PersistAllEntitiesService(
                gameEntityRepository, playerEntityRepository, titleEntityRepository, userEntityRepository);

        service.deleteAllPersistedEntities();

        InOrder deletionOrder =
                inOrder(titleEntityRepository, playerEntityRepository, gameEntityRepository, userEntityRepository);
        deletionOrder.verify(titleEntityRepository).deleteAllInBatch();
        deletionOrder.verify(playerEntityRepository).deleteAllInBatch();
        deletionOrder.verify(gameEntityRepository).deleteAllInBatch();
        deletionOrder.verify(userEntityRepository).deleteAllInBatch();
    }
}
