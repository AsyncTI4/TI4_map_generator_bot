package ti4.spring.service.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "game_webhook",
        uniqueConstraints = @UniqueConstraint(columnNames = {"game_name", "webhook_user_id"}))
public class GameWebhookEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "webhook_user_id", nullable = false)
    private Long webhookUserId;

    @Column(name = "event_types_csv", nullable = false)
    private String eventTypesCsv;
}
