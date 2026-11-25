package ti4.discord.interactions.commands.megagame;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.game.Game;
import ti4.service.megagame.MegagameGmPanelService;
import ti4.service.megagame.MegagameGroupService;

abstract class MegagameEphemeralSubcommand extends MegagameGmSubcommand {

    MegagameEphemeralSubcommand(String name, String description) {
        super(name, description, false);
    }

    @Override
    public boolean isEphemeral(SlashCommandInteractionEvent event) {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!MegagameGmPanelService.isAvailable(game)) {
            reply(
                    event,
                    "Needs the `" + MegagameGroupService.SC_GROUPS_TAG
                            + "` tag (run `/megagame enable`) in a non-fog game.");
            return;
        }
        runEphemeral(event, game);
    }

    abstract void runEphemeral(SlashCommandInteractionEvent event, Game game);

    @Override
    String run(SlashCommandInteractionEvent event, Game game) {
        return "";
    }

    static void reply(SlashCommandInteractionEvent event, String message) {
        MegagameGmPanelService.sendEphemeral(event.getHook(), message, null);
    }
}
