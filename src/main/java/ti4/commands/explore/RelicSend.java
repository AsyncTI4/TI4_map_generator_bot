package ti4.commands.explore;

import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class RelicSend extends GenericRelicAction {
    public RelicSend() {
        super(Constants.RELIC_SEND, "Send a relic to another Player", true);
        addOptions(new OptionData(OptionType.STRING, Constants.RELIC, "Relic to send from Target to Source").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR_2, "Target Faction or Color").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source Faction or Color (default is you)").setAutoComplete(true));
    }

    public void doAction(Player player1, SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        String relicID = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        String targetFaction = event.getOption(Constants.FACTION_COLOR_2, null, OptionMapping::getAsString);
        String sourceFaction = event.getOption(Constants.FACTION_COLOR, null, OptionMapping::getAsString);


        //resolve player1
        if (sourceFaction != null) {
            String factionColor = AliasHandler.resolveColor(sourceFaction.toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeMap.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    player1 = player_;
                    break;
                }
            }
        }

        //resolve player2
        Player player2 = null; //Player to send to
        if (targetFaction != null) {
            String factionColor = AliasHandler.resolveColor(targetFaction.toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeMap.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    player2 = player_;
                    break;
                }
            }
        }
        if (player2 == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        if (player1.equals(player2)) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "The two players provided are the same player");
            return;
        }

        List<String> player1Relics = player1.getRelics();
        if (!player1Relics.contains(relicID)) {
            sendMessage(player1.getUserName() + " does not have relic: " + relicID);
            return;
        }

        player1.removeRelic(relicID);
        player2.addRelic(relicID);

        //HANDLE SHARD OF THE THRONE
        switch (relicID) {
            case "shard" -> {
                Integer shardPublicObjectiveID = activeMap.getCustomPublicVP().get("Shard of the Throne");
                if (shardPublicObjectiveID != null) {
                    activeMap.unscorePublicObjective(player1.getUserID(), shardPublicObjectiveID);
                    activeMap.scorePublicObjective(player2.getUserID(), shardPublicObjectiveID);
                }
            }
            case "absol_shardofthethrone1", "absol_shardofthethrone2", "absol_shardofthethrone3" -> {
                int absolShardNum = Integer.parseInt(StringUtils.right(relicID, 1));
                String customPOName = "Shard of the Throne (" + absolShardNum + ")";
                Integer shardPublicObjectiveID = activeMap.getCustomPublicVP().get(customPOName);
                if (shardPublicObjectiveID != null) {
                    activeMap.unscorePublicObjective(player1.getUserID(), shardPublicObjectiveID);
                    activeMap.scorePublicObjective(player2.getUserID(), shardPublicObjectiveID);
                }
            }
        }


        if (player1.getRelics().contains(relicID) || !player2.getRelics().contains(relicID)) {
            sendMessage("Something may have gone wrong - please check your relics and ping Bothelper if there is a problem.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getPlayerRepresentation(player1, activeMap));
        sb.append(" sent a relic to ").append(Helper.getPlayerRepresentation(player2, activeMap));
        sb.append("\n").append(Helper.getRelicRepresentation(relicID));
        sendMessage(sb.toString());
    }
}
