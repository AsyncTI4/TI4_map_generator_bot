package ti4.commands.admin;

import java.util.List;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.async.TourneyWinnersService;

public class TourneyWinner extends Subcommand {

    private static final String TOURNEYNAME = "tournament_name";
    private static final String REMOVE = "remove";

    private static final List<Choice> removeOpts = CommandHelper.toChoices("NO", "REMOVE");

    TourneyWinner() {
        super("tourney_winner", "Adds or removes a user from the list of Tournament Winners.");
        addOptions(new OptionData(OptionType.USER, Constants.USER, "Player who won the tournament", true));
        addOptions(new OptionData(OptionType.STRING, TOURNEYNAME, "Which tournament the player won", true));
        addOptions(new OptionData(OptionType.STRING, REMOVE, "Remove this user from the list instead")
                .addChoices(removeOpts));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        User selectedUser = event.getOption("user", null, OptionMapping::getAsUser);
        if (selectedUser == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not resolve user, please try again.");
            return;
        }

        String tourneyName = event.getOption("tournament_name", null, OptionMapping::getAsString);
        if (tourneyName == null || tourneyName.isBlank()) {
            MessageHelper.sendMessageToEventChannel(event, "Tournament name cannot be blank.");
        }

        String removeStr = event.getOption("remove", "no", OptionMapping::getAsString);
        boolean remove = removeStr.equalsIgnoreCase("remove");

        String output;
        if (remove) {
            TourneyWinnersService.removeTourneyWinner(selectedUser, tourneyName);
            output = "Removed " + selectedUser.getAsMention() + " as a winner of `" + tourneyName + "`.";
        } else {
            TourneyWinnersService.addTourneyWinner(selectedUser, tourneyName);
            output = "Added " + selectedUser.getEffectiveName() + " as a winner of `" + tourneyName + "`.";
        }
        MessageHelper.sendMessageToEventChannel(event, output);
        MessageHelper.sendMessageToEventChannel(event, TourneyWinnersService.tournamentWinnersOutputString());
    }
}
