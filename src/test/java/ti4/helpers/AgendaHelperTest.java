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

        assertThat(AgendaSummaryHelper.getSummaryOfVotes(game, true))
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

    @Test
    void getSummaryOfVotesFormatsStrategyCardOutcomeWithEmoji() {
        Game game = new Game();
        Player player = playerWithFaction(game, "arborec");
        game.setPlayers(Map.of(player.getUserID(), player));
        game.setCurrentAgendaInfo("agenda_Elect Strategy Card");
        game.setCurrentAgendaVote("4", "arborec_10");

        String summary = AgendaSummaryHelper.getSummaryOfVotes(game, true);
        assertThat(summary).contains("**Construction**");
    }

    @Test
    void getSummaryOfVotesDoesNotThrowForNonNumericStrategyCardOutcome() {
        Game game = new Game();
        Player player = playerWithFaction(game, "arborec");
        game.setPlayers(Map.of(player.getUserID(), player));
        game.setCurrentAgendaInfo("agenda_Elect Strategy Card");
        game.setCurrentAgendaVote("Construction", "arborec_10");

        String summary = AgendaSummaryHelper.getSummaryOfVotes(game, true);
        assertThat(summary).contains("Construction: 10");
    }

    @Test
    void getVoteCountMessageShowsTotalVotesNormally() {
        Game game = voteCountGame();

        assertThat(AgendaHelper.getVoteCountMessage(game)).contains("Total votes:");
    }

    @Test
    void getVoteCountMessageOmitsTotalVotesForRepresentativeGovernment() {
        Game game = voteCountGame();
        game.setLaws(Map.of("rep_govt", 1));

        assertThat(AgendaHelper.getVoteCountMessage(game)).doesNotContain("Total votes:");
    }

    @Test
    void getVoteCountMessageShowsTotalVotesWhenExecutiveOrderDisablesRepresentativeGovernment() {
        Game game = voteCountGame();
        game.setLaws(Map.of("rep_govt", 1));
        game.setStoredValue("executiveOrder", "active");

        assertThat(AgendaHelper.getVoteCountMessage(game)).contains("Total votes:");
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

    private static Game voteCountGame() {
        Game game = new Game();
        Player speaker = game.addPlayer("speaker", "speaker");
        speaker.setFaction("arborec");
        speaker.setColor("green");
        game.setSpeaker(speaker);

        Player player = game.addPlayer("player", "player");
        player.setFaction("hacan");
        player.setColor("yellow");

        return game;
    }
}
