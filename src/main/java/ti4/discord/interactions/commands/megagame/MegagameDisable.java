package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupService;

class MegagameDisable extends MegagameGmSubcommand {

    MegagameDisable() {
        super("disable", "Turn off strategy card groups (stored groups are kept)", true);
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        game.getTags().removeIf(MegagameGroupService.SC_GROUPS_TAG::equalsIgnoreCase);
        return withSummary(game, "");
    }
}
