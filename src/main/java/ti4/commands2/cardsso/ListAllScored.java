package ti4.commands2.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class ListAllScored extends GameStateSubcommand {

    public ListAllScored() {
        super(Constants.SO_LIST_SCORED, "Displays scored secret objectives", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("__**Scored Secret Objectives**__\n");

        Game game = getGame();
        Player currentPlayer = getPlayer();
        for (Player player : game.getPlayers().values().stream().toList()) {
            if (!game.isFowMode() || FoWHelper.canSeeStatsOfPlayer(game, player, currentPlayer)) {
                for (String objective : player.getSecretsScored().keySet()) {
                    sb.append(player.getFactionEmoji()).append(SecretObjectiveInfoService.getSecretObjectiveRepresentation(objective));
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }
}
