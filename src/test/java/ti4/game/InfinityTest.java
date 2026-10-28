package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.model.Mapper;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.MoveUnitService;
import ti4.service.unit.RemoveUnitService;
import ti4.testUtils.BaseTi4Test;

class InfinityTest extends BaseTi4Test {

    private Game game;
    private Player letnev;
    private Player hacan;
    private Tile tile;
    private Tile otherTile;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("infinity-test");
        game.setCcNPlasticLimit(false);
        tile = new Tile("18", "000");
        otherTile = new Tile("20", "102");
        game.setTile(tile);
        game.setTile(otherTile);
        letnev = game.addPlayer("111", "letnev");
        letnev.setFaction(game, "letnev");
        letnev.setColor("black");
        letnev.setUnitsOwned(new HashSet<>(Mapper.getFaction("letnev").getUnits()));
        hacan = game.addPlayer("222", "hacan");
        hacan.setFaction(game, "hacan");
        hacan.setColor("yellow");
    }

    private UnitKey infantry(Player player) {
        return Units.getUnitKey(UnitType.Infantry, player.getColorID());
    }

    @Test
    void spendingInfiniteTradeGoodsLeavesThemInfinite() {
        letnev.setInfiniteTg(true);

        letnev.setTg(letnev.getTg() - 6);

        assertThat(letnev.isInfiniteTg()).isTrue();
        assertThat(letnev.getTgRepresentation()).isEqualTo("∞");
        assertThat(letnev.gainTG(-3)).isEqualTo("(∞ -> ∞)");
    }

    @Test
    void infiniteTradeGoodsCoverAnyCost() {
        letnev.setInfiniteTg(true);

        assertThat(letnev.getTg()).isGreaterThanOrEqualTo(SanityLimits.TRADE_GOODS);
    }

    @Test
    void addingInfinityInfantryMakesAnInfiniteStack() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "infinity infantry mecatol");

        UnitHolder mecatol = tile.getUnitHolders().get("mr");
        assertThat(mecatol.isInfinite(infantry(letnev))).isTrue();
        assertThat(mecatol.getPlayersUnitListOnHolder(letnev)).isEqualTo("infinity gf");
    }

    @ParameterizedTest
    @ValueSource(strings = {"infinity", "infinite", "inf", "∞", "INFINITE"})
    void everySpellingOfInfinityMakesAnInfiniteStack(String spelling) {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), spelling + " infantry mecatol");

        assertThat(tile.getUnitHolders().get("mr").isInfinite(infantry(letnev))).isTrue();
    }

    @Test
    void infShorthandWorksWithAnyInfantryAlias() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "inf inf mecatol");

        assertThat(tile.getUnitHolders().get("mr").isInfinite(infantry(letnev))).isTrue();
    }

    // "inf" is also the infantry alias, so "inf mecatol" must keep meaning one infantry on Mecatol.
    @Test
    void infOnItsOwnIsStillOneInfantry() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "inf mecatol");

        UnitHolder mecatol = tile.getUnitHolders().get("mr");
        assertThat(mecatol.isInfinite(infantry(letnev))).isFalse();
        assertThat(mecatol.getUnitCount(infantry(letnev))).isEqualTo(1);
    }

    @Test
    void anInfantryCountStillParses() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "3 inf mecatol");

        assertThat(tile.getUnitHolders().get("mr").getUnitCount(infantry(letnev)))
                .isEqualTo(3);
    }

    @Test
    void onlyInfantryCanBeInfinite() {
        AddUnitService.addUnits(null, tile, game, letnev.getColor(), "infinity mech mecatol");

        assertThat(tile.getUnitHolders().get("mr").getUnitCount(Units.getUnitKey(UnitType.Mech, letnev.getColorID())))
                .isZero();
    }

    @Test
    void destroyingInfantryFromAnInfiniteStackLeavesItInfinite() {
        UnitHolder mecatol = tile.getUnitHolders().get("mr");
        mecatol.makeInfinite(infantry(letnev));
        int before = mecatol.getUnitCount(infantry(letnev));

        List<Integer> removed = mecatol.removeUnit(infantry(letnev), 40);

        assertThat(removed.stream().mapToInt(Integer::intValue).sum()).isEqualTo(40);
        assertThat(mecatol.isInfinite(infantry(letnev))).isTrue();
        assertThat(mecatol.getUnitCount(infantry(letnev))).isEqualTo(before);
    }

    @Test
    void removingInfinityInfantryClearsTheStack() {
        tile.getUnitHolders().get("mr").makeInfinite(infantry(letnev));

        RemoveUnitService.removeUnits(null, tile, game, letnev.getColor(), "infinity infantry mecatol");

        UnitHolder mecatol = tile.getUnitHolders().get("mr");
        assertThat(mecatol.isInfinite(infantry(letnev))).isFalse();
        assertThat(mecatol.getUnitCount(infantry(letnev))).isZero();
    }

    @Test
    void movingSomeInfantryOffAnInfiniteStackLeavesItInfinite() {
        tile.getSpaceUnitHolder().makeInfinite(infantry(letnev));

        MoveUnitService.moveUnits(null, tile, game, letnev.getColor(), "3 infantry", otherTile, Constants.SPACE);

        assertThat(tile.getSpaceUnitHolder().isInfinite(infantry(letnev))).isTrue();
        assertThat(otherTile.getSpaceUnitHolder().getUnitCount(infantry(letnev)))
                .isEqualTo(3);
        assertThat(otherTile.getSpaceUnitHolder().isInfinite(infantry(letnev))).isFalse();
    }

    @Test
    void movingTheWholeInfiniteStackCarriesInfinity() {
        tile.getSpaceUnitHolder().makeInfinite(infantry(letnev));

        MoveUnitService.moveUnits(null, tile, game, letnev.getColor(), "infinity infantry", otherTile, Constants.SPACE);

        assertThat(tile.getSpaceUnitHolder().isInfinite(infantry(letnev))).isFalse();
        assertThat(otherTile.getSpaceUnitHolder().isInfinite(infantry(letnev))).isTrue();
    }

    @Test
    void addingToAnInfiniteStackKeepsItInfinite() {
        UnitHolder mecatol = tile.getUnitHolders().get("mr");
        mecatol.makeInfinite(infantry(letnev));

        mecatol.addUnit(infantry(letnev), 5);

        assertThat(mecatol.isInfinite(infantry(letnev))).isTrue();
    }

    @Test
    void anInfiniteStackOnlyBelongsToItsOwner() {
        tile.getUnitHolders().get("mr").makeInfinite(infantry(letnev));

        assertThat(tile.getUnitHolders().get("mr").hasInfiniteUnits(hacan.getColorID()))
                .isFalse();
        assertThat(tile.getUnitHolders().get("mr").hasInfiniteUnits(letnev.getColorID()))
                .isTrue();
    }
}
