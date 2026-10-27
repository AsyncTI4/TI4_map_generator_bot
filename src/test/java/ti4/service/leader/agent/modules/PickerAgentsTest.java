package ti4.service.leader.agent.modules;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedStep;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.TargetedAgent;
import ti4.testUtils.BaseTi4Test;

class PickerAgentsTest extends BaseTi4Test {

    private static final String LODOR = "lodor";

    @Test
    void targetedPickerAgentsDecodeFactionsColoursAndProjectPiIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        List<TargetedAgent> targeted = List.of(
                new CabalAgent(),
                new XxchaAgent(),
                new RebellionAgent(),
                new MuaatAgent(),
                new ArborecAgent(),
                new DeepwroughtAgent());

        targeted.forEach(fixture::assertDecodesTargets);
    }

    @Test
    void nekroWithoutATargetOffersAnOwnedPlayerPicker() {
        AgentModuleFixture fixture = new AgentModuleFixture("nekro");
        NekroAgent nekro = new NekroAgent();

        assertThat(nekro.decode(fixture.game, fixture.user, "")).contains(fixture.playerPicker(nekro));
        assertThat(nekro.decode(fixture.game, fixture.user, AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.pickedPlayer(nekro, fixture.target));
        AgentOutcome outcome = nekro.resolve(fixture.use(nekro, fixture.playerPicker(nekro)));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.namesRecipient()).isTrue();
            assertThat(message.buttons()).hasSize(2).allSatisfy(button -> assertOwnedStep(button, fixture.user, false));
            assertThat(handlerIds(message)).contains("agentPick_nekroagent_" + AgentModuleFixture.TARGET_FACTION);
        });
    }

    @Test
    void nekroGivesTheTargetTwoTradeGoodsAndItsChoices() {
        AgentModuleFixture fixture = new AgentModuleFixture("nekro");
        NekroAgent nekro = new NekroAgent();
        Player target = fixture.target;
        int tradeGoods = target.getTg();

        AgentOutcome outcome = nekro.resolve(fixture.use(nekro, fixture.pickedPlayer(nekro, target)));

        assertThat(target.getTg()).isEqualTo(tradeGoods + 2);
        assertThat(fixture.game.getStoredValue("originalCCsFor" + AgentModuleFixture.TARGET_FACTION))
                .isEqualTo(target.getCCRepresentation());
        assertThat(outcome.messages()).hasSize(4);
        assertThat(outcome.messages().getFirst().recipient()).isSameAs(fixture.user);
        assertThat(outcome.messages().subList(1, 4)).allSatisfy(message -> {
            assertThat(message.recipient()).isSameAs(target);
            assertThat(message.namesRecipient()).isTrue();
        });
        // The action card discard happens in the target's private cards-info thread.
        assertThat(outcome.messages().get(2).route()).isEqualTo(AgentOutcome.Message.Route.CARDS_INFO_THREAD);
        assertThat(outcome.messages().get(3).buttons()).isNotEmpty();
    }

    @Test
    void hacanUsedOnItselfGainsTwoCommodities() {
        AgentModuleFixture fixture = new AgentModuleFixture("hacan");
        HacanAgent hacan = new HacanAgent();
        fixture.user.setCommodities(0);

        AgentOutcome outcome = hacan.resolve(fixture.use(hacan, fixture.pickedPlayer(hacan, fixture.user)));

        assertThat(fixture.user.getCommodities()).isEqualTo(2);
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.text()).contains("gained 2 commodities");
        });
    }

    @Test
    void cabalAcceptsTheLegacyInitiationPayload() {
        AgentModuleFixture fixture = new AgentModuleFixture("cabal");
        CabalAgent cabal = new CabalAgent();

        assertThat(cabal.decode(fixture.game, fixture.user, "startCabalAgent_" + AgentModuleFixture.TARGET_FACTION))
                .contains(fixture.target);
    }

    @Test
    void cabalOffersCapturesUpToTheVictimsCommodityValueAndClearsThePrompt() {
        AgentModuleFixture fixture = new AgentModuleFixture("cabal");
        CabalAgent cabal = new CabalAgent();

        AgentOutcome outcome = cabal.resolve(fixture.useAsYssarilCopy(cabal, fixture.target));

        assertThat(outcome.deletesPressedMessage()).isTrue();
        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.user);
        assertThat(prompt.buttons()).allSatisfy(button -> assertOwnedStep(button, fixture.user, true));
        // Project Pi Hacan has a commodity value of 6: a dreadnought fits, a flagship does not.
        assertThat(handlerIds(prompt))
                .contains("cabalAgentCapture_fighter_pi_hacan~cc", "cabalAgentCapture_dreadnought_pi_hacan~cc")
                .doesNotContain("cabalAgentCapture_flagship_pi_hacan~cc");
    }

    @Test
    void xxchaOffersTheTargetsExhaustedPlanetsToTheXxcha() {
        AgentModuleFixture fixture = new AgentModuleFixture("xxcha");
        XxchaAgent xxcha = new XxchaAgent();
        fixture.target.addPlanet(LODOR);
        fixture.target.exhaustPlanet(LODOR);

        AgentOutcome outcome = xxcha.resolve(fixture.use(xxcha, fixture.target));

        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.user);
        assertThat(prompt.buttons())
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(
                        button,
                        fixture.user,
                        "khraskHeroStep4Ready_" + AgentModuleFixture.TARGET_FACTION + "_" + LODOR));
    }

    @Test
    void rebellionCoversTheThundersEdgeCardAndPromptsTheSwapper() {
        AgentModuleFixture fixture = new AgentModuleFixture("crimson");
        RebellionAgent rebellion = new RebellionAgent();
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, fixture.target.getColorID()), 1);

        AgentOutcome outcome = rebellion.resolve(fixture.use(rebellion, fixture.target));

        assertThat(rebellion.agentId()).isEqualTo("crimsonagent");
        assertThat(rebellion.aliasIds()).containsExactly("redcreussagent");
        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.target);
        assertThat(prompt.buttons())
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(
                        button, fixture.target, "redcreussAgentPart1_" + AgentModuleFixture.ACTIVE_SYSTEM));
    }

    @Test
    void muaatLetsTheProducerPickASystemWithTheirFlagship() {
        AgentModuleFixture fixture = new AgentModuleFixture("muaat");
        MuaatAgent muaat = new MuaatAgent();
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Flagship, fixture.target.getColorID()), 1);

        AgentOutcome outcome = muaat.resolve(fixture.use(muaat, fixture.target));

        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.target);
        assertThat(prompt.buttons())
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(
                        button, fixture.target, "umbatTile_" + AgentModuleFixture.ACTIVE_SYSTEM));
    }

    @Test
    void muaatWithoutAWarSunOrFlagshipSaysSo() {
        AgentModuleFixture fixture = new AgentModuleFixture("muaat");
        MuaatAgent muaat = new MuaatAgent();

        AgentOutcome outcome = muaat.resolve(fixture.use(muaat, fixture.target));

        // Only the user who spent the agent needs to hear that it did nothing.
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.buttons()).isEmpty();
        });
    }

    @Test
    void arborecPromptsTheReplacerWithTheirOwnButtons() {
        AgentModuleFixture fixture = new AgentModuleFixture("arborec");
        ArborecAgent arborec = new ArborecAgent();
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);

        AgentOutcome outcome = arborec.resolve(fixture.use(arborec, fixture.target));

        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.target);
        assertThat(handlerIds(prompt)).contains("arboAgentIn_" + AgentModuleFixture.ACTIVE_SYSTEM);
        assertThat(prompt.buttons())
                .allSatisfy(button -> assertThat(
                                ComponentIdEnvelope.decode(button.getCustomId()).ownerFaction())
                        .isEqualTo(AgentModuleFixture.TARGET_FACTION));
    }

    @Test
    void deepwroughtOffersTheResearchersNonHomePlanetsToCoexistOn() {
        AgentModuleFixture fixture = new AgentModuleFixture("deepwrought");
        DeepwroughtAgent deepwrought = new DeepwroughtAgent();
        fixture.target.addPlanet(LODOR);
        fixture.tile.addUnit(LODOR, Units.getUnitKey(UnitType.Infantry, fixture.target.getColorID()), 1);

        AgentOutcome outcome = deepwrought.resolve(fixture.use(deepwrought, fixture.target));

        assertThat(deepwrought.exhaustAnnouncementAfterName(fixture.use(deepwrought, fixture.target)))
                .contains("ignore 1 prerequisite");
        Message prompt = outcome.messages().getFirst();
        assertThat(prompt.recipient()).isSameAs(fixture.user);
        assertThat(prompt.buttons())
                .singleElement()
                .satisfies(button -> assertOwnedAgentButton(button, fixture.user, "exchangeProgramPart3_" + LODOR));
    }

    @Test
    void hacanReplenishesAnotherPlayerAndHidesItsNameInFogOfWar() {
        AgentModuleFixture fixture = new AgentModuleFixture("hacan");
        fixture.game.setFowMode(true);
        HacanAgent hacan = new HacanAgent();
        Player target = fixture.target;
        target.setCommodities(0);

        AgentOutcome outcome = hacan.resolve(fixture.use(hacan, fixture.pickedPlayer(hacan, target)));

        assertThat(target.getCommodities()).isEqualTo(target.getCommoditiesTotal());
        assertThat(outcome.messages()).anySatisfy(message -> {
            assertThat(message.recipient()).isSameAs(target);
            assertThat(message.text()).contains("an agent").doesNotContain("Hacan");
        });
    }

    @Test
    void arborecWithoutShipsTellsTheUserInsteadOfPromptingTheTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("arborec");
        ArborecAgent arborec = new ArborecAgent();

        AgentOutcome outcome = arborec.resolve(fixture.use(arborec, fixture.target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(message.buttons()).isEmpty();
        });
    }

    @Test
    void deepwroughtTellsTheResearcherTheyMayIgnoreAPrerequisite() {
        AgentModuleFixture fixture = new AgentModuleFixture("deepwrought");
        DeepwroughtAgent deepwrought = new DeepwroughtAgent();

        AgentOutcome outcome = deepwrought.resolve(fixture.use(deepwrought, fixture.target));

        assertThat(outcome.messages()).anySatisfy(message -> {
            assertThat(message.recipient()).isSameAs(fixture.target);
            assertThat(message.namesRecipient()).isTrue();
            assertThat(message.text()).contains("ignore 1 prerequisite");
        });
    }

    @Test
    void aCleverCleverXxchaFlagsItsReadySteps() {
        AgentModuleFixture fixture = new AgentModuleFixture("yssaril");
        XxchaAgent xxcha = new XxchaAgent();
        fixture.target.addPlanet(LODOR);
        fixture.target.exhaustPlanet(LODOR);

        AgentOutcome outcome = xxcha.resolve(fixture.useAsYssarilCopy(xxcha, fixture.target));

        assertThat(outcome.messages().getFirst().buttons())
                .isNotEmpty()
                .allSatisfy(button -> assertOwnedStep(button, fixture.user, true));
    }

    @Test
    void pickerAgentButtonsAreOwnedByTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("mentak");
        Player holder = fixture.target;
        Player replenisher = fixture.user;

        assertOwnedAgentButton(NekroAgent.button(holder), holder, "exhaustAgent_nekroagent");
        assertOwnedAgentButton(HacanAgent.button(holder), holder, "exhaustAgent_hacanagent");
        assertOwnedAgentButton(
                CabalAgent.button(holder, replenisher), holder, "exhaustAgent_cabalagent_" + replenisher.getFaction());
    }

    private static List<String> handlerIds(Message message) {
        return message.buttons().stream()
                .map(button -> ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .toList();
    }
}
