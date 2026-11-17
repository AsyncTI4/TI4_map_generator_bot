package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.megagame.MegagameGroupService;

class MegagameSetSpeaker extends MegagameGmSubcommand {

    MegagameSetSpeaker() {
        super("set_speaker", "Make a faction the speaker of its group", true);
        addOptions(factionOption());
    }

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        Player player = targetPlayer(event, game);
        String group = MegagameGroupService.groupOf(game, player);
        if (group == null) {
            return "That faction is not in a group.";
        }
        MegagameGroupService.setGroupSpeaker(game, group, player);
        return withSummary(game, "");
    }
}
