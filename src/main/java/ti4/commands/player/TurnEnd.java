package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.turn.EndTurnService;

class TurnEnd extends GameStateSubcommand {

    public TurnEnd() {
        super(Constants.TURN_END, "End Turn", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "In FoW, confirm with YES if you are not the active player"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        if (game.isFowMode() && !player.equals(game.getActivePlayer())) {
            OptionMapping confirm = event.getOption(Constants.CONFIRM);
            if (confirm == null || !"YES".equals(confirm.getAsString())) {
                MessageHelper.sendMessageToEventChannel(event, "You are not the active player. Confirm End Turn with YES.");
                return;
            }
        }

        EndTurnService.pingNextPlayer(event, game, player);
        player.resetOlradinPolicyFlags();
    }
}
