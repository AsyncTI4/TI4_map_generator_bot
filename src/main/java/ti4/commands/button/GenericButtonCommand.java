package ti4.commands.button;

import java.util.Collections;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

public class GenericButtonCommand implements Command {

    @Override
    public String getActionID() {
        return Constants.BUTTON;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (event.getName().equals(getActionID())) {
            String userID = event.getUser().getId();
            MapManager mapManager = MapManager.getInstance();
            if (!mapManager.isUserWithActiveMap(userID)) {
                MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
                return false;
            }
            Map userActiveMap = mapManager.getUserActiveMap(userID);
            if (!userActiveMap.getPlayerIDs().contains(userID) && !userActiveMap.isCommunityMode()) {
                MessageHelper.replyToMessage(event, "You're not a player of the game, please call function /join gameName");
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String buttonText = event.getOption(Constants.BUTTON_TEXT, "Button", OptionMapping::getAsString);
        String message = null;

        // Max button text is 80, so if higher, post a separate message and just ask to record responses
        if (buttonText.length() > 80) {
            message = buttonText;
            buttonText = "Record Response";
        }

        Button button = Button.secondary(Constants.GENERIC_BUTTON_ID_PREFIX + event.getId(), buttonText);

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, Collections.singletonList(button));
    }

    protected String getActionDescription() {
        return "Send a single generic button to the channel to collect faction responses.";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.BUTTON_TEXT, "The text/prompt that will appear on the button itself. Max 80 characters.").setRequired(true))
        );
    }
}
