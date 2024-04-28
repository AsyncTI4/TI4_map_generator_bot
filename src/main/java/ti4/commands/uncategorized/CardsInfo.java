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
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class CardsInfo implements Command, InfoThreadCommand {

    @Override
    public String getActionID() {
        return Constants.CARDS_INFO;
    }

    public boolean accept(SlashCommandInteractionEvent event) {
        return acceptEvent(event, getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        GameManager gameManager = GameManager.getInstance();
        Game activeGame;
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
            return;
        } else {
            activeGame = gameManager.getUserActiveGame(userID);
            String color = Helper.getColor(activeGame, event);
            if (!Mapper.isValidColor(color)) {
                MessageHelper.replyToMessage(event, "Color/Faction not valid");
                return;
            }
        }

        Player player = activeGame.getPlayer(userID);
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        activeGame.checkPromissoryNotes();
        PNInfo.checkAndAddPNs(activeGame, player);
        sendCardsInfo(activeGame, player, event);
    }

    public static void sendCardsInfo(Game activeGame, Player player, GenericInteractionCreateEvent event) {
        if (player == null)
            return;
        String headerText = player.getRepresentation(true, true) + CardsInfoHelper.getHeaderText(event);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame, headerText);
        sendCardsInfo(activeGame, player);
    }

    public static void sendCardsInfo(Game activeGame, Player player) {
        SOInfo.sendSecretObjectiveInfo(activeGame, player);
        ACInfo.sendActionCardInfo(activeGame, player);
        PNInfo.sendPromissoryNoteInfo(activeGame, player, false);
        sendVariousAdditionalButtons(activeGame, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(player, activeGame,
            "You can whisper to people from here by starting a message with to[color] or to[faction]." +
                "\nYou can schedule a message to yourself (delivered at start of your next turn) by starting a message with tofutureme"
                +
                "\nYou can schedule a message to others (delivered at start of their next turn) by starting a message with tofuture[color] or tofuture[faction]");

    }

    public static void sendVariousAdditionalButtons(Game activeGame, Player player) {
        List<Button> buttons = new ArrayList<>();
        Button transaction = Button.primary("transaction", "Transaction");
        buttons.add(transaction);
        Button modify = Button.secondary("getModifyTiles", "Modify Units");
        buttons.add(modify);
        if (activeGame.playerHasLeaderUnlockedOrAlliance(player, "naalucommander")) {
            Button naalu = Button.secondary("naaluCommander", "Do Naalu Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.Naalu));
            buttons.add(naalu);
        }
        if (player.hasAbility("oracle_ai") || player.getPromissoryNotesInPlayArea().contains("dspnauge")) {
            Button augers = Button.secondary("initialPeak", "Peek At Next Objective")
                .withEmoji(Emoji.fromFormatted(Emojis.augers));
            buttons.add(augers);
        }
        if (player.hasAbility("divination") && !ButtonHelperAbilities.getAllOmenDie(activeGame).isEmpty()) {
            StringBuilder omenDice = new StringBuilder();
            for (int omenDie : ButtonHelperAbilities.getAllOmenDie(activeGame)) {
                omenDice.append(" ").append(omenDie);
            }
            omenDice = new StringBuilder(omenDice.toString().trim());
            Button augers = Button.secondary("getOmenDice", "Use an omen die (" + omenDice + ")")
                .withEmoji(Emoji.fromFormatted(Emojis.mykomentori));
            buttons.add(augers);
        }
        if (player.hasUnexhaustedLeader("mykomentoriagent")) {
            Button nekroButton = Button.secondary("exhaustAgent_mykomentoriagent",
                "Use Myko Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.mykomentori));
            buttons.add(nekroButton);
        }
        if (player.hasUnexhaustedLeader("hacanagent")) {
            Button hacanButton = Button.secondary("exhaustAgent_hacanagent",
                "Use Hacan Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Hacan));
            buttons.add(hacanButton);
        }
        if (ButtonHelper.isPlayerElected(activeGame, player, "minister_peace")) {
            Button hacanButton = Button.secondary("ministerOfPeace", "Use Minister of Peace")
                .withEmoji(Emoji.fromFormatted(Emojis.Agenda));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("vadenagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_vadenagent",
                "Use Vaden Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.vaden));
            buttons.add(hacanButton);
        } // olradinagent
        if (player.hasUnexhaustedLeader("olradinagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_olradinagent",
                "Use Olradin Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.olradin));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("edynagent")) {
            Button hacanButton2 = Button.secondary("presetEdynAgentStep1", "Preset Edyn Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.edyn));
            buttons.add(hacanButton2);
        }
        if (player.hasUnexhaustedLeader("celdauriagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_celdauriagent",
                "Use Celdauri Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.celdauri));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("cheiranagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_cheiranagent",
                "Use Cheiran Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.cheiran));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("freesystemsagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_freesystemsagent",
                "Use Free Systems Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.freesystems));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("florzenagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_florzenagent",
                "Use Florzen Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.florzen));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("nokaragent")) {
            Button hacanButton = Button.secondary("getAgentSelection_nokaragent",
                "Use Nokar Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.nokar));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("zelianagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_zelianagent",
                "Use Zelian Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.zelian));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("mirvedaagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_mirvedaagent",
                "Use Mirveda Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.mirveda));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("cymiaeagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_cymiaeagent",
                "Use Cymiae Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.cymiae));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("mortheusagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_mortheusagent",
                "Use Mortheus Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.mortheus));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("zealotsagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_zealotsagent",
                "Use Zealot Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.zealots));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("rohdhnaagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_rohdhnaagent",
                "Use Roh'Dhna Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.rohdhna));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("veldyragent")) {
            Button hacanButton = Button.secondary("getAgentSelection_veldyragent",
                "Use Veldyr Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.veldyr));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("gledgeagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_gledgeagent",
                "Use Gledge Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.gledge));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("khraskagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_khraskagent",
                "Use Khrask Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.khrask));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("nivynagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_nivynagent",
                "Use Nivyn Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.nivyn));
            buttons.add(hacanButton);
        }
        if (player.hasUnexhaustedLeader("ghotiagent")) {
            Button hacanButton = Button.secondary("getAgentSelection_ghotiagent",
                "Use Ghoti Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.ghoti));
            buttons.add(hacanButton);
        }
        if (player.getNomboxTile().getUnitHolders().get("space").getUnits().size() > 0) {
            Button release = Button.secondary("getReleaseButtons", "Release captured units")
                .withEmoji(Emoji.fromFormatted(Emojis.Cabal));
            buttons.add(release);
        }
        if (player.hasRelicReady("e6-g0_network")) {
            buttons.add(Button.success("exhauste6g0network", "Exhaust E6-G0 Network Relic to Draw AC"));
        }
        if (player.hasTech("pa") && ButtonHelper.getPsychoTechPlanets(activeGame, player).size() > 1) {
            Button psycho = Button.success("getPsychoButtons", "Use Psychoarcheology");
            psycho = psycho.withEmoji(Emoji.fromFormatted(Emojis.BioticTech));
            buttons.add(psycho);
        }
        Button playerPref = Button.secondary("offerPlayerPref", "Change Player Settings");
        buttons.add(playerPref);
        boolean hadAnyUnplayedSCs = false;
        for (Integer SC : player.getSCs()) {
            if (!activeGame.getPlayedSCs().contains(SC)) {
                hadAnyUnplayedSCs = true;
            }
        }
        if (!hadAnyUnplayedSCs) {
            buttons.add(Button.danger("resolvePreassignment_Pre Pass " + player.getFaction(), "Pass on Next Turn"));
        }
        buttons.add(Button.success("cardsInfo", "Cards Info Refresh"));
        buttons.add(Buttons.REFRESH_INFO);

        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(),
            "_ _\nYou can use these buttons to do various things", buttons);
    }

    protected String getActionDescription() {
        return "Send to your Cards Info thread: Scored & Unscored SOs, ACs, and PNs in both hand and Play Area";
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addOptions(new OptionData(OptionType.STRING, Constants.LONG_PN_DISPLAY,
                    "Long promissory display, y or yes to show full promissory text").setRequired(false))
                .addOptions(new OptionData(OptionType.BOOLEAN, Constants.DM_CARD_INFO,
                    "Set TRUE to get card info as direct message also").setRequired(false)));
    }

}
