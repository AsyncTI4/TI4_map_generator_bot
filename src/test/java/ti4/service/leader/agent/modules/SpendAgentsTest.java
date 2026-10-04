package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.testUtils.BaseTi4Test;

class SpendAgentsTest extends BaseTi4Test {

    @Test
    void gledgeAddsProductionToTheTargetsSpend() {
        AgentModuleFixture fixture = new AgentModuleFixture("gledge");
        GledgeAgent gledge = new GledgeAgent();

        gledge.resolve(fixture.use(gledge, fixture.target));

        assertThat(fixture.target.getSpentThingsThisWindow())
                .containsExactly("Exhausted Durran, the Gledge agent, for +3 PRODUCTION value.");
    }

    @Test
    void gledgeAnnouncesWhoItWasUsedOn() {
        AgentModuleFixture fixture = new AgentModuleFixture("gledge");
        GledgeAgent gledge = new GledgeAgent();

        assertThat(gledge.exhaustAnnouncement(fixture.use(gledge, fixture.target)))
                .contains("has exhausted Durran, the Gledge agent for use on")
                .contains(fixture.target.getRepresentationNoPing());
    }

    @Test
    void khraskAndVeldyrAddTheirSpendModifiers() {
        AgentModuleFixture khraskFixture = new AgentModuleFixture("khrask");
        KhraskAgent khrask = new KhraskAgent();
        khrask.resolve(khraskFixture.use(khrask, khraskFixture.target));

        AgentModuleFixture veldyrFixture = new AgentModuleFixture("veldyr");
        VeldyrAgent veldyr = new VeldyrAgent();
        veldyr.resolve(veldyrFixture.use(veldyr, veldyrFixture.target));

        assertThat(khraskFixture.target.getSpentThingsThisWindow())
                .containsExactly("Exhausted Udosh B'rtul, the Khrask agent, to spend 1 non-home planet's resources as"
                        + " additional influence.");
        assertThat(veldyrFixture.target.getSpentThingsThisWindow())
                .containsExactly("Exhausted Solis Morden, the Veldyr agent, to pay with one planets influence instead"
                        + " of resources.");
    }

    @Test
    void winnuOnYourselfRewritesTheSpendMessage() {
        AgentModuleFixture fixture = new AgentModuleFixture("winnu");
        WinnuAgent winnu = new WinnuAgent();

        AgentOutcome outcome = winnu.resolve(fixture.use(winnu, fixture.user));

        assertThat(fixture.user.getSpentThingsThisWindow()).containsExactly("winnuagent");
        assertThat(outcome.pressedMessageEdit()).isNotBlank();
    }

    @Test
    void winnuOnSomeoneElseLeavesThePressedMessageAlone() {
        AgentModuleFixture fixture = new AgentModuleFixture("winnu");
        WinnuAgent winnu = new WinnuAgent();

        AgentOutcome outcome = winnu.resolve(fixture.use(winnu, fixture.target));

        assertThat(fixture.target.getSpentThingsThisWindow()).containsExactly("winnuagent");
        assertThat(outcome.pressedMessageEdit()).isNull();
    }
}
