package ti4.discord.interactions.commands.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.ManagedGame;

class CompetitiveExportFilterTest {

    @Test
    void acceptsThreeThroughEightPlayersAtTenTwelveOrFourteenVictoryPoints() {
        for (int players = 2; players <= 9; players++) {
            for (int vp : new int[] {8, 10, 11, 12, 13, 14, 16}) {
                Game game = competitiveGame(players, vp);
                boolean eligible = players >= 3 && players <= 8 && (vp == 10 || vp == 12 || vp == 14);
                assertThat(GameStatisticsFilterer.getCompetitiveExportGamesFilter()
                                .test(game))
                        .as("%s players and %s VP", players, vp)
                        .isEqualTo(eligible);
            }
        }
    }

    @Test
    void requiresBothExpansionsAndExcludesTwilightsFall() {
        for (boolean thundersEdge : new boolean[] {false, true}) {
            for (boolean prophecyOfKings : new boolean[] {false, true}) {
                for (boolean twilightsFall : new boolean[] {false, true}) {
                    Game game = competitiveGame(6, 10);
                    when(game.isThundersEdge()).thenReturn(thundersEdge);
                    when(game.isProphecyOfKings()).thenReturn(prophecyOfKings);
                    when(game.isTwilightsFallMode()).thenReturn(twilightsFall);
                    assertThat(GameStatisticsFilterer.getCompetitiveExportGamesFilter()
                                    .test(game))
                            .as(
                                    "Thunder's Edge=%s, Prophecy of Kings=%s, Twilight's Fall=%s",
                                    thundersEdge, prophecyOfKings, twilightsFall)
                            .isEqualTo(thundersEdge && prophecyOfKings && !twilightsFall);
                    assertThat(GameStatisticsFilterer.getStandardCompetitiveGamesFilter()
                                    .test(game))
                            .isTrue();
                }
            }
        }
    }

    @Test
    void preservesTheStandardSixPlayerTenPointFilter() {
        assertThat(GameStatisticsFilterer.getStandardCompetitiveGamesFilter().test(competitiveGame(6, 10)))
                .isTrue();
        assertThat(GameStatisticsFilterer.getStandardCompetitiveGamesFilter().test(competitiveGame(5, 10)))
                .isFalse();
        assertThat(GameStatisticsFilterer.getStandardCompetitiveGamesFilter().test(competitiveGame(6, 12)))
                .isFalse();
    }

    @Test
    void rejectsHomebrewFogGalacticEventsScenariosAbortedAndEarlyGames() {
        assertExcluded(game -> when(game.hasHomebrew()).thenReturn(true));
        assertExcluded(game -> when(game.isFowMode()).thenReturn(true));
        assertExcluded(game -> when(game.isAgeOfExplorationMode()).thenReturn(true));
        assertExcluded(game -> when(game.isOrdinianC1Mode()).thenReturn(true));
        assertExcluded(game -> when(game.isAllianceMode()).thenReturn(true));
        assertExcluded(game -> when(game.getWinner()).thenReturn(Optional.empty()));
        assertExcluded(game -> when(game.getRound()).thenReturn(2));
    }

    @Test
    void storesExportEligibilityInManagedMetadataAndRecomputesItWhenReplaced() {
        Game game = competitiveGame(8, 14);
        when(game.getName()).thenReturn("pbd1");
        when(game.isHasEnded()).thenReturn(true);
        assertThat(new ManagedGame(game).isCompetitiveExportEligible()).isTrue();
        when(game.hasHomebrew()).thenReturn(true);
        assertThat(new ManagedGame(game).isCompetitiveExportEligible()).isFalse();
        when(game.hasHomebrew()).thenReturn(false);
        when(game.isHasEnded()).thenReturn(false);
        assertThat(new ManagedGame(game).isCompetitiveExportEligible()).isFalse();
    }

    private static void assertExcluded(Consumer<Game> change) {
        Game game = competitiveGame(6, 10);
        change.accept(game);
        assertThat(GameStatisticsFilterer.getCompetitiveExportGamesFilter().test(game))
                .isFalse();
    }

    private static Game competitiveGame(int players, int vp) {
        Game game = mock(Game.class);
        when(game.getRealAndEliminatedPlayers()).thenReturn(Collections.nCopies(players, mock(Player.class)));
        when(game.getVp()).thenReturn(vp);
        when(game.getRound()).thenReturn(3);
        when(game.isThundersEdge()).thenReturn(true);
        when(game.isProphecyOfKings()).thenReturn(true);
        when(game.getWinner()).thenReturn(Optional.of(mock(Player.class)));
        return game;
    }
}
