package ti4.discord.interactions.commands.search;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.model.AbilityModel;

class SearchAbilitiesSubcommand extends SearchComponentModelSubcommand<AbilityModel> {

    SearchAbilitiesSubcommand() {
        super(
                Constants.SEARCH_ABILITIES,
                "List all abilities",
                Mapper::isValidAbility,
                Mapper::getAbility,
                valuesOf(() -> Mapper.getAbilities().values()),
                model -> model.getRepresentationEmbed(true),
                null);
    }

    @Override
    protected MessageEmbed exactMatchEmbed(AbilityModel model, SlashCommandInteractionEvent event) {
        return model.getRepresentationEmbed();
    }
}
