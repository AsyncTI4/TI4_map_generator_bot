package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.service.map.FractureService;
import ti4.testUtils.BaseTi4Test;

class ShipSpeedTest extends BaseTi4Test {

    private final MoveLine line = new MoveLine(3);
    private final Game game = line.test.game;
    private final Player seat = line.test.nekro;

    private void unlock(Player player, String breakthrough) {
        player.addBreakthrough(breakthrough);
        player.setBreakthroughUnlocked(breakthrough, true);
    }

    private void activate(Player player, Tile target) {
        line.test.isActive(player, "action");
        game.setActiveSystem(target.getPosition());
        game.setStoredValue("currentActionSummary" + player.getFaction(), " Activated " + target.getPosition());
    }

    // LRR 59.2a: a ship starting in a nebula has a move value of 1, and other effects raise it from there.
    @Test
    void aNebulaCapsTheBaseAndBoostsAddOnTop() {
        Tile nebula = line.swap(1, "42");

        assertThat(ShipSpeed.base(game, seat, UnitType.Cruiser, nebula)).isEqualTo(1);
        assertThat(MoveBoost.withOneMore(seat, () -> ShipSpeed.of(game, seat, UnitType.Cruiser, nebula, null)))
                .isEqualTo(2);
        assertThat(ShipSpeed.base(game, seat, UnitType.Cruiser, line.at(2))).isEqualTo(2);
    }

    @Test
    void voidbornAndTheCircletIgnoreTheNebulaCap() {
        Tile nebula = line.swap(1, "42");
        seat.addAbility("voidborn");
        assertThat(ShipSpeed.base(game, seat, UnitType.Cruiser, nebula)).isEqualTo(2);

        Player other = line.test.sol;
        other.addRelic("circletofthevoid");
        assertThat(ShipSpeed.base(game, other, UnitType.Cruiser, nebula)).isEqualTo(2);
    }

    // Shared Research only lets ships move through nebulae; a ship starting in one still moves 1.
    @Test
    void sharedResearchKeepsTheNebulaCap() {
        Tile nebula = line.swap(1, "42");
        game.addLaw("shared_research", null);

        assertThat(ShipSpeed.base(game, seat, UnitType.Cruiser, nebula)).isEqualTo(1);
    }

    @Test
    void aShipThatCannotMoveStaysAtZeroWhateverTheBoosts() {
        assertThat(MoveBoost.withOneMore(seat, () -> ShipSpeed.of(game, seat, UnitType.Fighter, line.home, null)))
                .isZero();
    }

    // Ghost ruling I: Slipstream counts from home and from alpha or beta wormhole systems only, not delta or gamma.
    @Test
    void slipstreamCountsFromHomeAndAlphaOrBetaOnly() {
        seat.addAbility("slipstream");

        assertThat(ShipSpeed.originBonus(game, seat, line.home)).isEqualTo(1);
        assertThat(ShipSpeed.originBonus(game, seat, line.swap(1, "39"))).isEqualTo(1);
        assertThat(ShipSpeed.originBonus(game, seat, line.swap(2, "17"))).isZero();
        assertThat(ShipSpeed.originBonus(game, seat, line.swap(3, "82a"))).isZero();
    }

    @Test
    void alRaithCountsOnlyFromTheFracture() {
        FractureService.spawnFracture(null, game);
        unlock(seat, "cabalbt");

        assertThat(ShipSpeed.originBonus(game, seat, game.getTileByPosition("frac3")))
                .isEqualTo(1);
        assertThat(ShipSpeed.originBonus(game, seat, line.at(1))).isZero();
    }

    // Crimson ruling I: the Resonance Generator keeps working while the card is exhausted.
    @Test
    void resonanceGeneratorCountsFromHomeAndBreachesWhileExhausted() {
        unlock(seat, "crimsonbt");
        seat.setBreakthroughExhausted("crimsonbt", true);
        line.at(2).addToken(Constants.TOKEN_BREACH_ACTIVE, "space");

        assertThat(ShipSpeed.originBonus(game, seat, line.home)).isEqualTo(1);
        assertThat(ShipSpeed.originBonus(game, seat, line.at(2))).isEqualTo(1);
        assertThat(ShipSpeed.originBonus(game, seat, line.at(1))).isZero();
    }

    // Empyrean ruling II: the active system itself being an anomaly does not count, only a neighbour.
    @Test
    void aetherstreamNeedsAnAnomalyBesideTheTarget() {
        seat.addTech("as");
        assertThat(ShipSpeed.arrivalBonus(game, seat, line.at(1))).isZero();

        line.swap(3, "42");
        assertThat(ShipSpeed.arrivalBonus(game, seat, line.at(2))).isEqualTo(1);
        assertThat(ShipSpeed.arrivalBonus(game, seat, line.at(3))).isZero();
    }

    @Test
    void callOfTheVoidCountsIntoTheFracture() {
        FractureService.spawnFracture(null, game);
        game.setCallOfTheVoidMode(true);

        assertThat(ShipSpeed.arrivalBonus(game, seat, game.getTileByPosition("frac4")))
                .isEqualTo(1);
        assertThat(ShipSpeed.arrivalBonus(game, seat, line.at(1))).isZero();
    }

    // The engine leaves flankspeedBoost set until the next activation, so it counts only for the player who played
    // it, in the system they activated, during that activation.
    @Test
    void aPlayedFlankSpeedCountsOnlyInItsOwnActivation() {
        Tile target = line.at(2);
        game.setStoredValue(ShipSpeed.FLANK_SPEED_BOOST, "1");
        assertThat(ShipSpeed.flankSpeed(game, seat, target)).isFalse();

        activate(seat, target);
        assertThat(ShipSpeed.flankSpeed(game, seat, target)).isTrue();
        assertThat(ShipSpeed.flankSpeed(game, seat, line.at(1))).isFalse();
        assertThat(ShipSpeed.targetBonus(game, seat, target)).isEqualTo(1);
    }

    @Test
    void aFlankSpeedFromThePreviousPlayersTurnDoesNotCount() {
        Tile target = line.at(2);
        game.setStoredValue(ShipSpeed.FLANK_SPEED_BOOST, "1");
        activate(seat, target);
        activate(line.test.sol, target);

        assertThat(ShipSpeed.flankSpeed(game, seat, target)).isFalse();
    }

    @Test
    void flankSpeedPlannedThroughMoveBoostAddsOne() {
        assertThat(MoveBoost.withOneMore(seat, () -> ShipSpeed.targetBonus(game, seat, line.at(1))))
                .isEqualTo(1);
        assertThat(MoveBoost.withOneMore(seat, () -> ShipSpeed.targetBonus(game, line.test.sol, line.at(1))))
                .isZero();
    }

    // Letnev ruling II: a Fighter II can set the Gravleash pace, a Fighter I cannot.
    @Test
    void fighterTwoJoinsThePaceSetters() {
        assertThat(ShipSpeed.paceSetters(game, seat)).doesNotContain(UnitType.Fighter);

        seat.removeOwnedUnitByID("fighter");
        seat.addOwnedUnitByID("fighter2");
        assertThat(ShipSpeed.paceSetters(game, seat)).contains(UnitType.Fighter);
    }

    @Test
    void theNebulaCapIsDecidedPerOriginNotPerTarget() {
        Tile nebula = line.swap(2, "42");

        assertThat(ShipSpeed.of(game, seat, UnitType.Cruiser, line.at(1), nebula))
                .isEqualTo(2);
        assertThat(AiTestGame.HOME).isEqualTo(line.home.getPosition());
    }
}
