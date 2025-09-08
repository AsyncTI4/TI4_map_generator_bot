package ti4.commands.async;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.service.async.TourneyWinnersService;

class ShowTourneyWinners extends Subcommand {

    public ShowTourneyWinners() {
        super("show_tourney_winners", "Display the list of all Async Tournament winners");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, TourneyWinnersService.tournamentWinnersOutputString());
    }
}
