package ti4.commands.combat;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.tokens.AddCC;
import ti4.generator.GenerateTile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CombatHelper;
import ti4.helpers.CombatRollType;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

public class StartCombat extends CombatSubcommandData {

    public StartCombat() {
        super(Constants.START_COMBAT, "Start a new combat thread for a given tile.");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile to move units from")
            .setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_TYPE,
            "Type of combat to start 'space' or 'ground' - Default: space").setRequired(false)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        String tileID = event.getOption(Constants.TILE_NAME, null, OptionMapping::getAsString);
        tileID = StringUtils.substringBefore(tileID, " ");
        String combatType = event.getOption(Constants.COMBAT_TYPE, "space", OptionMapping::getAsString);
        tileID = AliasHandler.resolveTile(tileID);
        if (game.isTileDuplicated(tileID)) {
            MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
            return;
        }
        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        List<Player> spacePlayers = ButtonHelper.getPlayersWithShipsInTheSystem(game, tile);
        List<Player> groundPlayers = ButtonHelper.getPlayersWithUnitsInTheSystem(game, tile);
        List<Player> onlyGroundPlayers = new ArrayList<>(groundPlayers);
        onlyGroundPlayers.removeAll(spacePlayers);

        List<Player> playersForCombat = new ArrayList<>();
        switch (combatType) {
            case "space" -> playersForCombat.addAll(spacePlayers);
            case "ground" -> playersForCombat.addAll(groundPlayers);
        }

        if (playersForCombat.size() > 2) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "There are more than 2 players in this system - something may not work correctly *yet*.");
        } else if (playersForCombat.size() < 2) {
            MessageHelper.sendMessageToChannel(event.getChannel(),
                "There are less than 2 players in this system - a combat thread could not be created.");
            return;
        }

        // Try to get players in order of [activePlayer, otherPlayer, ... (discarded
        // players)]
        Player player1 = game.getActivePlayer();
        if (player1 == null)
            player1 = playersForCombat.get(0);
        playersForCombat.remove(player1);
        Player player2 = playersForCombat.get(0);

        findOrCreateCombatThread(game, event.getChannel(), player1, player2, tile, event, combatType, "space");
    }

    public static String combatThreadName(Game game, Player player1, @Nullable Player player2, Tile tile) {
        StringBuilder sb = new StringBuilder();
        sb.append(game.getName()).append("-round-").append(game.getRound()).append("-system-")
            .append(tile.getPosition()).append("-turn-").append(player1.getTurnCount()).append("-");
        if (game.isFoWMode()) {
            sb.append(player1.getColor());
            if (player2 != null)
                sb.append("-vs-").append(player2.getColor()).append("-private");
        } else {
            sb.append(player1.getFaction());
            if (player2 != null)
                sb.append("-vs-").append(player2.getFaction());
        }
        return sb.toString();
    }

    public static void findOrCreateCombatThread(Game game, MessageChannel channel, Player player1, Player player2,
        Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, String unitHolderName) {
        findOrCreateCombatThread(game, channel, player1, player2, null, tile, event, spaceOrGround, unitHolderName);
    }

    public static void startSpaceCombat(Game game, Player player, Player player2, Tile tile, GenericInteractionCreateEvent event) {
        startSpaceCombat(game, player, player2, tile, event, null);
    }

    private static String combatThreadName(Game game, Player player1, @Nullable Player player2, Tile tile, String specialCombatTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append(game.getName()).append("-round-").append(game.getRound()).append("-system-")
            .append(tile.getPosition()).append("-turn-").append(player1.getTurnCount()).append("-");
        if (game.isFoWMode()) {
            sb.append(player1.getColor());
            if (player2 != null) {
                sb.append("-vs-").append(player2.getColor());
            }
            sb.append(specialCombatTitle != null ? specialCombatTitle : "");
            sb.append("-private");
        } else {
            sb.append(player1.getFaction());
            if (player2 != null) {
                sb.append("-vs-").append(player2.getFaction());
            }
            sb.append(specialCombatTitle != null ? specialCombatTitle : "");
        }
        return sb.toString();
    }

    public static void startSpaceCombat(Game game, Player player, Player player2, Tile tile, GenericInteractionCreateEvent event,
        String specialCombatTitle) {
        String threadName = combatThreadName(game, player, player2, tile, specialCombatTitle);
        if (!game.isFoWMode()) {
            findOrCreateCombatThread(game, game.getActionsChannel(), player, player2,
                threadName, tile, event, "space", "space");
        } else {
            findOrCreateCombatThread(game, player.getPrivateChannel(), player, player2,
                threadName, tile, event, "space", "space");
            if (player2.getPrivateChannel() != null) {
                findOrCreateCombatThread(game, player2.getPrivateChannel(), player2, player,
                    threadName, tile, event, "space", "space");
            }
            for (Player player3 : game.getRealPlayers()) {
                if (player3 == player2 || player3 == player) {
                    continue;
                }
                if (!tile.getRepresentationForButtons(game, player3).contains("(")) {
                    continue;
                }
                findOrCreateCombatThread(game, player3.getPrivateChannel(), player3, player3,
                    threadName, tile, event, "space", "space");
            }
        }
    }

    public static void startGroundCombat(Player player, Player player2, Game game, GenericInteractionCreateEvent event, UnitHolder unitHolder, Tile tile) {
        String threadName = StartCombat.combatThreadName(game, player, player2, tile);
        if (!game.isFoWMode()) {
            StartCombat.findOrCreateCombatThread(game, game.getActionsChannel(), player, player2,
                threadName, tile, event, "ground", unitHolder.getName());
            if ((unitHolder.getUnitCount(UnitType.Pds, player2.getColor()) < 1
                || (!player2.hasUnit("titans_pds") && !player2.hasUnit("titans_pds2")))
                && unitHolder.getUnitCount(UnitType.Mech, player2.getColor()) < 1
                && unitHolder.getUnitCount(UnitType.Infantry, player2.getColor()) < 1
                && (unitHolder.getUnitCount(UnitType.Pds, player2.getColor()) > 0
                    || unitHolder.getUnitCount(UnitType.Spacedock, player2.getColor()) > 0)) {
                String msg2 = player2.getRepresentation()
                    + " you may want to remove structures on " + unitHolder.getName() + " if your opponent is not playing infiltrate or using assimilate. Use buttons to resolve";
                List<Button> buttons = new ArrayList<>();
                buttons.add(
                    Button.danger(player2.getFinsFactionCheckerPrefix() + "removeAllStructures_" + unitHolder.getName(),
                        "Remove Structures"));
                buttons.add(Button.secondary("deleteButtons", "Dont remove Structures"));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
            }
        } else {
            StartCombat.findOrCreateCombatThread(game, player.getPrivateChannel(), player, player2,
                threadName, tile, event, "ground", unitHolder.getName());
            if (player2.isRealPlayer()) {
                StartCombat.findOrCreateCombatThread(game, player2.getPrivateChannel(), player2, player,
                    threadName, tile, event, "ground", unitHolder.getName());
            }
            for (Player player3 : game.getRealPlayers()) {
                if (player3 == player2 || player3 == player) {
                    continue;
                }
                if (!tile.getRepresentationForButtons(game, player3).contains("(")) {
                    continue;
                }
                StartCombat.findOrCreateCombatThread(game, player3.getPrivateChannel(), player3, player3,
                    threadName, tile, event, "ground", unitHolder.getName());
            }
        }
    }

    public static void findOrCreateCombatThread(Game game, MessageChannel channel, Player player1, Player player2,
        String threadName, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, String unitHolderName) {
        Helper.checkThreadLimitAndArchive(event.getGuild());
        if (threadName == null)
            threadName = combatThreadName(game, player1, player2, tile);
        if (!game.isFoWMode()) {
            channel = game.getMainGameChannel();
        }

        StartCombat.sendStartOfCombatSecretMessages(game, player1, player2, tile, spaceOrGround, unitHolderName);
        String combatName2 = "combatRoundTracker" + player1.getFaction() + tile.getPosition() + unitHolderName;
        game.setStoredValue(combatName2, "");
        combatName2 = "combatRoundTracker" + player2.getFaction() + tile.getPosition() + unitHolderName;
        game.setStoredValue(combatName2, "");

        TextChannel textChannel = (TextChannel) channel;
        // Use existing thread, if it exists
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                initializeCombatThread(threadChannel_, game, player1, player2, tile, event, spaceOrGround, null, unitHolderName);
                return;
            }
        }
        if ("18".equalsIgnoreCase(tile.getTileID()) && player1.getLeaderIDs().contains("winnucommander")
            && !player1.hasLeaderUnlocked("winnucommander")) {
            ButtonHelper.commanderUnlockCheck(player1, game, "winnu", event);
        }
        if ("18".equalsIgnoreCase(tile.getTileID()) && player2.getLeaderIDs().contains("winnucommander")
            && !player2.hasLeaderUnlocked("winnucommander")) {
            ButtonHelper.commanderUnlockCheck(player2, game, "winnu", event);
        }

        int context = getTileImageContextForPDS2(game, player1, tile, spaceOrGround);
        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(game, context, tile.getPosition(),
            event, player1);

        // Create the thread
        final String finalThreadName = threadName;

        channel.sendMessage("Resolve Combat in this thread:").queue(m -> {
            ThreadChannelAction threadChannel = textChannel.createThreadChannel(finalThreadName, m.getId());
            if (game.isFoWMode()) {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
            } else {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
            }
            threadChannel.queue(tc -> initializeCombatThread(tc, game, player1, player2, tile, event,
                spaceOrGround, systemWithContext, unitHolderName));
        });
    }

    private static void initializeCombatThread(ThreadChannel threadChannel, Game game, Player player1,
        Player player2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, FileUpload file, String unitHolderName) {
        StringBuilder message = new StringBuilder();
        message.append(player1.getRepresentation(true, true));
        if (!game.isFoWMode())
            message.append(player2.getRepresentation());

        boolean isSpaceCombat = "space".equalsIgnoreCase(spaceOrGround);
        boolean isGroundCombat = "ground".equalsIgnoreCase(spaceOrGround);

        message.append(" Please resolve the interaction here.\n");
        if (isSpaceCombat)
            message.append(getSpaceCombatIntroMessage());
        if (isGroundCombat)
            message.append(getGroundCombatIntroMessage());

        // PDS2 Context
        int context = getTileImageContextForPDS2(game, player1, tile, spaceOrGround);
        if (file == null) {
            file = GenerateTile.getInstance().saveImage(game, context, tile.getPosition(), event, player1);
        }

        message.append("\nImage of System:");
        MessageHelper.sendMessageWithFile(threadChannel, file, message.toString(), false);

        // Space Cannon Offense
        if (isSpaceCombat) {
            sendSpaceCannonButtonsToThread(threadChannel, game, player1, tile);
        }

        // Start of Space Combat Buttons
        if (isSpaceCombat) {
            sendStartOfSpaceCombatButtonsToThread(threadChannel, game, player1, player2, tile);
        }
        game.setStoredValue("solagent", "");
        game.setStoredValue("letnevagent", "");

        // AFB
        if (isSpaceCombat) {
            sendAFBButtonsToThread(event, threadChannel, game,
                ButtonHelper.getPlayersWithUnitsInTheSystem(game, tile), tile);
        }

        // General Space Combat
        sendGeneralCombatButtonsToThread(threadChannel, game, player1, player2, tile, spaceOrGround, event);

        if (isGroundCombat && !game.isFoWMode()) {
            List<Button> autoButtons = new ArrayList<>();
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                List<Player> playersWithGF = new ArrayList<>();
                for (Player player : game.getRealPlayersNDummies()) {
                    if (ButtonHelperModifyUnits.doesPlayerHaveGfOnPlanet(uH, player)) {
                        playersWithGF.add(player);
                    }
                }
                if (playersWithGF.size() > 1) {
                    Button automate = Button.success("automateGroundCombat_" + playersWithGF.get(0).getFaction() + "_" + playersWithGF.get(1).getFaction() + "_" + unitHolderName + "_unconfirmed", "Automate Combat For " + Helper.getPlanetRepresentation(unitHolderName, game));
                    autoButtons.add(automate);
                }
            }
            if (autoButtons.size() > 0) {
                MessageHelper.sendMessageToChannelWithButtons(threadChannel, "You can automate the entire combat if neither side has action cards or fancy tricks. Press this button to do so, and it will ask your opponent to confirm", autoButtons);
            }
        }
        // DS Lanefir ATS Armaments
        if ((player1.hasTech("dslaner") && player1.getAtsCount() > 0) || (player2.hasTech("dslaner") && player2.getAtsCount() > 0)) {
            List<Button> lanefirATSButtons = ButtonHelperFactionSpecific.getLanefirATSButtons(player1, player2);
            MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons to remove commodities from ATS Armaments:", lanefirATSButtons);
        }
    }

    public static void sendSpaceCannonButtonsToThread(MessageChannel threadChannel, Game game,
        Player activePlayer, Tile tile) {
        StringBuilder pdsMessage = new StringBuilder();
        if (game.isFoWMode()) {
            pdsMessage.append("In fog, it is the Players' responsibility to check for PDS2\n");
        }
        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(activePlayer, game, tile.getPosition());
        if (playersWithPds2.size() < 1) {
            return;
        }
        if (!game.isFoWMode() && playersWithPds2.size() > 0) {
            pdsMessage.append("These players have space cannon offense coverage in this system:\n");
            for (Player playerWithPds : playersWithPds2) {
                pdsMessage.append("> ").append(playerWithPds.getRepresentation()).append("\n");
            }
        }
        pdsMessage.append("Buttons for Space Cannon Offence:");
        List<Button> spaceCannonButtons = getSpaceCannonButtons(game, activePlayer, tile);
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, pdsMessage.toString(), spaceCannonButtons);
        for (Player player : game.getRealPlayers()) {
            if (ButtonHelper.doesPlayerHaveFSHere("argent_flagship", player, tile)) {
                MessageHelper.sendMessageToChannel(threadChannel, "Reminder that you cannot use space cannon offense against " + ButtonHelper.getIdentOrColor(player, game) + " due to their FS power");
            }
        }
    }

    private static void sendStartOfSpaceCombatButtonsToThread(ThreadChannel threadChannel, Game game,
        Player player1, Player player2, Tile tile) {
        List<Button> startOfSpaceCombatButtons = getStartOfSpaceCombatButtons(game, player1, player2, tile);
        if (!startOfSpaceCombatButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons for Start of Space Combat:",
                startOfSpaceCombatButtons);
        }
    }

    private static void sendStartOfCombatSecretMessages(Game game, Player p1, Player p2, Tile tile, String type, String unitHolderName) {
        List<Player> combatPlayers = new ArrayList<>();
        combatPlayers.add(p1);
        combatPlayers.add(p2);
        List<Button> buttons = new ArrayList<>();

        for (Player player : combatPlayers) {
            Player otherPlayer = p1;
            if (otherPlayer == player) {
                otherPlayer = p2;
            }
            String msg = player.getRepresentation() + " ";
            if (ButtonHelper.doesPlayerHaveFSHere("cymiae_flagship", player, tile)) {
                buttons.add(Button.success("resolveSpyStep1", "Resolve Cymiae Flagship Ability"));
                buttons.add(Button.danger("deleteButtons", "Delete These"));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + "if you win the combat, you have the opportuntiy to use the cymiae FS to force the other player to send you a random action card. It will send buttons to the other player to confirm",
                    buttons);
            }
            if (type.equalsIgnoreCase("space") && player.getSecretsUnscored().keySet().contains("uf")
                && tile.getUnitHolders().get("space").getUnitCount(UnitType.Flagship, player.getColor()) > 0) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    msg + " this is a reminder that if you win the combat, you can score unveil");
            }
            if (type.equalsIgnoreCase("space") && player.getSecretsUnscored().keySet().contains("dtgs")
                && (tile.getUnitHolders().get("space").getUnitCount(UnitType.Flagship, otherPlayer.getColor()) > 0
                    || tile.getUnitHolders().get("space").getUnitCount(UnitType.Warsun,
                        otherPlayer.getColor()) > 0)) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that you could potentially score destroy their greatest in this combat");
            }
            if (player.getSecretsUnscored().keySet().contains("sar")
                && otherPlayer.getTotalVictoryPoints() == game.getHighestScore()) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    msg + " this is a reminder that you could potentially score spark in this combat");
            }
            if (player.getSecretsUnscored().keySet().contains("btv") && tile.isAnomaly(game)) {
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(),
                    msg + " this is a reminder that you could potentially score brave the void in this combat");
            }

            if ((player.hasAbility("edict") || player.hasAbility("imperia"))
                && !player.getMahactCC().contains(otherPlayer.getColor())) {
                buttons = new ArrayList<>();
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons.add(Button
                    .secondary(finChecker + "mahactStealCC_" + otherPlayer.getColor(), "Add Opponent CC to Fleet")
                    .withEmoji(Emoji.fromFormatted(Emojis.Mahact)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that if you win this combat, you can add the opponents CC to your fleet pool",
                    buttons);
            }
            if (player.hasTechReady("dskortg") && AddCC.hasCC(player, tile)) {
                buttons = new ArrayList<>();
                buttons.add(Button.secondary("exhaustTech_dskortg_" + tile.getPosition(), "Tempest Drive")
                    .withEmoji(Emoji.fromFormatted(Emojis.kortali)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that if you win the combat, you can use this button to remove a CC from the system",
                    buttons);
            }
            if (player.hasAbility("technological_singularity")) {
                buttons = new ArrayList<>();
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons.add(Button.secondary(finChecker + "nekroStealTech_" + otherPlayer.getFaction(), "Steal Tech")
                    .withEmoji(Emoji.fromFormatted(Emojis.Nekro)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that when you first kill an opponent unit this combat, you can use the button to steal a tech",
                    buttons);
            }
            if (player.hasUnit("ghemina_mech") && type.equalsIgnoreCase("ground") && ButtonHelper.getUnitHolderFromPlanetName(unitHolderName, game).getUnitCount(UnitType.Mech, player) == 2) {
                buttons = new ArrayList<>();
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons.add(Button.secondary(finChecker + "gheminaMechStart_" + otherPlayer.getFaction(), "Mech Explores")
                    .withEmoji(Emoji.fromFormatted(Emojis.ghemina)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that if you win the combat, you can use the button to resolve your mech ability",
                    buttons);
            }

            if (type.equalsIgnoreCase("space") && player.hasTech("so")) {
                buttons = new ArrayList<>();
                buttons.add(Button.secondary("salvageOps_" + tile.getPosition(), "Salvage Ops")
                    .withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that if the combat does not end in a draw, you can use the button to resolve salvage ops",
                    buttons);
            }
            if (type.equalsIgnoreCase("space")
                && game.playerHasLeaderUnlockedOrAlliance(player, "mentakcommander")) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons = new ArrayList<>();
                buttons.add(Button
                    .secondary(finChecker + "mentakCommander_" + otherPlayer.getColor(),
                        "Mentak Commander on " + otherPlayer.getColor())
                    .withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that if you win the combat, you can use the button to resolve mentak commander",
                    buttons);
            }
            if (player.hasAbility("moult") && player != game.getActivePlayer()
                && "space".equalsIgnoreCase(type)) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons = new ArrayList<>();
                buttons.add(Button.secondary(finChecker + "moult_" + tile.getPosition(), "Moult")
                    .withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that if you win the combat, you can use the button to resolve moult and produce one ship, reducing the cost by 1 for each non-fighter ship you lost in the combat",
                    buttons);
            }
            if (player.getPromissoryNotes().containsKey("dspnmort")
                && !player.getPromissoryNotesOwned().contains("dspnmort") && player != game.getActivePlayer()
                && "space".equalsIgnoreCase(type)) {
                String finChecker = "FFCC_" + player.getFaction() + "_";
                buttons = new ArrayList<>();
                buttons.add(Button.secondary(finChecker + "startFacsimile_" + tile.getPosition(), "Play Mortheus PN")
                    .withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that you can play morpheus PN here to spend influence equal to the cost of 1 of the opponent ships to place 1 of that type of ship in the system",
                    buttons);
            }
            boolean techOrLegendary = false;
            for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                if (ButtonHelper.checkForTechSkips(game, planet.getName())
                    || ButtonHelper.isTileLegendary(tile, game)) {
                    techOrLegendary = true;
                }
            }
            if (techOrLegendary && player.getLeaderIDs().contains("augerscommander")
                && !player.hasLeaderUnlocked("augerscommander")) {
                buttons = new ArrayList<>();
                buttons.add(Button.success("unlockCommander_augers", "Unlock Commander")
                    .withEmoji(Emoji.fromFormatted(Emojis.augers)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that if you win the combat, you can use the button to unlock your commander",
                    buttons);
            }
            if (player.getLeaderIDs().contains("kortalicommander")
                && !player.hasLeaderUnlocked("kortalicommander")) {
                buttons = new ArrayList<>();
                buttons.add(Button.success("unlockCommander_kortali", "Unlock Commander")
                    .withEmoji(Emoji.fromFormatted(Emojis.kortali)));
                MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), msg
                    + " this is a reminder that if you destroy all the opponents units in this system, you can use the button to unlock your commander",
                    buttons);
            }
        }

    }

    private static void sendAFBButtonsToThread(GenericInteractionCreateEvent event, ThreadChannel threadChannel, Game game, List<Player> combatPlayers, Tile tile) {
        boolean thereAreAFBUnits = false;
        for (Player player : combatPlayers) {
            if (!CombatHelper.GetUnitsInAFB(tile, player, event).isEmpty())
                thereAreAFBUnits = true;
        }
        if (!thereAreAFBUnits)
            return;

        List<Button> afbButtons = new ArrayList<>();
        afbButtons.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_afb", "Roll " + CombatRollType.AFB.getValue()));
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons to roll AFB:", afbButtons);
        for (Player player : combatPlayers) {
            if (ButtonHelper.doesPlayerHaveMechHere("naalu_mech", player, tile) && !ButtonHelper.isLawInPlay(game, "articles_war")) {
                MessageHelper.sendMessageToChannel(threadChannel, "Reminder that you cannot use AFB against " + ButtonHelper.getIdentOrColor(player, game) + " due to their mech power");
            }
        }

    }

    private static List<Button> getSpaceCannonButtons(Game game, Player activePlayer, Tile tile) {
        List<Button> spaceCannonButtons = new ArrayList<>();
        spaceCannonButtons.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_spacecannonoffence",
            "Roll Space Cannon Offence"));
        if (game.isFoWMode())
            return spaceCannonButtons;
        spaceCannonButtons.add(Button.danger("declinePDS", "Decline PDS"));

        // Add Graviton Laser System button if applicable
        for (Player playerWithPds : ButtonHelper.tileHasPDS2Cover(activePlayer, game, tile.getPosition())) {
            if (playerWithPds.hasTechReady("gls")) { // Graviton Laser Systems
                spaceCannonButtons.add(Button.secondary("exhaustTech_gls", "Exhaust Graviton Laser System")
                    .withEmoji(Emoji.fromFormatted(Emojis.CyberneticTech)));
                break;
            }
        }
        return spaceCannonButtons;
    }

    private static List<Button> getStartOfSpaceCombatButtons(Game game, Player p1, Player p2, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        if (game.isFoWMode())
            return buttons;

        // Assault Cannon
        if ((p1.hasTech("asc") && (ButtonHelper.checkNumberNonFighterShips(p1, game, tile) > 2 || ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", p1, tile)))
            || (p2.hasTech("asc") && (ButtonHelper.checkNumberNonFighterShips(p2, game, tile) > 2 || ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", p2, tile)))) {
            buttons.add(Button.primary("assCannonNDihmohn_asc_" + tile.getPosition(), "Use Assault Cannon")
                .withEmoji(Emoji.fromFormatted(Emojis.WarfareTech)));
        }

        // Dimensional Splicer
        if (FoWHelper.doesTileHaveWHs(game, tile.getPosition()) && (p1.hasTech("ds") || p2.hasTech("ds"))) {
            buttons.add(Button.primary("assCannonNDihmohn_ds_" + tile.getPosition(), "Use Dimensional Splicer")
                .withEmoji(Emoji.fromFormatted(Emojis.Ghost)));
        }

        if ((p1.hasAbility("shroud_of_lith")
            && ButtonHelperFactionSpecific.getKolleccReleaseButtons(p1, game).size() > 1)
            || (p2.hasAbility("shroud_of_lith")
                && ButtonHelperFactionSpecific.getKolleccReleaseButtons(p2, game).size() > 1)) {
            buttons.add(Button.primary("shroudOfLithStart", "Use Shroud of Lith")
                .withEmoji(Emoji.fromFormatted(Emojis.kollecc)));
        }

        // Dihmohn Commander
        if ((game.playerHasLeaderUnlockedOrAlliance(p1, "dihmohncommander")
            && ButtonHelper.checkNumberNonFighterShips(p1, game, tile) > 2)
            || (game.playerHasLeaderUnlockedOrAlliance(p2, "dihmohncommander")
                && ButtonHelper.checkNumberNonFighterShips(p2, game, tile) > 2)) {
            buttons.add(Button.primary("assCannonNDihmohn_dihmohn_" + tile.getPosition(), "Use Dihmohn Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
        }

        // Ambush
        if ((p1.hasAbility("ambush")) || p2.hasAbility("ambush")) {
            buttons.add(Button.secondary("rollForAmbush_" + tile.getPosition(), "Ambush")
                .withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        }

        if ((p1.hasLeaderUnlocked("mentakhero")) || p2.hasLeaderUnlocked("mentakhero")) {
            buttons.add(Button.secondary("purgeMentakHero_" + tile.getPosition(), "Purge Mentak Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        }

        if ((p1.hasAbility("facsimile") && p1 != game.getActivePlayer())
            || p2.hasAbility("facsimile") && p2 != game.getActivePlayer() && !game.isFoWMode()) {
            buttons.add(Button.secondary("startFacsimile_" + tile.getPosition(), "Facsimile")
                .withEmoji(Emoji.fromFormatted(Emojis.mortheus)));
        }

        // mercenaries
        Player florzen = Helper.getPlayerFromAbility(game, "mercenaries");
        if (florzen != null && FoWHelper.playerHasFightersInAdjacentSystems(florzen, tile, game)) {
            buttons.add(
                Button.secondary(florzen.getFinsFactionCheckerPrefix() + "mercenariesStep1_" + tile.getPosition(),
                    "Mercenaries").withEmoji(Emoji.fromFormatted(Emojis.florzen)));
        }
        return buttons;
    }

    /**
     * # of extra rings to show around the tile image
     *
     * @return 0 if no PDS2 nearby, 1 if PDS2 is nearby
     */
    private static int getTileImageContextForPDS2(Game game, Player player1, Tile tile, String spaceOrGround) {
        if (game.isFoWMode() || "ground".equalsIgnoreCase(spaceOrGround)) {
            return 0;
        }
        if (!ButtonHelper.tileHasPDS2Cover(player1, game, tile.getPosition()).isEmpty()) {
            return 1;
        }
        return 0;
    }

    private static void sendGeneralCombatButtonsToThread(ThreadChannel threadChannel, Game game, Player player1,
        Player player2, Tile tile, String spaceOrGround, GenericInteractionCreateEvent event) {
        List<Button> buttons = getGeneralCombatButtons(game, tile.getPosition(), player1, player2, spaceOrGround,
            event);
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons for Combat:", buttons);
    }

    // TODO: Break apart into: [all combats, space combat, ground combat] methods
    public static List<Button> getGeneralCombatButtons(Game game, String pos, Player p1, Player p2,
        String groundOrSpace, GenericInteractionCreateEvent event) {
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        UnitHolder space = tile.getUnitHolders().get("space");
        boolean isSpaceCombat = "space".equalsIgnoreCase(groundOrSpace);
        boolean isGroundCombat = "ground".equalsIgnoreCase(groundOrSpace);

        if ("justPicture".equalsIgnoreCase(groundOrSpace)) {
            buttons.add(Button.primary(
                "refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace,
                "Refresh Picture"));
            return buttons;
        }
        buttons.add(Button.danger("getDamageButtons_" + pos + "_" + groundOrSpace + "combat", "Assign Hits"));
        // if (getButtonsForRepairingUnitsInASystem(p1, game, tile).size() > 1 ||
        // getButtonsForRepairingUnitsInASystem(p2, game, tile).size() > 1) {
        buttons.add(Button.success("getRepairButtons_" + pos, "Repair Damage"));
        // }
        buttons.add(Button.primary(
            "refreshViewOfSystem_" + pos + "_" + p1.getFaction() + "_" + p2.getFaction() + "_" + groundOrSpace,
            "Refresh Picture"));

        Player titans = Helper.getPlayerFromUnlockedLeader(game, "titansagent");
        if (!game.isFoWMode() && titans != null && titans.hasUnexhaustedLeader("titansagent")) {
            String finChecker = "FFCC_" + titans.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_titansagent",
                "Use Titans Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Titans)));
        }
        if (p1.hasTechReady("sc") || (!game.isFoWMode() && p2.hasTechReady("sc"))) {
            // TemporaryCombatModifierModel combatModAC =
            // CombatTempModHelper.GetPossibleTempModifier("tech", "sc",
            // p1.getNumberTurns());
            buttons.add(Button.success("applytempcombatmod__" + "tech" + "__" + "sc", "Use Super Charge")
                .withEmoji(Emoji.fromFormatted(Emojis.Naaz)));
        }

        Player ghemina = Helper.getPlayerFromUnlockedLeader(game, "gheminaagent");
        if (!game.isFoWMode() && ghemina != null && ghemina.hasUnexhaustedLeader("gheminaagent")) {
            String finChecker = "FFCC_" + ghemina.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_gheminaagent",
                "Use Ghemina Agents")
                .withEmoji(Emoji.fromFormatted(Emojis.ghemina)));
        }

        Player khal = Helper.getPlayerFromUnlockedLeader(game, "kjalengardagent");
        if (!game.isFoWMode() && khal != null && khal.hasUnexhaustedLeader("kjalengardagent")) {
            String finChecker = "FFCC_" + khal.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_kjalengardagent",
                "Use Kjalengard Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.kjalengard)));
        }

        Player sol = Helper.getPlayerFromUnlockedLeader(game, "solagent");
        if (!game.isFoWMode() && sol != null && sol.hasUnexhaustedLeader("solagent") && isGroundCombat) {
            String finChecker = "FFCC_" + sol.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "getAgentSelection_solagent",
                "Use Sol Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
        }
        Player kyro = Helper.getPlayerFromUnlockedLeader(game, "kyroagent");
        if (!game.isFoWMode() && kyro != null && kyro.hasUnexhaustedLeader("kyroagent") && isGroundCombat) {
            String finChecker = "FFCC_" + kyro.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "getAgentSelection_kyroagent",
                "Use Kyro Agent)")
                .withEmoji(Emoji.fromFormatted(Emojis.blex)));

        }

        Player letnev = Helper.getPlayerFromUnlockedLeader(game, "letnevagent");
        if ((!game.isFoWMode() || letnev == p1) && letnev != null && letnev.hasUnexhaustedLeader("letnevagent")
            && "space".equalsIgnoreCase(groundOrSpace)) {
            String finChecker = "FFCC_" + letnev.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "getAgentSelection_letnevagent",
                "Use Letnev Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
        }

        Player nomad = Helper.getPlayerFromUnlockedLeader(game, "nomadagentthundarian");
        if ((!game.isFoWMode() || nomad == p1) && nomad != null && nomad.hasUnexhaustedLeader("nomadagentthundarian")) {
            String finChecker = "FFCC_" + nomad.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_nomadagentthundarian", "Use The Thundarian (Nomad Agent)")
                .withEmoji(Emoji.fromFormatted(Emojis.Nomad)));
        }

        Player yin = Helper.getPlayerFromUnlockedLeader(game, "yinagent");
        if ((!game.isFoWMode() || yin == p1) && yin != null && yin.hasUnexhaustedLeader("yinagent")) {
            String finChecker = "FFCC_" + yin.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "yinagent_" + pos,
                "Use Yin Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
        }

        // if (p1.hasAbility("technological_singularity")) {
        // String finChecker = "FFCC_" + p1.getFaction() + "_";
        // buttons.add(Button.secondary(finChecker + "nekroStealTech_" +
        // p2.getFaction(), "Steal Tech").withEmoji(Emoji.fromFormatted(Emojis.Nekro)));
        // }
        // if (p2.hasAbility("technological_singularity") && !game.isFoWMode()) {
        // String finChecker = "FFCC_" + p2.getFaction() + "_";
        // buttons.add(Button.secondary(finChecker + "nekroStealTech_" +
        // p1.getFaction(), "Steal Tech").withEmoji(Emoji.fromFormatted(Emojis.Nekro)));
        // }

        // if (p1.hasAbility("moult") && p1 != game.getActivePlayer() &&
        // "space".equalsIgnoreCase(groundOrSpace)) {
        // String finChecker = "FFCC_" + p1.getFaction() + "_";
        // buttons.add(Button.secondary(finChecker + "moult_" +tile.getPosition(),
        // "Moult").withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
        // }
        // if (p2.hasAbility("moult") && !game.isFoWMode() && p2 !=
        // game.getActivePlayer() && "space".equalsIgnoreCase(groundOrSpace)) {
        // String finChecker = "FFCC_" + p2.getFaction() + "_";
        // buttons.add(Button.secondary(finChecker + "moult_" +tile.getPosition(),
        // "Moult").withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
        // }

        if ((p2.hasUnexhaustedLeader("kortaliagent")) && !game.isFoWMode() && isGroundCombat
            && p1.getFragments().size() > 0) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_kortaliagent_" + p1.getColor(),
                "Use Kortali Agent").withEmoji(Emoji.fromFormatted(Emojis.kortali)));
        }
        if (p1.hasUnexhaustedLeader("kortaliagent") && isGroundCombat && p2.getFragments().size() > 0) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "exhaustAgent_kortaliagent_" + p2.getColor(),
                "Use Kortali Agent").withEmoji(Emoji.fromFormatted(Emojis.kortali)));
        }

        // if ((p2.hasAbility("edict") || p2.hasAbility("imperia")) &&
        // !game.isFoWMode()) {
        // String finChecker = "FFCC_" + p2.getFaction() + "_";
        // buttons.add(Button.secondary(finChecker + "mahactStealCC_" + p1.getColor(),
        // "Add Opponent CC to Fleet").withEmoji(Emoji.fromFormatted(Emojis.Mahact)));
        // }
        // if (p1.hasAbility("edict") || p1.hasAbility("imperia")) {
        // String finChecker = "FFCC_" + p1.getFaction() + "_";
        // buttons.add(Button.secondary(finChecker + "mahactStealCC_" + p2.getColor(),
        // "Add Opponent CC to Fleet").withEmoji(Emoji.fromFormatted(Emojis.Mahact)));
        // }
        if ((p2.hasAbility("for_glory")) && !game.isFoWMode()
            && ButtonHelperAgents.getGloryTokenTiles(game).size() < 3) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "placeGlory_" + pos, "Place Glory (Upon Win)")
                .withEmoji(Emoji.fromFormatted(Emojis.kjalengard)));
        }
        if (p1.hasAbility("for_glory") && ButtonHelperAgents.getGloryTokenTiles(game).size() < 3) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "placeGlory_" + pos, "Place Glory (Upon Win)")
                .withEmoji(Emoji.fromFormatted(Emojis.kjalengard)));
        }

        if ((p2.hasAbility("collateralized_loans")) && !game.isFoWMode()
            && p2.getDebtTokenCount(p1.getColor()) > 0 && groundOrSpace.equalsIgnoreCase("space")) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "collateralizedLoans_" + pos + "_" + p1.getFaction(), "Collateralized Loans")
                .withEmoji(Emoji.fromFormatted(Emojis.vaden)));
        }
        if ((p1.hasAbility("collateralized_loans"))
            && p1.getDebtTokenCount(p2.getColor()) > 0 && groundOrSpace.equalsIgnoreCase("space")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "collateralizedLoans_" + pos + "_" + p2.getFaction(), "Collateralized Loans")
                .withEmoji(Emoji.fromFormatted(Emojis.vaden)));
        }

        if (p2.hasAbility("necrophage") && !game.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "offerNecrophage", "Necrophage")
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if (p1.hasAbility("necrophage")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "offerNecrophage", "Necrophage")
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        boolean hasDevotionShips = false;
        if (space != null && (space.getUnitCount(UnitType.Destroyer, p2) > 0 || space.getUnitCount(UnitType.Cruiser, p2) > 0)) {
            hasDevotionShips = true;
        }
        if (p2.hasAbility("devotion") && !game.isFoWMode() && isSpaceCombat && hasDevotionShips) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "startDevotion_" + tile.getPosition(), "Devotion")
                .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
        }
        if (space != null && (space.getUnitCount(UnitType.Destroyer, p1) > 0 || space.getUnitCount(UnitType.Cruiser, p1) > 0)) {
            hasDevotionShips = true;
        } else {
            hasDevotionShips = false;
        }
        if (p1.hasAbility("devotion") && isSpaceCombat && hasDevotionShips) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "startDevotion_" + tile.getPosition(), "Devotion")
                .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
        }

        // if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p2,
        // "mentakcommander") && !game.isFoWMode()) {
        // String finChecker = "FFCC_" + p2.getFaction() + "_";
        // buttons.add(Button.secondary(finChecker + "mentakCommander_" + p1.getColor(),
        // "Mentak Commander on " +
        // p1.getColor()).withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        // }
        // if (isSpaceCombat && ((p1.hasTech("so")) || (!game.isFoWMode() &&
        // p2.hasTech("so")))) {
        // buttons.add(Button.secondary("salvageOps_" + tile.getPosition(), "Salvage
        // Ops").withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        // }
        // if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p1,
        // "mentakcommander")) {
        // String finChecker = "FFCC_" + p1.getFaction() + "_";
        // buttons.add(Button.secondary(finChecker + "mentakCommander_" + p2.getColor(),
        // "Mentak Commander on " +
        // p2.getColor()).withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        // }

        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p2, "mykomentoricommander")
            && !game.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "resolveMykoCommander", "Myko Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p1, "mykomentoricommander")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "resolveMykoCommander", "Myko Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        if (isSpaceCombat && p2.hasAbility("munitions") && !game.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "munitionsReserves", "Use Munitions Reserves")
                .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
        }
        if (isSpaceCombat && p1.hasAbility("munitions")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "munitionsReserves", "Use Munitions Reserves")
                .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
        }

        if (isSpaceCombat && ButtonHelper.doesPlayerHaveFSHere("mykomentori_flagship", p2, tile) && !game.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "gain_1_comms_stay", "Myko Flagship").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }
        if (isSpaceCombat && ButtonHelper.doesPlayerHaveFSHere("mykomentori_flagship", p1, tile)) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "gain_1_comms_stay", "Myko Flagship").withEmoji(Emoji.fromFormatted(Emojis.getEmojiFromDiscord("mykomentori"))));
        }

        if (isSpaceCombat) {
            buttons.add(Button.secondary("announceARetreat", "Announce A Retreat"));
            buttons.add(Button.danger("retreat_" + pos, "Retreat"));
        }
        if (isSpaceCombat && p2.hasAbility("foresight") && p2.getStrategicCC() > 0 && !game.isFoWMode()) {
            buttons.add(Button.danger("retreat_" + pos + "_foresight", "Foresight")
                .withEmoji(Emoji.fromFormatted(Emojis.Naalu)));
        }
        if (isSpaceCombat && p1.hasAbility("foresight") && p1.getStrategicCC() > 0) {
            buttons.add(Button.danger("retreat_" + pos + "_foresight", "Foresight")
                .withEmoji(Emoji.fromFormatted(Emojis.Naalu)));
        }
        boolean gheminaCommanderApplicable = false;
        if (tile.getPlanetUnitHolders().isEmpty()) {
            gheminaCommanderApplicable = true;
        } else {
            for (Player p3 : game.getRealPlayers()) {
                if (ButtonHelper.getTilesOfPlayersSpecificUnits(game, p3, UnitType.Pds, UnitType.Spacedock)
                    .contains(tile)) {
                    gheminaCommanderApplicable = true;
                    break;
                }
            }
        }
        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p2, "gheminacommander")
            && gheminaCommanderApplicable && !game.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.danger(finChecker + "declareUse_Ghemina Commander", "Use Ghemina Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.ghemina)));
        }
        if (isSpaceCombat && game.playerHasLeaderUnlockedOrAlliance(p1, "gheminacommander")
            && gheminaCommanderApplicable) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.danger(finChecker + "declareUse_Ghemina Commander", "Use Ghemina Commander")
                .withEmoji(Emoji.fromFormatted(Emojis.ghemina)));
        }
        if (p1.hasLeaderUnlocked("keleresherokuuasi") && isSpaceCombat
            && ButtonHelper.doesPlayerOwnAPlanetInThisSystem(tile, p1, game)) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeKeleresAHero", "Keleres Argent Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Keleres)));
        }
        if (p2.hasLeaderUnlocked("keleresherokuuasi") && !game.isFoWMode() && isSpaceCombat
            && ButtonHelper.doesPlayerOwnAPlanetInThisSystem(tile, p1, game)) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeKeleresAHero", "Keleres Argent Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.Keleres)));
        }

        if (p1.hasLeaderUnlocked("dihmohnhero") && isSpaceCombat) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeDihmohnHero", "Dihmohn Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
        }
        if (p2.hasLeaderUnlocked("dihmohnhero") && !game.isFoWMode() && isSpaceCombat) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeDihmohnHero", "Dihmohn Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
        }

        if (p1.hasLeaderUnlocked("kortalihero")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeKortaliHero_" + p2.getFaction(), "Kortali Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
        }
        if (p2.hasLeaderUnlocked("kortalihero") && !game.isFoWMode()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "purgeKortaliHero_" + p1.getFaction(), "Kortali Hero")
                .withEmoji(Emoji.fromFormatted(Emojis.kortali)));
        }

        if (ButtonHelper.getTilesOfUnitsWithBombard(p1, game).contains(tile)
            || ButtonHelper.getTilesOfUnitsWithBombard(p2, game).contains(tile)) {
            if (tile.getUnitHolders().size() > 2) {
                buttons.add(Button.secondary(
                    "bombardConfirm_combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                    "Roll Bombardment"));
            } else {
                buttons.add(
                    Button.secondary("combatRoll_" + tile.getPosition() + "_space_" + CombatRollType.bombardment,
                        "Roll Bombardment"));
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(p1, "cheirancommander") && isGroundCombat
            && p1 != game.getActivePlayer()) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "cheiranCommanderBlock_hm", "Cheiran Commander Block")
                .withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
        }
        if (!game.isFoWMode() && game.playerHasLeaderUnlockedOrAlliance(p2, "cheirancommander")
            && isGroundCombat
            && p2 != game.getActivePlayer()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "cheiranCommanderBlock_hm", "Cheiran Commander Block")
                .withEmoji(Emoji.fromFormatted(Emojis.cheiran)));
        }
        if (p1.hasTechReady("absol_x89") && isGroundCombat && p1 != game.getActivePlayer()) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.success(finChecker + "exhaustTech_absol_x89", "Absol X-89")
                .withEmoji(Emoji.fromFormatted(Emojis.BioticTech)));
        }
        if (!game.isFoWMode() && p2.hasTechReady("absol_x89") && isGroundCombat
            && p2 != game.getActivePlayer()) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.success(finChecker + "exhaustTech_absol_x89", "Absol X-89")
                .withEmoji(Emoji.fromFormatted(Emojis.BioticTech)));
        }
        if (game.playerHasLeaderUnlockedOrAlliance(p1, "kortalicommander")) {
            String finChecker = "FFCC_" + p1.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "kortaliCommanderBlock_hm", "Kortali Commander Block")
                .withEmoji(Emoji.fromFormatted(Emojis.kortali)));
        }
        if (!game.isFoWMode() && game.playerHasLeaderUnlockedOrAlliance(p2, "kortalicommander")) {
            String finChecker = "FFCC_" + p2.getFaction() + "_";
            buttons.add(Button.secondary(finChecker + "kortaliCommanderBlock_hm", "Kortali Commander Block")
                .withEmoji(Emoji.fromFormatted(Emojis.kortali)));
        }
        for (UnitHolder unitH : tile.getUnitHolders().values()) {
            String nameOfHolder = "Space";
            if (unitH instanceof Planet) {
                nameOfHolder = Helper.getPlanetRepresentation(unitH.getName(), game);
                if (p1 != game.getActivePlayer()
                    && game.playerHasLeaderUnlockedOrAlliance(p1, "solcommander") && isGroundCombat) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "utilizeSolCommander_" + unitH.getName(),
                            "Sol Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
                }
                if (p1.hasUnit("vaden_mech")
                    && unitH.getUnitCount(UnitType.Mech, p1) > 0 && isGroundCombat && p1.getDebtTokenCount(p2.getColor()) > 0) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "resolveVadenMech_" + unitH.getName() + "_" + p2.getColor(),
                            "Vaden Mech Ability on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.vaden)));
                }
                if (p2.hasUnit("vaden_mech")
                    && unitH.getUnitCount(UnitType.Mech, p2) > 0 && isGroundCombat && p2.getDebtTokenCount(p1.getColor()) > 0) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "resolveVadenMech_" + unitH.getName() + "_" + p1.getColor(),
                            "Vaden Mech Ability on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.vaden)));
                }
                if (p2 != game.getActivePlayer()
                    && game.playerHasLeaderUnlockedOrAlliance(p2, "solcommander") && !game.isFoWMode()
                    && isGroundCombat) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "utilizeSolCommander_" + unitH.getName(),
                            "Sol Commander on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Sol)));
                }
                if (p1.hasAbility("indoctrination") && isGroundCombat) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "initialIndoctrination_" + unitH.getName(),
                            "Indoctrinate on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                }

                if (p1.hasAbility("assimilate") && isGroundCombat
                    && (unitH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0
                        || unitH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) > 0
                        || unitH.getUnitCount(UnitType.Pds, p2.getColor()) > 0)) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "assimilate_" + unitH.getName(),
                            "Assimilate Structures on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.L1Z1X)));
                }
                if (p1.hasUnit("letnev_mech") && isGroundCombat
                    && unitH.getUnitCount(UnitType.Infantry, p1.getColor()) > 0
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p1, "mech") < 4) {
                    String finChecker = "FFCC_" + p1.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "letnevMechRes_" + unitH.getName() + "_mech",
                            "Deploy Dunlain Reaper on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
                }
                if (p2.hasUnit("letnev_mech") && !game.isFoWMode() && isGroundCombat
                    && unitH.getUnitCount(UnitType.Infantry, p2.getColor()) > 0
                    && ButtonHelper.getNumberOfUnitsOnTheBoard(game, p2, "mech") < 4) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "letnevMechRes_" + unitH.getName() + "_mech",
                            "Deploy Dunlain Reaper on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Letnev)));
                }
                if (p2.hasAbility("indoctrination") && !game.isFoWMode() && isGroundCombat) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "initialIndoctrination_" + unitH.getName(),
                            "Indoctrinate on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.Yin)));
                }
                if (p2.hasAbility("assimilate") && !game.isFoWMode() && isGroundCombat
                    && (unitH.getUnitCount(UnitType.Spacedock, p1.getColor()) > 0
                        || unitH.getUnitCount(UnitType.CabalSpacedock, p1.getColor()) > 0
                        || unitH.getUnitCount(UnitType.Pds, p1.getColor()) > 0)) {
                    String finChecker = "FFCC_" + p2.getFaction() + "_";
                    buttons.add(Button
                        .secondary(finChecker + "assimilate_" + unitH.getName(),
                            "Assimilate Structures on " + nameOfHolder)
                        .withEmoji(Emoji.fromFormatted(Emojis.L1Z1X)));
                }
            }
            if ("space".equalsIgnoreCase(nameOfHolder) && isSpaceCombat) {
                buttons.add(Button.secondary("combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat"));
                if (p1.isDummy()) {
                    buttons.add(Button.secondary(p1.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat For Dummy").withEmoji(Emoji.fromFormatted(p1.getFactionEmoji())));
                }
                if (p2.isDummy()) {
                    buttons.add(Button.secondary(p2.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(), "Roll Space Combat For Dummy").withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                }
            } else {
                if (!isSpaceCombat && !"space".equalsIgnoreCase(nameOfHolder)) {
                    buttons.add(Button.secondary("combatRoll_" + pos + "_" + unitH.getName(),
                        "Roll Ground Combat for " + nameOfHolder + ""));
                    Player nonActive = p1;
                    if (p1 == game.getActivePlayer()) {
                        nonActive = p2;
                    }
                    if (p1.isDummy()) {
                        buttons.add(Button.secondary(p1.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(), "Roll Ground Combat for " + nameOfHolder + " for Dummy").withEmoji(Emoji.fromFormatted(p1.getFactionEmoji())));
                    }
                    if (p2.isDummy()) {
                        buttons.add(Button.secondary(p2.dummyPlayerSpoof() + "combatRoll_" + pos + "_" + unitH.getName(), "Roll Ground Combat for " + nameOfHolder + " for Dummy").withEmoji(Emoji.fromFormatted(p2.getFactionEmoji())));
                    }
                    if (new CombatRoll().checkIfUnitsOfType(nonActive, game, event, tile, unitH.getName(),
                        CombatRollType.SpaceCannonDefence)) {
                        buttons.add(Button.secondary(
                            "combatRoll_" + tile.getPosition() + "_" + unitH.getName() + "_spacecannondefence",
                            "Roll Space Cannon Defence for " + nameOfHolder));
                    }
                }
            }
        }
        return buttons;
    }

    private static String getSpaceCombatIntroMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Steps for Space Combat:\n");
        sb.append("> 1. End of movement abilities (Foresight, Stymie, etc.)\n");
        sb.append("> 2. Firing of PDS\n");
        sb.append("> 3. Start of Combat (Skilled retreat, Morale boost, etc.)\n");
        sb.append("> 4. Anti-Fighter Barrage\n");
        sb.append("> 5. Declare Retreats (including rout)\n");
        sb.append("> 6. Roll Dice!\n");
        return sb.toString();
    }

    private static String getGroundCombatIntroMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Steps for Invasion:\n");
        sb.append("> 1. Start of invasion abilities (Tekklar, Blitz, Bunker, etc.)\n");
        sb.append("> 2. Bombardment\n");
        sb.append("> 3. Commit Ground Forces\n");
        sb.append("> 4. After commit window (Parley, Ghost Squad, etc.)\n");
        sb.append("> 5. Start of Combat (morale boost, etc.)\n");
        sb.append("> 6. Roll Dice!\n");
        return sb.toString();
    }
}
