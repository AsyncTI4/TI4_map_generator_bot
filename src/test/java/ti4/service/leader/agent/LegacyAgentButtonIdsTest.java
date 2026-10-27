package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

/**
 * Buttons posted before an agent moved to a module stay in live async games for days. These are the exact ids
 * the legacy exhaustAgent chain built, most of them without an FFCC owner prefix, and each must still reach
 * its module and decode to a payload.
 */
class LegacyAgentButtonIdsTest extends BaseTi4Test {

    private static final String ACTIVE = AgentModuleFixture.ACTIVE_SYSTEM;
    private static final String TARGET = AgentModuleFixture.TARGET_FACTION;

    static Stream<Arguments> legacyIds() {
        return Stream.of(
                // getButtonsForAgentSelection: the chosen player is appended to the agent id.
                Arguments.of("naalu", "exhaustAgent_naaluagent_" + TARGET),
                // The Yin owner picker: system, then the destroyed unit's owner.
                Arguments.of("yin", "exhaustAgent_yinagent_" + ACTIVE + "_" + TARGET),
                // Argent's production button was already owned.
                Arguments.of("argent", "FFCC_argent_exhaustAgent_argentagent_" + ACTIVE),
                Arguments.of("nomad", "exhaustAgent_nomadagentmercer_" + ACTIVE + "_lodor"),
                Arguments.of("l1z1x", "exhaustAgent_l1z1xagent_" + ACTIVE + "_lodor"),
                Arguments.of("l1z1x", "exhaustAgent_l1z1xagent_" + TARGET),
                Arguments.of("l1z1x", "exhaustAgent_l1z1xagent"),
                Arguments.of("dihmohn", "exhaustAgent_dihmohnagent_lodor"));
    }

    @ParameterizedTest
    @MethodSource("legacyIds")
    void aLegacyButtonStillReachesItsModuleAndDecodes(String userFaction, String rawId) {
        AgentModuleFixture fixture = new AgentModuleFixture(userFaction);
        String handlerId = ComponentIdEnvelope.decode(rawId).handlerId();
        AgentButtonIds.Parsed parsed = AgentButtonIds.parse(handlerId);

        assertThat(AgentModules.find(parsed.agentId()))
                .as(rawId)
                .hasValueSatisfying(module -> assertThat(module.decode(fixture.game, fixture.user, parsed.payload()))
                        .as(rawId)
                        .isPresent());
    }
}
