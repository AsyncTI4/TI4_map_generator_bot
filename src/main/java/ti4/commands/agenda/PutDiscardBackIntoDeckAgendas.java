package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class PutDiscardBackIntoDeckAgendas extends GameStateSubcommand {

    public PutDiscardBackIntoDeckAgendas() {
        super(Constants.PUT_DISCARD_BACK_INTO_DECK, "Put agenda back into deck from discard", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.SHUFFLE_AGENDAS, "Enter YES to shuffle, otherwise NO to put on top").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        OptionMapping option2 = event.getOption(Constants.SHUFFLE_AGENDAS);
        boolean success = false;
        if (option2 != null) {
            if ("YES".equalsIgnoreCase(option2.getAsString())) {
                success = game.shuffleAgendaBackIntoDeck(option.getAsInt());
            } else {
                success = game.putAgendaBackIntoDeckOnTop(option.getAsInt());
            }
        }

        if (success) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda put back into deck");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda found");
        }
    }
}
