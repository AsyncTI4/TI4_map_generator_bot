package ti4.discord.interactions.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ShuffleAgendas extends GameStateSubcommand {

    ShuffleAgendas() {
        super(Constants.SHUFFLE_AGENDAS, "Shuffle agenda deck", true, false);
        markSuspicious();
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String confirm = event.getOption(Constants.CONFIRM, null, OptionMapping::getAsString);
        if (!CommandHelper.confirmedWithYes(event, confirm)) return;

        getGame().shuffleAgendas();
        MessageHelper.replyToMessage(event, "Agenda deck shuffled");
    }
}
