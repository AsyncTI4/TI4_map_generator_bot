package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class HomebrewPromptAgentsTest extends BaseTi4Test {

    @Test
    void targetedPromptAgentsDecodeTheirTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("ghoti");

        for (TargetedAgent agent : List.of(
                new RohDhnaAgent(),
                new GhotiAgent(),
                new KolumeAgent(),
                new AxisAgent(),
                new MortheusAgent(),
                new OnyxxaAgent(),
                new IronAgent(),
                new ArdentiaAgent(),
                new KryxosAgent(),
                new AeternaAgent(),
                new VeylorAgent(),
                new TaAgent(),
                new VanguardAgent())) {
            fixture.assertDecodesTargets(agent);
        }
    }

    @Test
    void rohDhnaRecordsTheSpendAndOffersCommandTokens() {
        AgentModuleFixture fixture = new AgentModuleFixture("rohdhna");
        RohDhnaAgent rohDhna = new RohDhnaAgent();

        AgentOutcome outcome = rohDhna.resolve(fixture.use(rohDhna, fixture.target));

        assertThat(fixture.target.getSpentThingsThisWindow())
                .singleElement()
                .asString()
                .contains("Rond Bri'ay, the Roh'Dhna agent");
        assertThat(fixture.game.getStoredValue("originalCCsFor" + AgentModuleFixture.TARGET_FACTION))
                .isNotEmpty();
        assertThat(outcome.messages().getFirst().recipient()).isSameAs(fixture.target);
    }

    @Test
    void kolumeOnlyLetsTheTargetDeleteTheirButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("kolume");
        KolumeAgent kolume = new KolumeAgent();

        List<Button> buttons = kolume.resolve(fixture.use(kolume, fixture.target))
                .messages()
                .getFirst()
                .buttons();

        assertThat(buttons.getLast().getCustomId()).startsWith(fixture.target.factionButtonChecker());
    }

    @Test
    void axisUsedOnItselfGainsNoCommodities() {
        AgentModuleFixture fixture = new AgentModuleFixture("axis");
        AxisAgent axis = new AxisAgent();
        int commodities = fixture.user.getCommodities();

        AgentOutcome outcome = axis.resolve(fixture.use(axis, fixture.user));

        assertThat(fixture.user.getCommodities()).isEqualTo(commodities);
        assertThat(outcome.messages().getFirst().buttons()).hasSize(2);
    }

    @Test
    void agentsWithNothingToOfferSayWhy() {
        AgentModuleFixture fixture = new AgentModuleFixture("ghoti");
        // The target has no units, planets or technologies, so none of these has anything to offer.
        assertNothingToOffer(fixture, new IronAgent(), "PRODUCTION");
        assertNothingToOffer(fixture, new KryxosAgent(), "no non-faction technology");
        assertNothingToOffer(fixture, new AeternaAgent(), "no eligible non-fighter ships");
        assertNothingToOffer(fixture, new TaAgent(), "no explorable planets");
        assertNothingToOffer(fixture, new VanguardAgent(), "no eligible non-home planet");
        assertNothingToOffer(fixture, new SarcosaAgent(), "no eligible systems");
    }

    @Test
    void sarcosaWithoutATargetHelpsItsUser() {
        AgentModuleFixture fixture = new AgentModuleFixture("sarcosa");

        assertThat(new SarcosaAgent().decode(fixture.game, fixture.user, "")).contains(fixture.user);
    }

    @Test
    void followUpButtonsBelongToThePlayerWhoMustPressThem() {
        AgentModuleFixture fixture = new AgentModuleFixture("ghoti");
        String targetOnly = fixture.target.factionButtonChecker();

        for (AgentModule<Player> agent :
                List.<AgentModule<Player>>of(new GhotiAgent(), new AxisAgent(), new KolumeAgent())) {
            List<Button> buttons = agent.resolve(fixture.use(agent, fixture.target))
                    .messages()
                    .getFirst()
                    .buttons();
            assertThat(buttons)
                    .as(agent.agentId())
                    .allSatisfy(button -> assertThat(button.getCustomId()).startsWith(targetOnly));
        }
        TyrisAgent tyris = new TyrisAgent();
        assertThat(tyris.resolve(fixture.use(tyris, fixture.user))
                        .messages()
                        .getFirst()
                        .buttons())
                .allSatisfy(button -> assertThat(button.getCustomId()).startsWith(fixture.user.factionButtonChecker()));
    }

    @Test
    void otherPlayersPromptsHideTheAgentNameInFogOfWar() {
        AgentModuleFixture fixture = new AgentModuleFixture("ghoti");
        fixture.game.setFowMode(true);
        GhotiAgent ghoti = new GhotiAgent();

        String prompt = ghoti.resolve(fixture.use(ghoti, fixture.target))
                .messages()
                .getFirst()
                .text();

        assertThat(prompt).doesNotContain("Ghoti").contains("an agent");
    }

    @Test
    void homebrewPromptAgentButtonsAreOwnedByTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("ghoti");
        Player holder = fixture.user;

        assertOwnedAgentButton(GhotiAgent.button(holder), holder, "exhaustAgent_ghotiagent_ghoti");
        assertOwnedAgentButton(MortheusAgent.button(holder), holder, "exhaustAgent_mortheusagent_ghoti");
        assertOwnedAgentButton(RohDhnaAgent.button(holder), holder, "exhaustAgent_rohdhnaagent_ghoti");
        assertOwnedAgentButton(LiZhoAgent.button(holder), holder, "exhaustAgent_lizhoagent");
        assertOwnedAgentButton(MykoMentoriAgent.button(holder), holder, "exhaustAgent_mykomentoriagent");
    }

    private static void assertNothingToOffer(AgentModuleFixture fixture, AgentModule<Player> agent, String reason) {
        AgentOutcome outcome = agent.resolve(fixture.use(agent, fixture.target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.text()).as(agent.agentId()).contains(reason);
            assertThat(message.buttons()).as(agent.agentId()).isEmpty();
        });
    }
}
