package ti4.commands.explore;

import java.util.List;
import java.util.Objects;

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
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Target Faction or Color").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR_2, "Source Faction or Color (default is you)").setAutoComplete(true));
    }

    public void doAction(Player player, SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        String relicID = event.getOption(Constants.RELIC, null, OptionMapping::getAsString);
        String targetFaction = event.getOption(Constants.FACTION_COLOR, null, OptionMapping::getAsString);
        String sourceFaction = event.getOption(Constants.FACTION_COLOR_2, player.getFaction(), OptionMapping::getAsString);


        //resolve player1
        Player player1 = null; //OG player
        if (sourceFaction == null) {
            player1 = player;
        } else {
            String factionColor = AliasHandler.resolveColor(sourceFaction.toLowerCase());
            factionColor = AliasHandler.resolveFaction(factionColor);
            for (Player player_ : activeMap.getPlayers().values()) {
                if (Objects.equals(factionColor, player_.getFaction()) || Objects.equals(factionColor, player_.getColor())) {
                    player1 = player_;
                    break;
                }
            }
        }
        if (player1 == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
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
            MessageHelper.sendMessageToChannel(event.getChannel(), "Players provided are the same player");
            return;
        }

        List<String> player1Relics = player1.getRelics();
        if (!player1Relics.contains(relicID)) {
            sendMessage(player1.getUserName() + " does not have relic: " + relicID);
        }
        player1.removeRelic(relicID);
        player2.addRelic(relicID);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getPlayerRepresentation(event, player1, false));
        sb.append(" sent a relic to ").append(Helper.getPlayerRepresentation(event, player2, false));
        sb.append("\n").append(Helper.getRelicRepresentation(relicID));
        sendMessage(sb.toString());
    }
}
