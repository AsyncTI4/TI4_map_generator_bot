package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

// TODO: maybe combine PutTop and PutBottom?
public class PutAgendaTop extends GameStateSubcommand {

    public PutAgendaTop() {
        super(Constants.PUT_TOP, "Put Agenda top", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        putTop(option.getAsInt(), getGame());
    }

    private static void putTop(int agendaID, Game game) {
        boolean success = game.putAgendaTop(agendaID);
        if (game.isFowMode()) {
            return;
        }
        if (!success) {
            MessageHelper.sendMessageToChannel(game.getActionsChannel(), "No Agenda ID found");
            return;
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "Agenda put on top");
        ButtonHelper.sendMessageToRightStratThread(game.getPlayer(game.getActivePlayerID()), game, "Agenda put on top", "politics");
    }

    @ButtonHandler("topAgenda_")
    public static void topAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        putTop(Integer.parseInt(agendaNumID), game);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "top");
        } else {
            game.setStoredValue(key, game.getStoredValue(key) + "_top");
        }
        AgendaModel agenda = Mapper.getAgenda(game.lookAtTopAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
                "Put " + agenda.getName()
                        + " on the top of the agenda deck. You may use this button to undo that and reassign it.",
                reassign);
        ButtonHelper.deleteMessage(event);
    }
}
