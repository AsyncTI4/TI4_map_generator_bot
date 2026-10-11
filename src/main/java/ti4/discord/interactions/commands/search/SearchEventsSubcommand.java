package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.EventModel;

class SearchEventsSubcommand extends SearchComponentModelSubcommand<EventModel> {

    SearchEventsSubcommand() {
        super(
                Constants.SEARCH_EVENTS,
                "List all events the bot can use",
                Mapper::isValidEvent,
                Mapper::getEvent,
                valuesOf(() -> Mapper.getEvents().values()),
                model -> model.getRepresentationEmbed(true, null),
                null);
    }
}
