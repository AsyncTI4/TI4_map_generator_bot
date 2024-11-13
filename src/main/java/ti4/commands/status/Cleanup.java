package ti4.commands.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.custom.SpinTilesInRings;
import ti4.commands.leaders.RefreshLeader;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class Cleanup extends GameStateSubcommand {

    public Cleanup() {
        super(Constants.CLEANUP, "Status phase cleanup", true, false);
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

        int prevRound = game.getRound() - 1;
        StatusCommand.reply(event, "End of round " + prevRound + " status phase.");
    }

    public void runStatusCleanup(Game game) {
        game.removeStoredValue("deflectedSC");
        Map<String, Tile> tileMap = game.getTileMap();
        for (Tile tile : tileMap.values()) {
            tile.removeAllCC();
            Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                unitHolder.removeAllCC();
                unitHolder.removeAllUnitDamage();
            }
        }
        Map<Integer, Boolean> scPlayed = game.getScPlayed();
        for (Map.Entry<Integer, Boolean> sc : scPlayed.entrySet()) {
            sc.setValue(false);
        }

        returnEndStatusPNs(game); // return any PNs with "end of status phase" return timing

        Map<String, Player> players = game.getPlayers();

        for (Player player : players.values()) {
            player.setPassed(false);
            Set<Integer> SCs = player.getSCs();
            for (int sc : SCs) {
                game.setScTradeGood(sc, 0);
            }
            player.clearSCs();
            player.setTurnCount(0);
            player.clearFollowedSCs();
            player.cleanExhaustedTechs();
            player.cleanExhaustedPlanets(true);
            player.cleanExhaustedRelics();
            player.clearExhaustedAbilities();

            if (player.isRealPlayer() && game.getStoredValue("Pre Pass " + player.getFaction()) != null
                && game.getStoredValue("Pre Pass " + player.getFaction()).contains(player.getFaction())) {
                if (game.getStoredValue("Pre Pass " + player.getFaction()).contains(player.getFaction()) && !player.isPassed()) {
                    game.setStoredValue("Pre Pass " + player.getFaction(), "");
                }
            }
            List<Leader> leads = new ArrayList<>(player.getLeaders());
            for (Leader leader : leads) {
                if (!leader.isLocked()) {
                    if (leader.isActive() && !leader.getId().equalsIgnoreCase("zealotshero")) {
                        player.removeLeader(leader.getId());
                    } else {
                        RefreshLeader.refreshLeader(player, leader, game);
                    }
                }
            }
        }
        for (int x = 0; x < 13; x++) {
            if (!game.getStoredValue("exhaustedSC" + x).isEmpty()) {
                game.setStoredValue("exhaustedSC" + x, "");
            }
        }
        game.setStoredValue("agendaCount", "0");
        game.removeStoredValue("absolMOW");
        game.removeStoredValue("naaluPNUser");
        game.removeStoredValue("politicalStabilityFaction");
        game.removeStoredValue("forcedScoringOrder");
        game.removeStoredValue("Public Disgrace");
        game.removeStoredValue("Public Disgrace Only");
        game.removeStoredValue("edynAgentPreset");
        game.removeStoredValue("Coup");
        game.removeStoredValue("PublicExecution");
        game.setHasHadAStatusPhase(true);
        if (game.isSpinMode()) {
            SpinTilesInRings.spinRings(game);
        }
        if (!game.isFowMode() && game.getTableTalkChannel() != null) {
            MessageHelper.sendMessageToChannel(game.getTableTalkChannel(), "## End of Round #" + game.getRound() + " Scoring Info");
            ListPlayerInfoButton.displayerScoringProgression(game, true, game.getTableTalkChannel(), "both");
        }
        game.clearAllEmptyStoredValues();
    }

    public void returnEndStatusPNs(Game game) {
        Map<String, Player> players = game.getPlayers();
        for (Player player : players.values()) {
            List<String> pns = new ArrayList<>(player.getPromissoryNotesInPlayArea());
            for (String pn : pns) {
                //MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Checking a new pn");
                Player pnOwner = game.getPNOwner(pn);
                if (pnOwner == null || !pnOwner.isRealPlayer()) {
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if (pnModel.getText().contains("eturn this card") && pnModel.getText().contains("end of the status phase")) {
                    player.removePromissoryNote(pn);
                    pnOwner.setPromissoryNote(pn);
                    PNInfo.sendPromissoryNoteInfo(game, pnOwner, false);
                    PNInfo.sendPromissoryNoteInfo(game, player, false);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), pnOwner.getFactionEmoji() + " " + pnModel.getName() + " was returned");
                }
            }
        }
    }
}
