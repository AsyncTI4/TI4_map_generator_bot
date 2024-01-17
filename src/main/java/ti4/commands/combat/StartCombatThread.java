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
import ti4.generator.GenerateTile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.CombatRollType;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class StartCombatThread extends CombatSubcommandData {

    public StartCombatThread() {
        super(Constants.START_COMBAT, "Start a new combat thread for a given tile.");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile to move units from").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COMBAT_TYPE, "Type of combat to start 'space' or 'ground' - Default: space").setRequired(false).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        String tileID = event.getOption(Constants.TILE_NAME, null, OptionMapping::getAsString);
        tileID = StringUtils.substringBefore(tileID, " ");
        String combatType = event.getOption(Constants.COMBAT_TYPE, "space", OptionMapping::getAsString);
        tileID = AliasHandler.resolveTile(tileID);
        if (activeGame.isTileDuplicated(tileID)) {
            MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
            return;
        }
        Tile tile = activeGame.getTile(tileID);
        if (tile == null) {
            tile = activeGame.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.replyToMessage(event, "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        List<Player> spacePlayers = ButtonHelper.getPlayersWithShipsInTheSystem(activeGame, tile);
        List<Player> groundPlayers = ButtonHelper.getPlayersWithUnitsInTheSystem(activeGame, tile);
        List<Player> onlyGroundPlayers = new ArrayList<>(groundPlayers);
        onlyGroundPlayers.removeAll(spacePlayers);
        
        List<Player> playersForCombat = new ArrayList<>();
        switch (combatType) {
            case "space" -> playersForCombat.addAll(spacePlayers);
            case "ground" -> playersForCombat.addAll(groundPlayers);
        }
        
        if (playersForCombat.size() > 2) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "There are more than 2 players in this system - something may not work correctly *yet*.");
        } else if (playersForCombat.size() < 2) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "There are less than 2 players in this system - a combat thread could not be created.");
            return;
        }

        // Try to get players in order of [activePlayer, otherPlayer, ... (discarded players)]
        Player player1 = activeGame.getActivePlayer();
        if (player1 == null) player1 = playersForCombat.get(0);
        playersForCombat.remove(player1);
        Player player2 = playersForCombat.get(0);

        makeACombatThread(activeGame, event.getChannel(), player1, player2, tile, event, combatType);
    }

    public static String combatThreadName(Game activeGame, Player player1, @Nullable Player player2, Tile tile) {
        StringBuilder sb = new StringBuilder();
        sb.append(activeGame.getName()).append("-round-").append(activeGame.getRound()).append("-system-").append(tile.getPosition()).append("-");
        if (activeGame.isFoWMode()) {
            sb.append(player1.getColor());
            if (player2 != null) sb.append("-vs-").append(player2.getColor()).append("-private");
        } else {
            sb.append(player1.getFaction());
            if (player2 != null) sb.append("-vs-").append(player2.getFaction());
        }
        return sb.toString();
    }

    public static void makeACombatThread(Game activeGame, MessageChannel channel, Player player1, Player player2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround) {
        makeACombatThread(activeGame, channel, player1, player2, null, tile, event, spaceOrGround);
    }

    public static void makeACombatThread(Game activeGame, MessageChannel channel, Player player1, Player player2, String threadName, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround) {
        Helper.checkThreadLimitAndArchive(event.getGuild());
        if (threadName == null) threadName = combatThreadName(activeGame, player1, player2, tile);
        TextChannel textChannel = (TextChannel) channel;

        // Use existing thread, if it exists
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                initializeCombatThread(threadChannel_, activeGame, player1, player2, tile, event, spaceOrGround);
                return;
            }
        }

        int context = getTileImageContextForPDS2(activeGame, player1, tile, spaceOrGround);
        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event, player1);

        // Create the thread
        final String finalThreadName = threadName;
        channel.sendMessage("Resolve Combat in this thread:").queue(m -> {
            ThreadChannelAction threadChannel = textChannel.createThreadChannel(finalThreadName, m.getId());
            if (activeGame.isFoWMode()) {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
            } else {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_1_HOUR);
            }
            threadChannel.queue(tc -> initializeCombatThread(tc, activeGame, player1, player2, tile, event, spaceOrGround, systemWithContext));
        });
    }

    private static void initializeCombatThread(ThreadChannel threadChannel, Game activeGame, Player player1, Player player2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround) {
        initializeCombatThread(threadChannel, activeGame, player1, player2, tile, event, spaceOrGround, null);
    }

    private static void initializeCombatThread(ThreadChannel threadChannel, Game activeGame, Player player1, Player player2, Tile tile, GenericInteractionCreateEvent event, String spaceOrGround, FileUpload file) {
        StringBuilder message = new StringBuilder();
        message.append(player1.getRepresentation(true, true));
        if (!activeGame.isFoWMode()) message.append(player2.getRepresentation());

        message.append(" Please resolve the interaction here.\n");
        switch (spaceOrGround) {
            case "space" -> message.append(getGroundCombatIntroMessage());
            case "ground" -> message.append(getSpaceCombatIntroMessage());
        }

        int context = getTileImageContextForPDS2(activeGame, player1, tile, spaceOrGround);
        FileUpload systemWithContext;
        if (file == null) {
            systemWithContext = GenerateTile.getInstance().saveImage(activeGame, context, tile.getPosition(), event, player1);
        } else {
            systemWithContext = file;
        }

        message.append("\nImage of System:");
        MessageHelper.sendMessageWithFile(threadChannel, systemWithContext, message.toString(), false);

        List<Button> buttons = ButtonHelper.getButtonsForPictureCombats(activeGame, tile.getPosition(), player1, player2, spaceOrGround);
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Combat", buttons);
        
        // Non-fog Space Cannon Offense
        Button graviton = null;
        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player1, activeGame, tile.getPosition());
        if (!activeGame.isFoWMode() && playersWithPds2.size() > 0 && "space".equalsIgnoreCase(spaceOrGround)) {
            StringBuilder pdsMessage = new StringBuilder("The following players have space cannon offense cover in the region, and can use the button to fire it:");
            for (Player playerWithPds : playersWithPds2) {
                pdsMessage.append(" ").append(playerWithPds.getRepresentation());
                if (playerWithPds.hasTechReady("gls")) {
                    graviton = Button.secondary("exhaustTech_gls", "Exhaust Graviton Laser Systems");
                    break;
                }
            }
            MessageHelper.sendMessageToChannelWithButtons(threadChannel, pdsMessage.toString(), List.of(graviton));
        } else if (activeGame.isFoWMode()) {
            MessageHelper.sendMessageToChannel(threadChannel, "In fog, it is the players responsibility to check for pds2");
        }


        if ("space".equalsIgnoreCase(spaceOrGround)) {
            List<Button> spaceCombatButtons = getSpaceCombatButtons(activeGame, player1, player2, tile);
            MessageHelper.sendMessageToChannelWithButtons(threadChannel, "You can use these buttons to roll AFB or Space Cannon Offence", spaceCombatButtons);
        }

        if ((player1.hasTech("dslaner") && player1.getAtsCount() > 0) || (player2.hasTech("dslaner") && player2.getAtsCount() > 0)) {
            List<Button> lanefirATSButtons = ButtonHelperFactionSpecific.getLanefirATSButtons(player1, player2);
            MessageHelper.sendMessageToChannelWithButtons(threadChannel, "You can use these buttons to remove commodities from ATS Armaments", lanefirATSButtons);
        }
    }

    private static List<Button> getSpaceCombatButtons(Game activeGame, Player p1, Player p2, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_afb", "Roll " + CombatRollType.AFB.getValue()));
        buttons.add(Button.secondary("combatRoll_" + tile.getPosition() + "_space_spacecannonoffence", "Roll Space Cannon Offence"));
        if (activeGame.isFoWMode()) return buttons;

        buttons.add(Button.danger("declinePDS", "Decline PDS"));
        // Assault Cannon
        if ((p1.hasTech("asc") && ButtonHelper.checkNumberNonFighterShips(p1, activeGame, tile) > 2) || (p2.hasTech("asc") && ButtonHelper.checkNumberNonFighterShips(p2, activeGame, tile) > 2)) {
            buttons.add(Button.primary("assCannonNDihmohn_asc_" + tile.getPosition(), "Use Assault Cannon").withEmoji(Emoji.fromFormatted(Emojis.WarfareTech)));
        }

        // Dimensional Splicer
        if (FoWHelper.doesTileHaveWHs(activeGame, tile.getPosition()) && (p1.hasTech("ds") || p2.hasTech("ds"))) {
            buttons.add(Button.primary("assCannonNDihmohn_ds_" + tile.getPosition(), "Use Dimensional Splicer").withEmoji(Emoji.fromFormatted(Emojis.Ghost)));
        }

        // Dihmohn Commander
        if ((activeGame.playerHasLeaderUnlockedOrAlliance(p1, "dihmohncommander") && ButtonHelper.checkNumberNonFighterShips(p1, activeGame, tile) > 2)
            || (activeGame.playerHasLeaderUnlockedOrAlliance(p2, "dihmohncommander") && ButtonHelper.checkNumberNonFighterShips(p2, activeGame, tile) > 2)) {
            buttons.add(Button.primary("assCannonNDihmohn_dihmohn_" + tile.getPosition(), "Use Dihmohn Commander").withEmoji(Emoji.fromFormatted(Emojis.dihmohn)));
        }

        // Ambush
        if ((p1.hasAbility("ambush")) || p2.hasAbility("ambush")) {
            buttons.add(Button.secondary("rollForAmbush_" + tile.getPosition(), "Ambush").withEmoji(Emoji.fromFormatted(Emojis.Mentak)));
        }
        buttons.add(Button.secondary("announceARetreat", "Announce A Retreat"));

        return buttons;
    }

    private static int getTileImageContextForPDS2(Game activeGame, Player player1, Tile tile, String spaceOrGround) {
        List<Player> playersWithPds2;
        if (activeGame.isFoWMode() || "ground".equalsIgnoreCase(spaceOrGround)) {
            playersWithPds2 = new ArrayList<>();
        } else {
            playersWithPds2 = ButtonHelper.tileHasPDS2Cover(player1, activeGame, tile.getPosition());
        }
        int context = 0;
        if (playersWithPds2.size() > 0) {
            context = 1;
        }
        return context;
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
