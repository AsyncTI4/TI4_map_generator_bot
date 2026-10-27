package ti4.service.leader.agent.modules.homebrew;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedStep;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ComponentIdEnvelope;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelperAgents;
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
    private static final String JORD = "jord";
    private static final String MECATOL = "mr";
    private static final String ATTACHMENT = "attachment_cybernetic_research.png";
    private static final String ACTIVE = AgentModuleFixture.ACTIVE_SYSTEM;
    // The ring 1 positions 102 and 106 and the centre 000 are all adjacent to the active system 101.
    private static final String ADJACENT = "102";
    private static final String OTHER_ADJACENT = "106";
    private static final String CENTRE = "000";

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
        // With one place to go, the removal step places the token straight away.
        assertThat(CheiranAgent.destinations(fixture.game, target, fixture.tile))
                .singleElement()
                .isSameAs(adjacent);
    }

    @Test
    void cheiranCannotMoveATokenIntoASystemThatAlreadyHasOne() {
        AgentModuleFixture fixture = new AgentModuleFixture("cheiran");
        Player target = fixture.target;
        Tile adjacent = new Tile("27", ADJACENT);
        fixture.game.setTile(adjacent);
        adjacent.addUnit(NEW_ALBION, Units.getUnitKey(UnitType.Pds, target.getColorID()), 1);
        adjacent.addCC(Mapper.getCCID(target.getColor()));

        assertThat(CheiranAgent.destinations(fixture.game, target, fixture.tile))
                .isEmpty();
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
        assertThat(FreeSystemsAgent.button(fixture.game, fixture.user)).isEmpty();
        fixture.target.addPlanet(PRIMOR);
        assertThat(freeSystems.unusableReason(fixture.game, fixture.user, fixture.user))
                .isEmpty();
        assertThat(FreeSystemsAgent.button(fixture.game, fixture.user)).isPresent();

        AgentOutcome outcome = freeSystems.resolve(fixture.use(freeSystems, fixture.user));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(fixture.user);
            assertThat(handlerIds(message)).containsExactly("freeSystemsAgentStep2_" + PRIMOR);
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedStep(button, fixture.user, false));
        });
    }

    @Test
    void florzenOffersAttachmentsThatHaveSomewhereToGo() {
        AgentModuleFixture fixture = new AgentModuleFixture("florzen");
        FlorzenAgent florzen = new FlorzenAgent();
        Player target = fixture.target;
        target.addPlanet(LODOR);
        fixture.tile.getUnitHolders().get(LODOR).addToken(ATTACHMENT);
        // Lodor is alone in its system, so with no other planet nearby the attachment cannot move.
        assertThat(florzen.unusableReason(fixture.game, fixture.user, target)).isPresent();
        assertThat(FlorzenAgent.button(fixture.game, target)).isEmpty();
        fixture.game.setTile(new Tile("27", ADJACENT));
        target.addPlanet(NEW_ALBION);
        assertThat(florzen.unusableReason(fixture.game, fixture.user, target)).isEmpty();

        AgentOutcome outcome = florzen.resolve(fixture.useAsYssarilCopy(florzen, target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(target);
            // Attachment ids may contain "_", which the step id carries as ";".
            assertThat(handlerIds(message))
                    .containsExactly(
                            "florzenAgentStep2_" + LODOR + "_cybernetic;research" + AgentStepIds.YSSARIL_COPY_FLAG);
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedStep(button, target, true));
        });
    }

    @Test
    void florzenNeverMovesAnAttachmentToAHomeSystemMecatolRexOrBackOntoItsPlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("florzen");
        Player target = fixture.target;
        fixture.game.setTile(new Tile("27", ADJACENT));
        fixture.game.setTile(new Tile("01", OTHER_ADJACENT));
        fixture.game.setTile(new Tile("18", CENTRE));
        target.setHomeSystemPosition(OTHER_ADJACENT);
        List.of(LODOR, NEW_ALBION, STARPOINT, JORD, MECATOL).forEach(target::addPlanet);
        assertThat(fixture.game.getTileByPosition(OTHER_ADJACENT).isHomeSystem(fixture.game))
                .isTrue();

        assertThat(FlorzenAgent.destinations(fixture.game, target, LODOR))
                .containsExactlyInAnyOrder(NEW_ALBION, STARPOINT);
        // An attachment on a home planet cannot be moved at all.
        fixture.game
                .getTileByPosition(OTHER_ADJACENT)
                .getUnitHolders()
                .get(JORD)
                .addToken(ATTACHMENT);
        assertThat(FlorzenAgent.movableAttachments(fixture.game, target)).isEmpty();
    }

    @Test
    void florzenMovesTheAttachmentTokenBetweenPlanets() {
        AgentModuleFixture fixture = new AgentModuleFixture("florzen");
        Tile albion = new Tile("27", ADJACENT);
        fixture.game.setTile(albion);
        fixture.tile.getUnitHolders().get(LODOR).addToken(ATTACHMENT);

        assertThat(FlorzenAgent.moveAttachment(fixture.game, LODOR, NEW_ALBION, "cybernetic;research"))
                .isTrue();

        assertThat(fixture.tile.getUnitHolders().get(LODOR).getTokenList()).doesNotContain(ATTACHMENT);
        assertThat(albion.getUnitHolders().get(NEW_ALBION).getTokenList()).contains(ATTACHMENT);
        assertThat(FlorzenAgent.moveAttachment(fixture.game, LODOR, NEW_ALBION, "cybernetic;research"))
                .as("the token is no longer on Lodor")
                .isFalse();
    }

    @Test
    void celdauriIsRefusedUnlessTheActivePlayerCanPayAndHasAPlanetWithoutASpaceDock() {
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
        assertThat(CeldauriAgent.button(fixture.game, target)).isEmpty();
        target.setCommodities(2);
        assertThat(celdauri.unusableReason(fixture.game, fixture.user, target)).isEmpty();
        assertThat(CeldauriAgent.button(fixture.game, target)).isPresent();

        fixture.tile.addUnit(LODOR, Units.getUnitKey(UnitType.Spacedock, target.getColorID()), 1);

        assertThat(celdauri.unusableReason(fixture.game, fixture.user, target))
                .hasValueSatisfying(reason -> assertThat(reason).contains("without a space dock"));
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
    void celdauriWithSeveralPlanetsLetsTheTargetChooseOnlyThoseWithoutADock() {
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
        target.addPlanet(LODOR);
        assertThat(CeldauriAgent.planetsForASpaceDock(fixture.game, target))
                .containsExactlyInAnyOrder(NEW_ALBION, STARPOINT);

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
        assertThat(zealots.unusableReason(fixture.game, fixture.user, target)).isPresent();
        assertThat(ZealotsAgent.button(fixture.game, target)).isEmpty();
        fixture.game.setTile(new Tile("27", ADJACENT));
        target.addPlanet(NEW_ALBION);
        assertThat(zealots.unusableReason(fixture.game, fixture.user, target)).isEmpty();
        assertThat(ZealotsAgent.button(fixture.game, target)).isPresent();

        AgentOutcome outcome = zealots.resolve(fixture.use(zealots, target));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(target);
            assertThat(handlerIds(message)).containsExactly("produceOneUnitInTile_" + ADJACENT + "_ZealotsAgent");
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, target));
        });
    }

    @Test
    void fogAllianceNeedsItsOwnShipsAndAnAllysSystemToMoveTo() {
        AgentModuleFixture fixture = new AgentModuleFixture("naalu");
        FogAllianceAgent fogAlliance = new FogAllianceAgent();
        Player user = fixture.user;
        assertThat(fogAlliance.unusableReason(fixture.game, user, user))
                .hasValueSatisfying(reason -> assertThat(reason).contains("no ships"));
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, user.getColorID()), 1);
        Tile commanded = new Tile("27", ADJACENT);
        fixture.game.setTile(commanded);
        commanded.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Destroyer, user.getColorID()), 1);
        commanded.addCC(Mapper.getCCID(user.getColor()));
        assertThat(fogAlliance.unusableReason(fixture.game, user, user))
                .hasValueSatisfying(reason -> assertThat(reason).contains("alliance member"));
        user.addAllianceMember(fixture.target.getFaction());
        commanded.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);
        assertThat(fogAlliance.unusableReason(fixture.game, user, user)).isEmpty();

        AgentOutcome outcome = fogAlliance.resolve(fixture.use(fogAlliance, user));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isSameAs(user);
            assertThat(handlerIds(message)).containsExactly("fogAllianceAgentStep2_" + ACTIVE);
            assertThat(message.buttons()).allSatisfy(button -> assertOwnedBy(button, user));
        });
    }

    @Test
    void fogAllianceCannotMoveIntoOrNextToAHostileHomeSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("naalu");
        Player user = fixture.user;
        Player hostile = fixture.game.addPlayer("hostileUser", "Hostile Player");
        hostile.setFaction(fixture.game, "jolnar");
        hostile.setColor("green");
        Tile allyShips = new Tile("27", ADJACENT);
        fixture.game.setTile(allyShips);
        user.addAllianceMember(fixture.target.getFaction());
        allyShips.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);
        assertThat(FogAllianceAgent.destinations(fixture.game, user))
                .containsExactlyInAnyOrder(allyShips, fixture.tile);

        fixture.game.setTile(new Tile("01", OTHER_ADJACENT));
        hostile.setHomeSystemPosition(OTHER_ADJACENT);

        // 101 is next to the hostile home system in 106; 102 is not.
        assertThat(FogAllianceAgent.destinations(fixture.game, user)).containsExactly(allyShips);
    }

    @Test
    void stepHandlersKeepTheirLegacyPrefixesSoPostedButtonsStillRoute() {
        assertThat(handlerPrefixes(CheiranAgent.class))
                .containsExactlyInAnyOrder("cheiranAgentStep2_", "cheiranAgentStep3_");
        assertThat(handlerPrefixes(FreeSystemsAgent.class)).containsExactly("freeSystemsAgentStep2_");
        assertThat(handlerPrefixes(FlorzenAgent.class)).contains("florzenAgentStep2_");
        assertThat(handlerPrefixes(CeldauriAgent.class)).containsExactly("celdauriAgentStep3_");
        assertThat(handlerPrefixes(FogAllianceAgent.class)).containsExactly("fogAllianceAgentStep2_");
        // The hero shares the old third Florzen step, so it stays with the hero's helpers.
        assertThat(handlerPrefixes(ButtonHelperAgents.class)).contains("florzenAgentStep3_");
        // A button posted before the migration has no owner and no Clever Clever flag; the router hands the
        // handler its id unchanged.
        ComponentIdEnvelope legacy = ComponentIdEnvelope.decode("cheiranAgentStep2_" + ACTIVE);
        assertThat(legacy.ownerFaction()).isNull();
        assertThat(AgentStepIds.parse(legacy.handlerId()).viaYssaril()).isFalse();
    }

    private static List<String> handlerPrefixes(Class<?> klass) {
        return Arrays.stream(klass.getDeclaredMethods())
                .map(Method::getDeclaredAnnotations)
                .flatMap(Arrays::stream)
                .filter(ButtonHandler.class::isInstance)
                .map(annotation -> ((ButtonHandler) annotation).value())
                .toList();
    }

    private static List<String> handlerIds(Message message) {
        return message.buttons().stream()
                .map(button -> ComponentIdEnvelope.decode(button.getCustomId()).handlerId())
                .toList();
    }
}
