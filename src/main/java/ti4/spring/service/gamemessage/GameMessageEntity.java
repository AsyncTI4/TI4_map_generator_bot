package ti4.spring.service.gamemessage;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.LinkedHashSet;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.message.GameMessage;
import ti4.message.GameMessageType;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "game_message",
        indexes = {
            @Index(name = "idx_game_message_game_name", columnList = "game_name"),
            @Index(name = "idx_game_message_type", columnList = "type")
        })
class GameMessageEntity {

    private static final String FACTION_SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Convert(converter = GameMessageTypeConverter.class)
    @Column(name = "type", nullable = false)
    private GameMessageType type;

    @Column(name = "message_key")
    private String messageKey;

    @Column(name = "game_save_time", nullable = false)
    private long gameSaveTime;

    @Column(name = "factions_that_reacted", nullable = false, columnDefinition = "TEXT")
    private String factionsThatReacted = "";

    static GameMessageEntity from(String gameName, GameMessage gameMessage) {
        GameMessageEntity entity = new GameMessageEntity();
        entity.gameName = gameName;
        entity.replaceWith(gameMessage);
        return entity;
    }

    void replaceWith(GameMessage gameMessage) {
        messageId = gameMessage.messageId();
        type = gameMessage.type();
        messageKey = gameMessage.key();
        gameSaveTime = gameMessage.gameSaveTime();
        factionsThatReacted = String.join(FACTION_SEPARATOR, gameMessage.factionsThatReacted());
    }

    void addReaction(String faction) {
        LinkedHashSet<String> factions = reactedFactions();
        if (factions.add(faction)) {
            factionsThatReacted = String.join(FACTION_SEPARATOR, factions);
        }
    }

    GameMessage toGameMessage() {
        return new GameMessage(messageId, type, reactedFactions(), gameSaveTime, messageKey);
    }

    private LinkedHashSet<String> reactedFactions() {
        if (factionsThatReacted == null || factionsThatReacted.isEmpty()) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(Arrays.asList(factionsThatReacted.split(FACTION_SEPARATOR)));
    }
}
