package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.megagame.MegagameGmPanelService;

class MegagameCardsInfo extends MegagameEphemeralSubcommand {

    MegagameCardsInfo() {
        super("cards_info", "Show a player's secret objectives, action cards and promissory notes (only to you)");
        addOptions(factionOption());
    }

    @Override
    void runEphemeral(SlashCommandInteractionEvent event, Game game) {
        Player target = targetPlayer(event, game);
        if (target == null) {
            reply(event, "Could not find that player.");
            return;
        }
        reply(event, MegagameGmPanelService.cardsInfo(game, target));
    }
}
