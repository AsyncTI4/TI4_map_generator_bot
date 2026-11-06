package ti4.spring.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

class FogGameAccessTest extends BaseTi4Test {

    private static final String OWNER_ID = "100";
    private static final String SEATED_ID = "200";
    private static final String SPECTATOR_ID = "300";

    @Test
    void anonymousUserHasNoAccess() {
        assertThat(FogGameAccess.of(fogGame(), null).role()).isEqualTo(FogGameAccess.Role.ANONYMOUS);
    }

    @Test
    void seatedPlayerSeesTheirOwnSeat() {
        FogGameAccess access = FogGameAccess.of(fogGame(), SEATED_ID);

        assertThat(access.role()).isEqualTo(FogGameAccess.Role.PLAYER);
        assertThat(access.seat().getFaction()).isEqualTo("hacan");
    }

    @Test
    void ownerWithoutSeatIsGameMaster() {
        assertThat(FogGameAccess.of(fogGame(), OWNER_ID).role()).isEqualTo(FogGameAccess.Role.GAME_MASTER);
    }

    @Test
    void ownerWhoHoldsASeatOnlySeesThatSeat() {
        // An owner playing their own fog game must not get the GM's full view.
        Game game = fogGame();
        game.setOwnerID(SEATED_ID);

        FogGameAccess access = FogGameAccess.of(game, SEATED_ID);

        assertThat(access.role()).isEqualTo(FogGameAccess.Role.PLAYER);
        assertThat(access.seat().getFaction()).isEqualTo("hacan");
    }

    @Test
    void factionlessSeatIsNotAPlayer() {
        assertThat(FogGameAccess.of(fogGame(), SPECTATOR_ID).role()).isEqualTo(FogGameAccess.Role.OUTSIDER);
    }

    @Test
    void strangerIsAnOutsider() {
        assertThat(FogGameAccess.of(fogGame(), "999").role()).isEqualTo(FogGameAccess.Role.OUTSIDER);
    }

    private static Game fogGame() {
        Game game = new Game();
        game.newGameSetup();
        game.setName("fogaccess");
        game.setFowMode(true);
        game.setOwnerID(OWNER_ID);
        Player seated = game.addPlayer(SEATED_ID, "Seated");
        seated.setFaction(game, "hacan");
        seated.setColor("yellow");
        game.addPlayer(SPECTATOR_ID, "Spectator");
        return game;
    }
}
