package ti4.helpers.twilight_kart;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.buttons.Buttons;
import ti4.buttons.handlers.edict.EdictPhaseHandler;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperActionCards;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.TechnologyModel;
import ti4.model.UnitModel;
import ti4.service.RemoveCommandCounterService;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.regex.RegexService;
import ti4.service.tech.PlayerTechService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class TkHelperActionCards {

    public static void nop() {}

    public List<String> tkCombatCards() {
        return List.of("tk-avenge", "tk-daunt", "tk-evade", "tk-exhort");
    }

    public static boolean resolveTkActionCard(ActionCardModel card, Player player, String introMsg) {
        Game game = player.getGame();
        String resolve = "Resolve " + card.getName();
        String ffcc = player.finChecker();
        List<Button> buttons = new ArrayList<>();

        // TODO ACTION CARDS
        // tk-graft (about ~half done)
        // tk-spite
        // tk-succor

        switch (card.getAutomationID()) {
            case "tk-amalgamate" ->
                buttons.add(Buttons.green(ffcc + "drawSingularNewSpliceCard_genome", "Draw 1 Genome (Agent)"));
            case "tk-avenge" -> buttons.add(Buttons.green(ffcc + "courageousStarter", resolve));
            case "tk-bestow" -> buttons.addAll(getTkBestowButtons(player, resolve));
            case "tk-commission" -> buttons.add(Buttons.green(ffcc + "tkCommission_page0", resolve));
            case "tk-conscript" -> buttons.add(Buttons.green(ffcc + "beginTkConscript", resolve));
            case "tk-contract" -> buttons.add(Buttons.green(ffcc + "beginTkContract", resolve));
            case "tk-daunt" -> nop(); // No automation required, just ignore or force the retreat
            case "tk-evade" -> nop(); // No automation required, just ignore the hits :)
            case "tk-exhort" -> nop(); // Automated via the Combat Modifier system
            case "tk-fortify" -> {
                buttons.add(Buttons.green("construction_spacedock", "Place A Space Dock", UnitEmojis.spacedock));
                buttons.add(Buttons.green("construction_pds", "Place A PDS", UnitEmojis.pds));
                buttons.add(Buttons.DONE_DELETE_BUTTONS);
            }
            case "tk-graft" -> {
                buttons.add(Buttons.green(ffcc + "transaction_BMD", "Start Graft Transaction"));
                introMsg += "\n-# NOTE: This is currently using Black Market automation.";
                introMsg += " Resolve trading spliced cards manually.";
            }
            case "tk-incubate" -> nop(); // Button is automatically served upon being activated
            case "tk-initiate" -> buttons.addAll(getTkInitiateButtons(game, player));
            case "tk-oppress" -> buttons.addAll(PlayerTechService.getMageonImplantsButtons(game, player));
            case "tk-orchestrate" -> {
                buttons.add(Buttons.green(ffcc + "resolveSummit", "Gain 2 Command Tokens"));
                ActionCardHelper.serveManipulateInvestmentButtons(game, player);
            }
            case "tk-ordain" -> buttons.add(Buttons.green(ffcc + "startOrdain", resolve));
            case "tk-posture" -> buttons.add(Buttons.green(ffcc + "non_sc_draw_so", resolve));
            case "tk-preside" -> {
                List<String> edicts = EdictPhaseHandler.getEdictDeck(game);
                buttons.add(Buttons.green(ffcc + "resolveEdict_" + edicts.getFirst(), "Resolve 1 Edict"));
            }
            case "tk-raze" -> buttons.add(Buttons.green(ffcc + "resolveRaze_" + game.getActiveSystem(), resolve));
            case "tk-riposte" -> nop(); // Button is automatically served upon being activated
            case "tk-spite" -> {
                // TODO
                nop();
            }
            case "tk-succor" -> {
                // TODO
                nop();
            }
            case "tk-thwart" -> buttons.add(Buttons.green(ffcc + "startThwart", "Start Thwart"));
        }

        if (buttons != null && !buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), introMsg, buttons);
            return true;
        }
        return false;
    }

    private static List<Button> getTkBestowButtons(Player player, String resolve) {
        String id = player.finChecker() + "resolveTkBestow_";
        List<Button> buttons = List.of(1, 2, 3, 4).stream()
                .map(n -> Buttons.green(id + n, "Resolve " + StringHelper.ordinal(n) + " Bestow"))
                .collect(Collectors.toCollection(() -> new ArrayList<>()));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        return buttons;
    }

    @ButtonHandler("resolveTkBestow")
    private static void tkBestowStep2(ButtonInteractionEvent event, Game game, Player player) {
        String message = player.getRepresentationUnfogged() + ", use buttons to replace 1 infantry with 1 mech.";
        List<Button> buttons2 = ButtonHelperAbilities.getPlanetPlaceUnitButtonsForMechMitosis(player, game, "bestow");
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons2);
        ButtonHelper.deleteTheOneButton(event);
    }

    // Basically a copy paste of Mercenary Contract
    @ButtonHandler("tkCommission_")
    private static void beginTkCommission(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = game.getPlanetsInfo().values().stream()
                .filter(p -> !p.isHomePlanet(game)
                        && !p.hasUnits()
                        && !player.getPlanetsAllianceMode().contains(p.getName()))
                .filter(p -> game.getTileFromPlanet(p.getName()) != null)
                .filter(p -> game.getUnitHolderFromPlanet(p.getName()) != null
                        && !game.getUnitHolderFromPlanet(p.getName()).isSpaceStation()
                        && !p.getTokenList().contains("dmz")
                        && !p.getTokenList().contains("dmz_large"))
                .map(p -> {
                    String id = player.finChecker() + "resolveTkCommission_" + p.getName();
                    String label = Helper.getPlanetRepresentation(p.getName(), game);
                    for (Player p2 : game.getRealPlayers()) {
                        if (p2.hasPlanet(p.getName())) {
                            return Buttons.red(id, label, p2.getFactionEmoji());
                        }
                    }
                    return Buttons.gray(id, label);
                })
                .filter(Objects::nonNull)
                .toList();

        String prefix = player.finChecker() + "resolveTkCommission__";
        String message = player.getRepresentation() + ", please choose a planet to place 1 neutral mech on.";
        NewStuffHelper.checkAndHandlePaginationChange(
                event, player.getCorrectChannel(), buttons, message, prefix, buttonID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveTkCommission_")
    private static void resolveTeMercenaryContract(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "resolveTkCommission_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            Tile tile = game.getTileFromPlanet(planet);
            TeHelperActionCards.resolvePiratesGeneric(event, game, player, tile, "mech " + planet);
            String message = player.getRepresentation() + " 'commissioned' some mercenaries to post up at "
                    + Helper.getPlanetRepresentation(planet, game) + ".";
            if (tile != null && tile.getPosition().contains("frac")) {
                Planet uh = game.getUnitHolderFromPlanet(planet);
                if (uh != null) {
                    uh.addToken("token_relictoken.png");
                }
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("beginTkConscript")
    private static void beginTkConscript(ButtonInteractionEvent event, Game game, Player player) {
        TeHelperActionCards.beginPirates(game, player, "resolveTkConscript", 0, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveTkConscript_")
    private static void resolvePirateContract(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "resolveTkConscript_" + RegexHelper.posRegex();
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            TeHelperActionCards.resolvePiratesGeneric(event, game, player, tile, "dd, 2 ff");

            String message = player.getRepresentation() + " conscripted some pirates to post up at "
                    + tile.getRepresentationForButtons(game, player) + ".";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("beginTkContract")
    private static void beginTkContract(ButtonInteractionEvent event, Game game, Player player) {
        Predicate<Tile> hasThreeShips = tile ->
                tile.getSpaceUnitHolder().countPlayersUnitsWithModelCondition(player, UnitModel::isNonFighterShip) <= 3;
        List<Button> buttons =
                ButtonHelper.getTilesWithPredicateForAction(player, game, "resolveTkContract", hasThreeShips, false);
        String message =
                player.getRepresentationUnfogged() + " choose a system to replace your ships with neutral ships.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveTkContract_")
    private static void resolveTkContract(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos = buttonID.replace("resolveTkContract_", "");
        Tile tile = game.getTileByPosition(pos);
        UnitHolder space = tile.getSpaceUnitHolder();

        int cost = 0;
        List<String> unitStrs = new ArrayList<>();
        List<String> unitDescrs = new ArrayList<>();
        for (UnitKey key : space.getUnitKeysForPlayer(player)) {
            UnitModel model = player.getUnitByType(key.getUnitType());

            int amt = space.getUnitCount(key);
            unitStrs.add(amt + " " + key.getUnitType().getValue());
            unitDescrs.add(amt + "x " + model.getUnitEmoji() + " "
                    + model.getUnitType().humanReadableName());

            if (model.getIsShip() && !key.getUnitType().equals(UnitType.Fighter)) {
                cost += amt * model.getCost();
            }
        }

        String unitStr = String.join(", ", unitStrs);
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), unitStr);
        AddUnitService.addUnits(event, tile, game, game.getNeutralColor(), unitStr);

        String msg = player.getRepresentationUnfogged() + " contracted away some ships to be used by the \"pirates\",";
        msg += " gaining " + cost + " trade goods " + player.gainTG(cost, true) + ":";
        msg += "\n> " + String.join("\n> ", unitDescrs);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelperAgents.resolveArtunoCheck(player, cost);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveIncubate_")
    private static void resolveIncubate(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos = buttonID.replace("resolveIncubate_", "");
        Tile tile = game.getTileByPosition(pos);
        UnitHolder space = tile.getSpaceUnitHolder();

        List<Button> buttons = new ArrayList<>();
        for (UnitKey key : space.getUnitKeysForPlayer(player)) {
            UnitModel model = player.getUnitFromUnitKey(key);
            if (!model.isNonFighterShip()) continue;

            for (UnitState state : UnitState.values()) {
                if (space.getUnitCountForState(key, state) == 0) continue;
                String unitStateStr = key.getUnitType().getValue() + "_" + state.name();
                String stateStr = state.humanDescr() + (state.equals(UnitState.none) ? "" : " ");

                String id = player.finChecker() + "incubateUnit_" + pos + "_" + unitStateStr;
                String label = "Replace 1 " + stateStr + key.getUnitType().humanReadableName();
                buttons.add(Buttons.blue(id, label, model.getUnitEmoji()));
            }
        }

        String msg = player.getRepresentationUnfogged() + ", choose a unit to replace with a Dreadnought:";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("incubateUnit_")
    private static void incubateUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pattern = "incubateUnit_" + RegexHelper.posRegex() + "_" + RegexHelper.unitTypeRegex("type") + "_"
                + RegexHelper.unitStateRegex();
        RegexService.runMatcher(pattern, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            UnitType type = Units.findUnitType(matcher.group("type"));
            UnitState state = Units.findUnitState(matcher.group("state"));
            UnitHolder space = tile.getSpaceUnitHolder();

            RemoveUnitService.removeUnit(event, tile, game, player, space, type, 1, state);
            AddUnitService.addUnits(event, tile, game, player.getColor(), "dn");

            String stateMsg = state.humanDescr() + (state.equals(UnitState.none) ? "" : " ");
            String message =
                    player.getRepresentationUnfogged() + " replaced 1 " + stateMsg + type.humanReadableName() + " on ";
            message +=
                    tile.getRepresentationForButtons(game, player) + " with a Dreadnought from their reinforcements.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        });
    }

    private static List<Button> getTkInitiateButtons(Game game, Player player) {
        int cr = 0, ci = 0;
        int ir = 0, ii = 0;
        int hr = 0, hi = 0;
        for (String p : player.getExhaustedPlanets()) {
            Planet planet = game.getPlanetsInfo().get(p);
            for (String type : planet.getPlanetTypes()) {
                switch (type) {
                    case "cultural" -> {
                        cr += planet.getResources();
                        ci += planet.getInfluence();
                    }
                    case "industrial" -> {
                        ir += planet.getResources();
                        ii += planet.getInfluence();
                    }
                    case "hazardous" -> {
                        hr += planet.getResources();
                        hi += planet.getInfluence();
                    }
                }
            }
        }

        int c = ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural", false);
        int i = ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", false);
        int h = ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", false);
        int tgs = Math.max(c, Math.max(i, h));

        String idPre = player.finChecker() + "tkInitiate_";
        List<Button> buttons = new ArrayList<>();
        if (cr + ci > 0)
            buttons.add(Buttons.blue(
                    idPre + "cultural", "Ready Cultural for (" + cr + "/" + ci + ")", ExploreEmojis.Cultural));
        if (cr + ci > 0)
            buttons.add(Buttons.green(
                    idPre + "industrial", "Ready Industrial for (" + ir + "/" + ii + ")", ExploreEmojis.Industrial));
        if (cr + ci > 0)
            buttons.add(Buttons.red(
                    idPre + "hazardous", "Ready Hazardous for (" + hr + "/" + hi + ")", ExploreEmojis.Hazardous));
        buttons.add(Buttons.gray(idPre + "tgs", "Gain " + tgs + " Trade Goods", MiscEmojis.tg));
        return buttons;
    }

    @ButtonHandler("tkInitiate_")
    private static void resolveTkInitiate(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String type = buttonID.split("_")[1];

        String msg = player.getRepresentation() + " chose to ";
        switch (type) {
            case "tgs" -> {
                int c = ButtonHelper.getNumberOfXTypePlanets(player, game, "cultural", false);
                int i = ButtonHelper.getNumberOfXTypePlanets(player, game, "industrial", false);
                int h = ButtonHelper.getNumberOfXTypePlanets(player, game, "hazardous", false);
                int tgs = Math.max(c, Math.max(i, h));

                msg += "gain " + tgs + " Trade Goods. " + player.gainTG(tgs, true);
                ButtonHelperAgents.resolveArtunoCheck(player, tgs);
            }
            default -> {
                int res = 0, inf = 0;
                List<String> refreshed = new ArrayList<>();
                for (String p : List.copyOf(player.getExhaustedPlanets())) {
                    Planet planet = game.getPlanetsInfo().get(p);
                    for (String t2 : planet.getPlanetTypes()) {
                        if (t2.equals(type)) {
                            player.refreshPlanet(p);
                            refreshed.add(planet.getRepresentationWithEmojis(game));
                            res += planet.getResources();
                            inf += planet.getInfluence();
                            break;
                        }
                    }
                }
                msg += " refresh all of their " + StringUtils.capitalize(type) + " planets:\n> ";
                msg += String.join("\n> ", refreshed);
                msg += "\nFor a total of " + MiscEmojis.getResourceEmoji(res) + "/" + MiscEmojis.getInfluenceEmoji(inf);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startOrdain")
    private static void startOrdain(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                String id = player.finChecker() + "ordainReadyPlanet_" + planet;
                String label = "Ready " + Helper.getPlanetRepresentation(planet, game);
                buttons.add(Buttons.green(id, label));
            }
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "", buttons);
    }

    @ButtonHandler("ordainReadyPlanet_")
    private static void ordainReadyPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetName = buttonID.replace("ordainReadyPlanet_", "");
        player.refreshPlanet(planetName);
        Planet planet = game.getPlanetsInfo().get(planetName);

        String msg = player.getRepresentationUnfogged() + " readied planet: ";
        msg += Helper.getPlanetRepresentation(planetName, game) + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);

        List<Button> buttons = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers(true)) {
            List<String> abilities = player.getTechs();
            // If they have biosynthetic, then that is the only discardable ability
            if (p2.hasAbility("tf-biosyntheticsynergy")) abilities = List.of("tf-biosyntheticsynergy");

            int count = 0;
            for (String tech : abilities) {
                TechnologyModel model = Mapper.getTech(tech);
                if (planet.getTechSpecialities().stream().anyMatch(model::isType)) {
                    count++;
                }
            }
            if (count > 0) {
                String id = player.finChecker() + "ordainDiscardOne_" + planetName + "_" + p2.getColor();
                String label = "Discard " + p2.getColorDisplayName() + " ability (" + count + " available)";
                buttons.add(Buttons.red(id, label, p2.getFactionEmoji()));
            }
        }
        if (buttons.isEmpty()) {
            String msg2 = player.getRepresentationNoPing() + ", there are no abilities available to discard";
            msg2 += " matching those technology specialties.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        } else {
            buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("Decline"));
            String msg2 =
                    player.getRepresentationUnfogged() + ", you may choose a player to discard 1 of their abilities";
            msg2 += ", or decline to do so:";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2, buttons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("ordainDiscardOne_")
    private static void ordainDiscardOne(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player victim = game.getPlayerFromColorOrFaction(buttonID.split("_")[2]);
        Planet planet = game.getPlanetsInfo().get(buttonID.split("_")[1]);

        List<String> abilities = player.getTechs();
        // If they have biosynthetic, then that is the only discardable ability
        if (victim.hasAbility("tf-biosyntheticsynergy")) abilities = List.of("tf-biosyntheticsynergy");

        String idPre = player.finChecker() + "ordainDiscard_" + victim.getColor() + "_";
        List<Button> buttons = new ArrayList<>();
        for (String tech : abilities) {
            TechnologyModel model = Mapper.getTech(tech);
            if (planet.getTechSpecialities().stream().anyMatch(model::isType)) {
                String label = "Discard " + model.getName();
                buttons.add(Buttons.red(idPre + tech, label, model.getSingleTechEmoji()));
            }
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("Decline"));

        String msg = player.getRepresentationUnfogged() + ", use the buttons to discard 1 of ";
        msg += victim.getRepresentation() + "'s abilities. You may still decline to discard, if you so wish:";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveRaze_")
    private static void resolveRaze(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        game.setStoredValue("BlitzFaction", player.getFaction());
        player.setStoredValue("RazeFaction", "y");
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(
                    player, game, event, "combatRoll_" + buttonID.split("_")[1] + "_space_bombardment");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find active system. You will need to roll using `/roll`.");
        }
        game.removeStoredValue("BlitzFaction");
        player.removeStoredValue("RazeFaction");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveRiposte_")
    public static void resolveRiposte(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        RemoveCommandCounterService.fromTile(player.getColor(), game.getTileByPosition(pos), game);
        String message = player.getFactionEmoji() + " removed their command token from tile " + pos
                + " using _Riposte_ and gained it to their tactic pool.";
        player.setTacticalCC(player.getTacticalCC() + 1);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);

        Player active = game.getActivePlayer();
        int tact = active.getTacticalCC();
        if (tact > 0 && !"1".equals(active.getStoredValue("TactStartOfAction"))) {
            String msg2 = active.getRepresentation() + " has had 1 command token removed from their tactics pool. ";
            msg2 += "(" + tact + " ->" + (tact - 1) + ")";
            active.setTacticalCC(tact - 1);

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
            if (game.isFowMode()) {
                msg2 = active.getRepresentationUnfogged()
                        + " 1 command token has been removed from your tactics pool. ";
                msg2 += "(" + tact + " ->" + (tact - 1) + ")";
                MessageHelper.sendMessageToChannel(active.getCorrectChannel(), msg2);
            }
        } else {
            String msg2 = active.getRepresentationNoPing()
                    + " does not have any tactics tokens remaining on their command sheet, so no token was removed.";
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg2);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("startThwart")
    public static void startThwart(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = game.getActiveSystem();
        String message =
                ", please choose the system that you wish to move the ship from. Reminder that it CAN contain a command counter.";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        for (Tile tile : tiles) {
            if (tile.getPosition().equalsIgnoreCase(pos)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.finChecker() + "rescuePart2_" + pos + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        ButtonHelper.deleteMessage(event);
    }
}
