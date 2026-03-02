package ti4.commands.rules;

import java.util.List;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;
import ti4.spring.jda.JdaService;

class AskRulesQuestion extends Subcommand {

    private static final String QUESTION_OPTION = "question";
    private static final String RULES_CHANNEL_NAME = "ti4-rules";

    AskRulesQuestion() {
        super("ask", "Ask a rules question in the hub's ti4-rules channel");
        addOptions(new OptionData(OptionType.STRING, QUESTION_OPTION, "The rules question to ask").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String question = event.getOption(QUESTION_OPTION).getAsString();

        List<TextChannel> channels = JdaService.guildPrimary.getTextChannelsByName(RULES_CHANNEL_NAME, true);
        if (channels.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find the " + RULES_CHANNEL_NAME + " channel.");
            return;
        }

        TextChannel rulesChannel = channels.getFirst();
        String message = "A player asked, \"" + question + "\"";
        MessageHelper.sendMessageToChannel(rulesChannel, message);
        MessageHelper.sendMessageToEventChannel(
                event, "Your question has been sent to the " + RULES_CHANNEL_NAME + " channel.");
    }
}
