package ti4.discord.interactions.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ResetDrawStateAgendas extends GameStateSubcommand {

    ResetDrawStateAgendas() {
        super(Constants.RESET_DRAW_STATE_FOR_AGENDAS, "Reset draw state of agenda deck", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!CommandHelper.confirmedWithYes(event, event.getOption(Constants.CONFIRM))) return;

        getGame().resetDrawStateAgendas();
        MessageHelper.replyToMessage(event, "Agenda draw state reset.");
    }
}
