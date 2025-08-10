package ti4.commands.bothelper;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.service.async.ReserveGameNumberService;

public class ReserveGame extends Subcommand {

    private static final String CATEGORY = "category";
    private static final String NUMBER = "number";
    private static final String REMOVE = "remove";

    private static final List<Choice> categories = CommandHelper.toChoices("pbd", "fow");
    private static final List<Choice> removeOpts = CommandHelper.toChoices("NO", "REMOVE");

    ReserveGame() {
        super("reserve_game", "Adds or removes a game number from the list of reserved game numbers.");
        addOptions(new OptionData(OptionType.STRING, CATEGORY, "'pbd' or 'fow'", true).addChoices(categories));
        addOptions(new OptionData(OptionType.INTEGER, NUMBER, "Game number to reserve", true).setMinValue(1));
        addOptions(new OptionData(OptionType.STRING, REMOVE, "Remove from the list instead", false)
                .addChoices(removeOpts));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String category = event.getOption(CATEGORY, "pbd", OptionMapping::getAsString);
        int number = event.getOption(NUMBER, 0, OptionMapping::getAsInt);
        String removeStr = event.getOption(REMOVE, "no", OptionMapping::getAsString);
        // ^^ These can never really be invalid

        String gameName = category.toLowerCase() + number;
        if (removeStr.equalsIgnoreCase("remove")) {
            MessageHelper.sendMessageToEventChannel(event, "Released `" + gameName + "`");
            ReserveGameNumberService.removeReservedGame(gameName);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Reserved `" + gameName + "`");
            ReserveGameNumberService.addReservedGame(gameName);
        }
        MessageHelper.sendMessageToEventChannel(event, ReserveGameNumberService.summarizeReservedGames());
    }
}
