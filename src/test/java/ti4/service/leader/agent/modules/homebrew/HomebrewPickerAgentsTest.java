package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedStep;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class HomebrewPickerAgentsTest extends BaseTi4Test {

    private static final String LODOR = "lodor";

    @Test
    void kolleccAndPharadnDecodeAPickOrAPiTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("kollecc");

        KolleccAgent kolleccPicker = new KolleccAgent();
        assertThat(kolleccPicker.decode(fixture.game, fixture.user, "")).contains(fixture.playerPicker(kolleccPicker));
        assertThat(new PharadnAgent().decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.pickedPlayer(new PharadnAgent(), fixture.target));
        assertThat(new PharadnAgent().decode(fixture.game, fixture.user, "nobody"))
                .isEmpty();
    }

    @Test
    void pharadnSendsTheTargetTheirOwnPlanetButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("pharadn");
        PharadnAgent pharadn = new PharadnAgent();
        fixture.target.addPlanet(LODOR);

        AgentOutcome outcome = pharadn.resolve(fixture.use(pharadn, fixture.pickedPlayer(pharadn, fixture.target)));

        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.target);
        assertThat(prompt.buttons())
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(button, fixture.target, "pharadnAgentKill_" + LODOR));
    }

    @Test
    void kolleccSendsTheTargetExploreButtonsOnlyTheyCanPress() {
        AgentModuleFixture fixture = new AgentModuleFixture("kollecc");
        KolleccAgent kollecc = new KolleccAgent();
        fixture.target.addPlanet(LODOR);

        AgentOutcome outcome = kollecc.resolve(fixture.use(kollecc, fixture.pickedPlayer(kollecc, fixture.target)));

        assertThat(outcome.messages()).hasSize(2);
        Message prompt = outcome.messages().get(1);
        assertThat(prompt.recipient()).isSameAs(fixture.target);
        // Lodor is cultural, so only the cultural deck is offered.
        assertThat(prompt.buttons())
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(button, fixture.target, "kolleccAgentResStep2_cultural"));
    }

    @Test
    void olradinLetsTheTargetReadyAnExhaustedPlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");
        OlradinAgent olradin = new OlradinAgent();
        fixture.target.addPlanet(LODOR);
        fixture.target.exhaustPlanet(LODOR);

        AgentOutcome outcome = olradin.resolve(fixture.useAsYssarilCopy(olradin, fixture.target));

        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.target);
        assertThat(prompt.buttons()).singleElement().satisfies(button -> {
            assertOwnedStep(button, fixture.target, true);
            assertThat(ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                    .isEqualTo("refreshWithOlradinAgent_" + LODOR + "~cc");
        });
    }

    @Test
    void olradinNeedsATarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("olradin");

        fixture.assertDecodesTargets(new OlradinAgent());
    }

    @Test
    void kjalengardNeedsAnActiveSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("kjalengard");
        KjalengardAgent kjalengard = new KjalengardAgent();

        assertThat(kjalengard.unusableReason(fixture.game, fixture.user, fixture.user))
                .isEmpty();
        fixture.game.setActiveSystem("");
        assertThat(kjalengard.unusableReason(fixture.game, fixture.user, fixture.user))
                .isPresent();
    }

    @Test
    void kjalengardWithoutGloryTokensStillTopsUpTheActivePlayer() {
        AgentModuleFixture fixture = new AgentModuleFixture("kjalengard");
        KjalengardAgent kjalengard = new KjalengardAgent();
        fixture.game.setActivePlayerID(fixture.target.getUserID());
        fixture.target.setCommodities(1);

        AgentOutcome outcome = kjalengard.resolve(fixture.use(kjalengard, fixture.user));

        assertThat(fixture.target.getCommodities()).isEqualTo(1 + fixture.target.getNeighbourCount());
        assertThat(outcome.messages()).hasSize(2);
        // The commodity count is the active player's own business in Fog of War.
        assertThat(outcome.messages().getFirst().recipient()).isSameAs(fixture.target);
        assertThat(outcome.messages().get(1).text()).contains("no **Glory** tokens");
    }

    @Test
    void kjalengardWithoutAnActivePlayerOnlyOffersTheGloryMove() {
        AgentModuleFixture fixture = new AgentModuleFixture("kjalengard");
        KjalengardAgent kjalengard = new KjalengardAgent();
        fixture.game.setActivePlayerID(null);

        AgentOutcome outcome = kjalengard.resolve(fixture.use(kjalengard, fixture.user));

        assertThat(outcome.messages())
                .singleElement()
                .satisfies(message -> assertThat(message.recipient()).isSameAs(fixture.user));
    }

    @Test
    void kjalengardOffersToMoveAGloryTokenIntoTheActiveSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("kjalengard");
        KjalengardAgent kjalengard = new KjalengardAgent();
        fixture.tile.addToken("token_ds_glory.png", Constants.SPACE);

        AgentOutcome outcome = kjalengard.resolve(fixture.use(kjalengard, fixture.user));

        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.user);
        String system = AgentModuleFixture.ACTIVE_SYSTEM;
        assertThat(prompt.buttons())
                .singleElement()
                .satisfies(
                        button -> assertOwnedAgentButton(button, fixture.user, "moveGlory_" + system + "_" + system));
    }

    @Test
    void homebrewPickerAgentButtonsAreOwnedByTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        Player holder = fixture.target;

        assertOwnedAgentButton(KolleccAgent.button(holder), holder, "exhaustAgent_kolleccagent");
        assertOwnedAgentButton(PharadnAgent.button(holder), holder, "exhaustAgent_pharadnagent");
        assertOwnedAgentButton(OlradinAgent.button(holder), holder, "exhaustAgent_olradinagent_pi_hacan");
        assertOwnedAgentButton(KjalengardAgent.button(holder), holder, "exhaustAgent_kjalengardagent");
    }

    private static List<String> handlerIds(Message message) {
        return message.buttons().stream()
                .map(button -> ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .toList();
    }
}
