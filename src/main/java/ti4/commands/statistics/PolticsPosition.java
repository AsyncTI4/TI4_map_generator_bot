package ti4.commands.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Predicate;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Player;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;
import ti4.service.game.ManagedGameService;

class PoliticsPosition extends Subcommand {

    public PoliticsPosition() {
        super(Constants.STATISTICS_POLITICS_POSITION, "List politics round 1 chances of getting custodians");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        searchGames(event);
    }

    public static void searchGames(SlashCommandInteractionEvent event) {

        Predicate<ManagedGame> endedGamesFilter = game -> game.isHasEnded()
                && game.getRound() == 5
                && game.getRealPlayers().size() == 6
                && !game.isFowMode() && game.isHasWinner();

        var filteredManagedGames = GameManager.getManagedGames().stream()
                .filter(endedGamesFilter)
                .sorted(Comparator.comparing(ManagedGameService::getGameNameForSorting))
                .toList();

        int games = 0;
        int gamesWithCustodians = 0;
        int gamesWith8Or1 = 0;
        int gamesWon = 0;

        for (var managedGame : filteredManagedGames) {

            var game = managedGame.getGame();
            if (game.getPhaseOfGame().contains("agenda") || game.isHomebrewSCMode() || game.isLiberationC4Mode()) {
                continue;
            }

            for (Player player : game.getRealPlayers()) {
                if (game.getStoredValue("Round1SCPickFor" + player.getFaction()).equalsIgnoreCase("3")
                        && player.getAllianceMembers().isEmpty()) {
                    games++;
                    if (game.getStoredValue("Round" + game.getRound() + "SCPickFor" + player.getFaction())
                                    .equalsIgnoreCase("8")
                            || game.getStoredValue("Round" + game.getRound() + "SCPickFor" + player.getFaction())
                                    .equalsIgnoreCase("1")) {
                        gamesWith8Or1++;
                    }

                    String idC = "";
                    for (Entry<String, Integer> po :
                            game.getRevealedPublicObjectives().entrySet()) {
                        if (po.getValue().equals(0)) {
                            idC = po.getKey();
                            break;
                        }
                    }
                    if (!idC.isEmpty()) {
                        List<String> scoredPlayerList =
                                game.getScoredPublicObjectives().computeIfAbsent(idC, key -> new ArrayList<>());
                        if (scoredPlayerList.size() > 0
                                && scoredPlayerList.get(0).equalsIgnoreCase(player.getUserID())) {
                            gamesWithCustodians++;
                        }
                    }

                    if (game.getWinner() != null && game.getWinner().get() == player) {
                        gamesWon++;
                    }
                }
            }
        }

        String msg = "Games Total: " + games + "\n";
        msg += "Games won: " + gamesWon + "\n";
        msg += "Games with custodians: " + gamesWithCustodians + "\n";
        msg += "Games with Imperial or Leadership in Round 5: " + gamesWith8Or1 + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), msg);
    }
}
