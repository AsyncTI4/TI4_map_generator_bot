package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.GalacticEventModel;

class SearchGalacticEventsSubcommand extends SearchComponentModelSubcommand<GalacticEventModel> {

    SearchGalacticEventsSubcommand() {
        super(
                Constants.SEARCH_GALACTIC_EVENTS,
                "List all galactic events the bot can use",
                Mapper::isValidGalacticEvent,
                Mapper::getGalacticEvent,
                valuesOf(() -> Mapper.getGalacticEvents().values()),
                model -> model.getRepresentationEmbed(true),
                null);
    }
}
