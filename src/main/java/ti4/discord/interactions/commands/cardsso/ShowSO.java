package ti4.discord.interactions.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class ShowSO extends GameStateSubcommand {

    ShowSO() {
        super(Constants.SHOW_SO, "Show a Secret Objective to a player", true, true);
        addOptions(new OptionData(
                        OptionType.INTEGER,
                        Constants.SECRET_OBJECTIVE_ID,
                        "Secret objective ID, which is found between ()")
                .setRequired(true));
        addOptions(CommonOptions.targetFactionOrColor("Target faction or color"));
        addOptions(CommonOptions.factionColor("Faction or Color (defaults to you)"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                Constants.ONLY_PHASE,
                "Show only the phase of the secret objective (action/agenda/status). Default false"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int soIndex = event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        String soID = CommandHelper.requireOrReport(
                event,
                CommandHelper.cardIdForNumber(player.getSecrets(), soIndex),
                "No such secret objective ID found, please retry.");
        if (soID == null) return;

        String info = SecretObjectiveInfoService.getSecretObjectiveRepresentation(soID);
        boolean onlyPhase = event.getOption(Constants.ONLY_PHASE, Boolean.FALSE, OptionMapping::getAsBoolean);
        if (onlyPhase) {
            info = Mapper.getSecretObjective(soID).getPhase();
        }
        Game game = getGame();
        String sb = "Game: " + game.getName() + "\n" + "Player: "
                + player.getUserName() + "\n" + "Showed Secret Objectives:"
                + "\n" + info
                + "\n";

        player.setSecret(soID);

        Player targetPlayer = CommandHelper.getOtherPlayerOrReply(game, event);
        if (targetPlayer == null) return;

        MessageHelper.sendMessageToEventChannel(event, "Secret objective shown to player.");
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(targetPlayer, sb);
    }
}
