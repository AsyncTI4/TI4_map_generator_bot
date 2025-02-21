package ti4.service.fow;

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
import ti4.helpers.ButtonHelperSCs;
import ti4.helpers.Constants;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.RandomHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.StellarConverterService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.SourceEmojis;

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
 *   - Recycles itself back to the deck instantly
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
        if (game.getPlayer(Constants.eronousId) == null && AsyncTI4DiscordBot.guildFogOfWar != null) {
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
        game.setFowOption(Constants.RIFTSET_MODE, "true");
        return true;
    }

    public static boolean isActive(Game game) {
        return Boolean.valueOf(game.getFowOption(Constants.RIFTSET_MODE));
    }

    public static Player getCabalPlayer(Game game) {
        return isActive(game) ? game.getPlayerFromColorOrFaction("cabal") : null;
    }

    public static void includeCrucibleAgendaButton(List<Button> buttons, Game game) {
        if (!isActive(game)) return;

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

        if (RIFTSET_INVASION_EXPLORE.equals(exploreCardId)) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), 
                "**GM ping:** " + game.getPlayersWithGMRole().stream().map(p -> p.getPing()).collect(Collectors.joining(", ")) + " Unstable Rifts Event waiting for resolving!");
            game.addExplore(RIFTSET_INVASION_EXPLORE);
        }
    }

    public static void concludeTacticalAction(Player player, Game game, GenericInteractionCreateEvent event) {
        if (!isActive(game) || !game.isCustodiansScored()) return;

        Tile tile = game.getTileByPosition(game.getActiveSystem());
        if (tile.getTileModel().isGravityRift() || tile.hasCabalSpaceDockOrGravRiftToken() || tile.isHomeSystem()) {
            return;
        }

        if (RandomHelper.isOneInX(CHANCE_TO_SPAWN_RIFT)) {
            AddTokenCommand.addToken(event, tile, "gravityrift", game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## A new Gravity Rift has formed in " + tile.getPosition());
        } else if (RandomHelper.isOneInX(CHANCE_TO_SPAWN_VORTEX)) {
            AddTokenCommand.addToken(event, tile, "vortex", game);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## A strange Vortex has formed in " + tile.getPosition());
        }
    }

    /* Round	Probability (%)
     *  1     1.00%
     *  2     1.19%
     *  3     1.47%
     *  4     1.92%
     *  5     2.78%
     *  6     4.00% (capped)
     */
    public static boolean willPlanetGetStellarConverted(String planetName, Player player, Game game, GenericInteractionCreateEvent event) {
        if (!isActive(game) || !game.isCustodiansScored()) return false;

        if (RandomHelper.isOneInX(Math.max(CHANCE_TO_STELLAR_CONVERT - (int)(16 * Math.pow(Math.min(game.getRound(), 6) - 1, 2)), CHANCE_TO_STELLAR_CONVERT_MIN))) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), "## While trying to explore the planet, you find something dark and dangerous...");
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
          case 1 -> { msg = "Time and space begin to unravel."; }
          case 2 -> { msg = "Tíme and space bégin tto unravl..."; }
          case 3 -> { msg = "Ti.m.e an d spa-ce bgin t.o u̷nravl.."; }
          case 4 -> { msg = "T!m- ænd sp^ce b...ggn t0 üñr@vl~"; }
          case 5 -> { msg = "T#m% & spa¿c€ ßegi_n tØøø u̘͔͜ń̢͜r̶͙̜a͓͉͟v̷̪͎l..."; }
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
        Button followButton = Buttons.green("resolveSacrificeSecondary", "Follow Sacrifice");  
        Button noFollowButton = Buttons.blue("sc_no_follow_9", "Not Following");
        return List.of(followButton, noFollowButton);
    }

    @ButtonHandler("resolveSacrificeSecondary")
    public static void resolveSacrificeSecondary(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        if (!isActive(game)) return;

        boolean used = ButtonHelperSCs.addUsedSCPlayer(event.getMessageId(), game, player);
        if (used) {
            return;
        }

        player.addFollowedSC(9, event);
        ReactionService.addReaction(event, game, player, "is following **Sacrifice**.");

        StringBuffer sb = new StringBuffer(player.getRepresentation(true, true));
        sb.append(", to resolve Sacrifce Secondary:\n");
        sb.append(" 1. Choose a system that contains your non-fighter ships. Use `/special system_info` for that system.\n");
        sb.append(" 2. Use `/roll roll_command:Xd10` replacing X with the number of your non-fighter ships.\n");
        sb.append(" 3. For each result of 1-3, order of the die matches the order of your ships in system_info list to be captured.\n");
        sb.append(" 4. Use `/capture add_units unit_names:X target_faction_or_color:");
        sb.append(player.getColor()).append(" faction_or_color:").append(getCabalPlayer(game).getColor()).append("`");
        sb.append(" to have Cabal capture those units.\n");
        sb.append(" 5. Use `/player stats trade_goods:+X` to give yourself Trade Goods equal to the combined value of what was captured.");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
    }

    public static void resolveSacrifice(GenericInteractionCreateEvent event, Game game, Player player) {
        if (!isActive(game)) return;

        String capturedUnits = getCapturedUnitsAsEmojis(game, player);
        StringBuffer sb = new StringBuffer(player.getRepresentation(true, true));
        sb.append(", to resolve Sacrifce Primary:\n");
        sb.append("Following units are currently captured: ").append(capturedUnits.isEmpty() ? "None" : capturedUnits);
        sb.append("\n 1. Use `/capture remove_units unit_names:X target_faction_or_color:");
        sb.append(player.getColor()).append(" faction_or_color:").append(getCabalPlayer(game).getColor()).append("`");
        sb.append(" to release up to 3 of your units from the Cabal.\n");
        sb.append(" 2. Use Modify Units button or `/add_units` to add up to 2 of those units to systems that contains your space dock.");
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
    }

    public static boolean canPickSacrifice(Player player, Game game) {
        if (!isActive(game) || game.isCustodiansScored()) return true;

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), MiscEmojis.GravityRift.emojiString());
        return false;
    }

    public static boolean deckInfoAvailable(Player player, Game game) {
        if (!isActive(game) || Constants.eronousId.equals(player.getUserID()) || game.getPlayersWithGMRole().contains(player)) return true;

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), MiscEmojis.GravityRift.emojiString());
        return false;
    }
}