package ti4.spring.service.webhook;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameWebhookRepository extends JpaRepository<GameWebhookEntity, Long> {

    Optional<GameWebhookEntity> findByGameNameAndWebhookUserId(String gameName, Long webhookUserId);

    List<GameWebhookEntity> findByGameName(String gameName);

    void deleteByGameNameAndWebhookUserId(String gameName, Long webhookUserId);
}
