package ti4.commands2.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.leader.RefreshLeaderService;

class PersonalCleanup extends GameStateSubcommand {

    public PersonalCleanup() {
        super(Constants.PERSONAL_CLEANUP, "Status phase cleanup", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }
        Game game = getGame();
        runStatusCleanup(game);
        MessageHelper.replyToMessage(event, "Player has completed status phase.");
    }

    private void runStatusCleanup(Game game) {
        Map<String, Tile> tileMap = game.getTileMap();
        Player player = getPlayer();
        String color = player.getColor();
        String ccID = Mapper.getCCID(color);

        for (Tile tile : tileMap.values()) {
            tile.removeCC(ccID);
            String ccPath = tile.getCCPath(ccID);

            Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                unitHolder.removeToken(ccPath);
                unitHolder.removeAllUnitDamage();
            }
        }
        Map<Integer, Boolean> scPlayed = game.getScPlayed();
        for (Map.Entry<Integer, Boolean> sc : scPlayed.entrySet()) {
            if (player.getSCs().contains(sc.getKey()))
                sc.setValue(false);
        }

        player.setPassed(false);
        Set<Integer> strategyCards = player.getSCs();
        for (int sc : strategyCards) {
            game.setScTradeGood(sc, 0);
        }
        player.clearSCs();
        player.clearFollowedSCs();
        player.cleanExhaustedTechs();
        player.cleanExhaustedPlanets(true);
        player.cleanExhaustedRelics();
        player.clearExhaustedAbilities();
        List<Leader> leads = new ArrayList<>(player.getLeaders());
        for (Leader leader : leads) {
            if (!leader.isLocked()) {
                if (leader.isActive()) {
                    player.removeLeader(leader.getId());
                } else {
                    RefreshLeaderService.refreshLeader(player, leader, game);
                }
            }
        }
    }
}
