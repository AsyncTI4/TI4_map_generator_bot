package ti4.commands.cardsac;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlayAC extends ACCardsSubcommandData {
    public PlayAC() {
        super(Constants.PLAY_AC, "Play Action Card");
        addOptions(new OptionData(OptionType.STRING, Constants.ACTION_CARD_ID, "Action Card ID that is sent between () or Name/Part of Name").setRequired(true));
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

        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            sendMessage("Please select what Action Card to discard");
            return;
        }
        
        String reply = playAC(event, activeMap, player, option.getAsString().toLowerCase(), event.getChannel(), event.getGuild());
        if (reply != null) {
            sendMessage(reply);
        }
    }

    public static String playAC(GenericInteractionCreateEvent event, Map activeMap, Player player, String value, MessageChannel channel, Guild guild) {
        MessageChannel mainGameChannel = activeMap.getMainGameChannel() == null ? channel : activeMap.getMainGameChannel();
        
        String acID = null;
        int acIndex = -1;
        try {
            acIndex = Integer.parseInt(value);
            for (java.util.Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
                if (so.getValue().equals(acIndex)) {
                    acID = so.getKey();
                }
            }
        } catch (Exception e) {
            boolean foundSimilarName = false;
            String cardName = "";
            for (java.util.Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
                String actionCardName = Mapper.getActionCardName(ac.getKey());
                if (actionCardName != null) {
                    actionCardName = actionCardName.toLowerCase();
                    if (actionCardName.contains(value)) {
                        if (foundSimilarName && !cardName.equals(actionCardName)) {
                            return "Multiple cards with similar name founds, please use ID";
                        }
                        acID = ac.getKey();
                        acIndex = ac.getValue();
                        foundSimilarName = true;
                        cardName = actionCardName;
                    }
                }
            }
        }
        if (acID == null) {
            //sendMessage();
            return "No such Action Card ID found, please retry";
        }
        String[] actionCard = Mapper.getActionCard(acID).split(";");
        String actionCardTitle = actionCard[0];
        String actionCardPhase = actionCard[1];
        String actionCardWindow = actionCard[2];
        String actionCardText = actionCard[3];

        String activePlayerID = activeMap.getActivePlayer();
        if (player.isPassed() && activePlayerID != null) {
            Player activePlayer = activeMap.getPlayer(activePlayerID);
            if (activePlayer != null && activePlayer.getTechs().contains("tp")) {
                return "You are passed and the active player has researched Transparasteel Plating. AC Play command cancelled.";
            }
        }

        activeMap.discardActionCard(player.getUserID(), acIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(guild, activeMap)).append(" ").append(activeMap.getName()).append("\n");

        if (activeMap.isFoWMode()) {
            sb.append("Someone played an Action Card:\n");
        } else {
            sb.append(Helper.getPlayerRepresentation(event, player)).append(" played an Action Card:\n");
        }
        sb.append(Emojis.ActionCard).append("__**").append(actionCardTitle).append("**__ (").append(actionCardPhase).append(" Phase)\n");
        sb.append(">  _").append(actionCardWindow).append(":_\n");
        sb.append(">  ").append(actionCardText).append("\n");

        Button sabotageButton = Button.danger("sabotage", "Sabotage").withEmoji(Emoji.fromFormatted(Emojis.Sabotage));
        Button noSabotageButton = Button.primary("no_sabotage", "No Sabotage").withEmoji(Emoji.fromFormatted(Emojis.NoSabotage));
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannelWithButtons(mainGameChannel, sb.toString(), null);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(mainGameChannel, sb.toString(), guild, sabotageButton, noSabotageButton);
        }
        
        //Fog of war ping
		if (activeMap.isFoWMode()) {
            String fowMessage = Helper.getPlayerRepresentation(event, player) + " played an Action Card: " + actionCardTitle;
			FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, fowMessage);
		}

        ACInfo_Legacy.sentUserCardInfo(event, activeMap, player, false);
        return null;
    }
}
