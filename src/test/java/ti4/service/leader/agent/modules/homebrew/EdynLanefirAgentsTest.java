package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class EdynLanefirAgentsTest extends BaseTi4Test {

    private static final String FRONTIER_CARD = "frln1";

    @Test
    void edynAndLanefirAreRegisteredAsModulesNotExhaustOnlyAgents() {
        assertThat(LeaderAgents.findAgent("edynagent")).containsInstanceOf(EdynAgent.class);
        assertThat(LeaderAgents.findAgent("lanefiragent")).containsInstanceOf(LanefirAgent.class);
        assertThat(HomebrewExhaustOnlyAgents.ALL)
                .noneMatch(agent -> "edynagent".equals(agent.agentId()) || "lanefiragent".equals(agent.agentId()));
    }

    @Test
    void edynDecodesTheActorAndTheNextPlayerIncludingAProjectPiFaction() {
        AgentModuleFixture fixture = new AgentModuleFixture("edyn");
        EdynAgent edyn = new EdynAgent();

        assertThat(edyn.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|edyn"))
                .contains(new EdynAgent.PresetAction(fixture.target, fixture.user));
        assertThat(edyn.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .isEmpty();
        assertThat(edyn.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void edynStillReadsPresetsStoredWithTheOldUnderscoreSeparator() {
        AgentModuleFixture fixture = new AgentModuleFixture("edyn");

        // Presets written before the module used "_"; colours stand in for factions that contain one.
        assertThat(EdynAgent.players(fixture.game, "edyn_" + AgentModuleFixture.TARGET_COLOR + "_edyn", 3))
                .containsExactly(fixture.user, fixture.target, fixture.user);
    }

    @Test
    void edynPresetOnlyFiresWhenTheChosenPlayerPasses() {
        AgentModuleFixture fixture = new AgentModuleFixture("edyn");
        String preset = String.join("|", "edyn", AgentModuleFixture.TARGET_FACTION, "edyn");
        fixture.game.setStoredValue(EdynAgent.PRESET_KEY, preset);

        assertThat(EdynAgent.triggerPresetOnPass(fixture.game, fixture.user, fixture.user, null))
                .isFalse();
        assertThat(fixture.game.getStoredValue(EdynAgent.PRESET_KEY)).isEqualTo(preset);
        assertThat(EdynAgent.endPresetAction(fixture.game, null)).isFalse();
    }

    @Test
    void edynPresetButtonsBelongToTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("edyn");

        List<Button> buttons = EdynAgent.playerButtons(fixture.game, fixture.user, "presetEdynAgentStep3_edyn|");

        assertThat(buttons).hasSize(2);
        buttons.forEach(button -> assertOwnedBy(button, fixture.user));
        assertThat(buttons)
                .extracting(Button::getCustomId)
                .anyMatch(id -> id.endsWith("presetEdynAgentStep3_edyn|pi_hacan"));
        assertOwnedBy(EdynAgent.cardsInfoButton(fixture.user), fixture.user);
    }

    @Test
    void lanefirDecodesAFrontierSystemOrAPlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("lanefir");
        LanefirAgent lanefir = new LanefirAgent();

        assertThat(lanefir.decode(fixture.game, fixture.user, "frontier_" + AgentModuleFixture.ACTIVE_SYSTEM))
                .contains(new LanefirAgent.Exploration(Constants.FRONTIER, fixture.tile, null));
        assertThat(lanefir.decode(fixture.game, fixture.user, "industrial_lodor"))
                .contains(new LanefirAgent.Exploration("industrial", fixture.tile, "lodor"));
        assertThat(lanefir.decode(fixture.game, fixture.user, "frontier_999")).isEmpty();
        assertThat(lanefir.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void lanefirOfferButtonsBelongToTheExplorer() {
        AgentModuleFixture fixture = new AgentModuleFixture("lanefir");

        List<Button> frontier = LanefirAgent.frontierButtons(fixture.user, FRONTIER_CARD, fixture.tile);
        List<Button> planet = LanefirAgent.planetButtons(fixture.user, "industrial", FRONTIER_CARD, "lodor");

        assertThat(frontier)
                .extracting(Button::getCustomId)
                .anyMatch(id -> id.endsWith(
                        "lanefirAgentRes_Decline_frontier_" + FRONTIER_CARD + "_" + AgentModuleFixture.ACTIVE_SYSTEM));
        assertThat(frontier)
                .extracting(Button::getCustomId)
                .anyMatch(id -> id.endsWith("lanefirAgentRes_Accept_frontier_" + AgentModuleFixture.ACTIVE_SYSTEM));
        assertThat(planet)
                .extracting(Button::getCustomId)
                .anyMatch(id -> id.endsWith("lanefirAgentRes_Accept_industrial_lodor"));
        frontier.forEach(button -> assertOwnedBy(button, fixture.user));
        planet.forEach(button -> assertOwnedBy(button, fixture.user));
    }
}
