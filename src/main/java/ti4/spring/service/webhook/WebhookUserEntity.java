package ti4.spring.service.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "webhook_user")
@Getter
@Setter
public class WebhookUserEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String description;

    @Column(name = "callback_url", nullable = false)
    private String callbackUrl;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(nullable = false)
    private boolean active;
}
