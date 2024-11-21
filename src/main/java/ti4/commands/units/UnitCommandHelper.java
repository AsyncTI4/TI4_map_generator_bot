package ti4.commands.units;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;

@UtilityClass
class UnitCommandHelper {

    public void handleGenerateMapOption(SlashCommandInteractionEvent event, Game game) {
        boolean generateMap = !event.getOption(Constants.NO_MAPGEN, false, OptionMapping::getAsBoolean);
        if (generateMap) {
            ShowGameService.simpleShowGame(game, event);
        } else {
            MessageHelper.replyToMessage(event, "Map update completed");
        }
    }

    public void handleCcUseOption(SlashCommandInteractionEvent event, Tile tile, String color, Game game) {
        OptionMapping ccUseOption = event.getOption(Constants.CC_USE);
        if (ccUseOption == null) {
            return;
        }
        String value = ccUseOption.getAsString().toLowerCase();
        switch (value) {
            case "t/tactics", "t", "tactics", "tac", "tact" -> {
                removeTacticsCC(event, color, tile, game);
                CommandCounterHelper.addCC(event, color, tile);
                Helper.isCCCountCorrect(event, game, color);
            }
            case "r/retreat/reinforcements", "r", "retreat", "reinforcements" -> {
                CommandCounterHelper.addCC(event, color, tile);
                Helper.isCCCountCorrect(event, game, color);
            }
        }
    }

    public static void removeTacticsCC(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        for (Player player : game.getPlayers().values()) {
            if (color.equals(player.getColor())) {
                int cc = player.getTacticalCC();
                if (cc == 0) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "You don't have CC in Tactics");
                    break;
                } else if (!CommandCounterHelper.hasCC(event, color, tile)) {
                    cc -= 1;
                    player.setTacticalCC(cc);
                    break;
                }
            }
        }
    }
}
