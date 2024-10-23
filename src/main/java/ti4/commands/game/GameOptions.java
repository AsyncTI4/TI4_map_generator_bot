package ti4.commands.game;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.listeners.annotations.ButtonHandler;
import ti4.message.MessageHelper;

public class GameOptions extends GameSubcommandData {

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
        factionReactButtons.add(Buttons.green("enableAidReacts", "Yes, Enable Faction Reactions"));
        factionReactButtons.add(Buttons.red("deleteButtons", "No Faction Reactions"));
        MessageHelper.sendMessageToChannel(channel, "A frequently used aid is the bot reacting with your faction emoji when you speak, to help others remember your faction. You can enable that with this button. Other such customization options, or if you want to turn this off, are under `/custom customization`", factionReactButtons);

        List<Button> hexBorderButtons = new ArrayList<>();
        hexBorderButtons.add(Buttons.green("showHexBorders_dash", "Dashed line"));
        hexBorderButtons.add(Buttons.blue("showHexBorders_solid", "Solid line"));
        hexBorderButtons.add(Buttons.red("showHexBorders_off", "Off (default)"));
        MessageHelper.sendMessageToChannel(channel, "Show borders around systems with player's ships, either a dashed line or a solid line. You can also control this setting with `/custom customization`", hexBorderButtons);
    }

    //TODO: move button handlers from UnfiledButtonHandlers.java to here: enableAidReacts, showHexBorders_

}
