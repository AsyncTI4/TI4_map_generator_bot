package ti4.commands.status;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.RefreshLeader;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.custom.SpinTilesInRings;
import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;
import ti4.generator.Mapper;
import ti4.model.PromissoryNoteModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Cleanup extends StatusSubcommandData {
    public Cleanup() {
        super(Constants.CLEANUP, "Status phase cleanup");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Confirm command with YES").setRequired(true));
    }

    @Override

    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }
        Game game = getActiveGame();
        runStatusCleanup(game);
    }

    public void runStatusCleanup(Game game) {

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
        game.setStoredValue("absolMOW", "");
        game.setStoredValue("agendaCount", "0");
        game.setStoredValue("politicalStabilityFaction", "");
        game.setStoredValue("forcedScoringOrder", "");
        game.setStoredValue("Public Disgrace", "");
        game.setStoredValue("Public Disgrace Only", "");
        game.setStoredValue("edynAgentPreset", "");
        game.setStoredValue("Coup", "");
        game.setStoredValue("PublicExecution", "");
        game.setHasHadAStatusPhase(true);
        if (game.isSpinMode()) {
            SpinTilesInRings.spinRings(game);
        }
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

    @Override

    public void reply(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        int prevRound = game.getRound() - 1;

        StatusCommand.reply(event, "End of round " + prevRound + " status phase.");
    }
}
