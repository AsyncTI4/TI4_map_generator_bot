package ti4.spring.service.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.message.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.spring.persistence.GameEntityRepository;
import ti4.spring.persistence.PlayerEntityRepository;
import ti4.spring.persistence.TitleEntityRepository;
import ti4.spring.persistence.UserEntityRepository;

@Service
@RequiredArgsConstructor
public class DeleteAllEntitiesService {

    private final GameEntityRepository gameEntityRepository;
    private final PlayerEntityRepository playerEntityRepository;
    private final TitleEntityRepository titleEntityRepository;
    private final UserEntityRepository userEntityRepository;

    public void deleteAllEntities() {
        BotLogger.info("Starting deleteAllEntities.");
        titleEntityRepository.deleteAllInBatch();
        userEntityRepository.deleteAllInBatch();
        playerEntityRepository.deleteAllInBatch();
        gameEntityRepository.deleteAllInBatch();
        BotLogger.info("Finished deleteAllEntities.");
    }

    public static DeleteAllEntitiesService getBean() {
        return SpringContext.getBean(DeleteAllEntitiesService.class);
    }
}
