package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.RefreshLeader;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

public class Cleanup extends StatusSubcommandData {
    public Cleanup() {
        super(Constants.CLEANUP, "Status phase cleanup");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())){
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        Map activeMap = getActiveMap();
        HashMap<String, Tile> tileMap = activeMap.getTileMap();
        for (Tile tile : tileMap.values()) {
            tile.removeAllCC();
            HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                unitHolder.removeAllCC();
                unitHolder.removeAllUnitDamage();
            }
        }
        HashMap<Integer, Boolean> scPlayed = activeMap.getScPlayed();
        for (java.util.Map.Entry<Integer, Boolean> sc : scPlayed.entrySet()) {
            sc.setValue(false);
        }
        LinkedHashMap<String, Player> players = activeMap.getPlayers();

        for (Player player : players.values()) {
            player.setPassed(false);
            Set<Integer> SCs = player.getSCs();
            for (int sc : SCs) {
                activeMap.setScTradeGood(sc, 0);
            }
            player.clearSCs();
            player.clearFollowedSCs();
            player.cleanExhaustedTechs();
            player.cleanExhaustedPlanets(true);
            player.cleanExhaustedRelics();

            for (Leader leader : player.getLeaders()) {
                if (!leader.isLocked()){
                    if (leader.isActive()){
                        player.removeLeader(leader.getId());
                    } else {
                        RefreshLeader.refreshLeader(player, leader);
                    }
                }
            }
        }
        int round = activeMap.getRound();
        round++;
        activeMap.setRound(round);
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        int prevRound = activeMap.getRound() - 1;

        StatusCommand.reply(event, "End of round " + prevRound + " status phase.");
    }
}
