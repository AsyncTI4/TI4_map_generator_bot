package ti4.commands.status;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.RefreshLeader;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.*;

public class PersonalCleanup extends StatusSubcommandData {

    public PersonalCleanup() {
        super(Constants.PERSONAL_CLEANUP, "Status phase cleanup");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())){
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }
        Game activeGame = getActiveGame();
        runStatusCleanup(activeGame);
    }

    public void runStatusCleanup(Game activeGame) {

        HashMap<String, Tile> tileMap = activeGame.getTileMap();
        Player player = activeGame.getPlayer(getUser().getId());
        String color = player.getColor();
        String ccID = Mapper.getCCID(color);

        for (Tile tile : tileMap.values()) {
            tile.removeCC(ccID);
            String ccPath = tile.getCCPath(ccID);

            HashMap<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                unitHolder.removeToken(ccPath);
                unitHolder.removeAllUnitDamage();
            }
        }
        HashMap<Integer, Boolean> scPlayed = activeGame.getScPlayed();
        for (Map.Entry<Integer, Boolean> sc : scPlayed.entrySet()) {
            if (player.getSCs().contains(sc.getKey()))
                sc.setValue(false);
        }

        player.setPassed(false);
        Set<Integer> SCs = player.getSCs();
        for (int sc : SCs) {
            activeGame.setScTradeGood(sc, 0);
        }
        player.clearSCs();
        player.clearFollowedSCs();
        player.cleanExhaustedTechs();
        player.cleanExhaustedPlanets(true);
        player.cleanExhaustedRelics();
        player.clearExhaustedAbilities();
      List<Leader> leads = new ArrayList<>(player.getLeaders());
        for (Leader leader : leads) {
            if (!leader.isLocked()){
                if (leader.isActive()){
                    player.removeLeader(leader.getId());
                } else {
                    RefreshLeader.refreshLeader(player, leader, activeGame);
                }
            }
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
        StatusCommand.reply(event, "Player has completed status phase.");
    }
}
