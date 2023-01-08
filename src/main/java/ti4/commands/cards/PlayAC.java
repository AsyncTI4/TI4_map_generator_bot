package ti4.commands.cards;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PlayAC extends CardsSubcommandData {
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
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Action Card to discard");
            return;
        }

        playAC(event, activeMap, player, option.getAsString().toLowerCase(), event.getChannel(), event.getGuild(), null);
    }

    public static void playAC(GenericCommandInteractionEvent event, Map activeMap, Player player, String value, MessageChannel channel, Guild guild, ButtonInteractionEvent buttonInteractionEvent) {
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
                            MessageHelper.sendMessageToChannel(channel, "Multiple cards with similar name founds, please use ID");
                            return;
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
            MessageHelper.sendMessageToChannel(channel, "No such Action Card ID found, please retry");
            return;
        }
        String[] actionCard = Mapper.getActionCard(acID).split(";");
        String actionCardTitle = actionCard[0];
        String actionCardPhase = actionCard[1];
        String actionCardWindow = actionCard[2];
        String actionCardText = actionCard[3];

        activeMap.discardActionCard(player.getUserID(), acIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(guild, activeMap)).append(" ").append(activeMap.getName()).append("\n");
        sb.append(Helper.getPlayerRepresentation(event, player)).append(" played an Action Card:\n");
        sb.append(Emojis.ActionCard).append("__**").append(actionCardTitle).append("**__ (").append(actionCardPhase).append(" Phase)\n");
        sb.append(">  _").append(actionCardWindow).append(":_\n");
        sb.append(">  ").append(actionCardText).append("\n");

        Button sabotageButton = Button.danger("sabotage", "Sabotage").withEmoji(Emoji.fromMarkdown(Emojis.Sabotage));
        Button noSabotageButton = Button.primary("no_sabotage", "No Sabotage").withEmoji(Emoji.fromMarkdown(Emojis.NoSabotage));
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannelWithButtons(channel, sb.toString(), null);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(channel, sb.toString(), guild, sabotageButton, noSabotageButton);
        }
        CardsInfo.sentUserCardInfo(event, activeMap, player, buttonInteractionEvent);
    }
}
