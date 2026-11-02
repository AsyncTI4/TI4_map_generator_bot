package ti4.service.leader.agent.modules.homebrew.theodisi;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedAgentButton;
import static ti4.service.leader.agent.modules.AgentModuleFixture.assertOwnedBy;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.xytheris.XytherisLeadersHandler;
import ti4.game.Planet;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.service.leader.LeaderAgents;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.modules.AgentModuleFixture;
import ti4.testUtils.BaseTi4Test;

class TheodisiAgentsPart1Test extends BaseTi4Test {

    private static final String TARGET = AgentModuleFixture.TARGET_FACTION;
    private static final String SYSTEM = AgentModuleFixture.ACTIVE_SYSTEM;
    // Tile 26 holds Lodor, a cultural planet.
    private static final String LODOR = "lodor";

    @Test
    void everyAgentIsRegistered() {
        assertThat(LeaderAgents.findAgent("verydithagent")).containsInstanceOf(VerydithAgent.class);
        assertThat(LeaderAgents.findAgent("myrragent")).containsInstanceOf(MyrrAgent.class);
        assertThat(LeaderAgents.findAgent("kairnagent")).containsInstanceOf(KairnAgent.class);
        assertThat(LeaderAgents.findAgent("arcanumagent")).containsInstanceOf(ArcanumAgent.class);
        assertThat(LeaderAgents.findAgent("xytherisagent")).containsInstanceOf(XytherisAgent.class);
        assertThat(LeaderAgents.findAgent("oblivionagent")).containsInstanceOf(OblivionAgent.class);
    }

    @Test
    void verydithDecodesTheReceiverThenTheSender() {
        AgentModuleFixture fixture = new AgentModuleFixture("verydith");
        VerydithAgent verydith = new VerydithAgent();

        assertThat(verydith.decode(fixture.game, fixture.user, TARGET + "|verydith"))
                .contains(new VerydithAgent.NoteExchange(fixture.target, fixture.user));
        assertThat(verydith.decode(fixture.game, fixture.user, TARGET)).isEmpty();
        assertThat(verydith.decode(fixture.game, fixture.user, "")).isEmpty();
    }

    @Test
    void verydithNeedsTwoDifferentPlayers() {
        AgentModuleFixture fixture = new AgentModuleFixture("verydith");
        VerydithAgent verydith = new VerydithAgent();

        assertThat(verydith.unusableReason(
                        fixture.game, fixture.user, new VerydithAgent.NoteExchange(fixture.target, fixture.target)))
                .isPresent();
        assertThat(verydith.unusableReason(
                        fixture.game, fixture.user, new VerydithAgent.NoteExchange(fixture.target, fixture.user)))
                .isEmpty();
    }

    @Test
    void verydithReceiverButtonsExhaustTheAgentForTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("verydith");

        List<Button> buttons = VerydithAgent.receiverButtons(fixture.game, fixture.user, fixture.user);

        assertThat(buttons).hasSize(1);
        assertOwnedAgentButton(buttons.getFirst(), fixture.user, "exhaustAgent_verydithagent_pi_hacan|verydith");
    }

    @Test
    void verydithOffersBothPlayersACommandToken() {
        AgentModuleFixture fixture = new AgentModuleFixture("verydith");
        VerydithAgent verydith = new VerydithAgent();

        AgentOutcome outcome =
                verydith.resolve(fixture.use(verydith, new VerydithAgent.NoteExchange(fixture.target, fixture.user)));

        assertThat(outcome.messages())
                .extracting(AgentOutcome.Message::recipient)
                .containsExactly(fixture.target, fixture.user);
        assertThat(outcome.messages())
                .allSatisfy(message -> assertThat(message.buttons()).isNotEmpty());
        assertThat(outcome.deletesPressedMessage()).isTrue();
    }

    @Test
    void myrrDecodesTheMoverAndSystemOrFallsBackToTheActivePlayer() {
        AgentModuleFixture fixture = new AgentModuleFixture("myrr");
        fixture.game.setActivePlayerID(fixture.target.getUserID());
        MyrrAgent myrr = new MyrrAgent();

        assertThat(myrr.decode(fixture.game, fixture.user, TARGET + "|" + SYSTEM))
                .contains(new MyrrAgent.ShipsMoved(fixture.target, fixture.tile, true));
        assertThat(myrr.decode(fixture.game, fixture.user, ""))
                .contains(new MyrrAgent.ShipsMoved(fixture.target, fixture.tile, false));
        assertThat(myrr.decode(fixture.game, fixture.user, TARGET + "|999")).isEmpty();
    }

    @Test
    void myrrNeedsTheActivePlayerToHaveACheapShipInTheActiveSystem() {
        AgentModuleFixture fixture = new AgentModuleFixture("myrr");
        fixture.game.setActivePlayerID(fixture.target.getUserID());
        MyrrAgent myrr = new MyrrAgent();
        MyrrAgent.ShipsMoved moved = new MyrrAgent.ShipsMoved(fixture.target, fixture.tile, true);

        assertThat(myrr.unusableReason(fixture.game, fixture.user, moved)).isPresent();

        fixture.target.addOwnedUnitByID("cruiser");
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);
        assertThat(myrr.unusableReason(fixture.game, fixture.user, moved)).isEmpty();
        assertThat(myrr.unusableReason(
                        fixture.game, fixture.user, new MyrrAgent.ShipsMoved(fixture.user, fixture.tile, true)))
                .isPresent();
    }

    @Test
    void myrrOfferBelongsToTheHolderAndDestroyButtonsToTheMover() {
        AgentModuleFixture fixture = new AgentModuleFixture("myrr");
        fixture.target.addOwnedUnitByID("cruiser");
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);

        assertOwnedAgentButton(
                MyrrAgent.offerButton(fixture.user, fixture.tile), fixture.user, "exhaustAgent_myrragent_myrr|101");
        assertOwnedAgentButton(MyrrAgent.cardsInfoButton(fixture.user), fixture.user, "exhaustAgent_myrragent");
        List<Button> destroy = MyrrAgent.destroyButtons(fixture.user, fixture.target, fixture.tile);
        assertThat(destroy).hasSize(2);
        assertOwnedBy(destroy.getFirst(), fixture.target);
        assertThat(destroy.getFirst().getCustomId()).endsWith("myrrAgentDestroy_myrr|101|ca");
    }

    @Test
    void myrrOnlyDeletesTheMoveOfferWhenUsed() {
        AgentModuleFixture fixture = new AgentModuleFixture("myrr");
        MyrrAgent myrr = new MyrrAgent();

        assertThat(myrr.resolve(fixture.use(myrr, new MyrrAgent.ShipsMoved(fixture.target, fixture.tile, true)))
                        .deletesPressedMessage())
                .isTrue();
        // The cards info message holds every other button too, so only the pressed button goes.
        assertThat(myrr.resolve(fixture.use(myrr, new MyrrAgent.ShipsMoved(fixture.target, fixture.tile, false)))
                        .deletesPressedMessage())
                .isFalse();
        assertThat(MyrrAgent.production(fixture.game, fixture.target, fixture.tile))
                .isZero();
    }

    @Test
    void kairnDecodesAnotherPlayerOrAPlanetToExploreNow() {
        AgentModuleFixture fixture = new AgentModuleFixture("kairn");
        KairnAgent kairn = new KairnAgent();
        Planet lodor = fixture.game.getUnitHolderFromPlanet(LODOR);

        assertThat(kairn.decode(fixture.game, fixture.user, TARGET))
                .contains(new KairnAgent.Explorer(fixture.target, null, null));
        assertThat(kairn.decode(fixture.game, fixture.user, LODOR + "|" + Constants.CULTURAL))
                .contains(new KairnAgent.Explorer(fixture.user, lodor, Constants.CULTURAL));
        assertThat(kairn.decode(fixture.game, fixture.user, "noplanet|" + Constants.CULTURAL))
                .isEmpty();
    }

    @Test
    void kairnNeedsAnEligibleTraitOrAnotherPlayerWithAnEligiblePlanet() {
        AgentModuleFixture fixture = new AgentModuleFixture("kairn");
        KairnAgent kairn = new KairnAgent();
        Planet lodor = fixture.game.getUnitHolderFromPlanet(LODOR);

        assertThat(kairn.unusableReason(
                        fixture.game, fixture.user, new KairnAgent.Explorer(fixture.user, lodor, Constants.FRONTIER)))
                .isPresent();
        assertThat(kairn.unusableReason(
                        fixture.game, fixture.user, new KairnAgent.Explorer(fixture.user, lodor, Constants.CULTURAL)))
                .isEmpty();
        assertThat(kairn.unusableReason(
                        fixture.game, fixture.user, new KairnAgent.Explorer(fixture.target, null, null)))
                .isPresent();
        assertThat(kairn.unusableReason(fixture.game, fixture.user, new KairnAgent.Explorer(fixture.user, null, null)))
                .isPresent();

        fixture.target.addPlanet(LODOR);
        assertThat(kairn.unusableReason(
                        fixture.game, fixture.user, new KairnAgent.Explorer(fixture.target, null, null)))
                .isEmpty();
    }

    @Test
    void kairnButtonsExhaustTheAgentForTheHolder() {
        AgentModuleFixture fixture = new AgentModuleFixture("kairn");
        fixture.target.addPlanet(LODOR);
        Planet lodor = fixture.game.getUnitHolderFromPlanet(LODOR);

        assertOwnedAgentButton(
                KairnAgent.interruptButton(fixture.user, lodor, Constants.CULTURAL),
                fixture.user,
                "exhaustAgent_kairnagent_lodor|cultural");
        List<Button> targets = KairnAgent.targetButtons(fixture.game, fixture.user);
        assertThat(targets).hasSize(1);
        assertOwnedAgentButton(targets.getFirst(), fixture.user, "exhaustAgent_kairnagent_pi_hacan");
        assertThat(KairnAgent.planetButtons(fixture.game, fixture.user, fixture.target))
                .allSatisfy(button -> assertOwnedBy(button, fixture.target));
    }

    @Test
    void kairnSendsThePlanetChoiceToTheChosenPlayer() {
        AgentModuleFixture fixture = new AgentModuleFixture("kairn");
        fixture.target.addPlanet(LODOR);
        KairnAgent kairn = new KairnAgent();

        AgentOutcome outcome = kairn.resolve(fixture.use(kairn, new KairnAgent.Explorer(fixture.target, null, null)));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isEqualTo(fixture.target);
            assertThat(message.buttons()).isNotEmpty();
        });
        assertThat(outcome.deletesPressedMessage()).isTrue();
    }

    @Test
    void arcanumDecodesTheTargetAndTheReturnedTechnology() {
        AgentModuleFixture fixture = new AgentModuleFixture("arcanum");
        ArcanumAgent arcanum = new ArcanumAgent();

        assertThat(arcanum.decode(fixture.game, fixture.user, TARGET + "|nm"))
                .contains(new ArcanumAgent.TechSwap(fixture.target, Mapper.getTech("nm")));
        assertThat(arcanum.decode(fixture.game, fixture.user, TARGET + "|notatech"))
                .isEmpty();
        assertThat(arcanum.decode(fixture.game, fixture.user, TARGET)).isEmpty();
    }

    @Test
    void arcanumNeedsATechnologyTheTargetOwnsAndCanReplace() {
        AgentModuleFixture fixture = new AgentModuleFixture("arcanum");
        ArcanumAgent arcanum = new ArcanumAgent();
        ArcanumAgent.TechSwap swap = new ArcanumAgent.TechSwap(fixture.target, Mapper.getTech("nm"));

        assertThat(arcanum.unusableReason(fixture.game, fixture.user, swap)).isPresent();

        fixture.target.addTech("nm");
        assertThat(ArcanumAgent.replacementButtons(fixture.game, fixture.target, "nm"))
                .isNotEmpty();
        assertThat(arcanum.unusableReason(fixture.game, fixture.user, swap)).isEmpty();
        assertThat(ArcanumAgent.returnTechButtons(fixture.game, fixture.user, fixture.target))
                .isNotEmpty()
                .allSatisfy(button -> assertOwnedBy(button, fixture.target));
    }

    @Test
    void arcanumReturnsTheTechnologyAndOffersAReplacement() {
        AgentModuleFixture fixture = new AgentModuleFixture("arcanum");
        fixture.target.addTech("nm");
        ArcanumAgent arcanum = new ArcanumAgent();

        AgentOutcome outcome =
                arcanum.resolve(fixture.use(arcanum, new ArcanumAgent.TechSwap(fixture.target, Mapper.getTech("nm"))));

        assertThat(fixture.target.hasTech("nm")).isFalse();
        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isEqualTo(fixture.target);
            assertThat(message.buttons()).isNotEmpty().allSatisfy(button -> assertOwnedBy(button, fixture.target));
        });
    }

    @Test
    void xytherisDecodesTheActivatingPlayer() {
        AgentModuleFixture fixture = new AgentModuleFixture("xytheris");
        XytherisAgent xytheris = new XytherisAgent();
        fixture.game.setStoredValue(XytherisLeadersHandler.MYRIX_AGENT_WINDOW, TARGET);

        assertThat(xytheris.decode(fixture.game, fixture.user, ""))
                .contains(new XytherisAgent.Activation(fixture.target, false));
        assertThat(xytheris.decode(fixture.game, fixture.user, TARGET))
                .contains(new XytherisAgent.Activation(fixture.target, true));
        assertThat(xytheris.decode(fixture.game, fixture.user, "nobody")).isEmpty();
    }

    @Test
    void xytherisNeedsTheActivationWindowAndAShip() {
        AgentModuleFixture fixture = new AgentModuleFixture("xytheris");
        XytherisAgent xytheris = new XytherisAgent();
        XytherisAgent.Activation activation = new XytherisAgent.Activation(fixture.target, true);
        fixture.game.setActivePlayerID(fixture.target.getUserID());

        assertThat(xytheris.unusableReason(fixture.game, fixture.user, activation))
                .isPresent();

        fixture.game.setStoredValue(XytherisLeadersHandler.MYRIX_AGENT_WINDOW, TARGET);
        assertThat(xytheris.unusableReason(fixture.game, fixture.user, activation))
                .isPresent();

        fixture.target.addOwnedUnitByID("cruiser");
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);
        assertThat(xytheris.unusableReason(fixture.game, fixture.user, activation))
                .isEmpty();
    }

    @Test
    void xytherisSendsShipChoicesToTheActivePlayerAndClosesTheWindow() {
        AgentModuleFixture fixture = new AgentModuleFixture("xytheris");
        XytherisAgent xytheris = new XytherisAgent();
        fixture.game.setStoredValue(XytherisLeadersHandler.MYRIX_AGENT_WINDOW, TARGET);
        fixture.target.addOwnedUnitByID("cruiser");
        fixture.tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, fixture.target.getColorID()), 1);

        AgentOutcome outcome =
                xytheris.resolve(fixture.use(xytheris, new XytherisAgent.Activation(fixture.target, true)));

        assertThat(outcome.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isEqualTo(fixture.target);
            assertThat(message.buttons()).singleElement().satisfies(button -> assertOwnedBy(button, fixture.target));
        });
        assertThat(outcome.deletesPressedMessage()).isTrue();
        assertThat(fixture.game.getStoredValue(XytherisLeadersHandler.MYRIX_AGENT_WINDOW))
                .isEmpty();
        assertThat(fixture.game.getStoredValue(XytherisLeadersHandler.MYRIX_AGENT_PENDING))
                .isEqualTo(TARGET);
        assertOwnedAgentButton(XytherisAgent.cardsInfoButton(fixture.user), fixture.user, "exhaustAgent_xytherisagent");
    }

    @Test
    void oblivionDecodesItsHolderOrAnotherPlayer() {
        AgentModuleFixture fixture = new AgentModuleFixture("oblivion");
        OblivionAgent oblivion = new OblivionAgent();

        assertThat(oblivion.decode(fixture.game, fixture.user, "")).contains(fixture.user);
        assertThat(oblivion.decode(fixture.game, fixture.user, TARGET)).contains(fixture.target);
        assertThat(oblivion.decode(fixture.game, fixture.user, "nobody")).isEmpty();
    }

    @Test
    void oblivionNeedsASystemWithoutPlanets() {
        AgentModuleFixture fixture = new AgentModuleFixture("oblivion");
        OblivionAgent oblivion = new OblivionAgent();

        assertThat(oblivion.unusableReason(fixture.game, fixture.user, fixture.target))
                .isPresent();

        // Tile 48 is an empty system.
        fixture.game.setTile(new Tile("48", "102"));
        assertThat(oblivion.unusableReason(fixture.game, fixture.user, fixture.target))
                .isEmpty();
    }

    @Test
    void oblivionSendsTheTokenChoiceToItsTarget() {
        AgentModuleFixture fixture = new AgentModuleFixture("oblivion");
        fixture.game.setTile(new Tile("48", "102"));
        OblivionAgent oblivion = new OblivionAgent();

        AgentOutcome onTarget = oblivion.resolve(fixture.use(oblivion, fixture.target));
        AgentOutcome onSelf = oblivion.resolve(fixture.use(oblivion, fixture.user));

        assertThat(onTarget.messages()).singleElement().satisfies(message -> {
            assertThat(message.recipient()).isEqualTo(fixture.target);
            assertThat(message.buttons()).singleElement().satisfies(button -> assertOwnedBy(button, fixture.target));
        });
        assertThat(onTarget.deletesPressedMessage()).isTrue();
        assertThat(onSelf.deletesPressedMessage()).isFalse();
        assertOwnedAgentButton(OblivionAgent.startTurnButton(fixture.user), fixture.user, "exhaustAgent_oblivionagent");
        assertThat(OblivionAgent.targetButtons(fixture.game, fixture.user))
                .singleElement()
                .satisfies(
                        button -> assertOwnedAgentButton(button, fixture.user, "exhaustAgent_oblivionagent_pi_hacan"));
    }
}
