package ti4.service.info;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands.CommandHelper;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.PromissoryNoteHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.fow.RiftSetModeService;

@UtilityClass
public class CardsInfoService {

    public static void sendCardsInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        if (player == null)
            return;
        String headerText = player.getRepresentationUnfogged() + CommandHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, headerText);
        sendCardsInfo(game, player);
    }

    public static void sendCardsInfo(Game game, Player player) {
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        ActionCardHelper.sendActionCardInfo(game, player);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);
        sendVariousAdditionalButtons(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player,
            """
                You may whisper to people from here by starting a message with `to[color]` or `to[faction]`.\

                You may schedule a message to yourself (delivered at start of your next turn) by starting a message with `tofutureme`.\

                You may schedule a message to others (delivered at start of their next turn) by starting a message with `tofuture[color]` or `tofuture[faction]`.""");

    }

    public static void sendVariousAdditionalButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Button transaction = Buttons.blue("transaction", "Transaction");
        buttons.add(transaction);
        Button modify = Buttons.gray("getModifyTiles", "Modify Units");
        buttons.add(modify);
        if (game.playerHasLeaderUnlockedOrAlliance(player, "naalucommander")) {
            buttons.add(Buttons.gray("naaluCommander", "Do Naalu Commander", FactionEmojis.Naalu));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "uydaicommander")) {
            buttons.add(Buttons.gray("uydaiCommander", "Pay 1tg to Use Uydai Commander", FactionEmojis.uydai));
        }
        if (player.hasAbility("oracle_ai") || player.getPromissoryNotesInPlayArea().contains("dspnauge")) {
            buttons.add(Buttons.gray("initialPeak", "Peek At Next Objective", FactionEmojis.augers));
        }
        if (player.hasUnexhaustedLeader("mykomentoriagent")) {
            buttons.add(Buttons.gray("exhaustAgent_mykomentoriagent", "Use Myko-Mentori Agent", FactionEmojis.mykomentori));
        }
        if (player.hasUnexhaustedLeader("hacanagent")) {
            buttons.add(Buttons.gray("exhaustAgent_hacanagent", "Use Hacan Agent", FactionEmojis.Hacan));
        }
        if (player.hasUnexhaustedLeader("pharadnagent")) {
            buttons.add(Buttons.gray("exhaustAgent_pharadnagent", "Use Pharadn Agent", FactionEmojis.pharadn));
        }
        if (ButtonHelper.isPlayerElected(game, player, "minister_peace")) {
            buttons.add(Buttons.gray("ministerOfPeace", "Use Minister of Peace", CardEmojis.Agenda));
        }
        if (player.hasUnexhaustedLeader("vadenagent")) {
            buttons.add(Buttons.gray("getAgentSelection_vadenagent", "Use Vaden Agent", FactionEmojis.vaden));
        }
        if (player.hasUnexhaustedLeader("olradinagent")) {
            buttons.add(Buttons.gray("getAgentSelection_olradinagent", "Use Olradin Agent", FactionEmojis.olradin));
        }
        if (player.hasUnexhaustedLeader("edynagent")) {
            buttons.add(Buttons.gray("presetEdynAgentStep1", "Preset Edyn Agent", FactionEmojis.edyn));
        }
        if (player.hasUnexhaustedLeader("celdauriagent")) {
            buttons.add(Buttons.gray("getAgentSelection_celdauriagent", "Use Celdauri Agent", FactionEmojis.celdauri));
        }
        if (player.hasUnexhaustedLeader("cheiranagent")) {
            buttons.add(Buttons.gray("getAgentSelection_cheiranagent", "Use Cheiran Agent", FactionEmojis.cheiran));
        }
        if (player.hasUnexhaustedLeader("freesystemsagent")) {
            buttons.add(Buttons.gray("getAgentSelection_freesystemsagent", "Use Free Systems Agent", FactionEmojis.freesystems));
        }
        if (player.hasUnexhaustedLeader("florzenagent")) {
            buttons.add(Buttons.gray("getAgentSelection_florzenagent", "Use Florzen Agent", FactionEmojis.florzen));
        }
        if (player.hasUnexhaustedLeader("nokaragent")) {
            buttons.add(Buttons.gray("getAgentSelection_nokaragent", "Use Nokar Agent", FactionEmojis.nokar));
        }
        if (player.hasUnexhaustedLeader("zelianagent")) {
            buttons.add(Buttons.gray("getAgentSelection_zelianagent", "Use Zelian Agent", FactionEmojis.zelian));
        }
        if (player.hasUnexhaustedLeader("mirvedaagent")) {
            buttons.add(Buttons.gray("getAgentSelection_mirvedaagent", "Use Mirveda Agent", FactionEmojis.mirveda));
        }
        if (player.hasUnexhaustedLeader("cymiaeagent")) {
            buttons.add(Buttons.gray("getAgentSelection_cymiaeagent", "Use Cymiae Agent", FactionEmojis.cymiae));
        }
        if (player.hasUnexhaustedLeader("mortheusagent")) {
            buttons.add(Buttons.gray("getAgentSelection_mortheusagent", "Use Mortheus Agent", FactionEmojis.mortheus));
        }
        if (player.hasUnexhaustedLeader("zealotsagent")) {
            buttons.add(Buttons.gray("getAgentSelection_zealotsagent", "Use Rhodun Agent", FactionEmojis.zealots));
        }
        if (player.hasUnexhaustedLeader("rohdhnaagent")) {
            buttons.add(Buttons.gray("getAgentSelection_rohdhnaagent", "Use Roh'Dhna Agent", FactionEmojis.rohdhna));
        }
        if (player.hasUnexhaustedLeader("veldyragent")) {
            buttons.add(Buttons.gray("getAgentSelection_veldyragent", "Use Veldyr Agent", FactionEmojis.veldyr));
        }
        if (player.hasUnexhaustedLeader("gledgeagent")) {
            buttons.add(Buttons.gray("getAgentSelection_gledgeagent", "Use Gledge Agent", FactionEmojis.gledge));
        }
        if (player.getPathTokenCounter() > 0) {
            buttons.add(Buttons.gray("redistributePath", "Redistribute 1 CC With Path", FactionEmojis.uydai));
        }
        if (player.hasUnexhaustedLeader("uydaiagent")) {
            buttons.add(Buttons.gray("getAgentSelection_uydaiagent", "Use Uydai Agent", FactionEmojis.uydai));
        }
        if (player.hasUnexhaustedLeader("khraskagent")) {
            buttons.add(Buttons.gray("getAgentSelection_khraskagent", "Use Khrask Agent", FactionEmojis.khrask));
        }
        if (player.hasUnexhaustedLeader("nivynagent")) {
            buttons.add(Buttons.gray("getAgentSelection_nivynagent", "Use Nivyn Agent", FactionEmojis.nivyn));
        }
        if (player.hasUnexhaustedLeader("ghotiagent")) {
            buttons.add(Buttons.gray("getAgentSelection_ghotiagent", "Use Ghoti Agent", FactionEmojis.ghoti));
        }
        if (!player.getNomboxTile().getUnitHolders().get("space").getUnits().isEmpty()) {
            FactionEmojis f = FactionEmojis.Cabal;
            if (player.hasAbility("mark_of_pharadn")) {
                f = FactionEmojis.pharadn;
            }
            if (player.hasAbility("shroud_of_lith")) {
                f = FactionEmojis.kollecc;
            }
            buttons.add(Buttons.gray("getReleaseButtons", "Release Captured Units", f));
        }
        if (player.hasRelicReady("e6-g0_network")) {
            buttons.add(Buttons.green("exhauste6g0network", "Exhaust E6-G0 Network Relic to Draw Action Card"));
        }
        if (player.hasTech("pa") && ButtonHelper.getPsychoTechPlanets(game, player).size() > 1) {
            buttons.add(Buttons.green("getPsychoButtons", "Use Psychoarcheology", TechEmojis.BioticTech));
        }
        if (player.hasUnexhaustedLeader("nekroagent")) {
            buttons.add(Buttons.gray("exhaustAgent_nekroagent", "Use Nekro Agent", FactionEmojis.Nekro));
        }
        if (player.hasUnexhaustedLeader("vaylerianagent")) {
            buttons.add(Buttons.gray("exhaustAgent_vaylerianagent", "Use Vaylerian Agent", FactionEmojis.vaylerian));
        }
        if (player.ownsUnit("ghost_mech")
            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", false) > 0
            && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            buttons.add(Buttons.gray("creussMechStep1_", "Use Creuss Mech", FactionEmojis.Ghost));
        }
        if (player.ownsUnit("nivyn_mech2")
            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", false) > 0
            && !game.getLaws().containsKey("articles_war")) {
            buttons.add(Buttons.gray("nivynMechStep1_", "Use Nivyn Mech", FactionEmojis.nivyn));
        }
        if (player.hasUnexhaustedLeader("kolleccagent")) {
            buttons.add(Buttons.gray("exhaustAgent_kolleccagent", "Use Kollecc Agent", FactionEmojis.kollecc));
        }
        if (player.hasAbility("cunning")) {
            buttons.add(Buttons.green("setTrapStep1", "Set a Trap"));
            buttons.add(Buttons.red("revealTrapStep1", "Reveal a Trap"));
            buttons.add(Buttons.gray("removeTrapStep1", "Remove a Trap"));
        }
        if (player.hasTech("absol_vw")) {
            buttons.add(Buttons.gray("resolveExp_Look_frontier", "Top Of Frontier Deck", FactionEmojis.Empyrean));
        }
        if (game.getPhaseOfGame().toLowerCase().contains("agendawaiting")) {
            buttons.add(Buttons.blue("declineToQueueAWhen", "Pass On Whens"));
        }

        if (player.hasAbility("divination") && !ButtonHelperAbilities.getAllOmenDie(game).isEmpty()) {
            StringBuilder omenDice = new StringBuilder();
            for (int omenDie : ButtonHelperAbilities.getAllOmenDie(game)) {
                omenDice.append(" ").append(omenDie);
            }
            omenDice = new StringBuilder(omenDice.toString().trim());
            buttons.add(Buttons.gray("getOmenDice", "Use an omen die (" + omenDice + ")", FactionEmojis.mykomentori));
        }
        buttons.add(Buttons.gray("offerPlayerPref", "Player Settings"));
        buttons.add(Buttons.gray("searchMyGames", "List My Games"));
        buttons.add(Buttons.green("showObjInfo_both", "Scoring Info"));
        if (!game.isFowMode()) {
            buttons.add(Buttons.gray("chooseMapView", "Map Features"));
        }
        boolean hadAnyUnplayedSCs = false;
        for (Integer SC : player.getSCs()) {
            if (!game.getPlayedSCs().contains(SC)) {
                hadAnyUnplayedSCs = true;
            }
        }
        if (!hadAnyUnplayedSCs) {
            buttons.add(Buttons.red("resolvePreassignment_Pre Pass " + player.getFaction(), "Pass on Next Turn"));
        }
        buttons.add(Buttons.REFRESH_INFO);

        List<String> phasesBeforeAction = List.of("miltydraft", "action", "strategy", "playerSetup");
        boolean hasSummary = false;
        for (int x = 1; x <= game.getRound(); ++x) {
            if (!game.getStoredValue("endofround" + x + player.getFaction()).isEmpty())
                hasSummary = true;
        }
        if (game.getRound() > 1 || !phasesBeforeAction.contains(game.getPhaseOfGame()) || hasSummary) {
            // after the action phase round 1, show the edit summary button by default
            buttons.add(Buttons.EDIT_SUMMARIES);
        }
        buttons.add(Buttons.POST_NOTEPAD);
        buttons.add(Buttons.EDIT_NOTEPAD);
        buttons.add(Buttons.green("cardsInfo", "Cards Info Refresh"));
        RiftSetModeService.addCapturedUnitsButton(buttons, game);

        String message = "You may use these buttons to do various things:";

        // Refresh the various buttons if they're the last message in the thread
        player.getCardsInfoThread().retrieveMessageById(player.getCardsInfoThread().getLatestMessageId()).queue(msg -> {
            if (msg != null && message.equals(msg.getContentRaw())) {
                msg.delete().queue();
            }
        }, BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }
}
