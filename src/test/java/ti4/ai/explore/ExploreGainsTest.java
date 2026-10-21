package ti4.ai.explore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// What a command token (Volatile Fuel Source) and a trade good (Core Mine) are worth to the AI, against the spare
// infantry that pays for them.
class ExploreGainsTest extends BaseTi4Test {

    private AiTestGame test;
    private Tile site;

    // Nekro holds Tequran with 2 infantry, so one of them is spare.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        test.nekroHome();
        site = test.place("28", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.nekro.addPlanet("tequran");
        test.units(site, "tequran", test.nekro, UnitType.Infantry, 2);
        test.aiIsActive("action");
    }

    private double spareInfantry() {
        ExploreOutlook outlook = new ExploreOutlook(test.game, test.nekro);
        return ExploreSite.onBoard(test.game, test.nekro, "tequran", outlook).infantryCost();
    }

    // A token is stored value across rounds, so it beats a spare infantry even when nothing in sight asks for it.
    @Test
    void aCommandTokenBeatsASpareInfantryWithoutAVisibleUse() {
        assertThat(ExploreGains.commandToken(test.game, test.nekro))
                .isGreaterThanOrEqualTo(ExploreValues.COMMAND_TOKEN);
        assertThat(ExploreGains.commandToken(test.game, test.nekro)).isGreaterThan(spareInfantry());
    }

    // Sol holds Technology and Nekro would follow it, but has no strategy token to do it with: the token is worth more
    // than one with nothing to follow.
    @Test
    void aCommandTokenIsWorthMoreWhenItEnablesAnotherFollow() {
        double idle = ExploreGains.commandToken(test.game, test.nekro);

        test.sol.addSC(7);
        test.nekro.setStrategicCC(0);

        assertThat(ExploreGains.commandToken(test.game, test.nekro)).isGreaterThan(idle);
    }

    @Test
    void aCommandTokenIsWorthNothingWithNoneLeftInReinforcements() {
        test.nekro.setTacticalCC(16);
        test.nekro.setFleetCC(16);
        test.nekro.setStrategicCC(16);

        assertThat(ExploreGains.commandToken(test.game, test.nekro)).isZero();
    }

    // A trade good is worth less than a token, but still more than an infantry that is truly spare.
    @Test
    void aTradeGoodBeatsASpareInfantry() {
        assertThat(ExploreGains.tradeGood(test.game, test.nekro)).isEqualTo(ExploreValues.TRADE_GOOD);
        assertThat(ExploreGains.tradeGood(test.game, test.nekro)).isGreaterThan(spareInfantry());
    }

    // Amass Wealth needs 3 resources, 3 influence and 3 trade goods: with 2 trade goods the next one closes the gap.
    @Test
    void aTradeGoodIsWorthMoreWhenItClosesATradeGoodGap() {
        test.nekro.addPlanet("torkan");
        test.nekro.setTg(2);
        double before = ExploreGains.tradeGood(test.game, test.nekro);
        test.game.getRevealedPublicObjectives().put("amass_wealth", 1);

        assertThat(ExploreGains.tradeGood(test.game, test.nekro)).isGreaterThan(before);
    }
}
