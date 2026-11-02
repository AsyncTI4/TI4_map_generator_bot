package ti4.service.leader.agent.modules.homebrew.beans;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
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

    @Test
    void ashenAndNetrunnersAreRegistered() {
        assertThat(LeaderAgents.findAgent("ashenagent")).containsInstanceOf(AshenAgent.class);
        assertThat(LeaderAgents.findAgent("netrunnersagent")).containsInstanceOf(NetrunnersAgent.class);
    }

    @Test
    void ashenDecodesAProjectPiTargetTheUnitAndTheDestination() {
        AgentModuleFixture fixture = new AgentModuleFixture("ashen");
        fixture.target.addOwnedUnitByID("cruiser");
        AshenAgent ashen = new AshenAgent();

        assertThat(ashen.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|ca|101"))
                .hasValueSatisfying(placement -> {
                    assertThat(placement.target()).isSameAs(fixture.target);
                    assertThat(placement.unit().getAsyncId()).isEqualTo("ca");
                    assertThat(placement.destination()).isSameAs(fixture.tile);
                });
        assertThat(ashen.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|ca|999"))
                .isEmpty();
        assertThat(ashen.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void ashenOnlyPlacesShipsOutsideTheActiveSystemWhereTheTargetHasShips() {
        AgentModuleFixture fixture = new AgentModuleFixture("ashen");
        fixture.target.addOwnedUnitByID("cruiser");
        fixture.target.addOwnedUnitByID("fighter");
        Tile otherSystem = new Tile("27", "102");
        fixture.game.setTile(otherSystem);
        otherSystem.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);
        AshenAgent ashen = new AshenAgent();
        UnitModel cruiser = fixture.target.getUnitFromAsyncID("ca");
        UnitModel fighter = fixture.target.getUnitFromAsyncID("ff");

        assertThat(ashen.unusableReason(
                        fixture.game, fixture.user, new AshenAgent.Placement(fixture.target, cruiser, otherSystem)))
                .isEmpty();
        // The active system is never a valid destination, even with the target's ships in it.
        assertThat(ashen.unusableReason(
                        fixture.game, fixture.user, new AshenAgent.Placement(fixture.target, cruiser, fixture.tile)))
                .isPresent();
        assertThat(ashen.unusableReason(
                        fixture.game, fixture.user, new AshenAgent.Placement(fixture.target, fighter, otherSystem)))
                .isPresent();
    }

    @Test
    void ashenUnitButtonsBelongToTheTargetAndCarryTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("ashen");
        fixture.target.addOwnedUnitByID("cruiser");

        List<Button> buttons = AshenAgent.unitButtons(fixture.user, fixture.target);

        assertThat(buttons).singleElement().satisfies(button -> {
            assertOwnedBy(button, fixture.target);
            assertThat(button.getCustomId()).endsWith("ashenAgentChooseUnit_ashen~ca");
        });
        assertThat(AshenAgent.cardsInfoButton(fixture.user).getLabel()).isEqualTo("Use Orrun");
        assertOwnedBy(AshenAgent.cardsInfoButton(fixture.user), fixture.user);
    }

    @Test
    void netrunnersDecodesAProjectPiTargetAndBothTechnologies() {
        AgentModuleFixture fixture = new AgentModuleFixture("netrunners");
        NetrunnersAgent netrunners = new NetrunnersAgent();

        assertThat(netrunners.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|amd|nm"))
                .contains(new NetrunnersAgent.TechSwap(fixture.target, "amd", "nm"));
        assertThat(netrunners.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|amd|notatech"))
                .isEmpty();
        assertThat(netrunners.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void netrunnersNeedsTheTargetToOwnTheReturnedTechnology() {
        AgentModuleFixture fixture = new AgentModuleFixture("netrunners");
        NetrunnersAgent netrunners = new NetrunnersAgent();
        NetrunnersAgent.TechSwap swap = new NetrunnersAgent.TechSwap(fixture.target, "amd", "nm");

        assertThat(netrunners.unusableReason(fixture.game, fixture.user, swap)).isPresent();

        fixture.target.addTech("amd");
        // Neural Motivator matches Antimass Deflectors' prerequisite count and shares none of its colours.
        assertThat(NetrunnersAgent.eligibleGains(fixture.game, fixture.target, "amd"))
                .extracting(TechnologyModel::getAlias)
                .contains("nm");
        assertThat(netrunners.unusableReason(fixture.game, fixture.user, swap)).isEmpty();
    }

    @Test
    void netrunnersGainButtonsBelongToTheTargetAndCarryTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("netrunners");
        fixture.target.addTech("amd");

        List<Button> buttons = NetrunnersAgent.gainTechButtons(fixture.game, fixture.user, fixture.target, "amd");

        assertThat(buttons).isNotEmpty();
        buttons.forEach(button -> assertOwnedBy(button, fixture.target));
        assertThat(buttons)
                .extracting(Button::getCustomId)
                .anyMatch(id -> id.endsWith("netrunnersAgentGainTech_netrunners|amd|nm"));
        assertOwnedBy(NetrunnersAgent.cardsInfoButton(fixture.user), fixture.user);
    }
}
