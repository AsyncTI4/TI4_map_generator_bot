package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.tokens.AddCC;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class DiploSystem extends SpecialSubcommandData {
    public DiploSystem() {
        super(Constants.DIPLO_SYSTEM, "Diplomacy a system");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }

        diploSystem(event, game, player, tileOption.getAsString().toLowerCase());
    }

    public static boolean diploSystem(GenericInteractionCreateEvent event, Game game, Player player, String tileToResolve) {
        String tileID = AliasHandler.resolveTile(tileToResolve);

        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToEventChannel(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return false;
        }

        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player && player_.isRealPlayer() && !player.getAllianceMembers().contains(player_.getFaction())) {
                String color = player_.getColor();
                if (Mapper.isValidColor(color)) {
                    AddCC.addCC(event, color, tile);
                    Helper.isCCCountCorrect(event, game, color);
                }
            }
        }

        return true;
    }
}
