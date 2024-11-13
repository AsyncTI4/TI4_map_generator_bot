package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class PutAgendaTop extends AgendaSubcommandData {
    public PutAgendaTop() {
        super(Constants.PUT_TOP, "Put Agenda top");
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
    }

    public void putTop(GenericInteractionCreateEvent event, int agendaID, Game game) {
        boolean success = game.putAgendaTop(agendaID);
        if (success && !game.isFowMode()) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Agenda put on top");
            ButtonHelper.sendMessageToRightStratThread(game.getPlayer(game.getActivePlayerID()), game, "Agenda put on top", "politics");
        } else {
            if (!game.isFowMode()) {
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), "No Agenda ID found");
            }

        }
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        putTop(event, option.getAsInt(), game);
    }
}
