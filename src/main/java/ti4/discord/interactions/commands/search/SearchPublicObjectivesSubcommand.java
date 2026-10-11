package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.PublicObjectiveModel;

class SearchPublicObjectivesSubcommand extends SearchComponentModelSubcommand<PublicObjectiveModel> {

    SearchPublicObjectivesSubcommand() {
        super(
                Constants.SEARCH_PUBLIC_OBJECTIVES,
                "List all public objectives the bot can use",
                Mapper::isValidPublicObjective,
                Mapper::getPublicObjective,
                valuesOf(() -> Mapper.getPublicObjectives().values()),
                model -> model.getRepresentationEmbed(true),
                PublicObjectiveModel.sortByPointsAndName);
    }
}
