package ti4.commands2.event;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

class PlayEvent extends GameStateSubcommand {

    public PlayEvent() {
        super(Constants.EVENT_PLAY, "Play an event from your hand", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.EVENT_ID, "Event Card ID, which is found between (), or name/part of name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String eventIDOption = StringUtils.substringBefore(event.getOption(Constants.EVENT_ID, "", OptionMapping::getAsString).toLowerCase(), " ");
        int eventNumericalID;
        try {
            eventNumericalID = Integer.parseInt(eventIDOption);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Event ID must be numeric.");
            return;
        }

        Player player = getPlayer();
        if (!player.getEvents().containsValue(eventNumericalID)) {
            MessageHelper.sendMessageToEventChannel(event, "Player does not have event `" + eventNumericalID + "` in hand.");
            return;
        }

        int numericID = eventNumericalID;
        String eventID = player.getEvents().entrySet().stream().filter(e -> numericID == e.getValue()).map(Map.Entry::getKey).findFirst().orElse(null);
        EventModel eventModel = Mapper.getEvent(eventID);
        if (eventModel == null) {
            MessageHelper.sendMessageToEventChannel(event, "Event ID `" + eventID + "` could not be found.");
            return;
        }

        playEventFromHand(event, getGame(), player, eventModel);
    }

    public static void playEventFromHand(GenericInteractionCreateEvent event, Game game, Player player, EventModel eventModel) {
        game.discardEvent(eventModel.getAlias());
        player.removeEvent(eventModel.getAlias());

        game.getActionsChannel().sendMessageEmbeds(eventModel.getRepresentationEmbed()).queue();

        Integer discardedEventNumericalID = game.getDiscardedEvents().get(eventModel.getAlias());

        if (eventModel.staysInPlay()) {
            game.addEventInEffect(discardedEventNumericalID);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Event `" + eventModel.getAlias() + "` is now in effect.");
        }
    }
}
