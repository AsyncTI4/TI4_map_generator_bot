package ti4.service.leader.genome.homebrew.tknova;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.model.LeaderModel;
import ti4.model.Mapper;
import ti4.model.Source.ComponentSource;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.service.leader.genome.Genome;
import ti4.testUtils.BaseTi4Test;

class TkNovaMissedGenomesTest extends BaseTi4Test {

    private static final String LODOR = "lodor";

    @Test
    void everyTkNovaGenomeCardHasAGenomeModule() {
        List<String> without = Mapper.getLeaders().values().stream()
                .filter(leader -> leader.getSource() == ComponentSource.tk_nova_cup)
                .map(LeaderModel::getId)
                .filter(id -> !(LeaderAgents.findAgent(id).orElse(null) instanceof Genome<?>))
                .toList();

        assertThat(without).isEmpty();
    }

    @Test
    void mirrorIsOfferedWithAnOwnedButtonAndOnlyExhausts() {
        AgentModuleFixture fixture = new AgentModuleFixture("firmament");
        MirrorGenome mirror = new MirrorGenome();

        assertOwnedAgentButton(MirrorGenome.button(fixture.user), fixture.user, "exhaustAgent_tknovamirroragent");
        assertThat(mirror.resolve(fixture.use(
                                mirror,
                                new ti4.service.leader.agent.ExhaustOnlyAgent.Choice(java.util.Optional.empty())))
                        .messages())
                .isEmpty();
    }

    @Test
    void investmentPutsTheGainedTradeGoodsOnItsOwnCard() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        fixture.user.addLeader("tknovainvestmentagent");
        InvestmentGenome investment = new InvestmentGenome();

        investment.resolve(fixture.use(investment, 3));

        assertThat(fixture.user.getLeader("tknovainvestmentagent").orElseThrow().getTgCount())
                .isEqualTo(3);
    }

    @Test
    void investmentAnyPlayerCanTriggerButFogOfWarKeepsOthersGainsPrivate() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        fixture.user.addLeader("tknovainvestmentagent");
        fixture.game.setFowMode(true);

        InvestmentGenome.offerTradeGoodsGained(fixture.game, fixture.target, 2);

        assertThat(fixture.user.hasUnexhaustedLeader("tknovainvestmentagent")).isTrue();
    }

    @Test
    void scornfulOffersEveryControlledPlanetAndTheSpaceOfSystemsWithShips() {
        AgentModuleFixture fixture = new AgentModuleFixture("argent");
        fixture.user.addPlanet(LODOR);

        List<String> handlerIds = handlerIds(ScornfulGenome.placementButtons(fixture.game, fixture.user));

        assertThat(handlerIds).contains("place_infantry_" + LODOR, "place_mech_" + LODOR, "deleteButtons_spitItOut");
        assertOwnedBy(ScornfulGenome.button(fixture.user, fixture.tile), fixture.user);
    }

    @Test
    void aristocraticAndHumanPromiseAnExtraDieOnTheTargetsNextRoll() {
        AgentModuleFixture fixture = new AgentModuleFixture("letnev");
        AristocraticGenome aristocratic = new AristocraticGenome();
        HumanGenome human = new HumanGenome();

        aristocratic.resolve(fixture.use(aristocratic, fixture.target));
        human.resolve(fixture.use(human, fixture.user));

        assertThat(fixture.game.getStoredValue(AristocraticGenome.ID)).isEqualTo(fixture.target.getFaction());
        assertThat(fixture.game.getStoredValue(HumanGenome.ID)).isEqualTo(fixture.user.getFaction());
    }

    @Test
    void brutalOnlyOffersMechPlacementOnPlanetsTheProducerControlsInThatSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("l1z1x");
        BrutalGenome brutal = new BrutalGenome();
        assertThat(brutal.unusableReason(fixture.game, fixture.user, fixture.tile))
                .isPresent();
        fixture.user.addPlanet(LODOR);

        AgentOutcome outcome = brutal.resolve(fixture.use(brutal, fixture.tile));

        assertThat(handlerIds(outcome.messages().getFirst().buttons()))
                .containsExactly("placeOneNDone_skipbuild_mech_" + LODOR, "deleteButtons");
        assertThat(brutal.decode(fixture.game, fixture.user, AgentModuleFixture.ACTIVE_SYSTEM))
                .contains(fixture.tile);
    }

    @Test
    void ravenousOffersFightersAndInfantryInTheProductionSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("cabal");
        fixture.user.addPlanet(LODOR);
        RavenousGenome ravenous = new RavenousGenome();

        AgentOutcome outcome = ravenous.resolve(fixture.use(ravenous, fixture.tile));

        assertThat(handlerIds(outcome.messages().getFirst().buttons()))
                .containsExactly(
                        "tknovaRavenousPlace_ff_" + AgentModuleFixture.ACTIVE_SYSTEM,
                        "tknovaRavenousPlace_gf_" + LODOR,
                        "deleteButtons");
        assertThat(outcome.messages().getFirst().text()).contains("up to 6 fighters or infantry");
    }

    @Test
    void valiantFromTkNovaTellsTheOpponentToDestroyTwoUnits() {
        AgentModuleFixture fixture = new AgentModuleFixture("bastion");
        AgentModule<?> valiant = LeaderAgents.findAgent("tknovavaliantagent").orElseThrow();

        assertThat(valiant).isInstanceOf(ti4.service.leader.genome.ValiantGenome.class);
        assertThat(valiant.reference("Valiant Genome", false)).isEqualTo("the _Valiant Genome_");
        assertThat(fixture.user.getFaction()).isEqualTo("bastion");
    }

    @Test
    void actionLetsTheTyrantDrawAnExtraEdictOnlyWhenOneRemains() {
        AgentModuleFixture fixture = new AgentModuleFixture("keleresm");
        ActionGenome action = new ActionGenome();

        assertThat(action.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(action.unusableReason(fixture.game, fixture.user, fixture.user))
                .isPresent();
        assertThat(action.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.target);
    }

    private static List<String> handlerIds(List<Button> buttons) {
        return buttons.stream()
                .map(button -> ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .toList();
    }
}
