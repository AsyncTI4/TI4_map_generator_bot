package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.service.leader.MuaatHeroService;

class NovaSeed extends GameStateSubcommand {

    public NovaSeed() {
        super(Constants.NOVA_SEED, "Nova seed a system", true, true);
        addOptions(CommonOptions.tileName());
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player using nova seed"));
        addOptions(CommonOptions.factionColor("Faction or Color using nova seed"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String tileID = event.getOption(Constants.TILE_NAME).getAsString().toLowerCase();
        Tile tile = CommandHelper.requireOrReport(
                event,
                TileHelper.getTile(event, tileID, game),
                "Could not resolve tileID:  `" + tileID + "`. Tile not found");
        if (tile == null) return;

        MuaatHeroService.secondHalfOfNovaSeed(getPlayer(), event, tile, game);
    }
}
