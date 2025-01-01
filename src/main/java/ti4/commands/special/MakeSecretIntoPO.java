package ti4.commands.special;

import java.util.LinkedHashMap;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class MakeSecretIntoPO extends GameStateSubcommand {

    public MakeSecretIntoPO() {
        super(Constants.MAKE_SO_INTO_PO, "Make a secret objective into a public objective", true, false);
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID, which is found between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        int soID = event.getOption(Constants.SECRET_OBJECTIVE_ID).getAsInt();
        String soName = "";
        Player playerWithSO = null;

        for (Map.Entry<String, Player> playerEntry : game.getPlayers().entrySet()) {
            Player player_ = playerEntry.getValue();
            Map<String, Integer> secretsScored = new LinkedHashMap<>(player_.getSecretsScored());
            for (Map.Entry<String, Integer> soEntry : secretsScored.entrySet()) {
                if (soEntry.getValue() == soID) {
                    soName = soEntry.getKey();
                    playerWithSO = player_;
                    break;
                }
            }
        }

        if (playerWithSO == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found.");
            return;
        }
        if (soName.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Only a scored secret objective may be made into a public objective.");
            return;
        }
        game.addToSoToPoList(soName);
        Integer poIndex = game.addCustomPO(soName, 1);
        game.scorePublicObjective(playerWithSO.getUserID(), poIndex);

        String sb = "**Public Objective added from Secret:**" + "\n" +
            "(" + poIndex + ") " + "\n" +
            Mapper.getSecretObjectivesJustNames().get(soName) + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);

        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, playerWithSO, event);

    }
}
