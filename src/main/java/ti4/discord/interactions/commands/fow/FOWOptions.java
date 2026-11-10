package ti4.discord.interactions.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.service.option.FOWOptionService;

class FOWOptions extends GameStateSubcommand {

    public FOWOptions() {
        super(Constants.FOW_OPTIONS, "GM: change the Fog of War options and presets", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        boolean gameHasGm = !getGame().getPlayersWithGMRole().isEmpty();
        if (gameHasGm && !FoWHelper.isGameMaster(event.getUser().getId(), getGame())) {
            MessageHelper.replyToMessage(event, "Only the GM can change the fog options.");
            return;
        }
        FOWOptionService.offerFOWOptionButtons(getGame());
    }
}
