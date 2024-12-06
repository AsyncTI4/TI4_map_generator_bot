package ti4.commands2.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.manage.GameManager;
import ti4.map.manage.GameSaveService;
import ti4.message.MessageHelper;

class ControlGameCreation extends Subcommand {

    public ControlGameCreation() {
        super(Constants.CONTROL_GAME_CREATION, "Stop or allow game creation buttons to be pressed");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ALLOW_GAME_CREATION,
            "True to allow the button to be pressed").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // GAME NAME
        Game mapReference = GameManager.getGame("finreference");
        boolean allowGameCreation = event.getOption(Constants.ALLOW_GAME_CREATION).getAsBoolean();
        if (!allowGameCreation) {
            mapReference.setStoredValue("allowedButtonPress", "false");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set game creation button presses as unallowed");
        } else {
            mapReference.setStoredValue("allowedButtonPress", "true");
            MessageHelper.sendMessageToChannel(event.getChannel(), "Set game creation button presses as allowed");
        }
        GameSaveService.saveGame(mapReference, "Updated Setting");
    }
}
