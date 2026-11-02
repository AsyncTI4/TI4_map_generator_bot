package ti4.service.leader.agent.modules.homebrew.theodisi;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.game.Planet;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class RevenantAgentsTest extends BaseTi4Test {

    private static final String TECH = "gd";
    private static final String PLANET = "lodor";

    @Test
    void everyRevenantAgentIsRegistered() {
        assertThat(LeaderAgents.findAgent("revenantagent")).containsInstanceOf(RevenantAgent.class);
        assertThat(LeaderAgents.findAgent("revenantverydithagent")).containsInstanceOf(RevenantVerydithAgent.class);
        assertThat(LeaderAgents.findAgent("revenantstonebornagent")).containsInstanceOf(RevenantStratumAgent.class);
        assertThat(LeaderAgents.findAgent("revenantardentiaagent")).containsInstanceOf(RevenantArdentiaAgent.class);
        assertThat(LeaderAgents.findAgent("revenantscrapyardagent")).containsInstanceOf(RevenantScrapyardAgent.class);
        assertThat(LeaderAgents.findAgent("revenantarcanumagent")).containsInstanceOf(RevenantArcanumAgent.class);
    }

    // Revenant agent: ready a technology.

    @Test
    void revenantDecodesAProjectPiTargetAndATechnology() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantAgent agent = new RevenantAgent();
        TechnologyModel tech = Mapper.getTech(TECH);

        assertThat(agent.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|" + TECH))
                .contains(new RevenantAgent.Readying(fixture.target, tech));
        assertThat(agent.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|notatech"))
                .isEmpty();
        assertThat(agent.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void revenantOnlyReadiesAnExhaustedTechnologyAndReadiesIt() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantAgent agent = new RevenantAgent();
        RevenantAgent.Readying readying = new RevenantAgent.Readying(fixture.target, Mapper.getTech(TECH));
        fixture.target.addTech(TECH);

        assertThat(agent.unusableReason(fixture.game, fixture.user, readying)).isPresent();

        fixture.target.getExhaustedTechs().add(TECH);
        assertThat(agent.unusableReason(fixture.game, fixture.user, readying)).isEmpty();

        agent.resolve(fixture.use(agent, readying));
        assertThat(fixture.target.getExhaustedTechs()).doesNotContain(TECH);
    }

    @Test
    void revenantSelfButtonIsAnOwnedAgentButtonAndTargetButtonsBelongToTheTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        fixture.target.addTech(TECH);
        fixture.target.getExhaustedTechs().add(TECH);

        Button self = RevenantAgent.selfReadyButtons(fixture.user, Mapper.getTech(TECH))
                .getFirst();
        assertOwnedAgentButton(self, fixture.user, "exhaustAgent_revenantagent_revenant|" + TECH);

        List<Button> targetButtons = RevenantAgent.targetReadyButtons(fixture.user, fixture.target);
        assertThat(targetButtons).hasSize(1);
        assertOwnedBy(targetButtons.getFirst(), fixture.target);
        assertThat(targetButtons.getFirst().getCustomId()).endsWith("readyTechWithRevBase_revenant~" + TECH);
    }

    // Verydith copy: place a command token from reinforcements, or give another player a tactic token.

    @Test
    void verydithDecodesItsOwnPendingTokenAndAProjectPiTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantVerydithAgent agent = new RevenantVerydithAgent();

        assertThat(agent.decode(fixture.game, fixture.user, AgentModuleFixture.ACTIVE_SYSTEM + "|1"))
                .contains(new RevenantVerydithAgent.OwnToken(
                        fixture.tile, true, AgentModuleFixture.ACTIVE_SYSTEM + "|1"));
        assertThat(agent.decode(fixture.game, fixture.user, "other|" + AgentModuleFixture.TARGET_FACTION))
                .contains(new RevenantVerydithAgent.OtherPlayer(fixture.target));
        assertThat(agent.decode(fixture.game, fixture.user, "other|nobody")).isEmpty();
        assertThat(agent.decode(fixture.game, fixture.user, "999|0")).isEmpty();
    }

    @Test
    void verydithOwnTokenMustStillBePending() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantVerydithAgent agent = new RevenantVerydithAgent();
        String payload = AgentModuleFixture.ACTIVE_SYSTEM + "|0";
        RevenantVerydithAgent.OwnToken own = new RevenantVerydithAgent.OwnToken(fixture.tile, false, payload);

        assertThat(agent.unusableReason(fixture.game, fixture.user, own)).isPresent();

        fixture.game.setStoredValue("revenantVerydithPending_revenant", payload);
        assertThat(agent.unusableReason(fixture.game, fixture.user, own)).isEmpty();
    }

    @Test
    void verydithOnAnotherPlayerGivesThemATacticToken() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantVerydithAgent agent = new RevenantVerydithAgent();
        int before = fixture.target.getTacticalCC();

        AgentOutcome outcome = agent.resolve(fixture.use(agent, new RevenantVerydithAgent.OtherPlayer(fixture.target)));

        assertThat(fixture.target.getTacticalCC()).isEqualTo(before + 1);
        assertThat(outcome.messages()).hasSize(1);
        assertThat(outcome.messages().getFirst().recipient()).isEqualTo(fixture.target);
        assertThat(agent.unusableReason(
                        fixture.game, fixture.user, new RevenantVerydithAgent.OtherPlayer(fixture.user)))
                .isPresent();
    }

    @Test
    void verydithButtonsAreOwnedAgentButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");

        Button use = RevenantVerydithAgent.promptButtons(fixture.user, "101|1").getFirst();
        assertOwnedAgentButton(use, fixture.user, "exhaustAgent_revenantverydithagent_101|1");

        Button other =
                RevenantVerydithAgent.targetButtons(fixture.game, fixture.user).getFirst();
        assertOwnedAgentButton(
                other, fixture.user, "exhaustAgent_revenantverydithagent_other|" + AgentModuleFixture.TARGET_FACTION);
    }

    // Stratum copy: explore a planet as another trait.

    @Test
    void stratumDecodesASelfExplorationAndAProjectPiExplorer() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantStratumAgent agent = new RevenantStratumAgent();
        Planet lodor = fixture.game.getUnitHolderFromPlanet(PLANET);

        assertThat(agent.decode(fixture.game, fixture.user, PLANET + "|" + Constants.CULTURAL))
                .contains(new RevenantStratumAgent.Exploration(fixture.user, lodor, fixture.tile, Constants.CULTURAL));
        assertThat(agent.decode(
                        fixture.game,
                        fixture.user,
                        AgentModuleFixture.TARGET_FACTION + "|" + PLANET + "|" + Constants.HAZARDOUS))
                .contains(
                        new RevenantStratumAgent.Exploration(fixture.target, lodor, fixture.tile, Constants.HAZARDOUS));
        assertThat(agent.decode(fixture.game, fixture.user, "nowhere|" + Constants.CULTURAL))
                .isEmpty();
    }

    @Test
    void stratumNeedsATraitAndAControlledPlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantStratumAgent agent = new RevenantStratumAgent();
        Planet lodor = fixture.game.getUnitHolderFromPlanet(PLANET);

        RevenantStratumAgent.Exploration exploration =
                new RevenantStratumAgent.Exploration(fixture.target, lodor, fixture.tile, Constants.CULTURAL);
        assertThat(agent.unusableReason(fixture.game, fixture.user, exploration))
                .isPresent();

        fixture.target.addPlanet(PLANET);
        assertThat(agent.unusableReason(fixture.game, fixture.user, exploration))
                .isEmpty();
        assertThat(agent.unusableReason(
                        fixture.game,
                        fixture.user,
                        new RevenantStratumAgent.Exploration(fixture.target, lodor, fixture.tile, Constants.FRONTIER)))
                .isPresent();
    }

    @Test
    void stratumSelfButtonsAreOwnedAgentButtonsAndTraitButtonsBelongToTheExplorer() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        if (!fixture.user.hasLeader("revenantstonebornagent")) {
            fixture.user.addLeader("revenantstonebornagent");
        }
        fixture.user.addPlanet(PLANET);
        Planet lodor = fixture.game.getUnitHolderFromPlanet(PLANET);

        List<Button> buttons = new ArrayList<>();
        RevenantStratumAgent.addExploreButtons(buttons, fixture.game, fixture.user, lodor);
        assertThat(buttons).isNotEmpty();
        buttons.forEach(button -> assertThat(button.getCustomId())
                .startsWith(fixture.user.factionButtonChecker() + "exhaustAgent_revenantstonebornagent_" + PLANET));
        buttons.forEach(button -> assertOwnedBy(button, fixture.user));

        List<Button> traits = RevenantStratumAgent.traitButtons(fixture.user, fixture.target, lodor);
        assertThat(traits).hasSize(3);
        traits.forEach(button -> assertOwnedBy(button, fixture.target));
    }

    // Ardentia copy: move another player's freshly placed command token.

    @Test
    void ardentiaDecodesAProjectPiTokenOwnerAndTheSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantArdentiaAgent agent = new RevenantArdentiaAgent();

        assertThat(agent.decode(
                        fixture.game,
                        fixture.user,
                        AgentModuleFixture.TARGET_FACTION + "|" + AgentModuleFixture.ACTIVE_SYSTEM))
                .contains(new RevenantArdentiaAgent.TokenMove(fixture.target, fixture.tile));
        assertThat(agent.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION + "|999"))
                .isEmpty();
    }

    @Test
    void ardentiaNeedsAnotherPlayersTokenAndSomewhereToMoveIt() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantArdentiaAgent agent = new RevenantArdentiaAgent();
        RevenantArdentiaAgent.TokenMove move = new RevenantArdentiaAgent.TokenMove(fixture.target, fixture.tile);

        assertThat(agent.unusableReason(fixture.game, fixture.user, move)).isPresent();

        fixture.tile.addCC(Mapper.getCCID(fixture.target.getColor()));
        assertThat(agent.unusableReason(fixture.game, fixture.user, move))
                .as("the owner has no units anywhere to move the token to")
                .isPresent();
        assertThat(agent.unusableReason(
                        fixture.game, fixture.user, new RevenantArdentiaAgent.TokenMove(fixture.user, fixture.tile)))
                .isPresent();
    }

    @Test
    void ardentiaOfferIsAnOwnedAgentButton() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");

        Button offer = RevenantArdentiaAgent.offerButtons(fixture.user, fixture.target, fixture.tile)
                .getFirst();

        assertOwnedAgentButton(
                offer,
                fixture.user,
                "exhaustAgent_revenantardentiaagent_" + AgentModuleFixture.TARGET_FACTION + "|"
                        + AgentModuleFixture.ACTIVE_SYSTEM);
    }

    // Scrapyard copy: a player produces 1 unit in a system with their ships.

    @Test
    void scrapyardTargetsItsUserWithoutAPayloadAndAProjectPiTargetWithOne() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantScrapyardAgent agent = new RevenantScrapyardAgent();

        assertThat(agent.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(agent.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.target);
    }

    @Test
    void scrapyardNeedsTheTargetToHaveShipsOnTheBoard() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantScrapyardAgent agent = new RevenantScrapyardAgent();

        assertThat(agent.unusableReason(fixture.game, fixture.user, fixture.user))
                .isPresent();

        fixture.user.addOwnedUnitByID("cruiser");
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.user.getColorID()), 1);
        assertThat(agent.unusableReason(fixture.game, fixture.user, fixture.user))
                .isEmpty();
    }

    @Test
    void scrapyardButtonsAreOwnedAgentButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");

        assertOwnedAgentButton(
                RevenantScrapyardAgent.startTurnButton(fixture.user),
                fixture.user,
                "exhaustAgent_revenantscrapyardagent");
        assertOwnedAgentButton(
                RevenantScrapyardAgent.targetButtons(fixture.game, fixture.user).getFirst(),
                fixture.user,
                "exhaustAgent_revenantscrapyardagent_" + AgentModuleFixture.TARGET_FACTION);
    }

    // Arcanum copy: a passed player swaps a technology for one with 1 fewer prerequisite.

    @Test
    void arcanumDecodesAProjectPiTargetAndNeedsAReturnableTechnology() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");
        RevenantArcanumAgent agent = new RevenantArcanumAgent();

        assertThat(agent.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.target);
        assertThat(agent.decode(fixture.game, fixture.user, "")).isEmpty();
        assertThat(agent.unusableReason(fixture.game, fixture.user, fixture.target))
                .as("the fixture target owns no technology")
                .isPresent();
    }

    @Test
    void arcanumOfferIsAnOwnedAgentButton() {
        AgentModuleFixture fixture = new AgentModuleFixture("revenant");

        assertOwnedAgentButton(
                RevenantArcanumAgent.offerButtons(fixture.user, fixture.target).getFirst(),
                fixture.user,
                "exhaustAgent_revenantarcanumagent_" + AgentModuleFixture.TARGET_FACTION);
    }
}
