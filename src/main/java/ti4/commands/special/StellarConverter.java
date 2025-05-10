package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.StellarConverterService;

class StellarConverter extends GameStateSubcommand {

    public StellarConverter() {
        super(Constants.STELLAR_CONVERTER, "Stellar Convert a planet.", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.PLANET, "Planet to be converted.").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String planetName = event.getOption(Constants.PLANET).getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map.");
            return;
        }
        StellarConverterService.secondHalfOfStellar(game, planetName, event);
    }
}
