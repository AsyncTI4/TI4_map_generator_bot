package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class ResetAgendas extends GameStateSubcommand {

    public ResetAgendas() {
        super(Constants.RESET_AGENDAS, "Reset agenda deck", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm undo command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping confirmOption = event.getOption(Constants.CONFIRM);
        if (confirmOption == null || !"YES".equals(confirmOption.getAsString())){
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        Game game = getGame();
        game.resetAgendas();
        MessageHelper.replyToMessage(event, "Agenda deck reset to deck: `" + game.getAgendaDeckID() + "`. Discards removed. All shuffled as new");
    }
}
