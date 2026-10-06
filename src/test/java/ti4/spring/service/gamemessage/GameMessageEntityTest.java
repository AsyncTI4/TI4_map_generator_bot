package ti4.spring.service.gamemessage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.message.GameMessage;
import ti4.message.GameMessageType;

class GameMessageEntityTest {

    @Test
    void roundTripsEveryField() {
        GameMessage original = new GameMessage(
                "123", GameMessageType.STRATEGY_CARD, new LinkedHashSet<>(List.of("sol", "argent")), 42L, "4::8");

        GameMessage reread = GameMessageEntity.from("pbd1000", original).toGameMessage();

        assertThat(reread).isEqualTo(original);
        assertThat(reread.factionsThatReacted()).containsExactly("sol", "argent");
    }

    @Test
    void messagesWithoutReactionsOrKeyRoundTrip() {
        GameMessage original = new GameMessage("123", GameMessageType.TURN, 42L);

        GameMessage reread = GameMessageEntity.from("pbd1000", original).toGameMessage();

        assertThat(reread.factionsThatReacted()).isEmpty();
        assertThat(reread.key()).isNull();
    }

    @Test
    void addReactionAppendsInOrderAndIgnoresRepeats() {
        GameMessageEntity entity =
                GameMessageEntity.from("pbd1000", new GameMessage("123", GameMessageType.ACTION_CARD, 42L));

        entity.addReaction("xxcha");
        entity.addReaction("argent");
        entity.addReaction("xxcha");

        assertThat(entity.toGameMessage().factionsThatReacted()).containsExactly("xxcha", "argent");
    }

    @Test
    void replaceWithOverwritesTheTrackedMessage() {
        GameMessageEntity entity = GameMessageEntity.from(
                "pbd1000", new GameMessage("old", GameMessageType.TURN, new LinkedHashSet<>(List.of("sol")), 1L, null));

        entity.replaceWith(new GameMessage("new", GameMessageType.TURN, 2L));

        assertThat(entity.toGameMessage()).isEqualTo(new GameMessage("new", GameMessageType.TURN, 2L));
        assertThat(entity.getGameName()).isEqualTo("pbd1000");
    }

    @Test
    void typeConverterRoundTripsEveryType() {
        // Stored as plain text without a CHECK constraint, so adding a type never needs a schema change.
        GameMessageTypeConverter converter = new GameMessageTypeConverter();
        for (GameMessageType type : GameMessageType.values()) {
            assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(type)))
                    .isEqualTo(type);
        }
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
