package ti4.service.leader.agent.modules.homebrew.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class BeansAgentsTest extends BaseTi4Test {

    @Test
    void crystellumIsRegistered() {
        assertThat(LeaderAgents.findAgent("crystellumagent")).containsInstanceOf(CrystellumAgent.class);
    }

    @Test
    void crystellumDecodesATargetWithAProjectPiFactionTheSystemAndTheAmount() {
        AgentModuleFixture fixture = new AgentModuleFixture("crystellum");
        CrystellumAgent crystellum = new CrystellumAgent();

        assertThat(crystellum.decode(
                        fixture.game,
                        fixture.user,
                        AgentModuleFixture.TARGET_FACTION + "|" + AgentModuleFixture.ACTIVE_SYSTEM + "|2"))
                .contains(new CrystellumAgent.Placement(fixture.target, fixture.tile, 2));
        assertThat(crystellum.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|101|x"))
                .isEmpty();
        assertThat(crystellum.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void crystellumCannotPlaceMoreFightersThanTheTargetHasNonFighterShips() {
        AgentModuleFixture fixture = new AgentModuleFixture("crystellum");
        fixture.user.addOwnedUnitByID("cruiser");
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.user.getColorID()), 1);
        CrystellumAgent crystellum = new CrystellumAgent();

        assertThat(crystellum.unusableReason(
                        fixture.game, fixture.user, new CrystellumAgent.Placement(fixture.user, fixture.tile, 1)))
                .isEmpty();
        assertThat(crystellum.unusableReason(
                        fixture.game, fixture.user, new CrystellumAgent.Placement(fixture.user, fixture.tile, 2)))
                .isPresent();
    }

    @Test
    void crystellumPlacementButtonsBelongToTheTargetAndCarryTheOwner() {
        AgentModuleFixture fixture = new AgentModuleFixture("crystellum");

        List<Button> buttons = CrystellumAgent.placementButtons(fixture.user, fixture.target, fixture.tile, 3);

        assertThat(buttons).hasSize(4);
        buttons.forEach(button -> assertOwnedBy(button, fixture.target));
        assertThat(buttons.getFirst().getCustomId()).endsWith("placeCrystellumAgentFighters_crystellum|pi_hacan|101|1");
    }
}
