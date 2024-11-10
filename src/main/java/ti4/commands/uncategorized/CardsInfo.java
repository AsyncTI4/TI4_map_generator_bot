package ti4.commands.uncategorized;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.buttons.Buttons;
import ti4.commands.Command;
import ti4.commands.cardsac.ACInfo;
import ti4.commands.cardspn.PNInfo;
import ti4.commands.cardsso.SOInfo;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

public class CardsInfo implements Command {

    @Override
    public String getActionId() {
        return Constants.CARDS_INFO;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfIsAdminOrIsPartOfGame(getActionId(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game;
        if (!UserGameContextManager.doesUserHaveContextGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        } else {
            game = UserGameContextManager.getContextGame(userID);
            String color = Helper.getColor(game, event);
            if (!Mapper.isValidColor(color)) {
                MessageHelper.replyToMessage(event, "Color/Faction not valid");
                return;
            }
        }

        Player player = game.getPlayer(userID);
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        game.checkPromissoryNotes();
        PNInfo.checkAndAddPNs(game, player);
        sendCardsInfo(game, player, event);
    }

    @ButtonHandler("cardsInfo")
    public static void sendCardsInfo(Game game, Player player, GenericInteractionCreateEvent event) {
        if (player == null)
            return;
        String headerText = player.getRepresentationUnfogged() + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game, headerText);
        sendCardsInfo(game, player);
    }

    public static void sendCardsInfo(Game game, Player player) {
        SOInfo.sendSecretObjectiveInfo(game, player);
        ACInfo.sendActionCardInfo(game, player);
        PNInfo.sendPromissoryNoteInfo(game, player, false);
        sendVariousAdditionalButtons(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, game,
                """
                        You may whisper to people from here by starting a message with to[color] or to[faction].\

                        You may schedule a message to yourself (delivered at start of your next turn) by starting a message with tofutureme\

                        You may schedule a message to others (delivered at start of their next turn) by starting a message with tofuture[color] or tofuture[faction]""");

    }

    public static void sendVariousAdditionalButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        Button transaction = Buttons.blue("transaction", "Transaction");
        buttons.add(transaction);
        Button modify = Buttons.gray("getModifyTiles", "Modify Units");
        buttons.add(modify);
        if (game.playerHasLeaderUnlockedOrAlliance(player, "naalucommander")) {
            Button naalu = Buttons.gray("naaluCommander", "Do Naalu Commander", Emojis.Naalu);
            buttons.add(naalu);
        }
        if (player.hasAbility("oracle_ai") || player.getPromissoryNotesInPlayArea().contains("dspnauge")) {
            Button augers = Buttons.gray("initialPeak", "Peek At Next Objective", Emojis.augers);
            buttons.add(augers);
        }
        if (player.hasUnexhaustedLeader("mykomentoriagent")) {
            Button nekroButton = Buttons.gray("exhaustAgent_mykomentoriagent",
                "Use Myko-Mentori Agent", Emojis.mykomentori);
            buttons.add(nekroButton);
        }
        if (player.hasUnexhaustedLeader("hacanagent")) {
            Button hacanButton = Buttons.gray("exhaustAgent_hacanagent",
                "Use Hacan Agent", Emojis.Hacan);
            buttons.add(hacanButton);
        }
        if (ButtonHelper.isPlayerElected(game, player, "minister_peace")) {
            Button hacanButton = Buttons.gray("ministerOfPeace", "Use Minister of Peace", Emojis.Agenda);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("vadenagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_vadenagent",
                "Use Vaden Agent", Emojis.vaden);
            buttons.add(hacanButton);
        } // olradinagent
        if (player.hasUnexhaustedLeader("olradinagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_olradinagent",
                "Use Olradin Agent", Emojis.olradin);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("edynagent")) {
            Button hacanButton2 = Buttons.gray("presetEdynAgentStep1", "Preset Edyn Agent", Emojis.edyn);
            buttons.add(hacanButton2);
        }
        if (player.hasUnexhaustedLeader("celdauriagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_celdauriagent",
                "Use Celdauri Agent", Emojis.celdauri);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("cheiranagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_cheiranagent",
                "Use Cheiran Agent", Emojis.cheiran);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("freesystemsagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_freesystemsagent",
                "Use Free Systems Agent", Emojis.freesystems);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("florzenagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_florzenagent",
                "Use Florzen Agent", Emojis.florzen);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("nokaragent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_nokaragent",
                "Use Nokar Agent", Emojis.nokar);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("zelianagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_zelianagent",
                "Use Zelian Agent", Emojis.zelian);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("mirvedaagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_mirvedaagent",
                "Use Mirveda Agent", Emojis.mirveda);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("cymiaeagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_cymiaeagent",
                "Use Cymiae Agent", Emojis.cymiae);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("mortheusagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_mortheusagent",
                "Use Mortheus Agent", Emojis.mortheus);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("zealotsagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_zealotsagent",
                "Use Rhodun Agent", Emojis.zealots);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("rohdhnaagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_rohdhnaagent",
                "Use Roh'Dhna Agent", Emojis.rohdhna);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("veldyragent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_veldyragent",
                "Use Veldyr Agent", Emojis.veldyr);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("gledgeagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_gledgeagent",
                "Use Gledge Agent", Emojis.gledge);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("khraskagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_khraskagent",
                "Use Khrask Agent", Emojis.khrask);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("nivynagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_nivynagent",
                "Use Nivyn Agent", Emojis.nivyn);
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("ghotiagent")) {
            Button hacanButton = Buttons.gray("getAgentSelection_ghotiagent",
                "Use Ghoti Agent", Emojis.ghoti);
            buttons.add(hacanButton);
        }
        if (!player.getNomboxTile().getUnitHolders().get("space").getUnits().isEmpty()) {
            Button release = Buttons.gray("getReleaseButtons", "Release captured units", Emojis.Cabal);
            buttons.add(release);
        }
        if (player.hasRelicReady("e6-g0_network")) {
            buttons.add(Buttons.green("exhauste6g0network", "Exhaust E6-G0 Network Relic to Draw AC"));
        }
        if (player.hasTech("pa") && ButtonHelper.getPsychoTechPlanets(game, player).size() > 1) {
            Button psycho = Buttons.green("getPsychoButtons", "Use Psychoarcheology");
            psycho = psycho.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
            buttons.add(psycho);
        }
        if (player.hasUnexhaustedLeader("nekroagent")) {
            Button nekroButton = Buttons.gray("exhaustAgent_nekroagent",
                "Use Nekro Agent", Emojis.Nekro);
            buttons.add(nekroButton);
        }
        if (player.hasUnexhaustedLeader("vaylerianagent")) {
            Button nekroButton = Buttons.gray("exhaustAgent_vaylerianagent",
                "Use Vaylerian Agent", Emojis.vaylerian);
            buttons.add(nekroButton);
        }
        if (player.ownsUnit("ghost_mech")
            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", false) > 0
            && !ButtonHelper.isLawInPlay(game, "articles_war")) {
            Button ghostButton = Buttons.gray("creussMechStep1_", "Use Creuss Mech", Emojis.Ghost);
            buttons.add(ghostButton);
        }
        if (player.ownsUnit("nivyn_mech2")
            && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", false) > 0
            && !game.getLaws().containsKey("articles_war")) {
            Button ghostButton = Buttons.gray("nivynMechStep1_", "Use Nivyn Mech", Emojis.nivyn);
            buttons.add(ghostButton);
        }
        if (player.hasUnexhaustedLeader("kolleccagent")) {
            Button nekroButton = Buttons.gray("exhaustAgent_kolleccagent",
                "Use Kollecc Agent", Emojis.kollecc);
            buttons.add(nekroButton);
        }
        if (player.hasAbility("cunning")) {
            buttons.add(Buttons.green("setTrapStep1", "Set a Trap"));
            buttons.add(Buttons.red("revealTrapStep1", "Reveal a Trap"));
            buttons.add(Buttons.gray("removeTrapStep1", "Remove a Trap"));
        }

        if (player.hasAbility("divination") && !ButtonHelperAbilities.getAllOmenDie(game).isEmpty()) {
            StringBuilder omenDice = new StringBuilder();
            for (int omenDie : ButtonHelperAbilities.getAllOmenDie(game)) {
                omenDice.append(" ").append(omenDie);
            }
            omenDice = new StringBuilder(omenDice.toString().trim());
            Button augers = Buttons.gray("getOmenDice", "Use an omen die (" + omenDice + ")", Emojis.mykomentori);
            buttons.add(augers);
        }
        Button playerPref = Buttons.gray("offerPlayerPref", "Player Settings");
        buttons.add(playerPref);
        Button listGames = Buttons.gray("searchMyGames", "List My Games");
        buttons.add(listGames);
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

        String message = "You may use these buttons to do various things:";

        // Refresh the various buttons if they're the last message in the thread
        player.getCardsInfoThread().retrieveMessageById(player.getCardsInfoThread().getLatestMessageId()).queue(
            msg -> {
                if (msg != null && message.equals(msg.getContentRaw())) {
                    msg.delete().queue();
                }
            }, BotLogger::catchRestError);

        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
    }

    protected String getActionDescription() {
        return "Send to your Cards Info thread: Scored & Unscored SOs, ACs, and PNs in both hand and Play Area";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionId(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY, "Long promissory display, y or yes to show full promissory text").setRequired(false)));
    }

}
