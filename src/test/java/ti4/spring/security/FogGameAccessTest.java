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
    private static final String TEAMMATE_ID = "400";
    private static final String OTHER_SEATED_ID = "500";

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
    void teammateSeesTheSeatTheyAreOn() {
        Game game = fogGame();
        game.getPlayer(SEATED_ID).addTeamMateID(TEAMMATE_ID);

        FogGameAccess access = FogGameAccess.of(game, TEAMMATE_ID);

        assertThat(access.role()).isEqualTo(FogGameAccess.Role.PLAYER);
        assertThat(access.seat().getUserID()).isEqualTo(SEATED_ID);
    }

    @Test
    void factionlessRosterEntryOnATeamSeesThatSeat() {
        // Someone added to the game without a faction, then made a teammate, still gets the team's view.
        Game game = fogGame();
        game.getPlayer(SEATED_ID).addTeamMateID(SPECTATOR_ID);

        FogGameAccess access = FogGameAccess.of(game, SPECTATOR_ID);

        assertThat(access.role()).isEqualTo(FogGameAccess.Role.PLAYER);
        assertThat(access.seat().getFaction()).isEqualTo("hacan");
    }

    @Test
    void ownerOnATeamOnlySeesThatSeat() {
        // Being part of a seat must win over GM rights, or the team would see past its own fog.
        Game game = fogGame();
        game.getPlayer(SEATED_ID).addTeamMateID(OWNER_ID);

        FogGameAccess access = FogGameAccess.of(game, OWNER_ID);

        assertThat(access.role()).isEqualTo(FogGameAccess.Role.PLAYER);
        assertThat(access.seat().getUserID()).isEqualTo(SEATED_ID);
    }

    @Test
    void seatedPlayerListedOnAnotherTeamKeepsTheirOwnSeat() {
        Game game = fogGame();
        Player other = game.addPlayer(OTHER_SEATED_ID, "Other");
        other.setFaction(game, "jolnar");
        other.setColor("blue");
        game.getPlayer(SEATED_ID).addTeamMateID(OTHER_SEATED_ID);

        FogGameAccess access = FogGameAccess.of(game, OTHER_SEATED_ID);

        assertThat(access.seat().getFaction()).isEqualTo("jolnar");
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
