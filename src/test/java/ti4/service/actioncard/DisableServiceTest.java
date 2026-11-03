package ti4.service.actioncard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.tile.PlanetShields;
import ti4.model.Mapper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.testUtils.BaseTi4Test;

// Disable: during the invasion the Disable player's opponents' PDS lose PLANETARY SHIELD and SPACE CANNON.
class DisableServiceTest extends BaseTi4Test {

    private Game game;
    private Player sol;
    private Player xxcha;
    private Tile tile;
    private UnitHolder lodor;
    private UnitModel xxchaPds;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newGameSetup();
        game.setName("disable-test");
        sol = game.addPlayer("sol-id", "sol");
        sol.setFaction(game, "sol");
        sol.setColor("blue");
        xxcha = game.addPlayer("xxcha-id", "xxcha");
        xxcha.setFaction(game, "xxcha");
        xxcha.setColor("green");
        tile = new Tile("26", "201");
        game.setTile(tile);
        game.setActiveSystem("201");
        xxcha.addOwnedUnitByID("pds");
        xxcha.addPlanet("lodor");
        lodor = tile.getUnitHolderFromPlanet("lodor");
        UnitKey pds = new UnitKey(UnitType.Pds, Mapper.getColorID(xxcha.getColor()));
        lodor.addUnit(pds, 1);
        xxchaPds = xxcha.getUnitFromUnitKey(pds);
    }

    private boolean xxchaCanFireSpaceCannonDefence() {
        return CombatRollService.checkIfUnitsOfType(
                xxcha, game, null, tile, "lodor", CombatRollType.SpaceCannonDefence);
    }

    @Test
    void anOpponentsPdsIsFoundInTheActiveSystem() {
        assertThat(DisableService.hasOpposingPds(game, tile, sol)).isTrue();
    }

    @Test
    void yourOwnPdsDoesNotCount() {
        assertThat(DisableService.hasOpposingPds(game, tile, xxcha)).isFalse();
    }

    @Test
    void aSystemWithoutPdsHasNothingToDisable() {
        lodor.removeUnit(new UnitKey(UnitType.Pds, Mapper.getColorID(xxcha.getColor())), 1);

        assertThat(DisableService.hasOpposingPds(game, tile, sol)).isFalse();
    }

    @Test
    void thePdsShootsAndShieldsUntilDisableIsResolved() {
        assertThat(xxchaCanFireSpaceCannonDefence()).isTrue();
        assertThat(PlanetShields.shouldHaveShield(lodor, game)).isTrue();
    }

    @Test
    void disableStopsTheOpponentsSpaceCannonAndShield() {
        DisableService.activate(game, tile, sol);

        assertThat(xxchaCanFireSpaceCannonDefence()).isFalse();
        assertThat(PlanetShields.shouldHaveShield(lodor, game)).isFalse();
        assertThat(DisableService.losesPdsAbilities(game, tile, xxcha, xxchaPds))
                .isTrue();
    }

    @Test
    void theDisablePlayersOwnPdsIsUnaffected() {
        DisableService.activate(game, tile, sol);

        assertThat(DisableService.losesPdsAbilities(game, tile, sol, xxchaPds)).isFalse();
    }

    @Test
    void disableOnlyAffectsTheSystemItWasResolvedIn() {
        DisableService.activate(game, tile, sol);
        Tile elsewhere = new Tile("25", "202");

        assertThat(DisableService.losesPdsAbilities(game, elsewhere, xxcha, xxchaPds))
                .isFalse();
    }

    @Test
    void aCanceledDisableHasNoEffect() {
        DisableService.activate(game, tile, sol);

        SabotageService.undoEffectsOfCanceledCard(game, sol, "Disable");

        assertThat(xxchaCanFireSpaceCannonDefence()).isTrue();
    }

    @Test
    void theEffectEndsWithTheTurn() {
        DisableService.activate(game, tile, sol);

        DisableService.clear(game);

        assertThat(xxchaCanFireSpaceCannonDefence()).isTrue();
        assertThat(PlanetShields.shouldHaveShield(lodor, game)).isTrue();
    }
}
