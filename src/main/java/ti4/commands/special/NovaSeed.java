package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.leader.MuaatHeroService;

class NovaSeed extends GameStateSubcommand {

    public NovaSeed() {
        super(Constants.NOVA_SEED, "Nova seed a system", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player using nova seed"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color using nova seed").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String tileID = event.getOption(Constants.TILE_NAME).getAsString().toLowerCase();
        Tile tile = TileHelper.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        MuaatHeroService.secondHalfOfNovaSeed(getPlayer(), event, tile, game);
    }
}
