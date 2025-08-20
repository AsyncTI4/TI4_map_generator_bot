package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ShuffleAgendas extends GameStateSubcommand {

    public ShuffleAgendas() {
        super(Constants.SHUFFLE_AGENDAS, "Shuffle agenda deck", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String confirm = event.getOption(Constants.CONFIRM, null, OptionMapping::getAsString);
        if (!"YES".equals(confirm)) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        getGame().shuffleAgendas();
        MessageHelper.replyToMessage(event, "Agenda deck shuffled");
    }
}
