package ti4.spring.service.webhook;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookUserRepository extends JpaRepository<WebhookUserEntity, Long> {

    Optional<WebhookUserEntity> findByApiKeyHashAndActiveTrue(String apiKeyHash);
}
