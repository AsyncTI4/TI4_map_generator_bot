package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupService;

class MegagameRotate extends MegagameGmSubcommand {

    MegagameRotate() {
        super("rotate", "Shift every group one column now (normally automatic when the strategy phase starts)", true);
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        MegagameGroupService.rotate(game);
        return withSummary(game, "Groups rotated.");
    }
}
