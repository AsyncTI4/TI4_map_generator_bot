package ti4.discord.interactions.commands.search;

import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.SecretObjectiveModel;

class SearchSecretObjectivesSubcommand extends SearchComponentModelSubcommand<SecretObjectiveModel> {

    SearchSecretObjectivesSubcommand() {
        super(
                Constants.SEARCH_SECRET_OBJECTIVES,
                "List all secret objectives the bot can use",
                Mapper::isValidSecretObjective,
                Mapper::getSecretObjective,
                valuesOf(() -> Mapper.getSecretObjectives().values()),
                model -> model.getRepresentationEmbed(true),
                SecretObjectiveModel.sortByPointsAndName);
    }
}
