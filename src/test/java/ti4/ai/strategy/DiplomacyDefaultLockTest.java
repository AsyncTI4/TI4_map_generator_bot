package ti4.ai.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

// aiwatch9, round 3: Xxcha played Diplomacy to claim a planet with Peace Accords and picked no system, so no other
// player placed a token. The primary always has a system to choose, and a token in it keeps that player from
// activating it until it is removed, so an unthreatened seat still locks its most valuable system.
class DiplomacyDefaultLockTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile home;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        home = test.nekroHome();
    }

    @Test
    void locksTheMostValuableSystemEvenWhenNothingThreatensIt() {
        Tile colony = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("lodor");

        assertThat(DiplomacyUses.mostValuableOpenSystem(test.game, test.nekro))
                .hasValueSatisfying(position -> assertThat(position).isIn(home.getPosition(), colony.getPosition()));
    }

    @Test
    void locksNothingWhenEveryOtherSeatAlreadyHasATokenThere() {
        home.addCC(Mapper.getCCID(test.sol.getColor()));

        assertThat(DiplomacyUses.mostValuableOpenSystem(test.game, test.nekro)).isEmpty();
    }

    @Test
    void neverLocksMecatolRex() {
        test.place("18", "000");
        test.nekro.addPlanet("mr");
        home.addCC(Mapper.getCCID(test.sol.getColor()));

        assertThat(DiplomacyUses.mostValuableOpenSystem(test.game, test.nekro)).isEmpty();
    }
}
