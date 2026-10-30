package ti4.ai.actioncards;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.prompt;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.actioncards.CardPlay.Stage;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.scoring.PaymentRules;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.testUtils.BaseTi4Test;

class StallCardFlowTest extends BaseTi4Test {

    private static final long SETTLED = NOW + 11_000L;
    private static final int HAND_INDEX = 412;
    private static final String OWNER = "FFCC_nekro_";

    private CardTable table;
    private MockedStatic<GameMessageManager> messages;

    @BeforeEach
    void setUp() {
        table = new CardTable();
        table.nekro.setTacticalCC(3);
        table.nekro.setFleetCC(3);
        table.nekro.setStrategicCC(2);
        messages = Mockito.mockStatic(GameMessageManager.class);
        messages.when(() -> GameMessageManager.getOne(anyString(), anyString()))
                .thenAnswer(call -> Optional.of(new GameMessage(
                        call.getArgument(1),
                        GameMessageType.ACTION_CARD,
                        new LinkedHashSet<>(List.of("nekro", "sol")),
                        0L)));
    }

    @AfterEach
    void tearDown() {
        messages.close();
    }

    private static String pressed(Optional<AiDecision> decision) {
        return decision.map(AiTestGame::pressedId).orElse("");
    }

    private String turnKey() {
        return "acPlay|turn:" + table.test.context().turnKey();
    }

    private Optional<CardPlay> play() {
        return table.test.memory.get(turnKey()).flatMap(CardPlay::decode);
    }

    private AiPrompt hand() {
        return prompt("hand", PromptSource.AI_THREAD, NOW, "ac_play_from_hand_" + HAND_INDEX, "cardsInfo");
    }

    private AiPrompt announcement(String title) {
        return prompt(
                "announce-" + title,
                PromptSource.PUBLIC,
                NOW,
                OWNER + "moveAlongAfterAllHaveReactedToAC_" + title,
                "no_sabotage",
                "sabotage_ac_" + title + "_nekro");
    }

    private AiPrompt resolve(String handler) {
        return prompt("resolve-" + handler, PromptSource.PUBLIC, NOW, OWNER + handler);
    }

    private AiPrompt owned(String... handlers) {
        String[] ids = java.util.Arrays.stream(handlers)
                .map(handler -> OWNER + handler)
                .toArray(String[]::new);
        return prompt("owned-" + handlers[0], PromptSource.PUBLIC, SETTLED, ids);
    }

    private AiPrompt mentioned(String... ids) {
        return AiTestGame.withContent(
                prompt("targets-" + ids[0], PromptSource.PUBLIC, SETTLED, ids),
                table.nekro.getRepresentationUnfogged() + ", please choose.");
    }

    private AiPrompt unmentioned(String... ids) {
        return prompt("anyone-" + ids[0], PromptSource.PUBLIC, SETTLED, ids);
    }

    private Optional<AiDecision> next(AiPrompt... prompts) {
        return ActionCardRules.continuePlay(table.test.contextAt(SETTLED, prompts));
    }

    private void playsAndResolves(String alias, String title, String resolveHandler) {
        table.nekro.setActionCard(alias, HAND_INDEX);
        assertThat(pressed(ActionCardRules.playBeforePassing(table.test.context(hand()))))
                .isEqualTo("ac_play_from_hand_" + HAND_INDEX);
        resolves(alias, title, resolveHandler);
    }

    // For a card worth less than its keep prior the AI would hold it, so the play is started as if it had been made.
    private void seededPlayResolves(String alias, String title, String resolveHandler) {
        String planned = AiActionCards.of(alias)
                .orElseThrow()
                .planFor(table.game, table.nekro)
                .orElseThrow()
                .target();
        table.nekro.setActionCard(alias, HAND_INDEX);
        table.test.memory.put(turnKey(), new CardPlay(Stage.PLAYED, NOW, NOW, alias, "", true, 0, planned).encode());
        resolves(alias, title, resolveHandler);
    }

    private void resolves(String alias, String title, String resolveHandler) {
        table.nekro.getActionCards().remove(alias);
        table.game.getGameStats().recordAcPlay(title, table.nekro);
        ActionCardRules.continuePlay(table.test.context(announcement(title)));
        ActionCardRules.continuePlay(table.test.context(announcement(title), resolve(resolveHandler)));
        assertThat(pressed(ActionCardRules.continuePlay(
                        table.test.contextAt(SETTLED, announcement(title), resolve(resolveHandler)))))
                .isEqualTo(OWNER + resolveHandler);
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);
    }

    private void isDone() {
        assertThat(play().map(CardPlay::stage)).contains(Stage.DONE);
    }

    // ---- Unexpected Action -------------------------------------------------------------------

    @Test
    void unexpectedActionLiftsTheTokenItDeclared() {
        table.test.nekroHome().addCC(ti4.model.Mapper.getCCID(table.nekro.getColor()));

        seededPlayResolves("unexpected", "Unexpected Action", "resolveUnexpected");

        assertThat(pressed(next(owned("removeCCFromBoard_unexpected_" + AiTestGame.HOME + "_nekro"))))
                .isEqualTo(OWNER + "removeCCFromBoard_unexpected_" + AiTestGame.HOME + "_nekro");
        isDone();
    }

    // ---- Signal Jamming ----------------------------------------------------------------------

    @Test
    void signalJammingChoosesThePlayerThenTheOriginThenTheSystem() {
        Tile next = table.system("40", "302");
        table.ships(table.nekro, next, UnitType.Cruiser, 1);
        table.solHome();
        table.ships(table.sol, table.game.getTileByPosition("304"), UnitType.Cruiser, 1);

        playsAndResolves("jamming", "Signal Jamming", "resolveSignalJammingStep1");

        assertThat(pressed(next(mentioned("signalJammingStep2_sol")))).isEqualTo("signalJammingStep2_sol");
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);
        assertThat(pressed(next(mentioned("signalJammingStep3_sol_302")))).isEqualTo("signalJammingStep3_sol_302");
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);
        assertThat(pressed(next(mentioned("signalJammingStep4_sol_302")))).isEqualTo("signalJammingStep4_sol_302");
        isDone();
    }

    // ---- Pirate Contract, Pirate Fleet, Mercenary Contract -------------------------------------

    @Test
    void pirateContractPostsThePiratesWhereItDeclaredWhicheverCopyItHolds() {
        table.system("40", "310");
        table.system("41", "311");

        playsAndResolves("piratecontract3", "Pirate Contract", "pirateContract");

        String chosen = pressed(next(owned("resolvePirateContract_310", "resolvePirateContract_311")));
        assertThat(chosen).startsWith(OWNER + "resolvePirateContract_31");
        isDone();
    }

    @Test
    void pirateFleetPaysThreeResourcesBeforeItPostsTheFleet() {
        table.solHome();
        table.points(table.sol, 8);
        table.system("40", "305");

        playsAndResolves("piratefleet", "Pirate Fleet", "pirateFleet");
        assertThat(PaymentRules.isPending(table.test.contextAt(SETTLED))).isTrue();

        assertThat(pressed(next(owned("resolvePirateFleet_305")))).isEqualTo(OWNER + "resolvePirateFleet_305");
        assertThat(PaymentRules.isPending(table.test.contextAt(SETTLED))).isTrue();
        isDone();
    }

    @Test
    void mercenaryContractPostsTheMercenariesOnThePlanetItDeclared() {
        table.points(table.sol, 8);
        table.system("19", "203");
        table.sol.addPlanet("wellon");
        table.nekro.setTg(4);

        seededPlayResolves("mercenarycontract", "Mercenary Contract", "teMercenaryContract_page0");

        assertThat(pressed(next(owned("resolveTeMercenaryContract_wellon"))))
                .isEqualTo(OWNER + "resolveTeMercenaryContract_wellon");
        isDone();
    }

    // ---- two steps: the player, then the planet --------------------------------------------------

    private void solHoldsWellonWith(UnitType type, int count) {
        table.solHome();
        table.system("19", "203");
        table.sol.addPlanet("wellon");
        table.test.units(table.game.getTileByPosition("203"), "wellon", table.sol, type, count);
    }

    private void picksThePlayerThenThePlanet(String stepOne, String stepTwo, String planetId) {
        assertThat(pressed(next(mentioned(stepOne + "sol")))).isEqualTo(stepOne + "sol");
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);
        assertThat(pressed(next(mentioned(stepTwo + "sol_jord", stepTwo + "sol_wellon"))))
                .isEqualTo(stepTwo + planetId);
        isDone();
    }

    @Test
    void crippleDefensesPicksThePlayerThenThePlanetWithPds() {
        solHoldsWellonWith(UnitType.Pds, 2);

        playsAndResolves("cripple", "Cripple Defenses", "resolveCrippleDefensesStep1");

        picksThePlayerThenThePlanet("crippleStep2_", "crippleStep3_", "sol_wellon");
    }

    @Test
    void crippleDefensesNeverPicksItsOwnPlanetEvenIfThatIsAllThatIsLeft() {
        solHoldsWellonWith(UnitType.Pds, 2);

        playsAndResolves("cripple", "Cripple Defenses", "resolveCrippleDefensesStep1");

        assertThat(pressed(next(mentioned("crippleStep2_nekro")))).isEmpty();
        assertThat(pressed(next(mentioned("crippleStep2_nekro", "crippleStep2_sol"))))
                .isEqualTo("crippleStep2_sol");
    }

    @Test
    void plaguePicksThePlayerThenThePlanetWithTheMostInfantry() {
        solHoldsWellonWith(UnitType.Infantry, 4);

        playsAndResolves("plague", "Plague", "resolvePlagueStep1");

        picksThePlayerThenThePlanet("plagueStep2_", "plagueStep3_", "sol_wellon");
    }

    @Test
    void unstablePlanetPicksThePlayerThenTheHazardousPlanet() {
        table.solHome();
        table.system("38", "204");
        table.sol.addPlanet("abyz");
        table.test.units(table.game.getTileByPosition("204"), "abyz", table.sol, UnitType.Infantry, 3);

        playsAndResolves("unstable", "Unstable Planet", "resolveUnstableStep1");

        assertThat(pressed(next(mentioned("unstableStep2_sol")))).isEqualTo("unstableStep2_sol");
        assertThat(pressed(next(mentioned("unstableStep3_sol_abyz")))).isEqualTo("unstableStep3_sol_abyz");
        isDone();
    }

    @Test
    void reactorMeltdownPicksThePlayerThenTheDock() {
        solHoldsWellonWith(UnitType.Spacedock, 1);

        playsAndResolves("meltdown", "Reactor Meltdown", "resolveReactorMeltdownStep1");

        assertThat(pressed(next(mentioned("reactorMeltdownStep2_sol")))).isEqualTo("reactorMeltdownStep2_sol");
        assertThat(pressed(next(mentioned("reactorMeltdownStep3_sol_203_wellon"))))
                .isEqualTo("reactorMeltdownStep3_sol_203_wellon");
        isDone();
    }

    // ---- one step: the system -------------------------------------------------------------------

    @Test
    void tacticalBombardmentPicksTheSystemItDeclared() {
        table.system("19", "203");
        table.sol.addPlanet("wellon");
        table.ships(table.nekro, table.game.getTileByPosition("203"), UnitType.Dreadnought, 1);

        playsAndResolves("tactical", "Tactical Bombardment", "resolveTacticalBombardmentStep1");

        assertThat(pressed(next(mentioned("tacticalBombardmentStep2_203")))).isEqualTo("tacticalBombardmentStep2_203");
        isDone();
    }

    @Test
    void luckyShotAimsAtTheSystemThenTheMostValuableShip() {
        Tile wellon = table.system("19", "203");
        table.nekro.addPlanet("wellon");
        table.ships(table.sol, wellon, UnitType.Cruiser, 1);
        table.ships(table.sol, wellon, UnitType.Destroyer, 1);

        playsAndResolves("lucky", "Lucky Shot", "startToLuckyShotAUnit_0");

        assertThat(pressed(next(owned("luckyShotIn_203")))).isEqualTo(OWNER + "luckyShotIn_203");
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);
        String cruiser = "luckyShotOn_203_cruiser_blue";
        String destroyer = "luckyShotOn_203_destroyer_blue";
        assertThat(pressed(next(owned(destroyer, cruiser)))).isEqualTo(OWNER + cruiser);
        isDone();
    }

    @Test
    void scuttleChoosesTheSystemTheShipThenFinishes() {
        Tile outpost = table.system("40", "310");
        table.ships(table.nekro, outpost, UnitType.Cruiser, 1);

        playsAndResolves("scuttle", "Scuttle", "startToScuttleAUnit_0");

        assertThat(pressed(next(owned("scuttleIn_310_0")))).isEqualTo(OWNER + "scuttleIn_310_0");
        assertThat(pressed(next(owned("scuttleOn_310_cruiser_0")))).isEqualTo(OWNER + "scuttleOn_310_cruiser_0");
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);
        assertThat(pressed(next(mentioned("startToScuttleAUnit_2", "endScuttle_2"))))
                .isEqualTo("endScuttle_2");
        isDone();
    }

    // ---- the table: relics, laws, breakthroughs -----------------------------------------------

    @Test
    void seizeArtifactPicksTheNeighbourThenTheFragment() {
        Tile next = table.system("40", "302");
        table.ships(table.sol, next, UnitType.Destroyer, 1);
        table.sol.addFragment("crf1");

        playsAndResolves("seize", "Seize Artifact", "resolveSeizeArtifactStep1");

        assertThat(pressed(next(mentioned("seizeArtifactStep2_sol_no")))).isEqualTo("seizeArtifactStep2_sol_no");
        assertThat(play().map(CardPlay::stage)).contains(Stage.TARGET);
        assertThat(pressed(next(unmentioned("seizeArtifactStep3_sol_crf1")))).isEqualTo("seizeArtifactStep3_sol_crf1");
        isDone();
    }

    @Test
    void repealLawRepealsTheLawItDeclared() {
        table.game.addLaw("shard_of_the_throne", "sol");
        table.game.addLaw("minister_war", "nekro");
        String shard = String.valueOf(table.game.getLaws().get("shard_of_the_throne"));
        String minister = String.valueOf(table.game.getLaws().get("minister_war"));

        playsAndResolves("repeal", "Repeal Law", "getRepealLawButtons");

        assertThat(pressed(next(unmentioned("repealLaw_" + minister, "repealLaw_" + shard))))
                .isEqualTo("repealLaw_" + shard);
        isDone();
    }

    @Test
    void repealLawNeverRepealsALawThatHelpsTheSeatEvenIfItIsAllThatIsOffered() {
        table.game.addLaw("shard_of_the_throne", "sol");
        table.game.addLaw("minister_war", "nekro");
        String minister = String.valueOf(table.game.getLaws().get("minister_war"));

        playsAndResolves("repeal", "Repeal Law", "getRepealLawButtons");

        assertThat(pressed(next(unmentioned("repealLaw_" + minister)))).isEmpty();
    }

    @Test
    void brillianceReadiesTheSpentPlanetItDeclared() {
        table.system("27", "203");
        table.nekro.addPlanet("newalbion");
        table.nekro.exhaustPlanet("newalbion");
        table.sol.addBreakthrough("solbt");

        playsAndResolves("brilliance", "Brilliance", "brilliance");

        assertThat(pressed(next(unmentioned("biostimsReady_planet_newalbion", "resolveBrillianceUnlock_sol"))))
                .isEqualTo("biostimsReady_planet_newalbion");
        isDone();
    }
}
