package ti4.spring.service.webhook;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookUserRepository extends JpaRepository<WebhookUserEntity, Long> {
    List<WebhookUserEntity> findByActiveTrue();
}
