package ti4.discord.interactions.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.service.leader.ZelianHeroService;

class ZelianHero extends GameStateSubcommand {

    public ZelianHero() {
        super(Constants.ZELIAN_HERO, "Celestial Impact a system (replace with Zelian Asteroid field)", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color using Zelian R, the Zelian heRo")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileID = event.getOption(Constants.TILE_NAME).getAsString().toLowerCase();
        Tile tile = TileHelper.getTile(event, tileID, getGame());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        ZelianHeroService.secondHalfOfCelestialImpact(getPlayer(), event, tile, getGame());
    }
}
