package ti4.spring.service.messagecache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bot_discord_message")
class BotDiscordMessageEntity {

    @Id
    private long messageId;

    @Column(columnDefinition = "TEXT")
    private String content;

    private long createdAtEpochMillis;
}
