package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.testUtils.BaseTi4Test;

class EconomyAgentsTest extends BaseTi4Test {

    @Test
    void bentorGivesCommoditiesPerBlueprint() {
        AgentModuleFixture fixture = new AgentModuleFixture("bentor");
        fixture.user.setHasFoundCulFrag(true);
        fixture.user.setHasFoundHazFrag(true);
        fixture.target.setCommodities(0);
        BentorAgent bentor = new BentorAgent();

        AgentOutcome outcome = bentor.resolve(fixture.use(bentor, fixture.target));

        assertThat(fixture.target.getCommodities()).isEqualTo(2);
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(message.text()).contains("2 commodities due to C.O.O. Mgur, the Bentor agent");
        });
    }

    @Test
    void bentorOverflowBecomesTradeGoods() {
        AgentModuleFixture fixture = new AgentModuleFixture("bentor");
        fixture.user.setHasFoundCulFrag(true);
        fixture.user.setHasFoundHazFrag(true);
        fixture.target.setCommodities(fixture.target.getCommoditiesTotal());
        fixture.target.setTg(0);
        BentorAgent bentor = new BentorAgent();

        bentor.resolve(fixture.use(bentor, fixture.target));

        assertThat(fixture.target.getTg()).isEqualTo(2);
    }

    @Test
    void vadenGivesCommoditiesEqualToTheBestInfluence() {
        AgentModuleFixture fixture = new AgentModuleFixture("vaden");
        fixture.target.addPlanet("lodor");
        fixture.target.setCommodities(0);
        VadenAgent vaden = new VadenAgent();

        AgentOutcome outcome = vaden.resolve(fixture.use(vaden, fixture.target));

        assertThat(fixture.target.getCommodities()).isEqualTo(1);
        assertThat(outcome.messages().getFirst().text()).contains("max influence planet has 1 influence");
    }

    @Test
    void kyroReplenishesTheTargetAndOffersInfantryToTheUser() {
        AgentModuleFixture fixture = new AgentModuleFixture("kyro");
        fixture.target.setCommodities(0);
        KyroAgent kyro = new KyroAgent();

        AgentOutcome outcome = kyro.resolve(fixture.use(kyro, fixture.target));

        assertThat(fixture.target.getCommodities()).isEqualTo(fixture.target.getCommoditiesTotal());
        assertThat(outcome.messages()).last().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.text()).contains("infantry upon");
        });
    }

    @Test
    void mirvedaSpendsAStrategyTokenAndOffersATechnology() {
        AgentModuleFixture fixture = new AgentModuleFixture("mirveda");
        fixture.target.setStrategicCC(2);
        MirvedaAgent mirveda = new MirvedaAgent();

        AgentOutcome outcome = mirveda.resolve(fixture.use(mirveda, fixture.target));

        assertThat(fixture.target.getStrategicCC()).isEqualTo(1);
        assertThat(outcome.messages())
                .hasSize(2)
                .allSatisfy(message -> assertThat(message.recipient()).isSameAs(fixture.target));
        assertThat(outcome.messages().get(1).buttons()).isNotEmpty();
    }

    @Test
    void mirvedaDoesNothingWithoutAStrategyToken() {
        AgentModuleFixture fixture = new AgentModuleFixture("mirveda");
        fixture.target.setStrategicCC(0);
        MirvedaAgent mirveda = new MirvedaAgent();

        AgentOutcome outcome = mirveda.resolve(fixture.use(mirveda, fixture.target));

        assertThat(fixture.target.getStrategicCC()).isZero();
        assertThat(outcome.messages())
                .singleElement()
                .satisfies(message -> assertThat(message.goesToPressedChannel()).isTrue());
    }
}
