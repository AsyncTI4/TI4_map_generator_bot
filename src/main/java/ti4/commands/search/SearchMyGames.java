package ti4.commands.search;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.SearchGameHelper;

class SearchMyGames extends Subcommand {

    public SearchMyGames() {
        super(Constants.SEARCH_MY_GAMES, "List all of your games you are currently in");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_MY_TURN, "True to only show games where it is your turn"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to show ended games as well (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_AVERAGE_TURN_TIME, "True to show average turn time as well (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_SECONDARIES, "True to show secondaries you need to follow in each game (default = false)"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.SHOW_GAME_MODES, "True to the game's set modes (default = false)"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to Show"));
        addOptions(new OptionData(OptionType.BOOLEAN, "ignore_spectate", "Do not show games you are spectating (default = true)"));
        addOptions(new OptionData(OptionType.BOOLEAN, "ignore_aborted", "Do not show games that have ended without a winner (default = true)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean onlyMyTurn = event.getOption(Constants.IS_MY_TURN, false, OptionMapping::getAsBoolean);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, false, OptionMapping::getAsBoolean);
        boolean showAverageTurnTime = event.getOption(Constants.SHOW_AVERAGE_TURN_TIME, false, OptionMapping::getAsBoolean);
        boolean showSecondaries = event.getOption(Constants.SHOW_SECONDARIES, true, OptionMapping::getAsBoolean);
        boolean showGameModes = event.getOption(Constants.SHOW_GAME_MODES, false, OptionMapping::getAsBoolean);
        boolean ignoreSpectate = event.getOption("ignore_spectate", true, OptionMapping::getAsBoolean);
        boolean ignoreAborted = event.getOption("ignore_aborted", true, OptionMapping::getAsBoolean);

        User user = event.getOption(Constants.PLAYER, event.getUser(), OptionMapping::getAsUser);
        SearchGameHelper.searchGames(user, event, onlyMyTurn, includeEndedGames, showAverageTurnTime, showSecondaries, showGameModes, ignoreSpectate, ignoreAborted, false);
    }
}
