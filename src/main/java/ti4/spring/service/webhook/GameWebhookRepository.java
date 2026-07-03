package ti4.spring.service.webhook;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameWebhookRepository extends JpaRepository<GameWebhookEntity, GameWebhookEntity.GameWebhookId> {
    List<GameWebhookEntity> findByGameName(String gameName);

    void deleteByGameNameAndWebhookUserId(String gameName, Long webhookUserId);
}
