package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SpinRingsHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class SpinTilesInRings extends GameStateSubcommand {

    public SpinTilesInRings() {
        super(
                Constants.SPIN_TILES_IN_RINGS,
                "Rotate the map according to fin logic or give custom rotations",
                true,
                true);
        addOptions(new OptionData(
                OptionType.STRING,
                Constants.CUSTOM,
                "Custom rotation (Ring:Direction:Steps 1:cw:1 2:ccw:2 1,2:rnd:1,2)"));
        addOptions(new OptionData(
                OptionType.STRING, Constants.MESSAGE, "Flavour message to send to main channel after spins"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String customSpins = event.getOption(Constants.CUSTOM, "", OptionMapping::getAsString);
        String flavourMsg = event.getOption(Constants.MESSAGE, null, OptionMapping::getAsString);

        if (customSpins.isEmpty()) {
            SpinRingsHelper.spinRings(game);
        } else {
            if (!SpinRingsHelper.validateSpinSettings(customSpins)) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Invalid spin settings: " + customSpins);
                return;
            }

            SpinRingsHelper.spinRingsCustom(game, customSpins, flavourMsg);
        }
    }
}
