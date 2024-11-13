package ti4.commands.special;

import java.util.LinkedHashMap;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsso.SOInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class MakeSecretIntoPO extends SpecialSubcommandData {
    public MakeSecretIntoPO() {
        super(Constants.MAKE_SO_INTO_PO, "Make Secret Objective a Public Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Secret Objective to make Public");
            return;
        }

        int soID = option.getAsInt();
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
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
            return;
        }
        if (soName.isEmpty()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Can make just Scored SO to Public");
            return;
        }
        game.addToSoToPoList(soName);
        Integer poIndex = game.addCustomPO(soName, 1);
        game.scorePublicObjective(playerWithSO.getUserID(), poIndex);

        String sb = "**Public Objective added from Secret:**" + "\n" +
            "(" + poIndex + ") " + "\n" +
            Mapper.getSecretObjectivesJustNames().get(soName) + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);

        SOInfo.sendSecretObjectiveInfo(game, playerWithSO, event);

    }
}
