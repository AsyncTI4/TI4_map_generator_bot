package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.testUtils.BaseTi4Test;

class AgendaHelperTest extends BaseTi4Test {

    @Test
    void getPossibleWhenNamesOmitsAgendaActionCardsForCensuredPlayers() {
        Player player = censuredPlayer();
        player.addAbility("quash");
        player.setStrategicCC(1);
        player.setActionCard("veto", 1);

        assertThat(AgendaHelper.getPossibleWhenNames(player))
                .contains(Mapper.getAbility("quash").getName())
                .doesNotContain(Mapper.getActionCard("veto").getName());
    }

    @Test
    void getPossibleAfterNamesOmitsAgendaActionCardsForCensuredPlayers() {
        Player player = censuredPlayer();
        player.addAbility("conspirators");
        player.setActionCard("sanction", 1);

        assertThat(AgendaHelper.getPossibleAfterNames(player))
                .contains(Mapper.getAbility("conspirators").getName())
                .doesNotContain(Mapper.getActionCard("sanction").getName());
    }

    @Test
    void getSummaryOfVotesUsesPlanetNameForElectPlanetOutcome() {
        Game game = new Game();
        Player player = playerWithFaction(game, "arborec");
        game.setPlayers(Map.of(player.getUserID(), player));
        game.setCurrentAgendaInfo("agenda_Elect Planet");
        game.setCurrentAgendaVote("mrte", "arborec_14");

        assertThat(AgendaHelper.getSummaryOfVotes(game, true))
                .contains("Mecatol Rex: 14")
                .doesNotContain("Mrte: 14");
    }

    @Test
    void buildSpentThingsMessageForVotingUsesPlanetNameForElectPlanetOutcome() {
        Game game = new Game();
        Player player = playerWithFaction(game, "arborec");
        game.setCurrentAgendaInfo("agenda_Elect Planet");
        game.setStoredValue("latestOutcomeVotedFor" + player.getFaction(), "mrte");

        assertThat(Helper.buildSpentThingsMessageForVoting(player, game, false))
                .contains("outcome \"Mecatol Rex\"")
                .doesNotContain("outcome \"Mrte\"");
    }

    private static Player censuredPlayer() {
        Game game = new Game();
        game.setLaws(Map.of("censure", 1));
        game.setLawsInfo(Map.of("censure", "faction"));
        return playerWithFaction(game, "faction");
    }

    private static Player playerWithFaction(Game game, String faction) {
        Player player = new Player("userId", "userName", game);
        player.setFaction(faction);
        return player;
    }
}
