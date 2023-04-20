package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class MakeSecretIntoPO extends SpecialSubcommandData {
    public MakeSecretIntoPO() {
        super(Constants.MAKE_SO_INTO_PO, "Make Secret Objective a Public Objective");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECRET_OBJECTIVE_ID, "Secret objective ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping option = event.getOption(Constants.SECRET_OBJECTIVE_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Secret Objective to make Public");
            return;
        }

        int soID = option.getAsInt();
        String soName = "";
        Player playerWithSO = null;


        for (java.util.Map.Entry<String, Player> playerEntry : activeMap.getPlayers().entrySet()) {
            Player player_ = playerEntry.getValue();
            LinkedHashMap<String, Integer> secretsScored = new LinkedHashMap<>(player_.getSecretsScored());
            for (java.util.Map.Entry<String, Integer> soEntry : secretsScored.entrySet()) {
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
        activeMap.addToSoToPoList(soName);
        Integer poIndex = activeMap.addCustomPO(soName, 1);
        activeMap.scorePublicObjective(playerWithSO.getUserID(), poIndex);

        String sb = "**Public Objective added from Secret:**" + "\n" +
                "(" + poIndex + ") " + "\n" +
                Mapper.getSecretObjectivesJustNames().get(soName) + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);

        ACInfo_Legacy.sentUserCardInfo(event, activeMap, playerWithSO);

    }
}