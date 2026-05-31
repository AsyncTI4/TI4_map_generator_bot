package ti4.spring.service.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serial;
import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "game_webhook")
@IdClass(GameWebhookEntity.GameWebhookId.class)
@Getter
@Setter
public class GameWebhookEntity {

    @Id
    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Id
    @Column(name = "webhook_user_id", nullable = false)
    private Long webhookUserId;

    @Column(name = "event_types_csv", nullable = false)
    private String eventTypesCsv;

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class GameWebhookId implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private String gameName;
        private Long webhookUserId;
    }
}
