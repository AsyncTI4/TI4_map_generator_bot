package ti4.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.SpinRingsHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.player.RefreshCardsService;

@UtilityClass
public class StatusCleanupService {

    public void runStatusCleanup(Game game) {
        game.removeStoredValue("deflectedSC");
        game.removeStoredValue("pharadnPNUsed");
        Map<String, Tile> tileMap = game.getTileMap();
        for (Tile tile : tileMap.values()) {
            for (Player toldar : game.getRealPlayers()) {
                if (ButtonHelper.doesPlayerHaveFSHere("toldar_flagship", toldar, tile)) {
                    for (Player player : game.getRealPlayers()) {
                        if (player == toldar) {
                            continue;
                        }
                        if (CommandCounterHelper.hasCC(player, tile)) {
                            String msg =
                                    player.getRepresentation() + ", in order to remove your command token from tile "
                                            + tile.getRepresentationForButtons()
                                            + " you need to first spend 1 command token from your command sheet, due to the ability of the Errant, the Toldar flagship."
                                            + " If you don't wish to spend this command token, then your token will stay in the system. Use buttons to decide.";
                            List<Button> buttons = new ArrayList<>();
                            buttons.add(Buttons.gray("placeCCBack_" + tile.getPosition(), "Don't Spend"));
                            buttons.add(Buttons.red("lose1CC", "Spend 1 Command Token"));
                            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
                        }
                    }
                }
            }
            tile.removeAllCC();
            Map<String, UnitHolder> unitHolders = tile.getUnitHolders();
            for (UnitHolder unitHolder : unitHolders.values()) {
                unitHolder.removeAllCC();
                unitHolder.removeAllUnitDamage();
            }
        }
        game.removeStoredValue("galacticThreatUsed");
        game.removeStoredValue("conspiratorsUsed");
        game.removeStoredValue("violatedSystems");
        Map<Integer, Boolean> scPlayed = game.getScPlayed();
        for (Map.Entry<Integer, Boolean> sc : scPlayed.entrySet()) {
            sc.setValue(false);
        }

        returnEndStatusPNs(game); // return any PNs with "end of status phase" return timing
        closeRoundThreads(game);

        Map<String, Player> players = game.getPlayers();
        if (ButtonHelper.isLawInPlay(game, "tf-censure")) {
            game.removeLaw("tf-censure");
        }
        for (Player player : players.values()) {

            player.setPassed(false);
            Set<Integer> SCs = player.getSCs();
            for (int sc : SCs) {
                game.setScTradeGood(sc, 0);
            }
            player.setInRoundTurnCount(0);
            player.clearSCs();
            player.clearFollowedSCs();
            player.setBreakthroughExhausted(false);
            RefreshCardsService.refreshPlayerCards(game, player, true);
            game.removeStoredValue("passOnAllWhensNAfters" + player.getFaction());
            game.removeStoredValue(player.getFaction() + "scpickqueue");

            if (player.isRealPlayer()
                    && game.getStoredValue("Pre Pass " + player.getFaction()) != null
                    && game.getStoredValue("Pre Pass " + player.getFaction()).contains(player.getFaction())) {
                if (game.getStoredValue("Pre Pass " + player.getFaction()).contains(player.getFaction())
                        && !player.isPassed()) {
                    game.setStoredValue("Pre Pass " + player.getFaction(), "");
                }
            }
            if (player.getPromissoryNotesInPlayArea().contains("sigma_cyber")) {
                for (String planet : game.getPlanets()) {
                    game.getPlanetsInfo().get(planet).removeToken("attachment_sigma_cyber.png");
                }
                player.removePromissoryNote("sigma_cyber");
                Player nonActivePlayer = game.getPNOwner("sigma_cyber");
                nonActivePlayer.setPromissoryNote("sigma_cyber");
                PromissoryNoteHelper.sendPromissoryNoteInfo(game, nonActivePlayer, false);
                PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get("sigma_cyber");
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(), "_" + pnModel.getName() + "_ has been returned.");
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
        game.removeStoredValue("hiredGunsInPlay");
        game.removeStoredValue("allianceModeSimultaneousAction");
        game.removeStoredValue("Coup");
        game.removeStoredValue("PublicExecution");
        game.setHasHadAStatusPhase(true);
        if (game.getSpinMode() != null && !"OFF".equalsIgnoreCase(game.getSpinMode())) {
            if ("ON".equalsIgnoreCase(game.getSpinMode())) {
                SpinRingsHelper.spinRings(game);
            } else {
                SpinRingsHelper.spinRingsCustom(game, game.getSpinMode(), null);
            }
        }
        if (!game.isFowMode() && game.getTableTalkChannel() != null && !game.isOmegaPhaseMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getTableTalkChannel(), "## End of Round #" + game.getRound() + " Scoring Info");
            ListPlayerInfoService.displayerScoringProgression(game, true, game.getTableTalkChannel(), "both");
        }
        game.clearAllEmptyStoredValues();
    }

    private void returnEndStatusPNs(Game game) {
        Map<String, Player> players = game.getPlayers();
        for (Player player : players.values()) {
            List<String> pns = new ArrayList<>(player.getPromissoryNotesInPlayArea());
            for (String pn : pns) {
                // MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Checking a new pn");
                Player pnOwner = game.getPNOwner(pn);
                if (pnOwner == null || !pnOwner.isRealPlayer()) {
                    continue;
                }
                PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(pn);
                if (pnModel.getText().contains("eturn this card")
                        && pnModel.getText().contains("end of the status phase")) {
                    player.removePromissoryNote(pn);
                    pnOwner.setPromissoryNote(pn);
                    PromissoryNoteHelper.sendPromissoryNoteInfo(game, pnOwner, false);
                    PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
                    MessageHelper.sendMessageToChannel(
                            player.getCorrectChannel(),
                            "_" + pnModel.getName() + "_ has been returned to " + pnOwner.getRepresentationNoPing()
                                    + ".");
                }
            }
        }
    }

    private static void closeRoundThreads(Game game) {
        String threadName = "-round-" + game.getRound();
        try {
            TextChannel main = game.getMainGameChannel();
            for (ThreadChannel thread : main.getThreadChannels()) {
                if (thread.getName().contains(threadName)) {
                    thread.getManager().setArchived(true).queueAfter(10, TimeUnit.SECONDS);
                }
            }
        } catch (Exception e) {
        }
    }
}
