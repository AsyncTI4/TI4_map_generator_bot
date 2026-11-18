package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.service.megagame.MegagameGmPanelService;

class MegagameGm extends MegagameEphemeralSubcommand {

    MegagameGm() {
        super("gm", "Show megagame GM buttons (only visible to you)");
    }

    @Override
    void runEphemeral(SlashCommandInteractionEvent event, Game game) {
        MegagameGmPanelService.sendPanel(event.getHook(), game, event.getMember());
    }
}
