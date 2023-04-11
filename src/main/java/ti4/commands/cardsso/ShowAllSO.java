package ti4.commands.cardsso;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cardsac.ACInfo_Legacy;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;

public class ShowAllSO extends SOCardsSubcommandData {
    public ShowAllSO() {
        super(Constants.SHOW_ALL_SO, "Show all Secret Objectives to one player");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Game: ").append(activeMap.getName()).append("\n");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Showed Secret Objectives:").append("\n");
        List<String> secrets = new ArrayList<>(player.getSecrets().keySet());
        LinkedHashMap<String, Integer> secretsScored = player.getSecretsScored();
        Collections.shuffle(secrets);
        for (String id : secrets) {
            sb.append(Mapper.getSecretObjective(id)).append("\n");
            if (!secretsScored.containsKey(id)) {
                player.setSecret(id);
            }
        }

        Player player_ = Helper.getPlayer(activeMap, null, event);
        if (player_ == null) {
            sendMessage("Player not found");
            return;
        }
        sendMessage("All SOs shown to player");
        MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, sb.toString());
        ACInfo_Legacy.sentUserCardInfo(event, activeMap, player);
    }
}
