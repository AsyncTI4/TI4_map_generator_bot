package ti4.commands.async;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.spring.context.SpringContext;
import ti4.spring.service.tournamentwinner.TourneyWinnerService;

class ShowTourneyWinners extends Subcommand {

    ShowTourneyWinners() {
        super("show_tourney_winners", "Display the list of all Async Tournament winners");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(
                event, getTournamentWinnerService().allWinnersToString());
    }

    private static TourneyWinnerService getTournamentWinnerService() {
        return SpringContext.getBean(TourneyWinnerService.class);
    }
}
