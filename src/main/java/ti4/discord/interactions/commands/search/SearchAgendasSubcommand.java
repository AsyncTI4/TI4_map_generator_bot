package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.AgendaModel;

class SearchAgendasSubcommand extends SearchComponentModelSubcommand<AgendaModel> {

    SearchAgendasSubcommand() {
        super(
                Constants.SEARCH_AGENDAS,
                "List all agendas the bot can use",
                Mapper::isValidAgenda,
                Mapper::getAgenda,
                valuesOf(() -> Mapper.getAgendas().values()),
                model -> model.getRepresentationEmbed(true),
                null);
    }
}
