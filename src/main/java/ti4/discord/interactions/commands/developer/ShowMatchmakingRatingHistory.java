package ti4.discord.interactions.commands.developer;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.spring.service.statistics.matchmaking.MatchmakingRatingEventService;

class ShowMatchmakingRatingHistory extends Subcommand {

    ShowMatchmakingRatingHistory() {
        super(Constants.MMR_HISTORY, "Show a player's matchmaking rating game-by-game.");
        addOptions(new OptionData(OptionType.USER, Constants.USER, "The player to inspect").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User user = event.getOption(Constants.USER).getAsUser();
        MatchmakingRatingEventService.get().showRatingHistory(event, user.getId(), user.getName());
    }
}
