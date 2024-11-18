package ti4.commands.player;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ColorChangeHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ChangeColor extends GameStateSubcommand {

    public ChangeColor() {
        super(Constants.CHANGE_COLOR, "Player Color Change", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of units").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String newColor = AliasHandler.resolveColor(event.getOption(Constants.COLOR).getAsString().toLowerCase());
        if (!Mapper.isValidColor(newColor)) {
            MessageHelper.sendMessageToEventChannel(event, "Color not valid");
            return;
        }

        Player player = getPlayer();
        Game game = getGame();
        Map<String, Player> players = game.getPlayers();
        for (Player playerInfo : players.values()) {
            if (playerInfo != player) {
                if (newColor.equals(playerInfo.getColor())) {
                    MessageHelper.sendMessageToEventChannel(event, "Player:" + playerInfo.getUserName() + " already uses color:" + newColor);
                    return;
                }
            }
        }

        if (ColorChangeHelper.colorIsExclusive(newColor, player)) {
            MessageHelper.sendMessageToEventChannel(event, "You cannot use this color.");
            return;
        }

        String oldColor = player.getColor();
        ColorChangeHelper.changePlayerColor(game, player, oldColor, newColor);
    }


}
