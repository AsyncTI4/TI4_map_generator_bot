package ti4.commands.statistics;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.service.statistics.DiceLuckService;

class DiceLuck extends Subcommand {

    public DiceLuck() {
        super(Constants.DICE_LUCK, "Dice luck as recorded by the bot");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)"));
        addOptions(new OptionData(
                OptionType.INTEGER,
                Constants.MINIMUM_NUMBER_OF_EXPECTED_HITS,
                "Minimum number of expected hits to show (Default = 10)"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.IGNORE_ENDED_GAMES,
                "True to exclude ended games from the calculation (default = false)"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                "ascending",
                "True to sort the values in ascending order, lowest to highest (default = true)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        DiceLuckService.queueReply(event);
    }
}
