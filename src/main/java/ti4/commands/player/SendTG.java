package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendTG extends PlayerSubcommandData {
    public SendTG() {
        super(Constants.SEND_TG, "Sent TG to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade goods count").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send TG").setAutoComplete(true).setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        Player player_ = Helper.getPlayer(activeMap, player, event);
        if (player_ == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player to send TG/Commodities could not be found");
            return;
        }

        OptionMapping optionTG = event.getOption(Constants.TG);
        if (optionTG != null) {
            int sendTG = optionTG.getAsInt();
            int tg = player.getTg();
            sendTG = Math.min(sendTG, tg);
            tg -= sendTG;
            player.setTg(tg);

            int targetTG = player_.getTg();
            targetTG += sendTG;
            player_.setTg(targetTG);

            MessageHelper.sendMessageToChannel(event.getChannel(), getPlayerRepresentation(event, player) + " send " + sendTG + " tg to: " + getPlayerRepresentation(event, player_));
        }
    }

    public static String getPlayerRepresentation(SlashCommandInteractionEvent event, Player player) {
        String text = "";
        String playerFaction = player.getFaction();
        text += Helper.getFactionIconFromDiscord(playerFaction);
        text += " " + Helper.getPlayerPing(event, player);
        return text;
    }
}
