package ti4.service.combat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.attribute.IThreadContainer;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.requests.restaction.ThreadChannelAction;
import net.dv8tion.jda.api.utils.FileUpload;
import org.apache.commons.lang3.StringUtils;
import ti4.ResourceHelper;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.DreamButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.arvaxi.ArvaxiLeaderHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraLeaderHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa.OnyxxaBreakthroughHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.onyxxa.OnyxxaUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.zephyrion.ZephyrionBreakthroughHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperModifyUnits;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.TechEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.GMService;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.turn.StartTurnService;
import ti4.spring.context.SpringContext;
import ti4.spring.service.gameevent.GameEventDraft;
import ti4.spring.service.gameevent.GameSubEvent;

/** Creates combat threads and sends the established combat buttons and prompts to Discord. */
@UtilityClass
public class CombatV2StartService {

    private static final String COMBAT_ROUND_TRACKER = "combatRoundTracker";

    public static void startSpaceCombat(
            Game game,
            Player player,
            Player player2,
            Tile tile,
            GenericInteractionCreateEvent event,
            String specialCombatTitle) {
        if (CombatContestSettings.isEnabledStatic()) {
            SpringContext.getBean(CombatReplayService.class).onSpaceCombatStarted(game, player, player2, tile);
        }
        String threadName = combatThreadName(game, player, player2, tile, specialCombatTitle);
        if (!game.isFowMode()) {
            findOrCreateCombatThread(
                    game.getActionsChannel(),
                    threadName,
                    new StartContext(game, player, player2, tile, event, "space", "space", true));
            game.setStoredValue(
                    "currentActionSummary" + player.getFaction(),
                    game.getStoredValue("currentActionSummary" + player.getFaction()) + " Had a space combat in "
                            + tile.getRepresentationForButtons() + " against " + player2.getFactionNameOrColor()
                            + ".");
            GameEventDraft.stage(
                    game, new GameSubEvent.Combat("space", tile.getPosition(), null, player2.getFaction()));
            return;
        }
        findOrCreateCombatThread(
                player.getPrivateChannel(),
                threadName,
                new StartContext(game, player, player2, tile, event, "space", "space", true));
        if (player2.getPrivateChannel() != null) {
            findOrCreateCombatThread(
                    player2.getPrivateChannel(),
                    threadName,
                    new StartContext(game, player2, player, tile, event, "space", "space", false));
        }
        for (Player player3 : game.getRealPlayers()) {
            if (player3 == player2 || player3 == player) {
                continue;
            }
            if (!tile.getRepresentationForButtons(game, player3).contains("(")) {
                continue;
            }
            createSpectatorThread(game, player3, threadName, tile, event, "space");
        }
    }

    public static void startGroundCombat(
            Player player,
            Player player2,
            Game game,
            GenericInteractionCreateEvent event,
            UnitHolder unitHolder,
            Tile tile) {
        String threadName = combatThreadName(game, player, player2, tile, null);
        game.setStoredValue(
                "currentActionSummary" + player.getFaction(),
                game.getStoredValue("currentActionSummary" + player.getFaction()) + " Had a ground combat on "
                        + Helper.getPlanetRepresentation(unitHolder.getName(), game) + " against "
                        + player2.getFactionNameOrColor() + ".");
        GameEventDraft.stage(
                game,
                new GameSubEvent.Combat("ground", tile.getPosition(), unitHolder.getName(), player2.getFaction()));
        if (!game.isFowMode()) {
            findOrCreateCombatThread(
                    game.getActionsChannel(),
                    threadName,
                    new StartContext(game, player, player2, tile, event, "ground", unitHolder.getName(), true));
            if ((unitHolder.getUnitCount(Units.UnitType.Pds, player2.getColor()) < 1
                            || (!player2.hasUnit("titans_pds") && !player2.hasUnit("titans_pds2")))
                    && unitHolder.getUnitCount(Units.UnitType.Mech, player2.getColor()) < 1
                    && unitHolder.getUnitCount(Units.UnitType.Infantry, player2.getColor()) < 1
                    && (unitHolder.getUnitCount(Units.UnitType.Pds, player2.getColor()) > 0
                            || unitHolder.getUnitCount(Units.UnitType.Spacedock, player2.getColor()) > 0)) {
                String msg2 =
                        player2.getRepresentation() + ", you may wish to remove structures on " + unitHolder.getName()
                                + " if your opponent is not playing _Infiltrate_ or using **Assimilate**. Use buttons to resolve.";
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.red(
                        player2.factionButtonChecker() + "removeAllStructures_" + unitHolder.getName(),
                        "Remove Structures"));
                buttons.add(Buttons.gray("deleteButtons", "Don't Remove Structures"));
                MessageHelper.sendMessageToChannelWithButtons(player2.getCorrectChannel(), msg2, buttons);
            }
        } else {
            findOrCreateCombatThread(
                    player.getPrivateChannel(),
                    threadName,
                    new StartContext(game, player, player2, tile, event, "ground", unitHolder.getName(), true));
            if (player2.getPrivateChannel() != null) {
                findOrCreateCombatThread(
                        player2.getPrivateChannel(),
                        threadName,
                        new StartContext(game, player2, player, tile, event, "ground", unitHolder.getName(), false));
            }
            for (Player player3 : game.getRealPlayers()) {
                if (player3 == player2 || player3 == player) {
                    continue;
                }
                if (!tile.getRepresentationForButtons(game, player3).contains("(")) {
                    continue;
                }
                createSpectatorThread(game, player3, threadName, tile, event, "ground");
            }
        }
        for (Player p : List.of(player, player2)) {
            if (p.hasUnlockedBreakthrough("onyxxabt")) {
                OnyxxaBreakthroughHandler.offerGroundCombatMechButtons(game, p, unitHolder, tile);
            }
        }
    }

    private static void findOrCreateCombatThread(MessageChannel channel, String threadName, StartContext combat) {
        Game game = combat.game();
        Player player1 = combat.player1();
        Player player2 = combat.player2();
        Tile tile = combat.tile();
        if (threadName == null) threadName = combatThreadName(game, player1, player2, tile, null);
        if (!game.isFowMode()) {
            channel = game.getMainGameChannel();
        }
        game.setStoredValue("factionsInCombat", player1.getFaction() + "_" + player2.getFaction());

        sendStartOfCombatSecretMessages(game, player1, player2, tile, combat.spaceOrGround(), combat.unitHolderName());
        String combatName2 = COMBAT_ROUND_TRACKER + player1.getFaction() + tile.getPosition() + combat.unitHolderName();
        game.setStoredValue(combatName2, "");
        combatName2 = COMBAT_ROUND_TRACKER + player2.getFaction() + tile.getPosition() + combat.unitHolderName();
        game.setStoredValue(combatName2, "");
        if (player1.hasAbility("refraction") || player2.hasAbility("refraction")) {
            CrystellumAbilityHandler.resetRefractionForCombat(game, player1, tile);
            CrystellumAbilityHandler.resetRefractionForCombat(game, player2, tile);
        }

        TextChannel textChannel = (TextChannel) channel;

        // Use existing thread, if it exists
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                initializeCombatThread(threadChannel_, combat, null);
                CommanderUnlockCheckService.checkPlayer(player1, "redcreuss");
                CommanderUnlockCheckService.checkPlayer(player2, "redcreuss");
                return;
            }
        }

        if (tile.isMecatol(game)) {
            CommanderUnlockCheckService.checkPlayer(player1, "winnu");
            CommanderUnlockCheckService.checkPlayer(player2, "winnu");
        }

        int context = getTileImageContextForPDS2(game, player1, tile, combat.spaceOrGround());
        FileUpload systemWithContext =
                new TileGenerator(game, combat.event(), null, context, tile.getPosition(), player1).createFileUpload();

        // Create the thread
        String finalThreadName = threadName;

        try {
            var threadMessage =
                    channel.sendMessage("Resolve combat in this thread:").complete();
            ThreadChannelAction threadChannel = textChannel.createThreadChannel(finalThreadName, threadMessage.getId());
            if (game.isFowMode()) {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
            } else {
                threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_24_HOURS);
            }
            // mutates game state, so much be done using complete rather than queue
            initializeCombatThread(threadChannel.complete(), combat, systemWithContext);
        } catch (Exception e) {
            BotLogger.error(new LogOrigin(game), "Failed to create combat thread for game: " + game.getName(), e);
        }
        CommanderUnlockCheckService.checkPlayer(player1, "redcreuss");
        CommanderUnlockCheckService.checkPlayer(player2, "redcreuss");
    }

    private static void initializeCombatThread(ThreadChannel threadChannel, StartContext combat, FileUpload file) {
        Game game = combat.game();
        Player player1 = combat.player1();
        Player player2 = combat.player2();
        Tile tile = combat.tile();
        GenericInteractionCreateEvent event = combat.event();
        String spaceOrGround = combat.spaceOrGround();
        String unitHolderName = combat.unitHolderName();
        boolean firstCombatThread = combat.firstCombatThread();
        StringBuilder message = new StringBuilder();
        message.append(player1.getRepresentationUnfogged());
        if (!game.isFowMode()) message.append(player2.getRepresentation());

        boolean isSpaceCombat = "space".equalsIgnoreCase(spaceOrGround);
        boolean isGroundCombat = "ground".equalsIgnoreCase(spaceOrGround);

        message.append(", please resolve the interaction here.\n");
        if (isSpaceCombat) message.append(getSpaceCombatIntroMessage());
        if (isGroundCombat) message.append(getGroundCombatIntroMessage());

        // PDS2 Context
        int context = getTileImageContextForPDS2(game, player1, tile, spaceOrGround);
        String threadName = threadChannel.getName();
        boolean benediction = threadName.toLowerCase().contains("benediction");
        if (benediction) {
            context = 0;
        }
        if (file == null) {
            file = new TileGenerator(game, event, null, context, tile.getPosition(), player1).createFileUpload();
        }

        message.append("\nImage of System:");
        MessageHelper.sendMessageWithFile(threadChannel, file, message.toString(), false);
        int amount = 0;
        for (Player p : game.getRealPlayers()) {
            if (FoWHelper.playerHasUnitsInSystem(p, tile)) {
                amount++;
            }
        }
        if (CombatContestSettings.isEnabledStatic() && (amount > 2 || tile.getNumberOfUnitsInSystem() > 2)) {
            MessageHelper.sendMessageToChannel(
                    threadChannel,
                    ButtonHelper.getCombatTileSummaryMessage(
                            game, tile, player1, event, spaceOrGround, unitHolderName, List.of(player1, player2)));
        }

        // Space Cannon Offense
        if (isSpaceCombat && !benediction) {
            sendSpaceCannonButtonsToThread(threadChannel, game, player1, tile);
        }

        // Start of Space Combat Buttons
        if (isSpaceCombat) {
            sendStartOfSpaceCombatButtonsToThread(threadChannel, game, player1, player2, tile);
        }
        game.setStoredValue("solagent", "");
        game.setStoredValue("letnevagent", "");
        game.setStoredValue("classifiedWeapons", "");

        // sigma homebrew
        if (isSpaceCombat) {
            boolean sustain = false;
            UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
            for (Units.UnitKey unit : space.getUnits().keySet()) {
                Player player = game.getPlayerFromColorOrFaction(unit.getColor());
                UnitModel removedUnit = player.getPriorityUnitByAsyncID(unit.asyncID(), space);
                if (removedUnit.getIsShip() && removedUnit.getSustainDamage()) {
                    sustain = true;
                    break;
                }
            }
            for (Player player : game.getRealPlayers()) {
                if (sustain && player.hasTech("sigma_cow")) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel, player.getRepresentation() + " may use _The Changer of Ways_.");
                }
            }
        }

        // AFB
        if (isSpaceCombat) {
            sendAFBButtonsToThread(
                    event, threadChannel, game, ButtonHelper.getPlayersWithUnitsInTheSystem(game, tile), tile);
        }

        // General Space Combat
        sendGeneralCombatButtonsToThread(threadChannel, game, player1, player2, tile, spaceOrGround, event);
        if (!game.isFowMode()) {
            if (player1.getPlayableActionCards().isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        threadChannel,
                        player2.getRepresentation()
                                + ", your opponent has no action cards to play, so if they have no applicable technologies/abilities/retreats you can roll.");
            }
            if (player2.getPlayableActionCards().isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        threadChannel,
                        player1.getRepresentation()
                                + ", your opponent has no action cards to play, so if they have no applicable technologies/abilities/retreats you can roll.");
            }
            String ms2 = StartTurnService.getMissedSCFollowsText(game, player1);
            if (ms2 != null && !"".equalsIgnoreCase(ms2)) {
                MessageHelper.sendMessageToChannel(threadChannel, ms2);
            }
            ms2 = StartTurnService.getMissedSCFollowsText(game, player2);
            if (ms2 != null && !"".equalsIgnoreCase(ms2)) {
                MessageHelper.sendMessageToChannel(threadChannel, ms2);
            }
            if (isSpaceCombat) {
                if (ButtonHelper.doesPlayerHaveFSHere("l1z1x_flagship", player2, tile)) {
                    UnitHolder space = tile.getUnitHolders().get("space");
                    int dreadCount = space.getUnitCount(UnitType.Dreadnought, player2.getColor());
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            player1.getRepresentation()
                                    + ", a reminder that your opponent has the [0.0.1] here, and so their flagship"
                                    + (dreadCount > 0 ? " and dreadnought" : "")
                                    + " hits must be assigned to non-fighter ships if possible."
                                    + " The bot will not enforce this.");
                }
                if (ButtonHelper.doesPlayerHaveFSHere("l1z1x_flagship", player1, tile)) {
                    UnitHolder space = tile.getUnitHolders().get("space");
                    int dreadCount = space.getUnitCount(UnitType.Dreadnought, player1.getColor());
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            player2.getRepresentation()
                                    + ", a reminder that your opponent has the [0.0.1] here, and so their flagship"
                                    + (dreadCount > 0 ? " and dreadnought" : "")
                                    + " hits must be assigned to non-fighter ships if possible."
                                    + " The bot will not enforce this.");
                }
                if (ButtonHelper.doesPlayerHaveFSHere("qhet_flagship", player2, tile)) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            player1.getRepresentation()
                                    + ", a reminder that your opponent has the Khage here, and their flagship hits cannot be canceled (including by SUSTAIN DAMAGE). The bot will not enforce this.");
                }
                if (ButtonHelper.doesPlayerHaveFSHere("qhet_flagship", player1, tile)) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            player2.getRepresentation()
                                    + ", a reminder that your opponent has the Khage here, and their flagship hits cannot be canceled (including by SUSTAIN DAMAGE). The bot will not enforce this.");
                }
                if (ButtonHelper.doesPlayerHaveFSHere("florzen_flagship", player2, tile)) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            player1.getRepresentation()
                                    + ", a reminder that your opponent has the Man O’ War here, and so you (and all other players) cannot play action cards during this space combat.");
                }
                if (ButtonHelper.doesPlayerHaveFSHere("florzen_flagship", player1, tile)) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            player2.getRepresentation()
                                    + ", a reminder that your opponent has the Man O’ War here, and so you (and all other players) cannot play action cards during this space combat.");
                }
            }
        }

        if (isGroundCombat && !game.isFowMode()) {
            List<Button> autoButtons = new ArrayList<>();
            boolean thalnos = false;
            for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                if (!uH.getName().equalsIgnoreCase(unitHolderName)) {
                    continue;
                }
                List<Player> playersWithGF = new ArrayList<>();
                for (Player player : game.getRealPlayersNDummies()) {
                    if (ButtonHelperModifyUnits.doesPlayerHaveGfOnPlanet(uH, player)) {
                        if (playersWithGF.isEmpty()
                                || !playersWithGF
                                        .getFirst()
                                        .getAllianceMembers()
                                        .contains(player.getFaction())) {
                            playersWithGF.add(player);
                        }
                    }
                }
                if (playersWithGF.size() > 1) {
                    Player p1 = playersWithGF.get(0);
                    Player p2 = playersWithGF.get(1);
                    if (game.getActivePlayer() != null && playersWithGF.contains(game.getActivePlayer())) {
                        p1 = game.getActivePlayer();
                        if (p2 == p1) {
                            p2 = playersWithGF.getFirst();
                        }
                    }
                    for (Player player : playersWithGF) {
                        if (player.getPlanets().contains(unitHolderName) && player != p1) {
                            p2 = player;
                        }
                    }

                    Button automate = Buttons.green(
                            "automateGroundCombat_" + p1.getFaction() + "_" + p2.getFaction() + "_" + unitHolderName
                                    + "_unconfirmed",
                            "Automate Combat For " + Helper.getPlanetRepresentation(unitHolderName, game));
                    autoButtons.add(automate);
                }
                for (Player player : playersWithGF) {
                    if (player.hasRelic("thalnos")) {
                        thalnos = true;
                    }

                    String magenFmt = "%s, a reminder to use _%s_. The button should be above, ";
                    magenFmt += "but it (and SPACE CANNON) are not part of automated ground combat.";
                    if (player.hasUnit("tk-blacktrenchbulwark")) {
                        if (uH.getUnitCount(UnitType.Pds, player) > 0) {
                            String msg = String.format(magenFmt, player.getRepresentation(), "Black Trench Bulwark");
                            MessageHelper.sendMessageToChannel(threadChannel, msg);
                        }
                    }
                    if ((player.hasTech("md") || player.hasTech("md_c1"))
                            && player.getPlanetsAllianceMode().contains(unitHolderName)) {
                        if (uH.getUnitCount(UnitType.Pds, player) > 0
                                || uH.getUnitCount(UnitType.Spacedock, player) > 0) {
                            String msg = String.format(magenFmt, player.getRepresentation(), "Magen Defense Grid");
                            MessageHelper.sendMessageToChannel(threadChannel, msg);
                        }
                    }
                }
            }
            if (!autoButtons.isEmpty()) {
                String automMessage =
                        "You may automate the entire combat if neither side has action cards or fancy tricks."
                                + " Press this button to do so, and it will ask your opponent to confirm."
                                + " Note that SPACE CANNON and BOMBARDMENT are __not__ part of combat and will __not__ be automated.";
                if (thalnos) {
                    automMessage +=
                            " One of you may have __The Crown of Thalnos__, and thus not wish to automate the combat.";
                }
                MessageHelper.sendMessageToChannelWithButtons(threadChannel, automMessage, autoButtons);
            }
        }
        // DS Lanefir ATS Armaments
        if ((player1.hasTech("dslaner") && player1.getAtsCount() > 0)
                || (player2.hasTech("dslaner") && player2.getAtsCount() > 0)) {
            List<Button> lanefirATSButtons = ButtonHelperFactionSpecific.getLanefirATSButtons(player1, player2);
            MessageHelper.sendMessageToChannelWithButtons(
                    threadChannel, "Buttons to remove commodities from _ATS Armaments_:", lanefirATSButtons);
        }

        if (firstCombatThread) {
            for (Player p : game.getRealPlayers()) {
                // offer buttons for all crimson commander holders
                offerRedGhostCommanderButtons(p, game);

                boolean inExileRange = FoWHelper.isTileInExileRange(game, tile, p);
                if (inExileRange) {
                    String msg = p.getRepresentation()
                            + ", at the end of the combat, if your destroyer is still within or adjacent to the tile containing the combat, you may place an inactive Breach.";
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(
                            p.factionButtonChecker() + "placeInactiveBreach_" + tile.getPosition(),
                            "Place Inactive Breach"));
                    buttons.add(Buttons.red(p.factionButtonChecker() + "deleteButtons", "Decline to place"));
                    MessageHelper.sendMessageToChannel(p.getCorrectChannel(), msg, buttons);
                }

                boolean inUpgradedExileRange = FoWHelper.isTileInUpgradedExileRange(game, tile, p);
                if (inUpgradedExileRange) {
                    String msg = p.getRepresentation()
                            + ", at the end of the combat, if your destroyer is still in the active system or within 2 tiles away, you may place a Breach (active or inactive).";
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(
                            p.factionButtonChecker() + "placeBreach_" + tile.getPosition() + "_destroyer",
                            "Place Active Breach"));
                    buttons.add(Buttons.blue(
                            p.factionButtonChecker() + "placeInactiveBreach_" + tile.getPosition(),
                            "Place Inactive Breach"));
                    buttons.add(Buttons.red(p.factionButtonChecker() + "deleteButtons", "Decline to place"));
                    MessageHelper.sendMessageToChannel(p.getCorrectChannel(), msg, buttons);
                }
            }
        }

        if (TeHelperUnits.affectedByQuietus(game, player2, tile)) {
            MessageHelper.sendMessageToChannel(
                    threadChannel,
                    player2.getRepresentation()
                            + ", you are affected by the Quietus (the Rebellion flagship), and your units will have lost all unit abilities.");
        }
        if (TeHelperUnits.affectedByQuietus(game, player1, tile)) {
            MessageHelper.sendMessageToChannel(
                    threadChannel,
                    player1.getRepresentation()
                            + ", you are affected by the Quietus (the Rebellion flagship), and your units will have lost all unit abilities.");
        }

        if (tile.isHomeSystem(game)
                && isGroundCombat
                && game.getStoredValue("audioSent").isEmpty()) {
            for (Player p3 : game.getRealPlayers()) {
                if (p3.getHomeSystemTile() == tile && game.getActivePlayer() != null) {
                    File audioFile = ResourceHelper.getFile("voices/" + p3.getFaction() + "/", "homedefense.mp3");
                    if (audioFile.exists()) {
                        MessageHelper.sendFileToChannel(threadChannel, audioFile);
                        game.setStoredValue("audioSent", "Yes");
                    }
                    Player invader = game.getActivePlayer();
                    File audioFile2 =
                            ResourceHelper.getFile("voices/" + invader.getFaction() + "/", "homeinvasion.mp3");
                    if (audioFile2.exists() && invader != p3) {
                        MessageHelper.sendFileToChannel(threadChannel, audioFile2);
                        game.setStoredValue("audioSent", "Yes");
                    }
                }
            }
        }

        GMService.logPlayerActivity(
                game,
                player1,
                player1.getRepresentationUnfoggedNoPing() + " VS " + player2.getRepresentationUnfoggedNoPing() + " "
                        + StringUtils.capitalize(spaceOrGround) + " combat began",
                threadChannel.getJumpUrl(),
                false);
    }

    private static void offerRedGhostCommanderButtons(Player player, Game game) {
        if (game.playerHasLeaderUnlockedOrAlliance(player, "redcreusscommander")
                || game.playerHasLeaderUnlockedOrAlliance(player, "crimsoncommander")) {
            String message = player.getRepresentation(true, true)
                    + ", you may, at the __end__ of combat, gain 1 commodity or convert 1 of your commodities to a trade good,"
                    + " with Ahk Siever, the Rebellion commander."
                    + "\n-# You have " + player.getCommoditiesRepresentation() + " commodities.";
            List<Button> buttons = ButtonHelperFactionSpecific.gainOrConvertCommButtons(player, true);
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), message, buttons);
        }
    }

    private static void createSpectatorThread(
            Game game,
            Player player,
            String threadName,
            Tile tile,
            GenericInteractionCreateEvent event,
            String spaceOrGround) {
        FileUpload systemWithContext =
                new TileGenerator(game, event, null, 0, tile.getPosition(), player).createFileUpload();

        // Use existing thread, if it exists
        TextChannel textChannel = player.getPrivateChannel();
        for (ThreadChannel threadChannel_ : textChannel.getThreadChannels()) {
            if (threadChannel_.getName().equals(threadName)) {
                initializeSpectatorThread(threadChannel_, game, player, tile, event, systemWithContext, spaceOrGround);
                return;
            }
        }

        MessageChannel channel = player.getPrivateChannel();
        channel.sendMessage("Spectate Combat in this thread:").queue(m -> {
            ThreadChannelAction threadChannel = ((IThreadContainer) channel).createThreadChannel(threadName, m.getId());
            threadChannel = threadChannel.setAutoArchiveDuration(ThreadChannel.AutoArchiveDuration.TIME_3_DAYS);
            threadChannel.queue(
                    tc -> initializeSpectatorThread(tc, game, player, tile, event, systemWithContext, spaceOrGround));
        });
    }

    private static void initializeSpectatorThread(
            ThreadChannel threadChannel,
            Game game,
            Player player,
            Tile tile,
            GenericInteractionCreateEvent event,
            FileUpload systemWithContext,
            String spaceOrGround) {
        StringBuilder message = new StringBuilder();
        message.append(player.getRepresentationUnfogged());
        message.append(" Please spectate the interaction here.\n");
        if ("ground".equals(spaceOrGround)) {
            message.append("## Invasion");
        } else {
            message.append("## Space Combat");
        }
        if (!game.isAllianceMode()) {
            message.append(
                    "\nPlease note, that although you can see the combat participants' messages, you cannot communicate with them.\n");
        }
        message.append("\nImage of System:");
        MessageHelper.sendMessageWithFile(threadChannel, systemWithContext, message.toString(), false);
        sendGeneralCombatButtonsToThread(threadChannel, game, player, player, tile, "justPicture", event);
    }

    private static void sendSpaceCannonButtonsToThread(
            MessageChannel threadChannel, Game game, Player activePlayer, Tile tile) {
        StringBuilder pdsMessage = new StringBuilder();
        List<Player> playersWithPds2 = ButtonHelper.tileHasPDS2Cover(activePlayer, game, tile.getPosition());
        if (tile.isScar(game)) {
            MessageHelper.sendMessageToChannel(
                    threadChannel, "## Reminder that you cannot use any unit abilities in an Entropic Scar.");
            return;
        }
        if (playersWithPds2.isEmpty() || (game.isFowMode() && !playersWithPds2.contains(activePlayer))) {
            return;
        }
        if (!playersWithPds2.contains(activePlayer) && !FoWHelper.playerHasActualShipsInSystem(activePlayer, tile)) {
            return;
        }
        if (!game.isFowMode()) {
            pdsMessage.append("These players have SPACE CANNON coverage against ships in this system:\n");
            for (Player playerWithPds : playersWithPds2) {
                pdsMessage
                        .append("> ")
                        .append(playerWithPds.getRepresentation())
                        .append('\n');
            }
        }
        List<Button> spaceCannonButtons = getSpaceCannonButtons(game, activePlayer, tile);
        if (game.getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_flagship"))) {
            for (Player player : game.getRealPlayers()) {
                String empMessage = NetrunnersUnitsHandler.getEmpSpaceCannonBlockMessage(
                        player, tile, CombatRollType.SpaceCannonOffence);
                if (!empMessage.isEmpty()) {
                    pdsMessage.append(empMessage).append('\n');
                }
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, pdsMessage.toString(), spaceCannonButtons);
        if (!game.isFowMode()) {
            for (Player player : game.getRealPlayers()) {
                if (ButtonHelper.doesPlayerHaveFSHere("argent_flagship", player, tile)) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            "Reminder that you cannot use SPACE CANNON against the ships of "
                                    + player.getFactionEmojiOrColor()
                                    + " due to the ability of the Quetzecoatl (the Argent flagship).");
                }
            }
            UnitHolder space = tile.getSpaceUnitHolder();
            if (game.isOrdinianC1Mode()
                    && (space.getTokenList().contains("token_custc1.png")
                            || space.getTokenList().contains("token_custvpc1.png"))) {
                MessageHelper.sendMessageToChannel(
                        threadChannel,
                        "Reminder that you cannot use SPACE CANNON against ships in this system "
                                + " due to the ability of the Coatl (the Argent flagship represented as the Custodians token).");
            }
        }
    }

    private static void sendStartOfSpaceCombatButtonsToThread(
            ThreadChannel threadChannel, Game game, Player player1, Player player2, Tile tile) {
        List<Button> startOfSpaceCombatButtons = getStartOfSpaceCombatButtons(game, player1, player2, tile);
        if (!startOfSpaceCombatButtons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(
                    threadChannel, "Buttons for start of space combat abilities.", startOfSpaceCombatButtons);
        }
    }

    private static void sendStartOfCombatSecretMessages(
            Game game, Player p1, Player p2, Tile tile, String type, String unitHolderName) {
        List<Player> combatPlayers = new ArrayList<>();
        combatPlayers.add(p1);
        combatPlayers.add(p2);
        List<Button> buttons = new ArrayList<>();

        for (Player player : combatPlayers) {
            Player otherPlayer = p1;
            if (otherPlayer == player) {
                otherPlayer = p2;
            }
            String msg = player.getRepresentation();
            if (ButtonHelper.doesPlayerHaveFSHere("cymiae_flagship", player, tile)) {
                buttons.add(Buttons.green("resolveSpyStep1", "Resolve Cymiae Flagship"));
                buttons.add(Buttons.red("deleteButtons", "Delete These"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", if you win the combat, you have the opportunity to use the Reprocessor Alpha (the Cymiae flagship)"
                                + " to force the other player to send you a random action card. It will send buttons to the other player to confirm.",
                        buttons);
            }
            List<Button> buttons2 = new ArrayList<>();
            buttons2.add(Buttons.red("get_so_score_buttons", "Score A Secret Objective"));
            if ("space".equalsIgnoreCase(type)
                    && player.getSecretsUnscored().containsKey("uf")
                    && tile.getUnitHolders().get("space").getUnitCount(Units.UnitType.Flagship, player.getColor())
                            > 0) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if you win the combat, and your flagship survives, you could score _Unveil Flagship_.",
                        buttons2);
            }
            if ("space".equalsIgnoreCase(type)
                    && player.getSecretsUnscored().containsKey("dtgs")
                    && (tile.getUnitHolders().get("space").getUnitCount(Units.UnitType.Flagship, otherPlayer.getColor())
                                    > 0
                            || tile.getUnitHolders()
                                            .get("space")
                                            .getUnitCount(Units.UnitType.Warsun, otherPlayer.getColor())
                                    > 0)) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that you could potentially score _Destroy Their Greatest Ship_ in this combat.",
                        buttons2);
            }
            if (player.getSecretsUnscored().containsKey("sar")
                    && otherPlayer.getTotalVictoryPoints() == game.getHighestScore()) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        msg + ", a reminder that if you win the combat, you could score _Spark a Rebellion_.",
                        buttons2);
            }
            if (player.getSecretsUnscored().containsKey("btv") && tile.isAnomaly(game, player)) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        msg + ", a reminder that if you win the combat, you could score _Brave the Void_.",
                        buttons2);
            }
            if (player.getSecretsUnscored().containsKey("dts")
                    && tile.isHomeSystem(game)
                    && tile != player.getHomeSystemTile()) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        msg + ", a reminder that if you win the combat, you could score _Darken the Skies_.",
                        buttons2);
            }
            if (player.hasAbility("war_stories")) {
                msg +=
                        ", a reminder that if you win the combat, and you have not already done so this action, you may use **War Stories** to explore any planet you control.";
                buttons = new ArrayList<>();
                buttons.add(Buttons.green("warStoriesPlanetExplore", "Explore A Planet You Control"));
                if (tile.getPlanetUnitHolders().isEmpty()) {
                    msg +=
                            " Instead of exploring a planet you control, you may instead explore the frontier exploration deck in this system, since it contains no planets.";
                    buttons.add(Buttons.gray("warStoriesFrontier_" + game.getActiveSystem(), "Explore Frontier"));
                }

                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            }
            int capitalShips = ButtonHelper.checkFleetAndCapacity(player, game, tile, true, true)[0];
            if ("space".equalsIgnoreCase(type) && player.getSecretsUnscored().containsKey("dyp") && capitalShips >= 3) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if you keep alive at least 3 non-fighter ships in the active system until the end of combat, you could score _Demonstrate Your Power_.",
                        buttons2);
            }

            if ((player.hasAbility("primacy")
                            || player.hasAbility("edict")
                            || player.hasAbility("edict_y")
                            || player.hasAbility("imperia")
                            || player.hasAbility("imperia_y"))
                    && !player.getMahactCC().contains(otherPlayer.getColor())
                    && !"neutral".equalsIgnoreCase(otherPlayer.getFaction())) {
                buttons = new ArrayList<>();
                String factionChecker = player.factionButtonChecker();
                String location = player.hasAbility("primacy") ? "Primacy" : "Fleet";
                buttons.add(Buttons.gray(
                        factionChecker + "mahactStealCC_" + otherPlayer.getColor(),
                        "Add " + otherPlayer.getColor() + " Token to " + location,
                        FactionEmojis.Mahact));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg + ", a reminder that if you win this combat, you may add the opponents ("
                                + otherPlayer.getColor() + ") command token to your "
                                + (player.hasAbility("primacy") ? "Primacy ability." : "fleet pool."),
                        buttons);
            }
            if (player.hasUnlockedBreakthrough("sardakkbt")) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray(
                        player.factionButtonChecker() + "sardakkbtRes",
                        "Resolve N'orr Supremacy (Upon Win)",
                        FactionEmojis.Sardakk));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if you win this combat, you may resolve _N'orr Supremacy_ for a unit upgrade technology or a command token.",
                        buttons);
            }
            if ("space".equalsIgnoreCase(type) && player.hasUnexhaustedLeader("kaloraagent")) {
                KaloraLeaderHandler.offerKaloraAgentButtons(player, msg);
            }
            if ("space".equalsIgnoreCase(type) && ButtonHelper.doesPlayerHaveFSHere("onyxxa_flagship", player, tile)) {
                OnyxxaUnitHandler.offerFlagshipWinButton(player, msg);
            }
            if ("space".equalsIgnoreCase(type) && game.playerHasLeaderUnlockedOrAlliance(player, "arvaxicommander")) {
                ArvaxiLeaderHandler.sendCombatButtons(player, otherPlayer, game, msg);
            }
            if (player.hasTechReady("dskortg") && CommandCounterHelper.hasCC(player, tile)) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray(
                        "exhaustTech_dskortg_" + tile.getPosition(), "Tempest Drive", FactionEmojis.kortali));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if you win the combat, you may use this button to remove a command token from the system.",
                        buttons);
            }
            if (player == p2
                    && player.hasUnlockedBreakthrough("dreambt")
                    && tile.getPosition().equals(game.getActiveSystem())
                    && CommandCounterHelper.hasCC(player, tile)
                    && DreamButtonHandler.tileContainsNexusToken(game, tile, true)) {
                DreamButtonHandler.offerDreamBtRemoveCommandTokenButton(game, player, tile, msg);
            }
            if (player.hasUnlockedBreakthrough("zephyrionbt")
                    && "space".equalsIgnoreCase(type)
                    && ButtonHelper.isTileInOrAdjacentToPlayersHome(game, tile, otherPlayer, player)) {
                ZephyrionBreakthroughHandler.offerBtCombatButtons(player, otherPlayer, game, msg);
            }
            if (player.hasTechReady("bakalor") && "space".equalsIgnoreCase(type) && !tile.isHomeSystem(game)) {
                KaloraAbilityHandler.chitinShielding(player, otherPlayer, game);
            }
            if (player.hasAbility("technological_singularity")
                    && !otherPlayer.isDummy()
                    && (!ButtonHelperAbilities.getPossibleTechForNekroToGainFromPlayer(
                                            player, otherPlayer, new ArrayList<>(), game)
                                    .isEmpty()
                            || player.hasUnlockedBreakthrough("nekrobt"))) {
                Button steal = Buttons.gray(
                        player.factionButtonChecker() + "nekroStealTech_" + otherPlayer.getFaction(),
                        "Copy a Technology From " + StringUtils.capitalize(otherPlayer.getColor()),
                        FactionEmojis.Nekro);
                String message = msg
                        + ", a reminder that when you first kill an opponent's unit this combat, you may use the button to copy a technology.";
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, steal);
            }
            if (player.hasUnit("tk-maleagant") && "space".equalsIgnoreCase(type)) {
                String message = player.getRepresentation() + ", a reminder that when you first kill an opponent's";
                message += " unit this combat, you may use the button to resolve your ";
                message += Mapper.getUnit("tk-maleagant").getNameRepresentation() + " ability.";
                Button steal = Buttons.gray("maleagantBegin", "Discard then Draw Ability", UnitEmojis.fighter);
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, steal);
            }
            if ((player.hasTech("tf-singularityz")
                            || player.hasTech("tf-singularityy")
                            || player.hasTech("tf-singularityx"))
                    && !otherPlayer.isDummy()) {
                Button steal = Buttons.gray(
                        player.factionButtonChecker() + "nekroStealTech_" + otherPlayer.getFaction(),
                        "Copy a Technology From " + StringUtils.capitalize(otherPlayer.getColor()),
                        FactionEmojis.Nekro);
                String message = msg
                        + ", a reminder that when you first kill an opponent's unit this combat, you may use the button to copy a technology."
                        + " If you copy more techs than you have singularities, manually remove old ones with `/tech remove`.";
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, steal);
            }
            if (player.hasUnit("ghemina_mech")
                    && "ground".equalsIgnoreCase(type)
                    && ButtonHelper.getUnitHolderFromPlanetName(unitHolderName, game)
                                    .getUnitCount(Units.UnitType.Mech, player)
                            == 2) {
                Button explore = Buttons.gray(
                        player.factionButtonChecker() + "gheminaMechStart_" + unitHolderName,
                        "Mech Explores",
                        FactionEmojis.ghemina);
                String message = msg
                        + ", a reminder that if you win the combat, you may use the button to resolve your mech ability.";
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, explore);
            }
            if (player.hasUnlockedBreakthrough("obsidianbt") && player.isOtherPlayerPuppeted(otherPlayer)) {
                Button reap = Buttons.gray(
                        player.factionButtonChecker() + "theReapingAddTg",
                        "Add TG to The Reaping",
                        FactionEmojis.Obsidian);
                String message = msg
                        + ", a reminder that if you win this combat, you may use the button to add a trade good to _The Reaping_.";
                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(), message, reap);
            }

            boolean salvage = player.hasTech("so");
            salvage |= player.hasUnit("tk-salvagebarge")
                    & tile.getSpaceUnitHolder().getUnitCount(UnitType.Dreadnought, player) > 0;
            if ("space".equalsIgnoreCase(type) && salvage) {
                buttons = new ArrayList<>();
                String label = game.isTwilightKart() ? "Salvage Barge" : "Salvage Operations";
                buttons.add(Buttons.gray("salvageOps_" + tile.getPosition(), label, FactionEmojis.Mentak));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if the combat does not end in a draw, you may use the button to resolve _Salvage Operations_.",
                        buttons);
            }
            if ("space".equalsIgnoreCase(type) && game.playerHasLeaderUnlockedOrAlliance(player, "mentakcommander")) {
                String factionChecker = player.factionButtonChecker();
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray(
                        factionChecker + "mentakCommander_" + otherPlayer.getColor(),
                        "Resolve Mentak Commander on " + otherPlayer.getColor(),
                        FactionEmojis.Mentak));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg + ", a reminder that if you win the combat, "
                                + "you may use the button to resolve S'ula Mentarion, the Mentak commander.",
                        buttons);
            }
            var qhethero = player.getLeader("qhethero");
            if (qhethero.map(Leader::isActive).orElse(false)) {
                String factionChecker = player.factionButtonChecker();
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray(
                        factionChecker + "qhetHero_" + tile.getPosition(),
                        "Unlock " + tile.getRepresentationForButtons(),
                        FactionEmojis.qhet));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg + ", a reminder that if you win the combat, "
                                + "you may use the button unlock the system, due to Tvor Khage, the Qhet hero.",
                        buttons);
            }
            if (player.hasAbility("black_ops") && player == game.getActivePlayer()) {
                int debt = player.getDebtTokenCount(otherPlayer.getColor());
                if (debt > 2) {
                    String factionChecker = player.factionButtonChecker();
                    buttons = new ArrayList<>();
                    buttons.add(Buttons.gray(
                            factionChecker + "blackOps_" + otherPlayer.getColor() + "_3",
                            "Turn in 3 Tokens",
                            FactionEmojis.qhet));
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCardsInfoThread(),
                            msg + ", a reminder that if you win the combat, you may use the button to"
                                    + " cash in 3 of the control tokens you hold in order to draw a secret objective,"
                                    + " or draw 2 action cards, or gain 1 command token.",
                            buttons);
                }
            }
            if (game.playerHasLeaderUnlockedOrAlliance(player, "qhetcommander") && player == game.getActivePlayer()) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        msg + ", a reminder that if you win a combat during this action "
                                + "you may take an additional action due to Ghaist Asmora, the Qhet commander.");
            }
            if (player.getPromissoryNotes().containsKey("dspnqhet")
                    && !player.getPromissoryNotesOwned().contains("dspnqhet")) {
                MessageHelper.sendMessageToChannel(
                        player.getCardsInfoThread(),
                        player.getRepresentationUnfogged() + ", a reminder you have _Alloy Shipment_.");
            }
            if (player.hasAbility("moult") && player != game.getActivePlayer() && "space".equalsIgnoreCase(type)) {
                String factionChecker = player.factionButtonChecker();
                buttons = new ArrayList<>();
                buttons.add(
                        Buttons.gray(factionChecker + "moult_" + tile.getPosition(), "Moult", FactionEmojis.cheiran));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if you win the combat, you will be given buttons to resolve **Moult**, allowing you to produce 1 ship, reducing the cost"
                                + " by 1 for each non-fighter ship you will have lost in the combat.",
                        buttons);
            }
            if (player.getPromissoryNotes().containsKey("dspnmort")
                    && !player.getPromissoryNotesOwned().contains("dspnmort")
                    && player != game.getActivePlayer()
                    && "space".equalsIgnoreCase(type)) {
                String factionChecker = player.factionButtonChecker();
                buttons = new ArrayList<>();
                buttons.add(Buttons.gray(
                        factionChecker + "startFacsimile_" + tile.getPosition(),
                        "Play Secrets of the Weave",
                        FactionEmojis.mortheus));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that you may play _Secrets of the Weave_ here to spend influence equal to the cost of 1 of the opponent ships to "
                                + "place 1 of that type of ship in the system.",
                        buttons);
            }
            boolean techOrLegendary = false;
            for (UnitHolder planet : tile.getPlanetUnitHolders()) {
                if (ButtonHelper.checkForTechSkips(game, planet.getName()) || ButtonHelper.isTileLegendary(tile)) {
                    techOrLegendary = true;
                }
            }
            if (techOrLegendary
                    && player.getLeaderIDs().contains("augerscommander")
                    && !player.hasLeaderUnlocked("augerscommander")) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.green("unlockCommander_augers", "Unlock Ilyxum Commander", FactionEmojis.augers));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if you win the combat here, you may use the button to unlock Lachis, the Ilyxum commander.",
                        buttons);
            }
            if (player.getLeaderIDs().contains("qhetcommander") && !player.hasLeaderUnlocked("qhetcommander")) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.green("unlockCommander_qhet", "Unlock Qhet Commander", FactionEmojis.qhet));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if you win two combats this turn, you may use the button to unlock Ghaist Asmora, the Qhet commander.",
                        buttons);
            }
            if (player.getLeaderIDs().contains("belkoseahero")
                    && player.hasLeaderUnlocked("belkoseahero")
                    && !player.hasUnexhaustedLeader("belkoseahero")) {
                buttons = new ArrayList<>();
                buttons.add(Buttons.green("refreshBelkoseaHero", "Ready Belkosea Hero", FactionEmojis.belkosea));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg + ", a reminder that if you lose this combat, you ready Mobius Spike, Belkosea Hero.",
                        buttons);
            }
            if (player.getLeaderIDs().contains("kortalicommander") && !player.hasLeaderUnlocked("kortalicommander")) {
                buttons = new ArrayList<>();
                buttons.add(
                        Buttons.green("unlockCommander_kortali", "Unlock Kortali commander", FactionEmojis.kortali));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        msg
                                + ", a reminder that if you destroy all of the opponent's units in this system, you may use the button to unlock Queen Lorena, "
                                + "the Kortali commander.",
                        buttons);
            }
        }
    }

    private static void sendAFBButtonsToThread(
            GenericInteractionCreateEvent event,
            ThreadChannel threadChannel,
            Game game,
            List<Player> combatPlayers,
            Tile tile) {

        if (tile.isScar(game)) {
            MessageHelper.sendMessageToChannel(
                    threadChannel, "## Reminder that you cannot use any unit abilities in an Entropic Scar.");
            return;
        }

        List<Button> afbButtons = new ArrayList<>();
        afbButtons.add(Buttons.gray("combatRoll_" + tile.getPosition() + "_space_afb", "Roll ANTI-FIGHTER BARRAGE"));
        for (Player player : combatPlayers) {
            if (player.isNpc() || player.isDummy()) {
                afbButtons.add(Buttons.green(
                        player.dummyPlayerSpoof() + "combatRoll_" + tile.getPosition() + "_space_afb",
                        "Roll ANTI-FIGHTER BARRAGE For Dummy"));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                threadChannel, "Buttons to roll ANTI-FIGHTER BARRAGE (if applicable).", afbButtons);
        if (!game.isFowMode()) {
            for (Player player : combatPlayers) {
                if (player.hasRelic("metalivoidarmaments")) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            player.getRepresentationUnfogged()
                                    + " Reminder that you have the Metal Void Armaments relic to use AFB 3x6.");
                }
                if (player.hasTech("tf-projectionofpow")) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            player.getRepresentationUnfogged()
                                    + " Reminder that you have the Projection of Power to use AFB 2x6.");
                }
                if (player.hasAbility("projection_of_power")) {
                    boolean adj = false;
                    for (Tile tile2 : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Spacedock)) {
                        if (FoWHelper.getAdjacentTiles(game, tile2.getPosition(), player, false, true)
                                .contains(tile.getPosition())) {
                            adj = true;
                            break;
                        }
                    }
                    if (adj) {
                        MessageHelper.sendMessageToChannel(
                                threadChannel,
                                player.getRepresentationUnfogged()
                                        + " Reminder that you have the Projection of Power to use AFB 1x6.");
                    }
                }
                if ((ButtonHelper.doesPlayerHaveMechHere("naalu_mech_omega", player, tile)
                                && !ButtonHelper.isLawInPlay(game, "articles_war"))
                        || ButtonHelper.doesPlayerHaveFSHere("sigma_naalu_flagship_1", player, tile)
                        || ButtonHelper.doesPlayerHaveFSHere("sigma_naalu_flagship_2", player, tile)) {
                    MessageHelper.sendMessageToChannel(
                            threadChannel,
                            "Reminder that you cannot use ANTI-FIGHTER BARRAGE against "
                                    + player.getFactionEmojiOrColor() + " due to their mech power.");
                }
            }
        }
    }

    private static List<Button> getSpaceCannonButtons(Game game, Player activePlayer, Tile tile) {
        List<Button> spaceCannonButtons = new ArrayList<>();
        if (game.getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_flagship"))
                && NetrunnersUnitsHandler.empBlocksSpaceCannonAgainst(
                        activePlayer, tile, CombatRollType.SpaceCannonOffence)) {
            if (!game.isFowMode()) {
                spaceCannonButtons.add(Buttons.red("declinePDS_" + tile.getTileID(), "Decline SPACE CANNON"));
            }
            return spaceCannonButtons;
        }
        spaceCannonButtons.add(Buttons.gray(
                "combatRoll_" + tile.getPosition() + "_space_spacecannonoffence", "Roll SPACE CANNON Offence"));
        if (game.isFowMode()) return spaceCannonButtons;
        spaceCannonButtons.add(Buttons.red("declinePDS_" + tile.getTileID(), "Decline SPACE CANNON"));

        // Add Graviton Laser System button if applicable
        for (Player playerWithPds : ButtonHelper.tileHasPDS2Cover(activePlayer, game, tile.getPosition())) {
            if (playerWithPds.hasTechReady("gls")) { // Graviton Laser Systems
                spaceCannonButtons.add(
                        Buttons.gray("exhaustTech_gls", "Exhaust Graviton Laser System", TechEmojis.CyberneticTech));
                break;
            }
        }
        if (game.getRealPlayers().stream().anyMatch(player -> player.hasAbility("control_network"))) {
            for (Player rollingPlayer : ButtonHelper.tileHasPDS2Cover(activePlayer, game, tile.getPosition())) {
                if (game.getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_flagship"))
                        && NetrunnersUnitsHandler.empBlocksSpaceCannonAgainstOpponent(
                                game, rollingPlayer, tile, CombatRollType.SpaceCannonOffence)) {
                    continue;
                }
                spaceCannonButtons.addAll(NetrunnersAbilitiesHandler.getControlNetworkSpaceCannonButtons(
                        game, rollingPlayer, tile, CombatRollType.SpaceCannonOffence, "space"));
            }
        }
        return spaceCannonButtons;
    }

    private static boolean hasCendos(Player player, Tile tile) {
        int nonFighterShips = 0;
        boolean hasDestroyer = false;
        for (UnitKey unit : tile.getSpaceUnitHolder().getUnitKeysForPlayer(player)) {
            UnitModel model = player.getUnitFromUnitKey(unit);
            if (unit.unitType() == UnitType.Destroyer) {
                hasDestroyer = true;
            }
            if (model.isNonFighterShip()) {
                nonFighterShips += tile.getSpaceUnitHolder().getUnitCount(unit);
            }
        }
        return player.hasUnit("tk-cendos") && hasDestroyer && nonFighterShips >= 3;
    }

    private static List<Button> getStartOfSpaceCombatButtons(Game game, Player p1, Player p2, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        if (game.isFowMode()) return buttons;

        // Assault Cannon
        if ((p1.hasTech("asc")
                        && (ButtonHelper.checkNumberNonFighterShips(p1, tile) >= 3
                                || ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", p1, tile)
                                || ButtonHelper.doesPlayerHaveFSHere("sigma_nekro_flagship_2", p1, tile)))
                || (p2.hasTech("asc")
                        && (ButtonHelper.checkNumberNonFighterShips(p2, tile) >= 3
                                || ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", p2, tile)
                                || ButtonHelper.doesPlayerHaveFSHere("sigma_nekro_flagship_2", p2, tile)))) {
            buttons.add(Buttons.blue(
                    "assCannonNDihmohn_asc_" + tile.getPosition(), "Use Assault Cannon", TechEmojis.WarfareTech));
        }

        // Assault Escort
        if (hasCendos(p1, tile) || hasCendos(p2, tile)) {
            buttons.add(
                    Buttons.blue("assCannonNDihmohn_assEsc_" + tile.getPosition(), "Use Cendos", UnitEmojis.destroyer));
        }

        // Dimensional Splicer
        if (FoWHelper.doesTileHaveWHs(game, tile.getPosition()) && (p1.hasTech("ds") || p2.hasTech("ds"))) {
            buttons.add(Buttons.blue(
                    "assCannonNDihmohn_ds_" + tile.getPosition(), "Use Dimensional Splicer", FactionEmojis.Ghost));
        }

        if ((p1.hasAbility("shroud_of_lith")
                        && ButtonHelperFactionSpecific.getKolleccReleaseButtons(p1, game)
                                        .size()
                                > 1)
                || (p2.hasAbility("shroud_of_lith")
                        && ButtonHelperFactionSpecific.getKolleccReleaseButtons(p2, game)
                                        .size()
                                > 1)) {
            buttons.add(Buttons.blue("shroudOfLithStart", "Use Shroud of Lith", FactionEmojis.kollecc));
        }

        // Dihmohn Commander
        if ((game.playerHasLeaderUnlockedOrAlliance(p1, "dihmohncommander")
                        && ButtonHelper.checkNumberNonFighterShips(p1, tile) > 2)
                || (game.playerHasLeaderUnlockedOrAlliance(p2, "dihmohncommander")
                        && ButtonHelper.checkNumberNonFighterShips(p2, tile) > 2)) {
            buttons.add(Buttons.blue(
                    "assCannonNDihmohn_dihmohn_" + tile.getPosition(),
                    "Use Dih-Mohn Commander",
                    FactionEmojis.dihmohn));
        }

        // Ambush
        if ((p1.hasAbility("ambush")) || p2.hasAbility("ambush")) {
            buttons.add(Buttons.gray("rollForAmbush_" + tile.getPosition(), "Ambush", FactionEmojis.Mentak));
        }

        if ((p1.hasLeaderUnlocked("mentakhero")) || p2.hasLeaderUnlocked("mentakhero")) {
            buttons.add(
                    Buttons.gray("purgeMentakHero_" + tile.getPosition(), "Purge Mentak Hero", FactionEmojis.Mentak));
        }

        if ((p1.hasLeaderUnlocked("belkoseahero") && p1.hasUnexhaustedLeader("belkoseahero"))
                || (p2.hasLeaderUnlocked("belkoseahero") && p2.hasUnexhaustedLeader("belkoseahero"))) {
            buttons.add(Buttons.gray("exhaustBelkoseaHero", "Exhaust Belkosea Hero", FactionEmojis.belkosea));
        }

        if ((p1.hasAbility("facsimile") && p1 != game.getActivePlayer())
                || p2.hasAbility("facsimile") && p2 != game.getActivePlayer() && !game.isFowMode()) {
            buttons.add(Buttons.gray("startFacsimile_" + tile.getPosition(), "Facsimile", FactionEmojis.mortheus));
        }

        // Facet
        if (CrystellumLeadersHandler.canUseCrystellumHero(p1)) {
            buttons.add(CrystellumLeadersHandler.getCrystellumHeroButton(p1, tile));
        } else if (CrystellumLeadersHandler.canUseCrystellumHero(p2)) {
            buttons.add(CrystellumLeadersHandler.getCrystellumHeroButton(p2, tile));
        }

        // mercenaries
        Player florzen = Helper.getPlayerFromAbility(game, "mercenaries");
        if (florzen != null && FoWHelper.playerHasFightersInAdjacentSystems(florzen, tile, game)) {
            buttons.add(Buttons.gray(
                    florzen.factionButtonChecker() + "mercenariesStep1_" + tile.getPosition(),
                    "Mercenaries",
                    FactionEmojis.florzen));
        }
        return buttons;
    }

    /**
     * # of extra rings to show around the tile image
     *
     * @return 0 if no PDS2 nearby, 1 if PDS2 is nearby
     */
    private static int getTileImageContextForPDS2(Game game, Player player1, Tile tile, String spaceOrGround) {
        if (game.isFowMode() || "ground".equalsIgnoreCase(spaceOrGround)) {
            return 0;
        }
        if (!ButtonHelper.tileHasPDS2Cover(player1, game, tile.getPosition()).isEmpty()) {
            return 1;
        }
        return 0;
    }

    private static void sendGeneralCombatButtonsToThread(
            ThreadChannel threadChannel,
            Game game,
            Player player1,
            Player player2,
            Tile tile,
            String spaceOrGround,
            GenericInteractionCreateEvent event) {
        List<Button> buttons =
                CombatV2StartButtons.getGeneralCombatButtons(game, tile.getPosition(), player1, player2, spaceOrGround);
        MessageHelper.sendMessageToChannelWithButtons(threadChannel, "Buttons for combat.", buttons);
    }

    private static String getSpaceCombatIntroMessage() {
        return """
            ## Steps for End of Movement & Space Combat:
            > 1. End of movement abilities (**Foresight**, _Stymie_, etc.)
            > 2. Space Cannon Offense
            > 3. Start of Combat (_Skilled Retreat_, _Morale Boost_, etc.)
            > 4. Anti-Fighter Barrage
            > 5. Declare Retreats (including _Rout_)
            > 6. Roll Dice!
            > 7. Rerolls (Thundarian, War Funding, Thalnos, etc.)
            > 8. Cancel hits (Shields Holding, Sustain Damage, Titans agent)
            > 9. Assign Hits (This is the only step that is done simultaneously)
            > 10. Retreat (if declared in step 5)
            > 11. After a round of combat abilities (Yin Devotion, Sardakk Exo 2)
            """;
    }

    private static String getGroundCombatIntroMessage() {
        return """
            ## Steps for Invasion:
            > 1. Start of invasion abilities (_Tekklar Legion_, _Blitz_, _Bunker_, etc.)
            > 2. Bombardment
            > 3. Commit Ground Forces
            > 4. After commit window (_Parley_, _Ghost Squad_, etc.)
            > 5. Space Cannon Defense
            > 6. Start of Combat (_Morale Boost_, etc.)
            > 7. Roll Dice!
            """;
    }

    private static String combatThreadName(
            Game game, Player player1, @Nullable Player player2, Tile tile, String specialCombatTitle) {
        StringBuilder sb = new StringBuilder();
        sb.append(game.getName())
                .append("-round-")
                .append(game.getRound())
                .append("-system-")
                .append(tile.getPosition())
                .append("-turn-")
                .append(player1.getInRoundTurnCount())
                .append("-");
        if (game.isFowMode()) {
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

    private record StartContext(
            Game game,
            Player player1,
            Player player2,
            Tile tile,
            GenericInteractionCreateEvent event,
            String spaceOrGround,
            String unitHolderName,
            boolean firstCombatThread) {}
}
