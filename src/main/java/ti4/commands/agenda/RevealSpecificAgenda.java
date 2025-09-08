package ti4.commands.agenda;

import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RevealSpecificAgenda extends GameStateSubcommand {

    public RevealSpecificAgenda() {
        super(Constants.REVEAL_SPECIFIC, "Reveal top Agenda from deck", true, false);
        addOptions(new OptionData(
                        OptionType.STRING, Constants.AGENDA_ID, "Agenda Card ID (text ID found in /search agendas)")
                .setRequired(true)
                .setAutoComplete(true));
        addOption(OptionType.BOOLEAN, Constants.FORCE, "Force reveal the agenda (even if it's not in the deck)");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String agendaID = event.getOption(Constants.AGENDA_ID, "", OptionMapping::getAsString);
        if (!Mapper.isValidAgenda(agendaID)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Agenda ID found, please retry");
            return;
        }

        Game game = getGame();
        boolean force = event.getOption(Constants.FORCE, false, OptionMapping::getAsBoolean);
        if (!game.revealAgenda(agendaID, force)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda not found in deck, please retry");
            return;
        }

        revealAgenda(event, game, event.getChannel(), agendaID);
    }

    /**
     * @deprecated This needs to be refactored to use {@link AgendaHelper#revealAgenda}'s version
     */
    @Deprecated
    public void revealAgenda(GenericInteractionCreateEvent event, Game game, MessageChannel channel, String agendaID) {
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(agendaID);
        if (uniqueID == null) {
            MessageHelper.sendMessageToChannel(channel, "Agenda `" + agendaID + "` not found in discard, please retry");
            return;
        }
        game.putAgendaBackIntoDeckOnTop(uniqueID);
        AgendaHelper.revealAgenda(event, false, game, channel);
    }
}
