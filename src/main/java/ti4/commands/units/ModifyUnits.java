package ti4.commands.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands2.CommandHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ModifyUnits implements Command {

    @Override
    public String getName() {
        return Constants.MODIFY_UNITS;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getName());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game  game = GameManager.getUserActiveGame(event.getUser().getId());
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
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

    @Override
    public void register(CommandListUpdateAction commands) {
        commands.addCommands(Commands.slash(getName(), "Present the Modify Units menu"));
    }

}
