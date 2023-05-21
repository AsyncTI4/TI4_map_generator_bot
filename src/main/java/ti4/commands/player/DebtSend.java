package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class DebtSend extends PlayerSubcommandData {
    public DebtSend() {
        super(Constants.SEND_DEBT, "Sent Debt to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.DEBT_COUNT, "Debt count").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send Debt").setAutoComplete(true).setRequired(true));
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
        Player player_ = Helper.getPlayer(activeMap, player, event);
        if (player_ == null) {
            sendMessage("Player to send Debt could not be found");
            return;
        }

        OptionMapping optionTG = event.getOption(Constants.DEBT_COUNT);
        if (optionTG != null) {
            int sendTG = optionTG.getAsInt();
            int tg = player.getTg();
            sendTG = Math.min(sendTG, tg);
            tg -= sendTG;
            player.setTg(tg);

            int targetTG = player_.getTg();
            targetTG += sendTG;
            player_.setTg(targetTG);

            sendMessage(Helper.getPlayerRepresentation(player, activeMap) + " sent " + sendTG + Emojis.tg + " trade goods to " + Helper.getPlayerRepresentation(player_, activeMap));
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
        MapSaveLoadManager.saveMap(activeMap, event);
    }
}
