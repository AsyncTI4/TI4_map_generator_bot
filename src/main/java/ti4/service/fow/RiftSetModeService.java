package ti4.service.fow;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.AsyncTI4DiscordBot;
import ti4.buttons.Buttons;
import ti4.commands.tokens.AddTokenCommand;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RandomHelper;
import ti4.helpers.RiftUnitsHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.model.UnitModel;
import ti4.service.StellarConverterService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.option.FOWOptionService.FOWOption;

/*
 * For Eronous to run fow300
 * 
 * HOW TO RUN
 * 1. Have Eronous in a FoW game
 * 2. Run /game weird_game_setup riftset_mode: true
 * 3. Setup any player as Cabal
 * 
 * SPECS:
 * - When any unit fails rift throw, Cabal eats it
 * - One additional Custom Strategy Card, 9. Sacrifice
 * - One additional agenda, Crucible Reallocation
 *   - Removed from the deck at setup. Can be flipped with a button in every agenda phase.
 * - Custom frontier explore Unstable Rifts (tells player to ping GM to resolve)
 * - /special swap_systems to support RANDOM options
 * - A way to see what _own_ units Cabal has captured (button in Cards Thread)
 * AFTER CUSTODIANS IS SCORED:
 * - When concluding tactical action, tile has a 1/10 chance of placing a gravity rift
 * - When concluding tactical action, tile has a 1/25 chance of placing Vortex (gravity rift wormhole)
 * - Exploring a planet has chance of Stellar Converting it (with a custom token)
 * 
 */
public class RiftSetModeService {
    private static final String CRUCIBLE_PN = "crucible";
    private static final String CRUCIBLE_AGENDA = "riftset_crucible";
    private static final String RIFTSET_INVASION_EXPLORE = "riftset_invasion";

    private static final int CHANCE_TO_SPAWN_RIFT = 8; // 1/8
    private static final int CHANCE_TO_SPAWN_VORTEX = 16; // 1/16
    private static final int CHANCE_TO_STELLAR_CONVERT = 100; // 1/100
    private static final int CHANCE_TO_STELLAR_CONVERT_MIN = 25; // 1/25

    public static boolean activate(GenericInteractionCreateEvent event, Game game) {
        if (game.getPlayer(Constants.eronousId) == null && !AsyncTI4DiscordBot.fowServers.isEmpty()) {
            MessageHelper.replyToMessage(event, "Can only use RiftSetMode if Eronous is in the game.");
            return false;
        }

        if (!game.isFowMode()) {
            MessageHelper.replyToMessage(event, "Can only use RiftSetMode in FoW");
            return false;
        }

        if (!game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_riftset"))) return false;
        if (!game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_riftset"))) return false;
        game.discardSpecificAgenda(CRUCIBLE_AGENDA);
        game.setStrategyCardSet("riftset");
        game.addTag("RiftSet");
        game.setFowOption(FOWOption.RIFTSET_MODE, true);
        return true;
    }

    public static boolean isActive(Game game) {
        return game.getFowOption(FOWOption.RIFTSET_MODE);
    }

    private static Player getCabalPlayer(Game game) {
        return isActive(game) ? game.getPlayerFromColorOrFaction("cabal") : null;
    }

    public static String riftSetCabalEatsUnit(String msg, Player player, Game game, String unit, GenericInteractionCreateEvent event) {
        if (!isActive(game)) return msg;

        Player cabal = getCabalPlayer(game);
        ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, 1, unit, event);
        msg = msg.replace("Condolences for your loss.", "");
        msg += "Mysteriously, not even debris was left behind...";
        return msg;
    }

    public static void includeCrucibleAgendaButton(List<Button> buttons, Game game) {
        if (!isActive(game) || !"2".equals(game.getStoredValue("agendaCount"))) return;

        buttons.add(Buttons.blue("riftsetflip_" + CRUCIBLE_AGENDA, "Flip Crucible Reallocation"));
    }

    @ButtonHandler("riftsetflip_" + CRUCIBLE_AGENDA)
    public static void flipRiftSetCrucible(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer uniqueID = discardAgendas.get(CRUCIBLE_AGENDA);
        if (uniqueID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Agenda `" + CRUCIBLE_AGENDA + "` not found.");
            return;
        }
        game.putAgendaBackIntoDeckOnTop(uniqueID);
        AgendaHelper.revealAgenda(event, false, game, game.getMainGameChannel());
    }

    public static void resolveRiftSetCrucible(String agendaID, Player winner, Game game) {
        if (!isActive(game) || !CRUCIBLE_AGENDA.equalsIgnoreCase(agendaID)) return;

        for (Player p : game.getRealPlayers()) {
            if (p.hasPlayablePromissoryInHand(CRUCIBLE_PN)) {
                p.removePromissoryNote(CRUCIBLE_PN);
                PromissoryNoteHelper.sendPromissoryNoteInfo(game, p, false);
                break;
            }
        }

        Player cabal = getCabalPlayer(game);
        cabal.removePromissoryNote(CRUCIBLE_PN);

        winner.setPromissoryNote(CRUCIBLE_PN);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, winner, false);

        PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(CRUCIBLE_PN);
        MessageHelper.sendMessageToChannel(winner.getCorrectChannel(), winner.getRepresentation(true, true) + ", you recieved " + CardEmojis.PN + pnModel.getName());
    }

    public static void resolveExplore(String exploreCardId, Player player, Game game) {
        if (!isActive(game)) return;

        if (exploreCardId.startsWith(RIFTSET_INVASION_EXPLORE)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                "-# GM ping: " + getGMs(game) + " Unstable Rifts Event waiting for resolving!");
        }
    }

    private static String getGMs(Game game) {
        return game.getPlayersWithGMRole().stream().map(Player::getPing).collect(Collectors.joining(", "));
    }

    public static void concludeTacticalAction(Player player, Game game, GenericInteractionCreateEvent event) {
        if (!isActive(game) || !game.isCustodiansScored()) return;

        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile.getTileModel().isGravityRift() || tile.hasCabalSpaceDockOrGravRiftToken() || tile.isHomeSystem()) {
            return;
        }

        if (RandomHelper.isOneInX(CHANCE_TO_SPAWN_VORTEX)) {
            AddTokenCommand.addToken(event, tile, "vortex", game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## A strange Vortex has formed in " + tile.getPosition()
                + "\n-# " + getGMs(game));
        } else  if (RandomHelper.isOneInX(CHANCE_TO_SPAWN_RIFT)) {
            AddTokenCommand.addToken(event, tile, "gravityrift", game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## A new Gravity Rift has formed in " + tile.getPosition()
                + "\n-# " + getGMs(game));
        }
    }

    /* Round  Probability (%)
     *  1     1.00%
     *  2     1.19%
     *  3     1.47%
     *  4     1.92%
     *  5     2.78%
     *  6     4.00% (capped)
     */
    public static boolean willPlanetGetStellarConverted(String planetName, Player player, Game game, GenericInteractionCreateEvent event) {
        if (!isActive(game) || !game.isCustodiansScored()) return false;

        if (RandomHelper.isOneInX(Math.max(CHANCE_TO_STELLAR_CONVERT - (int) (16 * Math.pow(Math.min(game.getRound(), 6) - 1, 2)), CHANCE_TO_STELLAR_CONVERT_MIN))) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## While trying to explore the planet, you find something dark and dangerous..."
                + "\n-# " + getGMs(game));
            StellarConverterService.secondHalfOfStellar(game, planetName, event);
            Tile tile = game.getTileFromPlanet(planetName);
            UnitHolder unitHolder = tile.getUnitHolderFromPlanet(planetName);
            unitHolder.removeAllTokens();
            unitHolder.addToken("token_worlddestroyed_riftset.png");
            return true;
        }

        return false;
    }

    public static void swappedSystems(Game game) {
        if (!isActive(game)) return;

        String msg = "T##m% & sp¿c€ ß̶e̷g̷i̵n̸ T0øøø U̴̪̖͒͛͗̏N̸̻̦̜̊͒̈́̄R̵͎̅͆͘Ȧ̵̳̔̚V̴̹̜̽̾̄̓L̶̥̩̎.̷̨͕̻͑̄̓̕.̸̙̏̄̄͜.̷̼̝̲̩̆́̕";
        switch (game.getRound()) {
            case 1,2 -> {
                msg = "Time and space begin to unravel.";
            }
            case 3 -> {
                msg = "Tíme and space bégin tto unravl...";
            }
            case 4 -> {
                msg = "Ti.m.e an d spa-ce bgin t.o u̷nravl..";
            }
            case 5 -> {
                msg = "T!m- ænd sp^ce b...ggn t0 üñr@vl~";
            }
            case 6 -> {
                msg = "T#m% & spa¿c€ ßegi_n tØøø u̘͔͜ń̢͜r̶͙̜a͓͉͟v̷̪͎l...";
            }
        }
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), "# " + msg);
    }

    public static void addCapturedUnitsButton(List<Button> buttons, Game game) {
        if (!isActive(game)) return;
        buttons.add(Buttons.gray("riftsetshowcaptured", "Show captured units", SourceEmojis.Eronous));
    }

    @ButtonHandler("riftsetshowcaptured")
    public static void showCapturedUnits(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        if (!isActive(game)) return;

        String capturedUnits = getCapturedUnitsAsEmojis(game, player);
        MessageHelper.sendMessageToChannel(event.getChannel(),
            "Following units of " + player.getRepresentation(false, false) + " are currently held captive:\n"
                + (capturedUnits.isEmpty() ? "None" : capturedUnits));
    }

    private static String getCapturedUnitsAsEmojis(Game game, Player player) {
        Player cabal = getCabalPlayer(game);
        UnitHolder nombox = cabal.getNomboxTile().getSpaceUnitHolder();
        return nombox.getPlayersUnitListEmojisOnHolder(player);
    }

    public static List<Button> getSacrificeButtons() {
        Button followButton = Buttons.red("resolveSacrificeSecondary", "SACRIFICE");
        return List.of(followButton);
    }

    @ButtonHandler("resolveSacrificeSecondary")
    public static void resolveSacrificeSecondary(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        if (!isActive(game)) return;

        ButtonHelperSCs.addUsedSCPlayer(event.getMessageId(), game, player);
        player.addFollowedSC(9, event);
        ReactionService.addReaction(event, game, player, "IS PERFORMING A **SACRIFICE**.");

        List<Button> buttonsWithTilesWithShips = new LinkedList<>();
        for (Tile tile : ButtonHelper.getTilesWithShipsInTheSystem(player, game)) {
            if (ButtonHelper.checkNumberNonFighterShips(player, tile) > 0) {
                buttonsWithTilesWithShips.add(Buttons.red("rollSacrifice_" + tile.getPosition(), tile.getRepresentationForButtons(), FactionEmojis.Cabal));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getPrivateChannel(), player.getRepresentation(true, true) 
            + " choose a system to Sacrifice.", buttonsWithTilesWithShips);
    }

    @ButtonHandler("rollSacrifice_")
    public static void resolveSacrificeSecondaryPart2(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String pos = buttonID.replace("rollSacrifice_", "");
        Tile tile = game.getTileByPosition(pos);
        String ident = player.getFactionEmoji();
        int totalTGsGained = 0;
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (!(unitHolder instanceof Planet)) {
                Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
                for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                    UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                    UnitKey key = unitEntry.getKey();
                    if (unitModel == null
                        || key.getUnitType() == UnitType.Infantry
                        || key.getUnitType() == UnitType.Mech
                        || key.getUnitType() == UnitType.Fighter
                        || key.getUnitType() == UnitType.Spacedock
                        || key.getUnitType() == UnitType.Pds) {
                        continue;
                    }

                    int sacrificedUnits = 0;
                    int totalUnits = unitEntry.getValue();
                    String unitAsyncID = unitModel.getAsyncId();
                    int damagedUnits = 0;
                    if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(key) != null) {
                        damagedUnits = unitHolder.getUnitDamage().get(key);
                    }
                    for (int x = 1; x < damagedUnits + 1; x++) {
                        String msg = RiftUnitsHelper.riftUnit(unitAsyncID + "damaged", tile, game, event, player, null);
                        if (msg.contains("failed")) {
                            sacrificedUnits++;
                        } 
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "A " + ident + msg);
                    }
                    totalUnits -= damagedUnits;
                    for (int x = 1; x < totalUnits + 1; x++) {
                        String msg = RiftUnitsHelper.riftUnit(unitAsyncID, tile, game, event, player, null);
                        if (msg.contains("failed")) {
                            sacrificedUnits++;
                        } 
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "A " + ident + msg);
                    }

                    totalTGsGained += sacrificedUnits * unitModel.getCost();
                }
            }
        }
        
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "Sacrifice was performed. "
            + player.getRepresentation() + " gained " + (totalTGsGained == 0 ? "0" : MiscEmojis.tg(totalTGsGained) + " " + player.gainTG(totalTGsGained)) + " trade goods.");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveSacrifice(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!isActive(game)) return;

        Player cabal = getCabalPlayer(game);
        UnitHolder nombox = cabal.getNomboxTile().getSpaceUnitHolder();
        String sb = player.getRepresentation(true, true) + " is resolving Sacrifce.\n\n" +
            "Following units are currently captured: " + nombox.getPlayersUnitListEmojisOnHolder(player) +
            "\nAfter releasing, use Modify Units button or `/add_units` to add up to 2 of those units to systems that contains your space dock.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb);

        List<Button> buttonsToReleaseUnits = new LinkedList<>();
        for (Map.Entry<String,Integer> unit : nombox.getUnitAsyncIdsOnHolder(player.getColorID()).entrySet()) {
            UnitModel model = player.getUnitFromAsyncID(unit.getKey());
            buttonsToReleaseUnits.add(Buttons.gray("riftsetCabalRelease_" + player.getFaction() + "_" + model.getBaseType(), model.getBaseType(), model.getUnitEmoji()));
        }
        buttonsToReleaseUnits.add(Buttons.red("deleteButtons", "Done"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), 
            "Use buttons to release up to 3 of your non-fighter units from the Cabal", buttonsToReleaseUnits);
    }

    @ButtonHandler("riftsetCabalRelease")
    public static void resolveReleaseButton(Player cabal, Game game, String buttonID, ButtonInteractionEvent event) {
        ButtonHelperFactionSpecific.resolveReleaseButton(getCabalPlayer(game), game, buttonID, event);
    }

    public static boolean canPickSacrifice(Player player, Game game) {
        if (!isActive(game) || game.isCustodiansScored()) return true;

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), MiscEmojis.GravityRift.emojiString());
        return false;
    }

    public static boolean deckInfoAvailable(Player player, Game game) {
        if (!isActive(game) || player == null || Constants.eronousId.equals(player.getUserID()) || game.getPlayersWithGMRole().contains(player)) return true;

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), MiscEmojis.GravityRift.emojiString());
        return false;
    }

    //Cabal Hero works in every tile with a rift or adjacent to a rift
    public static List<Tile> getAllTilesWithRift(Game game) {
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# All " + MiscEmojis.GravityRift + " tremble violently!\n"
            + "## Ships nearby shake as a terrifying " + MiscEmojis.GravityRift + " force builds — danger is imminent.");
        return game.getTileMap().values().stream()
            .filter(tile -> tile.isGravityRift(game))
            .collect(Collectors.toList());
    }
}