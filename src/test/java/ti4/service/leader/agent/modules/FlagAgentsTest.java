package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import org.junit.jupiter.api.Test;
import ti4.service.leader.agent.AgentOutcome;
import ti4.testUtils.BaseTi4Test;

class FlagAgentsTest extends BaseTi4Test {

    @Test
    void artunoPutsTheOfferedTradeGoodsOnHerself() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        ArtunoAgent artuno = new ArtunoAgent();
        Integer tradeGoods = artuno.decode(fixture.game, fixture.user, "3").orElseThrow();

        AgentOutcome outcome = artuno.resolve(fixture.use(artuno, tradeGoods));

        assertThat(fixture.user.getLeader("nomadagentartuno").orElseThrow().getTgCount())
                .isEqualTo(3);
        assertThat(outcome.messages().getFirst().text()).contains("placed 3 trade goods on top of");
    }

    @Test
    void artunoWithoutTradeGoodsOnlyExhausts() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        ArtunoAgent artuno = new ArtunoAgent();

        assertThat(artuno.decode(fixture.game, fixture.user, "")).contains(0);
        assertThat(artuno.resolve(fixture.use(artuno, 0)).messages()).isEmpty();
        assertThat(artuno.decode(fixture.game, fixture.user, "lots")).isEmpty();
    }

    @Test
    void keleresTargetingSomeoneElseRemembersThem() {
        AgentModuleFixture fixture = new AgentModuleFixture("keleresm");
        KeleresAgent keleres = new KeleresAgent();

        keleres.resolve(fixture.use(keleres, fixture.target));

        assertThat(fixture.game.getStoredValue(KeleresAgent.TARGET_KEY)).isEqualTo(fixture.target.getFaction());
        assertThat(keleres.exhaustAnnouncementAfterName(fixture.use(keleres, fixture.target)))
                .contains("to target " + fixture.target.getRepresentationNoPing());
    }

    @Test
    void keleresUsedOnYourselfRemembersNobody() {
        AgentModuleFixture fixture = new AgentModuleFixture("keleresm");
        KeleresAgent keleres = new KeleresAgent();

        assertThat(keleres.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        keleres.resolve(fixture.use(keleres, fixture.user));

        assertThat(fixture.game.getStoredValue(KeleresAgent.TARGET_KEY)).isEmpty();
    }

    @Test
    void solAndLetnevMarkTheTargetsNextRoll() {
        AgentModuleFixture fixture = new AgentModuleFixture("sol");
        SolAgent sol = new SolAgent();
        LetnevAgent letnev = new LetnevAgent();

        AgentOutcome outcome = sol.resolve(fixture.use(sol, fixture.target));
        letnev.resolve(fixture.use(letnev, fixture.target));

        assertThat(fixture.game.getStoredValue("solagent")).isEqualTo(fixture.target.getFaction());
        assertThat(fixture.game.getStoredValue("letnevagent")).isEqualTo(fixture.target.getFaction());
        assertThat(outcome.messages().getFirst().text()).contains("on their next roll");
        fixture.assertDecodesTargets(sol);
        fixture.assertDecodesTargets(letnev);
    }

    @Test
    void ghostOutsideFogOfWarOnlyExhausts() {
        AgentModuleFixture fixture = new AgentModuleFixture("ghost");
        GhostAgent ghost = new GhostAgent();

        assertThat(ghost.resolve(fixture.use(ghost, fixture.user)).messages()).isEmpty();
        assertThat(fixture.game.getStoredValue(GhostAgent.ACTIVE_KEY)).isEmpty();
    }

    @Test
    void obsidianExplainsHowToDestroyTheUnit() {
        AgentModuleFixture fixture = new AgentModuleFixture("obsidian");
        ObsidianAgent obsidian = new ObsidianAgent();

        assertThat(obsidian.resolve(fixture.use(obsidian, fixture.user))
                        .messages()
                        .getFirst()
                        .text())
                .contains("assign hits");
    }

    @Test
    void flagAgentButtonsAreOwnedByTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("nomad");
        var holder = fixture.user;

        assertOwnedAgentButton(ArtunoAgent.button(holder, 2), holder, "exhaustAgent_nomadagentartuno_2");
        assertOwnedAgentButton(GhostAgent.button(holder), holder, "exhaustAgent_ghostagent");
    }
}
