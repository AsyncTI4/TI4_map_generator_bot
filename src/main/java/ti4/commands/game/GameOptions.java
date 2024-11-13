package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class GameOptions extends GameStateSubcommand {

    public GameOptions() {
        super(Constants.OPTIONS, "Modify some Game Options");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        offerGameOptionButtons(event.getMessageChannel());
    }

    @ButtonHandler("offerGameOptionButtons")
    public static void offerGameOptionButtons(MessageChannel channel) {
        List<Button> factionReactButtons = new ArrayList<>();
        factionReactButtons.add(Buttons.green("enableAidReacts", "Enable Faction Reactions"));
        factionReactButtons.add(Buttons.red("deleteButtons", "No Faction Reactions"));
        MessageHelper.sendMessageToChannel(channel, "Enable to have the bot react to player messages with their faction emoji.", factionReactButtons);

        List<Button> hexBorderButtons = new ArrayList<>();
        hexBorderButtons.add(Buttons.green("showHexBorders_dash", "Dashed line"));
        hexBorderButtons.add(Buttons.blue("showHexBorders_solid", "Solid line"));
        hexBorderButtons.add(Buttons.red("showHexBorders_off", "Off (default)"));
        MessageHelper.sendMessageToChannel(channel, "Show borders around systems with player's ships.", hexBorderButtons);
    }

    @ButtonHandler("enableAidReacts")
    public static void enableAidReact(ButtonInteractionEvent event, Game game) {
        game.setBotFactionReacts(true);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Faction reaction icons have been enabled. Use `/game options` to change this.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("showHexBorders_")
    public static void editShowHexBorders(ButtonInteractionEvent event, Game game, String buttonID) {
        String value = buttonID.replace("showHexBorders_", "");
        game.setHexBorderStyle(value);
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "Updated Hex Border Style to `" + value + "`.\nUse `/game options` to change this.");
        ButtonHelper.deleteMessage(event);
    }

}
