package ti4.commands.event;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class PutEventTop extends EventSubcommandData {
    public PutEventTop() {
        super(Constants.PUT_TOP, "Put Agenda top");
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
    }


    public void putTop(GenericInteractionCreateEvent event, int agendaID, Game activeGame) {
        boolean success = activeGame.putAgendaTop(agendaID);
        if (success && !activeGame.isFoWMode()) {

            MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "Agenda put on top");
            ButtonHelper.sendMessageToRightStratThread(activeGame.getPlayer(activeGame.getActivePlayer()), activeGame, "Agenda put on top", "politics");
        } else {
            if (!activeGame.isFoWMode()) {
                MessageHelper.sendMessageToChannel(activeGame.getActionsChannel(), "No Agenda ID found");
            }

        }
    }
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        putTop(event, option.getAsInt(), activeGame);
    }
}
