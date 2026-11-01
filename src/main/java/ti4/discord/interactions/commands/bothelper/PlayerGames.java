package ti4.discord.interactions.commands.bothelper;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.bothelper.PlayerGamesReportService;

class PlayerGames extends Subcommand {

    PlayerGames() {
        super(Constants.PLAYER_GAMES, "Moderation view of a player's games, fog included, plus track record");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player to evaluate").setRequired(true));
        addOptions(new OptionData(
                OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to show ended games as well (default = false)"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.IGNORE_ABORTED,
                "Do not show games that have ended without a winner (default = true)"));
        addOptions(new OptionData(
                OptionType.BOOLEAN, Constants.SHOW_GAME_MODES, "True to show the game's set modes (default = false)"));
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User target = event.getOption(Constants.PLAYER, event.getUser(), OptionMapping::getAsUser);
        boolean includeEndedGames = event.getOption(Constants.ENDED_GAMES, Boolean.FALSE, OptionMapping::getAsBoolean);
        boolean ignoreAborted = event.getOption(Constants.IGNORE_ABORTED, Boolean.TRUE, OptionMapping::getAsBoolean);
        boolean showGameModes = event.getOption(Constants.SHOW_GAME_MODES, Boolean.FALSE, OptionMapping::getAsBoolean);
        PlayerGamesReportService.sendReport(event, target, includeEndedGames, ignoreAborted, showGameModes);
    }
}
