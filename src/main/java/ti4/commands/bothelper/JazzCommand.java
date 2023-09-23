package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.AsyncTI4DiscordBot;
import ti4.message.MessageHelper;
import ti4.helpers.AgendaHelper;

public class JazzCommand extends BothelperSubcommandData {
    public JazzCommand() {
        super("jazz_command", "Jazz's custom command");
        // addOptions(new OptionData(OptionType.INTEGER, "num_dice", "description", true).setRequiredRange(0, 1000));
        // addOptions(new OptionData(OptionType.INTEGER, "threshold", "description", true).setRequiredRange(1, 10));
        // addOptions(new OptionData(OptionType.INTEGER, "num_dice_2", "description", true).setRequiredRange(0, 1000));
        // addOptions(new OptionData(OptionType.INTEGER, "threshold_2", "description", true).setRequiredRange(1, 10));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!"228999251328368640".equals(event.getUser().getId())) {
            String jazz = AsyncTI4DiscordBot.jda.getUserById("228999251328368640").getAsMention();
            if ("150809002974904321".equals(event.getUser().getId())) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz + ", but you are an honorary jazz so you may proceed");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You are not " + jazz);
                return;
            }
        }

        AgendaHelper.rollIxthian(getActiveGame());
    }
}