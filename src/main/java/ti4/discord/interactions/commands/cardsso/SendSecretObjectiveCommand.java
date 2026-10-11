package ti4.discord.interactions.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.model.SecretObjectiveModel;
import ti4.service.info.SecretObjectiveInfoService;

class SendSecretObjectiveCommand extends GameStateSubcommand {

    SendSecretObjectiveCommand() {
        super("send_secret", "Send a Secret Objective to a player", true, true);
        addOptions(CommonOptions.targetFactionOrColor());
        addOptions(new OptionData(
                        OptionType.INTEGER,
                        Constants.SECRET_OBJECTIVE_ID,
                        "Secret objective ID, which is found between ()")
                .setRequired(true));
        addOptions(CommonOptions.factionColor("Source faction or color (default is you)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        Integer secretObjectiveId =
                event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        if (!player.getSecrets().containsValue(secretObjectiveId)) {
            MessageHelper.sendMessageToEventChannel(event, "Could not find a secret objective with that id.");
            return;
        }

        Player otherPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (otherPlayer == null) return;

        SecretObjectiveModel secretObjective = player.getSecret(secretObjectiveId);
        player.removeSecret(secretObjectiveId);
        otherPlayer.setSecret(secretObjective.getID());

        SecretObjectiveInfoService.sendSecretObjectiveInfoWithHeaderText(
                game, player, "You sent a secret objective to another player.", false, false);

        SecretObjectiveInfoService.sendSecretObjectiveInfoWithHeaderText(
                game, otherPlayer, "You were sent a secret objective by another player.", false, false);
    }
}
