package ti4.commands.custom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.SpinRingsHelper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class SpinTilesInRings extends GameStateSubcommand {

    public SpinTilesInRings() {
        super(
                Constants.SPIN_TILES_IN_RINGS,
                "Rotate normal 6p map according to Fin logic. /spin for advanced spinning.",
                true,
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.getSpinMode().equals("ON")) {
            MessageHelper.replyToMessage(
                    event,
                    "Enable classic Spin Mode first with `/custom customization spin_mode: ON` or use the advanced `/spin`");
            return;
        }
        SpinRingsHelper.spinRings(game);
    }
}
