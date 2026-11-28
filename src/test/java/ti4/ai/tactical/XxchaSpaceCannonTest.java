package ti4.ai.tactical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static ti4.ai.AiTestGame.NOW;
import static ti4.ai.AiTestGame.pressedId;
import static ti4.ai.AiTestGame.prompt;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiDecision;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.AiPrompt.PromptSource;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.testUtils.BaseTi4Test;

// Loncara Ssodu and Indomitus carry a deep space cannon: they fire at ships in the systems next to them, the way a
// second-tier PDS does. The engine offers the Space Cannon Offense window to whoever covers the activated system.
class XxchaSpaceCannonTest extends BaseTi4Test {

    private static final double EXACT = 1e-9;

    private AiTestGame test;
    private Player xxcha;
    private Tile activated;
    private Tile beside;
    private String fire;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        xxcha = test.addSeat("200000000000000007", "xxcha", "green");
        String position = AiTestGame.neighbourOf(AiTestGame.HOME);
        activated = test.place("26", position);
        beside = test.place("25", neighbourBeyond(position));
        test.units(activated, "space", test.nekro, UnitType.Destroyer, 1);
        test.units(activated, "space", test.sol, UnitType.Cruiser, 2);
        test.game.setActiveSystem(position);
        test.isActive(test.sol, "action");
        fire = "combatRoll_" + position + "_space_spacecannonoffence";
    }

    private static String neighbourBeyond(String position) {
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .findFirst()
                .orElseThrow();
    }

    private Optional<AiDecision> decide(long created, AiPrompt... prompts) {
        return CombatRules.next(test.contextFor(xxcha, Set.of(), created, prompts));
    }

    private AiPrompt offer(String id, long created) {
        return prompt(id, PromptSource.PUBLIC, created, fire);
    }

    @Test
    void firesTheFlagshipsSpaceCannonAtShipsInTheNextSystem() {
        test.units(beside, "space", xxcha, UnitType.Flagship, 1);

        assertThat(pressedId(decide(NOW, offer("window", NOW)).orElseThrow())).isEqualTo(fire);
    }

    @Test
    void firesAnIndomitusOnAPlanetNextToTheActivatedSystem() {
        test.units(beside, "quann", xxcha, UnitType.Mech, 1);

        assertThat(pressedId(decide(NOW, offer("window", NOW)).orElseThrow())).isEqualTo(fire);
    }

    @Test
    void cannotReachAFlagshipTwoSystemsAway() {
        test.units(test.place("27", farFromTheActivatedSystem()), "space", xxcha, UnitType.Flagship, 1);

        assertThat(decide(NOW, offer("window", NOW)).filter(AiDecision.Press.class::isInstance))
                .isEmpty();
    }

    // The engine re-offers the window in the combat thread and again on each later tactical action; the seat fires
    // once per window and fires again on the next turn.
    @Test
    void firesOncePerWindowAndAgainOnTheNextTurn() {
        test.units(beside, "space", xxcha, UnitType.Flagship, 1);
        AiPrompt first = offer("first", NOW);

        decide(NOW, first);
        Optional<AiDecision> again = decide(NOW, first, offer("reoffered", NOW + 500));
        assertThat(again.filter(AiDecision.Press.class::isInstance)).isEmpty();

        test.game.setLastActivePlayerChange(new java.util.Date(NOW + 60_000));
        Optional<AiDecision> nextTurn = decide(NOW + 61_000, offer("next", NOW + 61_000));
        assertThat(pressedId(nextTurn.orElseThrow())).isEqualTo(fire);
    }

    // On its own tactical action the flagship in the neighbouring system shoots at the defenders it activates.
    @Test
    void firesTheFlagshipWhenItIsTheActivePlayer() {
        test.units(beside, "space", xxcha, UnitType.Flagship, 1);
        test.isActive(xxcha, "action");

        assertThat(pressedId(decide(NOW, offer("window", NOW)).orElseThrow())).isEqualTo(fire);
    }

    // Space Cannon Defense: a mech holding a planet fires at the landing even without a PDS.
    @Test
    void firesIndomitusInSpaceCannonDefence() {
        test.units(activated, "lodor", xxcha, UnitType.Mech, 1);
        String defence = "combatRoll_" + activated.getPosition() + "_lodor_spacecannondefence";
        AiPrompt window = prompt("defence", PromptSource.COMBAT_THREAD, NOW, defence);

        assertThat(pressedId(decide(NOW, window).orElseThrow())).isEqualTo(defence);
    }

    @Test
    void doesNotFireSpaceCannonDefenceFromAPlanetWithoutCannons() {
        test.units(activated, "lodor", xxcha, UnitType.Infantry, 2);
        String defence = "combatRoll_" + activated.getPosition() + "_lodor_spacecannondefence";
        AiPrompt window = prompt("defence", PromptSource.COMBAT_THREAD, NOW, defence);

        assertThat(decide(NOW, window).filter(AiDecision.Press.class::isInstance))
                .isEmpty();
    }

    // The threat model counts the flagship and the mechs next door as cannon cover: three dice on 5 and one on 8.
    @Test
    void countsTheXxchaFleetAsCannonCoverOfAdjacentSystems() {
        test.units(beside, "space", xxcha, UnitType.Flagship, 1);
        test.units(beside, "quann", xxcha, UnitType.Mech, 1);

        assertThat(BattleMath.cannonFire(test.game, activated, xxcha, null)).isCloseTo(3 * 0.6 + 0.3, within(EXACT));
        assertThat(BattleMath.expectedCannonHits(test.game, test.sol, activated))
                .isEqualTo(2);
    }

    @Test
    void doesNotCountCannonOutOfReach() {
        test.units(test.place("27", farFromTheActivatedSystem()), "space", xxcha, UnitType.Flagship, 1);

        assertThat(BattleMath.cannonFire(test.game, activated, xxcha, null)).isZero();
    }

    private String farFromTheActivatedSystem() {
        return MapPositionMapper.getAdjacentTilePositions(beside.getPosition()).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !AiTestGame.HOME.equals(candidate)
                        && !activated.getPosition().equals(candidate)
                        && !MapPositionMapper.getAdjacentTilePositions(activated.getPosition())
                                .contains(candidate))
                .findFirst()
                .orElseThrow();
    }

    // The defence forecast for a system gets the Xxcha flagship next door as extra dice before the first round.
    @Test
    void theFlagshipNextDoorLowersTheChanceOfAnAttackSucceeding() {
        test.sol.setTacticalCC(3);
        test.sol.setFleetCC(6);
        test.units(beside, "space", xxcha, UnitType.Cruiser, 2);
        test.units(activated, "space", test.sol, UnitType.Cruiser, 2);
        DefenseOutlook defense = new DefenseOutlook(test.game, xxcha);
        double without = attackSucceeds(defense);

        test.units(test.place("27", otherNeighbourOfTheDefendedSystem()), "space", xxcha, UnitType.Flagship, 1);
        double with = attackSucceeds(new DefenseOutlook(test.game, xxcha));

        assertThat(with).isLessThan(without);
    }

    private double attackSucceeds(DefenseOutlook defense) {
        return defense.chances(test.sol, beside, IncomingForces.Horizon.IMMEDIATE, defense.defendersAt(beside))
                .space();
    }

    private String otherNeighbourOfTheDefendedSystem() {
        return MapPositionMapper.getAdjacentTilePositions(beside.getPosition()).stream()
                .filter(candidate -> !"x".equals(candidate)
                        && !AiTestGame.HOME.equals(candidate)
                        && !activated.getPosition().equals(candidate)
                        && test.game.getTileByPosition(candidate) == null)
                .findFirst()
                .orElseThrow();
    }

    // Articles of War switches off the Indomitus space cannon in the engine, so the model leaves it out too.
    @Test
    void leavesIndomitusOutUnderArticlesOfWar() {
        test.units(beside, "quann", xxcha, UnitType.Mech, 1);
        assertThat(BattleMath.cannonFire(test.game, activated, xxcha, null)).isCloseTo(0.3, within(EXACT));

        test.game.addLaw("articles_war", null);

        assertThat(BattleMath.cannonFire(test.game, activated, xxcha, null)).isZero();
    }
}
