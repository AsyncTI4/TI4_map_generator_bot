package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupService;

class MegagameClear extends MegagameGmSubcommand {

    MegagameClear() {
        super("clear", "Remove all stored groups and turn strategy card groups off", true);
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        MegagameGroupService.clearGroupState(game);
        game.getTags().removeIf(MegagameGroupService.SC_GROUPS_TAG::equalsIgnoreCase);
        return "Megagame groups cleared.";
    }
}
