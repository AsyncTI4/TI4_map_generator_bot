package ti4.ai.sardakk;

import static org.assertj.core.api.Assertions.assertThat;
import static ti4.ai.AiTestGame.NOW;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.ai.sardakk.SardakkHeroRules.Case;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Sh'val, Harbinger skips the space combat: the ships go back to the reinforcements, the ground forces land. The seat
// activates Sol's home system with a carrier and infantry against Sol's dreadnoughts.
class SardakkHeroRulesTest extends BaseTi4Test {

    private static final String HERO_BUTTON = "FFCC_nekro_purgeSardakkHero";

    private AiTestGame test;
    private Tile solHome;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.game.setRound(3);
        test.nekroHome();
        solHome = test.solHomeBeside(AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addLeader("sardakkhero");
        test.nekro.unsafeGetLeader("sardakkhero").setLocked(false);
        test.aiIsActive("action");
        test.game.setActiveSystem(solHome.getPosition());
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + solHome.getPosition() + " (Jord).");
    }

    private void scene(int ourCarriers, int ourDreadnoughts, int ourInfantry, int theirDreadnoughts, int defenders) {
        place(UnitType.Carrier, test.nekro, "space", ourCarriers);
        place(UnitType.Dreadnought, test.nekro, "space", ourDreadnoughts);
        place(UnitType.Infantry, test.nekro, "space", ourInfantry);
        place(UnitType.Dreadnought, test.sol, "space", theirDreadnoughts);
        place(UnitType.Infantry, test.sol, "jord", defenders);
    }

    private void place(UnitType type, ti4.game.Player player, String holder, int count) {
        if (count > 0) test.units(solHome, holder, player, type, count);
    }

    private AiPrompt landing() {
        return AiTestGame.prompt(
                "landing", PromptSource.PUBLIC, NOW, "FFCC_nekro_doneLanding_" + solHome.getPosition(), HERO_BUTTON);
    }

    private Optional<AiDecision> next() {
        return SardakkHeroRules.next(test.context(landing()));
    }

    private String pressed() {
        return next().map(AiTestGame::pressedId).orElse("");
    }

    // One carrier cannot beat three dreadnoughts, but four infantry beat Sol's single defender on its home planet.
    @Test
    void skipsALostSpaceCombatToTakeAnEnemyHomePlanet() {
        scene(1, 0, 4, 3, 1);

        assertThat(pressed()).isEqualTo(HERO_BUTTON);

        Optional<TacticalPlan> plan = TacticalRules.plan(test.context(landing()), solHome.getPosition());
        assertThat(plan).isPresent();
        assertThat(plan.get().landings()).containsEntry("jord", 4);
    }

    // Once the hero has taken the carrier off the board, the tactical rules wait for no space combat: they land the
    // infantry still in orbit on the planet the hero chose.
    @Test
    void landsOnTheChosenPlanetOnceTheShipsAreGone() {
        scene(1, 0, 4, 3, 1);
        assertThat(pressed()).isEqualTo(HERO_BUTTON);
        solHome.removeUnit("space", ti4.helpers.Units.getUnitKey(UnitType.Carrier, test.nekro.getColor()), 1);
        AiPrompt landing = AiTestGame.prompt(
                "landing2",
                PromptSource.PUBLIC,
                NOW,
                "FFCC_nekro_doneLanding_" + solHome.getPosition(),
                "FFCC_nekro_landUnits_" + solHome.getPosition() + "_2gf_jord_" + test.nekro.getColor());

        Optional<AiDecision> next = TacticalRules.continueAction(test.context(landing));

        assertThat(next.map(AiTestGame::pressedId).orElse(""))
                .isEqualTo("FFCC_nekro_landUnits_" + solHome.getPosition() + "_2gf_jord_" + test.nekro.getColor());
    }

    @Test
    void keepsTheHeroWhenTheShipsWinTheSpaceCombat() {
        scene(1, 4, 4, 1, 1);

        assertThat(pressed()).isEmpty();
    }

    @Test
    void keepsTheHeroWhenTheLandingWouldLose() {
        scene(1, 0, 1, 3, 8);

        assertThat(pressed()).isEmpty();
    }

    @Test
    void keepsTheHeroWithoutAnEnemyFleetToSkip() {
        scene(1, 0, 4, 0, 1);

        assertThat(pressed()).isEmpty();
    }

    @Test
    void keepsTheHeroWhileItIsLocked() {
        scene(1, 0, 4, 3, 1);
        test.nekro.unsafeGetLeader("sardakkhero").setLocked(true);

        assertThat(pressed()).isEmpty();
    }

    // A planet nobody holds and no objective wants is not worth a hero and the whole fleet.
    @Test
    void keepsTheHeroForAPlanetNotWorthAPoint() {
        Tile lodor = test.place("26", otherNeighbourOfHome());
        test.units(lodor, "space", test.nekro, UnitType.Carrier, 1);
        test.units(lodor, "space", test.nekro, UnitType.Infantry, 4);
        test.units(lodor, "space", test.sol, UnitType.Dreadnought, 3);
        test.game.setActiveSystem(lodor.getPosition());
        test.game.setStoredValue("currentActionSummarynekro", " Activated " + lodor.getPosition() + " (Lodor).");

        assertThat(SardakkHeroRules.next(test.context(landingAt(lodor)))).isEmpty();
    }

    private AiPrompt landingAt(Tile tile) {
        return AiTestGame.prompt(
                "landing", PromptSource.PUBLIC, NOW, "FFCC_nekro_doneLanding_" + tile.getPosition(), HERO_BUTTON);
    }

    private String otherNeighbourOfHome() {
        return ti4.model.MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position) && test.game.getTileByPosition(position) == null)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void weighsTheSpaceFightTheLandingAndWhatIsAtStake() {
        double vp = SardakkHeroRules.VP;

        assertThat(new Case(0.05, 3, 3, 0.9, 2 * vp).worthPlaying()).isTrue();
        assertThat(new Case(0.05, 3, 3, 0.9, 0.5 * vp).worthPlaying())
                .as("a stake under a point")
                .isFalse();
        assertThat(new Case(0.05, 3, 3, 0.5, 2 * vp).worthPlaying())
                .as("a coin-flip landing")
                .isFalse();
        assertThat(new Case(0.7, 3, 1, 0.9, 2 * vp).worthPlaying())
                .as("ships likely win")
                .isFalse();
    }

    // A fleet worth 40 that would survive the fight at a loss of only 5 is not thrown away for a one-point prize, but
    // the same fleet that would be wiped out anyway is.
    @Test
    void countsWhatTheFleetWouldCostBeyondTheFight() {
        double vp = SardakkHeroRules.VP;

        assertThat(new Case(0.3, 40, 5, 0.9, 1.2 * vp).worthPlaying()).isFalse();
        assertThat(new Case(0.3, 40, 40, 0.9, 1.2 * vp).worthPlaying()).isTrue();
    }

    @Test
    void valuesMecatolWithTheCustodiansAndAnEnemyHomeAbovePlainPlanets() {
        Tile rex = test.place("18", "000");
        rex.addToken(Constants.CUSTODIAN_TOKEN_PNG, "mr");
        test.nekro.setTg(10);
        var mecatol = (ti4.game.Planet) rex.getUnitHolders().get("mr");
        var jord = (ti4.game.Planet) solHome.getUnitHolders().get("jord");

        double mecatolStake = SardakkHeroRules.stake(test.game, test.nekro, rex, mecatol);
        double homeStake = SardakkHeroRules.stake(test.game, test.nekro, solHome, jord);

        assertThat(mecatolStake).isGreaterThan(SardakkHeroRules.VP);
        assertThat(homeStake).isGreaterThanOrEqualTo(SardakkHeroRules.ENEMY_HOME_VALUE);
    }
}
