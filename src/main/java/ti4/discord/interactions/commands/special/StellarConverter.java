package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.relic.StellarConverterService;

class StellarConverter extends GameStateSubcommand {

    public StellarConverter() {
        super(Constants.STELLAR_CONVERTER, "Stellar Convert a planet.", true, false);
        addOptions(CommonOptions.planet("Planet to be converted."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String planetName = event.getOption(Constants.PLANET).getAsString();
        if (!game.getPlanets().contains(planetName)) {
            MessageHelper.replyToMessage(event, "Planet not found in map.");
            return;
        }
        StellarConverterService.secondHalfOfStellar(game, planetName, event, null);
    }
}
