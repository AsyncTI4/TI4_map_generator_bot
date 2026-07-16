package ti4.discord.interactions.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.service.turn.StartTurnService;

class Unpass extends GameStateSubcommand {

    public Unpass() {
        super(Constants.UNPASS, "Unpass", true, true);
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        Game game = getGame();
        game.setPhaseOfGame("action");
        player.setPassed(false);
        StartTurnService.turnStart(event, getGame(), player);
    }
}
