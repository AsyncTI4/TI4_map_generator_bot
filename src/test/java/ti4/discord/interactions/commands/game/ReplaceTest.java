package ti4.discord.interactions.commands.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Replace.isSpectator answers "is this person already in this game", for the command's own check on the incoming
 * player and for the TIGL lower-rank confirmation button's revalidation of it. An eliminated player has to count as
 * in the game: elimination sets them to dummy, so isRealPlayer() reports false, and testing it that way would have
 * let the confirmation admit a player who was eliminated from this very game as their own replacement.
 *
 * <p>Replacing an eliminated player is refused by the command outright - there is no seat to hand over - so the
 * confirmation button requires a live seat and does not need to be lenient here.
 */
class ReplaceTest extends BaseTi4Test {

    private static Player addPlayer(Game game, String userId, String faction) {
        Player player = game.addPlayer(userId, userId);
        player.setFaction(faction);
        player.setColor("blue");
        return player;
    }

    private static Player eliminate(Player player) {
        player.setEliminated(true);
        player.setDummy(true);
        return player;
    }

    @Test
    void anEliminatedPlayerStillCountsAsBeingInTheGame() {
        Game game = new Game();
        Player eliminated = eliminate(addPlayer(game, "user1", "sol"));

        assertThat(eliminated.isRealPlayer()).isFalse();
        assertThat(Replace.isSpectator(eliminated)).isFalse();
    }

    @Test
    void aSeatedPlayerIsNotASpectator() {
        Game game = new Game();

        assertThat(Replace.isSpectator(addPlayer(game, "user1", "sol"))).isFalse();
    }

    @Test
    void aPlayerWithoutAFactionIsASpectator() {
        Game game = new Game();

        assertThat(Replace.isSpectator(game.addPlayer("user1", "user1"))).isTrue();
    }

    // The save format writes the literal string "null" for an absent faction, so both spellings have to count.
    @Test
    void theLiteralNullFactionIsASpectator() {
        Game game = new Game();

        assertThat(Replace.isSpectator(addPlayer(game, "user1", "null"))).isTrue();
    }

    // A user who is not in the game at all resolves to no Player, which is not a spectator to remove.
    @Test
    void aUserWhoIsNotInTheGameIsNotASpectator() {
        assertThat(Replace.isSpectator(null)).isFalse();
    }
}
