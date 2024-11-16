package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.commands2.GameStateCommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ModifyUnits extends GameStateCommand {

    public ModifyUnits() {
        super(true, true);
    }

    @Override
    public String getName() {
        return Constants.MODIFY_UNITS;
    }

    @Override
    public String getDescription() {
        return "Modify units";
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game  game = getGame();
        Player player = getPlayer();
        getModifyTiles(player, game);
    }

    @ButtonHandler("getModifyTiles")
    public static void getModifyTiles(Player player, Game game) {
        List<Button> buttons = ButtonHelper.getTilesToModify(player, game);
        String message = player.getRepresentation() + " Use the buttons to select the tile in which you wish to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("modifyUnitsAllTiles")
    public static void modifyUnitsAllTiles(Player player, Game game) {
        List<Button> buttons = ButtonHelper.getAllTilesToModify(player, game, "genericModifyAllTiles", true);
        String message = player.getRepresentation() + " Use the buttons to select the tile in which you wish to modify units. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }
}
