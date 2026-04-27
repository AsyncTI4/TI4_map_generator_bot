package ti4.contest.replay.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class ReplayDispatchPayloadTest extends BaseTi4Test {

    private final ReplayDispatchSerializer serializer = new ReplayDispatchSerializer();

    @Test
    void readsLegacyDiscordMessagePayloads() {
        ReplayDispatchPayload payload =
                serializer.read("{\"kind\":\"DISCORD_MESSAGE\",\"message\":{\"content\":\"old\",\"embeds\":[]}}");

        ReplayDispatchPayload.GenericMessageDispatch message =
                assertInstanceOf(ReplayDispatchPayload.GenericMessageDispatch.class, payload);
        assertEquals("old", message.message().content());
    }

    @Test
    void writesGenericMessagePayloadsForNewLiteralMessages() {
        String json = serializer.write(ReplayDispatchPayload.genericMessage("new"));

        assertTrue(json.contains("\"kind\":\"GENERIC_MESSAGE\""));
    }

    @Test
    void writesStructuredInteractionPayloads() {
        assertTrue(serializer
                .write(ReplayDispatchPayload.leaderPlayed("yssarilagent"))
                .contains("\"kind\":\"LEADER_PLAYED\""));
        assertTrue(serializer
                .write(ReplayDispatchPayload.actionCardPlayed("mb1"))
                .contains("\"kind\":\"ACTION_CARD_PLAYED\""));
        assertTrue(serializer.write(ReplayDispatchPayload.techPlayed("asc")).contains("\"kind\":\"TECH_PLAYED\""));
        assertTrue(
                serializer.write(ReplayDispatchPayload.techExhausted("gls")).contains("\"kind\":\"TECH_EXHAUSTED\""));
        assertTrue(serializer.write(ReplayDispatchPayload.retreatDeclared()).contains("\"kind\":\"RETREAT_DECLARED\""));
        assertTrue(serializer
                .write(ReplayDispatchPayload.retreatResolved("A1"))
                .contains("\"kind\":\"RETREAT_RESOLVED\""));
    }
}
