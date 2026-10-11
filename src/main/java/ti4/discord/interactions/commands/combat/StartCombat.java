package ti4.discord.interactions.commands.combat;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;

class StartCombat extends GameStateSubcommand {

    public StartCombat() {
        super(Constants.START_COMBAT, "Start a new combat in a given tile.", true, false);
        addOptions(CommonOptions.tileName("System/Tile to start combat"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String tileID = event.getOption(Constants.TILE_NAME, null, OptionMapping::getAsString);

        Tile tile = TileHelper.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        StartCombatService.combatCheck(game, event, tile);
    }
}
