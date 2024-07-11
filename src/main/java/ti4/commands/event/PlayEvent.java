package ti4.commands.event;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.EventModel;

public class PlayEvent extends EventSubcommandData {

    public PlayEvent() {
        super(Constants.EVENT_PLAY, "Play an Event from your hand");
        addOptions(new OptionData(OptionType.STRING, Constants.EVENT_ID, "Event Card ID that is sent between () or Name/Part of Name").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        String eventIDOption = StringUtils.substringBefore(event.getOption(Constants.EVENT_ID, "", OptionMapping::getAsString).toLowerCase(), " ");
        if (eventIDOption.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "Please select what Event ID to play");
            return;
        }

        Integer eventNumericalID = null;
        try {
            eventNumericalID = Integer.parseInt(eventIDOption);
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Event ID must be numeric");
            return;
        }

        if (!player.getEvents().containsValue(eventNumericalID)) {
            MessageHelper.sendMessageToEventChannel(event, "Player does not have Event `" + eventNumericalID + "` in hand");
            return;
        }

        final int numericID = eventNumericalID;
        String eventID = player.getEvents().entrySet().stream().filter(e -> numericID == e.getValue()).map(e -> e.getKey()).findFirst().orElse(null);
        EventModel eventModel = Mapper.getEvent(eventID);
        if (eventModel == null) {
            MessageHelper.sendMessageToEventChannel(event, "Event ID `" + eventID + "` could not be found");
            return;
        }

        playEventFromHand(event, game, player, eventModel);
    }

    public static void playEventFromHand(GenericInteractionCreateEvent event, Game game, Player player, EventModel eventModel) {
        game.discardEvent(eventModel.getAlias());
        player.removeEvent(eventModel.getAlias());

        game.getActionsChannel().sendMessageEmbeds(eventModel.getRepresentationEmbed()).queue();

        Integer discardedEventNumericalID = game.getDiscardedEvents().get(eventModel.getAlias());

        if (eventModel.staysInPlay()) {
            game.addEventInEffect(discardedEventNumericalID);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Event `" + eventModel.getAlias() + "` is now in effect");
        }
    }
}
