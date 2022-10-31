package ti4.commands.cards;

import net.dv8tion.jda.api.entities.Emoji;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
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

        String value = option.getAsString().toLowerCase();
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
                            MessageHelper.sendMessageToChannel(event.getChannel(), "Multiple cards with similar name founds, please use ID");
                            return;
                        }
                        acID = ac.getKey();
                        foundSimilarName = true;
                        cardName = actionCardName;
                    }
                }
            }
        }
        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }
        activeMap.discardActionCard(player.getUserID(), acIndex);
        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getGamePing(event, activeMap)).append(" ").append(activeMap.getName()).append(" ");
        sb.append("Player: ").append(player.getUserName()).append("\n");
        sb.append("Played: ");
        sb.append(Mapper.getActionCard(acID)).append("\n");

        Emoji sabotage = Emoji.fromMarkdown(":sabotage:");
        Emoji noSabo = Emoji.fromMarkdown(":nosabo:");

        for (Emote emote : event.getJDA().getEmotes()) {
            if (emote.getName().toLowerCase().contains("sabotage")) {
                sabotage = Emoji.fromEmote(emote);
            } else if (emote.getName().toLowerCase().contains("nosabo")) {
                noSabo = Emoji.fromEmote(emote);
            }
        }
        Button sabotageButton = Button.danger("sabotage", "Sabotage").withEmoji(sabotage);
        Button noSabotageButton = Button.primary("no_sabotage", "No Sabotage").withEmoji(noSabo);
        if (acID.contains("sabo")) {
            MessageHelper.sendMessageToChannelWithButtons(event, sb.toString());
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event, sb.toString(), sabotageButton, noSabotageButton);
        }
        CardsInfo.sentUserCardInfo(event, activeMap, player);
    }
}
