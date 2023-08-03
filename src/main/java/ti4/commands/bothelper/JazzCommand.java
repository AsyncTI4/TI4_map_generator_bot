package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.message.MessageHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JazzCommand extends BothelperSubcommandData {
    public JazzCommand() {
        super("jazz_command", "Jazz's custom command");
        addOptions(new OptionData(OptionType.INTEGER, "num_dice", "description", true).setRequiredRange(0, 1000));
        addOptions(new OptionData(OptionType.INTEGER, "threshold", "description", true).setRequiredRange(1, 10));
        addOptions(new OptionData(OptionType.INTEGER, "num_dice_2", "description", true).setRequiredRange(0, 1000));
        addOptions(new OptionData(OptionType.INTEGER, "threshold_2", "description", true).setRequiredRange(1, 10));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.getUser().getId().equals("228999251328368640")) {
            String jazz = MapGenerator.jda.getUserById("228999251328368640").getAsMention();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz);
            return;
        }

        int num1 = event.getOption("num_dice").getAsInt();
        int t1 = event.getOption("threshold").getAsInt();
        int num2 = event.getOption("num_dice_2").getAsInt();
        int t2 = event.getOption("threshold_2").getAsInt();

        List<Die> roll_em = DiceHelper.rollDice(t1, num1);
        roll_em.addAll(DiceHelper.rollDice(t2, num2));

        sendMessage(DiceHelper.formatDiceOutput(roll_em));
    }
}
