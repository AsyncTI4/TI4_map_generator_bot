package ti4.discord.interactions.commands.tf;

import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.tech.PlayerTechService;

class GuildAgents extends GameStateSubcommand {

    public GuildAgents() {
        super(Constants.GUILD_AGENTS, "Do Guild Agents", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        Game game = getGame();
        List<Button> buttons = PlayerTechService.getMageonImplantsButtons(game, player);
        String message = player.getRepresentationUnfogged()
                + ", please choose the player who should be the target of guild agents.";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
    }
}
