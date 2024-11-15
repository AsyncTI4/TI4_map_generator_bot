package ti4.commands.agenda;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;

class PutAgendaBottom extends GameStateSubcommand {

    public PutAgendaBottom() {
        super(Constants.PUT_BOTTOM, "Put Agenda bottom", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.AGENDA_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No Agenda ID defined");
            return;
        }
        AgendaHelper.putBottom(option.getAsInt(), getGame());
    }



    @ButtonHandler("bottomAgenda_")
    public static void bottomAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        AgendaHelper.putBottom(Integer.parseInt(agendaNumID), game);
        AgendaModel agenda = Mapper.getAgenda(game.lookAtBottomAgenda(0));
        Button reassign = Buttons.gray("retrieveAgenda_" + agenda.getAlias(), "Reassign " + agenda.getName());
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(),
                "Put " + agenda.getName()
                        + " on the bottom of the agenda deck. You may use this button to undo that and reassign it.",
                reassign);
        String key = "round" + game.getRound() + "AgendaPlacement";
        if (game.getStoredValue(key).isEmpty()) {
            game.setStoredValue(key, "bottom");
        } else {
            game.setStoredValue(key, game.getStoredValue(key) + "_bottom");
        }
        ButtonHelper.deleteMessage(event);
    }
}
