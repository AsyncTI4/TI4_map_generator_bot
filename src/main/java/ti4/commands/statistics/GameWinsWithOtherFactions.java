package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.GameWinsWithOtherFactionsService;

class GameWinsWithOtherFactions extends Subcommand {

    private static final String PLAYER_COUNT_FILTER = "player_count";
    private static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    private static final String GAME_TYPE_FILTER = "game_type";
    private static final String FOG_FILTER = "is_fog";
    private static final String HOMEBREW_FILTER = "has_homebrew";

    public GameWinsWithOtherFactions() {
        super(Constants.GAMES_WITH_FACTIONS, "Game Wins With Certain Factions In Them");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction That You Want In The Games")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION2, "Faction That You Want In The Games")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION3, "Faction That You Want In The Games")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION4, "Faction That You Want In The Games")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION5, "Faction That You Want In The Games")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION6, "Faction That You Want In The Games")
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, PLAYER_COUNT_FILTER, "Filter by player count, e.g. 3-8"));
        addOptions(new OptionData(
                OptionType.INTEGER, VICTORY_POINT_GOAL_FILTER, "Filter by victory point goal, e.g. 10-14"));
        addOptions(new OptionData(
                OptionType.STRING,
                GAME_TYPE_FILTER,
                "Filter by game type, e.g. base, pok, absol, ds, action_deck_2, little_omega"));
        addOptions(new OptionData(OptionType.BOOLEAN, FOG_FILTER, "Filter by if the game is a fog game"));
        addOptions(new OptionData(OptionType.BOOLEAN, HOMEBREW_FILTER, "Filter by if the game has any homebrew"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GameWinsWithOtherFactionsService.queueReply(event);
    }
}
