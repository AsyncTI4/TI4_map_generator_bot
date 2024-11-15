package ti4.commands2.agenda;

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

// TODO: maybe combine PutTop and PutBottom?
class PutDrawnAgendaBackIntoDeck extends GameStateSubcommand {

    public PutDrawnAgendaBackIntoDeck() {
        super(Constants.PUT_IN_DECK, "Put a drawn agenda back into the deck", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.AGENDA_ID, "Agenda ID that is sent between ()").setRequired(true));
        addOption(OptionType.BOOLEAN, Constants.PUT_ON_BOTTOM, "Put the agenda on the bottom of the deck");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int agendaId = event.getOption(Constants.AGENDA_ID).getAsInt();
        boolean onBottom = event.getOption(Constants.PUT_ON_BOTTOM, false, OptionMapping::getAsBoolean);
        if (onBottom) {
            AgendaHelper.putBottom(agendaId, getGame());
        } else {
            AgendaHelper.putTop(agendaId, getGame());
        }
    }

    @ButtonHandler("topAgenda_")
    public static void topAgenda(ButtonInteractionEvent event, String buttonID, Game game) {
        String agendaNumID = buttonID.substring(buttonID.indexOf("_") + 1);
        AgendaHelper.putTop(Integer.parseInt(agendaNumID), game);
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
