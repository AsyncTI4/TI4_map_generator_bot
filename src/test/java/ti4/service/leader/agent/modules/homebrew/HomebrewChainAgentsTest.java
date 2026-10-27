package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedStep;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.TargetedAgent;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class HomebrewChainAgentsTest extends BaseTi4Test {

    private static final String LODOR = "lodor";
    private static final String NEW_ALBION = "newalbion";
    private static final String STARPOINT = "starpoint";
    private static final String PRIMOR = "primor";
    private static final String ACTIVE = AgentModuleFixture.ACTIVE_SYSTEM;
    // Ring 1 neighbours are adjacent to the active system 101.
    private static final String ADJACENT = "102";

    @Test
    void theTargetedChainAgentsDecodeFactionsColoursAndProjectPiIds() {
        AgentModuleFixture fixture = new AgentModuleFixture("cheiran");
        List<TargetedAgent> targeted =
                List.of(new CheiranAgent(), new FlorzenAgent(), new CeldauriAgent(), new ZealotsAgent());

        targeted.forEach(fixture::assertDecodesTargets);
        assertThat(new FreeSystemsAgent().decode(fixture.game, fixture.user, ""))
                .contains(fixture.user);
    }

    @Test
    void cheiranOffersTheTargetEachTokenThatCanMoveNextToTheirStructures() {
        AgentModuleFixture fixture = new AgentModuleFixture("cheiran");
        CheiranAgent cheiran = new CheiranAgent();
        Player target = fixture.target;
        assertThat(cheiran.unusableReason(fixture.game, fixture.user, target)).isPresent();
        Tile adjacent = new Tile("27", ADJACENT);
        fixture.game.setTile(adjacent);
        adjacent.addUnit(NEW_ALBION, Units.getUnitKey(UnitType.Pds, target.getColorID()), 1);
        fixture.tile.addCC(Mapper.getCCID(target.getColor()));
        assertThat(cheiran.unusableReason(fixture.game, fixture.user, target)).isEmpty();

        AgentOutcome outcome = cheiran.resolve(fixture.useAsYssarilCopy(cheiran, target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(target);
            assertThat(handlerIds(message))
                    .containsExactly("cheiranAgentStep2_" + ACTIVE + AgentStepIds.YSSARIL_COPY_FLAG);
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedStep(button, target, true));
        });
    }

    @Test
    void cheiranOnlyOffersItsButtonWhenATokenCanMove() {
        AgentModuleFixture fixture = new AgentModuleFixture("cheiran");
        assertThat(CheiranAgent.button(fixture.game, fixture.user)).isEmpty();
        Tile adjacent = new Tile("27", ADJACENT);
        fixture.game.setTile(adjacent);
        adjacent.addUnit(NEW_ALBION, Units.getUnitKey(UnitType.Spacedock, fixture.user.getColorID()), 1);
        fixture.tile.addCC(Mapper.getCCID(fixture.user.getColor()));

        assertThat(CheiranAgent.button(fixture.game, fixture.user))
                .hasValueSatisfying(button -> assertOwnedAgentButton(
                        button, fixture.user, "exhaustAgent_cheiranagent_" + fixture.user.getFaction()));
    }

    @Test
    void freeSystemsOffersEveryLegendaryAbilityAnyPlayerControls() {
        AgentModuleFixture fixture = new AgentModuleFixture("freesystems");
        FreeSystemsAgent freeSystems = new FreeSystemsAgent();
        assertThat(freeSystems.unusableReason(fixture.game, fixture.user, fixture.user))
                .isPresent();
        fixture.target.addPlanet(PRIMOR);
        assertThat(freeSystems.unusableReason(fixture.game, fixture.user, fixture.user))
                .isEmpty();

        AgentOutcome outcome = freeSystems.resolve(fixture.use(freeSystems, fixture.user));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(handlerIds(message)).containsExactly("freeSystemsAgentStep2_" + PRIMOR);
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedStep(button, fixture.user, false));
        });
    }

    @Test
    void florzenOffersTheTargetsAttachmentsOnNonHomePlanets() {
        AgentModuleFixture fixture = new AgentModuleFixture("florzen");
        FlorzenAgent florzen = new FlorzenAgent();
        Player target = fixture.target;
        target.addPlanet(LODOR);
        assertThat(florzen.unusableReason(fixture.game, fixture.user, target)).isPresent();
        // Attachment ids may contain "_", which the step id carries as ";".
        fixture.tile.getUnitHolders().get(LODOR).addToken("attachment_cybernetic_research.png");
        assertThat(florzen.unusableReason(fixture.game, fixture.user, target)).isEmpty();

        AgentOutcome outcome = florzen.resolve(fixture.use(florzen, target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(target);
            assertThat(handlerIds(message)).containsExactly("florzenAgentStep2_" + LODOR + "_cybernetic;research");
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, target));
        });
    }

    @Test
    void celdauriIsRefusedUnlessTheActivePlayerCanPayAndControlsAPlanetThere() {
        AgentModuleFixture fixture = new AgentModuleFixture("celdauri");
        CeldauriAgent celdauri = new CeldauriAgent();
        Player target = fixture.target;
        target.setCommodities(2);
        target.addPlanet(LODOR);
        assertThat(celdauri.unusableReason(fixture.game, fixture.user, target))
                .hasValueSatisfying(reason -> assertThat(reason).contains("not the active player"));
        fixture.game.setActivePlayerID(target.getUserID());
        target.setCommodities(1);
        target.setTg(1);
        assertThat(celdauri.unusableReason(fixture.game, fixture.user, target))
                .hasValueSatisfying(reason -> assertThat(reason).contains("cannot pay"));
        target.setCommodities(2);

        assertThat(celdauri.unusableReason(fixture.game, fixture.user, target)).isEmpty();
    }

    @Test
    void celdauriWithOnePlanetPlacesTheSpaceDockAndPaysCommodities() {
        AgentModuleFixture fixture = new AgentModuleFixture("celdauri");
        CeldauriAgent celdauri = new CeldauriAgent();
        Player target = fixture.target;
        fixture.game.setActivePlayerID(target.getUserID());
        target.setCommodities(3);
        target.addPlanet(LODOR);

        AgentOutcome outcome = celdauri.resolve(fixture.use(celdauri, target));

        assertThat(fixture.tile.getUnitHolders().get(LODOR).getUnitCount(UnitType.Spacedock, target.getColor()))
                .isEqualTo(1);
        assertThat(target.getCommodities()).isEqualTo(1);
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(target);
            assertThat(message.buttons()).isEmpty();
            assertThat(message.text()).contains("2 commodities");
        });
    }

    @Test
    void celdauriWithSeveralPlanetsLetsTheTargetChoose() {
        AgentModuleFixture fixture = new AgentModuleFixture("celdauri");
        CeldauriAgent celdauri = new CeldauriAgent();
        Player target = fixture.target;
        Tile albion = new Tile("27", ADJACENT);
        fixture.game.setTile(albion);
        fixture.game.setActiveSystem(ADJACENT);
        fixture.game.setActivePlayerID(target.getUserID());
        target.setTg(2);
        target.addPlanet(NEW_ALBION);
        target.addPlanet(STARPOINT);

        AgentOutcome outcome = celdauri.resolve(fixture.useAsYssarilCopy(celdauri, target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(target);
            assertThat(message.buttons()).hasSize(2).allSatisfy(button -> assertOwnedStep(button, target, true));
        });
        assertThat(target.getTg())
                .as("nothing is paid until a planet is chosen")
                .isEqualTo(2);
    }

    @Test
    void zealotsLetsTheTargetProduceAShipInATechSpecialtySystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("zealots");
        ZealotsAgent zealots = new ZealotsAgent();
        Player target = fixture.target;
        fixture.game.setTile(new Tile("27", ADJACENT));
        target.addPlanet(NEW_ALBION);

        AgentOutcome outcome = zealots.resolve(fixture.use(zealots, target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(target);
            assertThat(handlerIds(message)).containsExactly("produceOneUnitInTile_" + ADJACENT + "_ZealotsAgent");
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, target));
        });
    }

    @Test
    void zealotsWithNowhereToProduceTellsTheUser() {
        AgentModuleFixture fixture = new AgentModuleFixture("zealots");
        ZealotsAgent zealots = new ZealotsAgent();

        AgentOutcome outcome = zealots.resolve(fixture.use(zealots, fixture.target));

        assertThat(outcome.messages())
                .singleElement()
                .satisfies(message -> assertThat(message.recipient()).isSameAs(fixture.user));
    }

    @Test
    void fogAllianceOffersSystemsWithTheUsersShipsAndNoCommandToken() {
        AgentModuleFixture fixture = new AgentModuleFixture("naalu");
        FogAllianceAgent fogAlliance = new FogAllianceAgent();
        assertThat(fogAlliance.unusableReason(fixture.game, fixture.user, fixture.user))
                .isPresent();
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, fixture.user.getColorID()), 1);
        Tile commanded = new Tile("27", ADJACENT);
        fixture.game.setTile(commanded);
        commanded.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, fixture.user.getColorID()), 1);
        commanded.addCC(Mapper.getCCID(fixture.user.getColor()));

        AgentOutcome outcome = fogAlliance.resolve(fixture.use(fogAlliance, fixture.user));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(handlerIds(message)).containsExactly("fogAllianceAgentStep2_" + ACTIVE);
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, fixture.user));
        });
    }

    @Test
    void stepsPostedBeforeTheMigrationStillParse() {
        // Old step buttons carry no owner and no Clever Clever flag.
        assertThat(AgentStepIds.parse("cheiranAgentStep2_" + ACTIVE).viaYssaril())
                .isFalse();
        assertThat(AgentStepIds.parse("celdauriAgentStep3_" + LODOR).id()).isEqualTo("celdauriAgentStep3_" + LODOR);
    }

    private static void assertOwnedBy(Button button, Player owner) {
        assertThat(ComponentIdEnvelope.decode(button.getCustomId()).ownerFaction())
                .isEqualTo(owner.getFaction());
    }

    private static List<String> handlerIds(Message message) {
        return message.buttons().stream()
                .map(button -> ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .toList();
    }
}
