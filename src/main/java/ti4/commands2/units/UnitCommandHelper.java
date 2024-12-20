package ti4.commands2.units;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.Nullable;
import ti4.commands2.CommandHelper;
import ti4.commands2.commandcounter.RemoveCommandCounterService;
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
            MessageHelper.replyToMessage(event, "Update completed");
        }
    }

    public void handleCcUseOption(SlashCommandInteractionEvent event, Tile tile, String color, Game game) {
        OptionMapping ccUseOption = event.getOption(Constants.CC_USE);
        if (ccUseOption == null) {
            return;
        }
        String value = ccUseOption.getAsString().toLowerCase();
        switch (value) {
            case "t/tactic", "t", "tactic", "tac", "tact" -> {
                RemoveCommandCounterService.fromTacticsPool(event, color, tile, game);
                CommandCounterHelper.addCC(event, color, tile);
                Helper.isCCCountCorrect(event, game, color);
            }
            case "r/retreat/reinforcements", "r", "retreat", "reinforcements" -> {
                CommandCounterHelper.addCC(event, color, tile);
                Helper.isCCCountCorrect(event, game, color);
            }
        }
    }

    @Nullable
    static String getTargetColor(SlashCommandInteractionEvent event, Game game) {
        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer != null) {
            return otherPlayer.getColor();
        }
        MessageHelper.replyToMessage(event, Constants.TARGET_FACTION_OR_COLOR + " option is not valid. Use `/special2 setup_neutral_player` for neutrals.");
        return null;
    }
}
