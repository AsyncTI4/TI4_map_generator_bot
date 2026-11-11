package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.megagame.MegagameChannelAccessService;

class MegagameChannels extends MegagameEphemeralSubcommand {

    MegagameChannels() {
        super("channels", "List the channels a player can see, with links where the GM role has access (only to you)");
        addOptions(factionOption());
    }

    @Override
    void runEphemeral(SlashCommandInteractionEvent event, Game game) {
        Player target = targetPlayer(event, game);
        if (target == null) {
            reply(event, "Could not find that player.");
            return;
        }
        MegagameChannelAccessService.channelReport(game, target, event.getMember(), report -> reply(event, report));
    }
}
