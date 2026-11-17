package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupService;
import ti4.service.megagame.MegagameScOverviewService;

class MegagameEnable extends MegagameGmSubcommand {

    MegagameEnable() {
        super("enable", "Turn on strategy card groups for this game", true);
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        if (game.isFowMode()) {
            return "SC groups are not supported in Fog of War.";
        }
        if (!MegagameGroupService.isConfigured(game)) {
            return "No groups yet. Run `/megagame init` first.";
        }
        if (!MegagameGroupService.hasTag(game)) {
            game.addTag(MegagameGroupService.SC_GROUPS_TAG);
        }
        return withSummary(game, MegagameScOverviewService.ensureChannel(game));
    }
}
