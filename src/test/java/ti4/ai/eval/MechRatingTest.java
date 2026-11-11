package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.helpers.Units.UnitType;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.testUtils.BaseTi4Test;

class MechRatingTest extends BaseTi4Test {

    // Every mech of a base, Prophecy of Kings, codex (Keleres) or Thunder's Edge faction has its own rating, so none
    // of them silently takes the homebrew prior.
    @Test
    void everyOfficialFactionMechHasARating() {
        List<String> mechs = Mapper.getFactionsValues().stream()
                .filter(faction ->
                        faction.getSource() != null && faction.getSource().isTe())
                .map(FactionModel::getUnits)
                .flatMap(List::stream)
                .filter(unitId -> {
                    UnitModel unit = Mapper.getUnit(unitId);
                    return unit != null && unit.getUnitType() == UnitType.Mech;
                })
                .distinct()
                .toList();

        assertThat(mechs).isNotEmpty();
        assertThat(MechRating.ratedUnits()).containsAll(mechs);
    }

    @Test
    void homebrewMechFallsBackToThePrior() {
        assertThat(MechRating.ofUnit("absol_arborec_mech")).isEqualTo(MechRating.prior());
        assertThat(MechRating.ofUnit("arachian_mech")).isEqualTo(MechRating.prior());
    }

    @Test
    void theFactionlessMechTakesNothingFromArticlesOfWar() {
        assertThat(MechRating.ofUnit("mech").articlesStake()).isZero();
    }

    // Seating each faction by its bot alias checks the alias (Creuss is "ghost", Keleres has three) and that its
    // owned mech resolves to a rated unit rather than the prior.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "arborec",
                "argent",
                "cabal",
                "empyrean",
                "ghost",
                "hacan",
                "jolnar",
                "l1z1x",
                "letnev",
                "mahact",
                "mentak",
                "muaat",
                "naalu",
                "naaz",
                "nekro",
                "nomad",
                "saar",
                "sardakk",
                "sol",
                "titans",
                "winnu",
                "xxcha",
                "yin",
                "yssaril",
                "keleresa",
                "keleresm",
                "keleresx",
                "bastion",
                "deepwrought",
                "crimson",
                "ralnel",
                "firmament",
                "obsidian"
            })
    void everyOfficialFactionAliasResolvesToARatedMech(String faction) {
        Player seat = seat(faction);

        UnitModel mech = seat.getUnitByType(UnitType.Mech);
        assertThat(mech).as(faction).isNotNull();
        assertThat(MechRating.ratedUnits()).as(faction).contains(mech.getId());
    }

    // The user's notes: production, shield, cannon or cancel mechs work per mech on the board.
    @ParameterizedTest
    @ValueSource(strings = {"arborec", "xxcha", "deepwrought", "empyrean"})
    void mechsWithOnBoardAbilitiesCarryAPerMechTerm(String faction) {
        assertThat(MechRating.onBoardAbility(seat(faction))).as(faction).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"letnev", "sardakk", "hacan", "nekro"})
    void deployAndPlainMechsHaveNoOnBoardTerm(String faction) {
        assertThat(MechRating.onBoardAbility(seat(faction))).as(faction).isZero();
    }

    // Articles of War: strong mechs (Mahact, Deepwrought, Arborec, ...) lose far more than weak or narrow ones.
    @Test
    void strongMechsStakeMoreOnArticlesThanWeakOnes() {
        for (String strong : List.of("mahact", "deepwrought", "arborec", "xxcha", "letnev", "sardakk", "titans")) {
            for (String weak : List.of("hacan", "mentak", "nekro", "cabal", "jolnar")) {
                assertThat(MechRating.articlesStake(seat(strong)))
                        .as("%s vs %s", strong, weak)
                        .isGreaterThan(MechRating.articlesStake(seat(weak)));
            }
        }
    }

    // User ratings: Naalu's current (Thunder's Edge) mech is good, Crimson good, Ral Nel and Last Bastion okayish.
    @Test
    void userRatedMechsTakeTheUserRatings() {
        assertThat(seat("naalu").getUnitByType(UnitType.Mech).getId()).isEqualTo("naalu_mech_te");
        assertThat(MechRating.articlesStake(seat("naalu"))).isGreaterThan(MechRating.articlesStake(seat("ralnel")));
        assertThat(MechRating.articlesStake(seat("crimson"))).isGreaterThan(MechRating.articlesStake(seat("bastion")));
        assertThat(MechRating.abilityQuality(seat("deepwrought")))
                .isGreaterThan(MechRating.abilityQuality(seat("ralnel")));
    }

    // Rearmament Agreement: a home mech with production or cannon (Arborec, Deepwrought, Xxcha) is worth more than
    // a home defender (Mahact, Keleres, Ral Nel, Jol-Nar), which beats a mech that belongs at the front (Creuss).
    @Test
    void homeMechWorthFollowsWhatTheMechDoesAtHome() {
        for (String ability : List.of("arborec", "deepwrought", "xxcha")) {
            for (String guard : List.of("mahact", "keleresa", "ralnel", "jolnar")) {
                assertThat(MechRating.homeWorth(seat(ability))).isGreaterThan(MechRating.homeWorth(seat(guard)));
                assertThat(MechRating.homeWorth(seat(guard))).isGreaterThan(MechRating.homeWorth(seat("ghost")));
            }
        }
    }

    private static Player seat(String faction) {
        AiTestGame test = new AiTestGame();
        if ("nekro".equals(faction)) return test.nekro;
        if ("sol".equals(faction)) return test.sol;
        return test.addSeat("300000000000000009", faction, "green");
    }
}
