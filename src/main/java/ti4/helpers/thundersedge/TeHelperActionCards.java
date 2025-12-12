package ti4.helpers.thundersedge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.commands.special.SetupNeutralPlayer;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperHeroes;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.SecretObjectiveHelper;
import ti4.helpers.Units.UnitType;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.ColorModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.BlindSelectionService;
import ti4.service.planet.FlipTileService;
import ti4.service.regex.RegexService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

public class TeHelperActionCards {

    public static void nop() {}

    public static boolean resolveTeActionCard(ActionCardModel card, Player player, String introMsg) {
        String resolve = "Resolve " + card.getName();
        String ffcc = player.getFinsFactionCheckerPrefix();
        List<Button> buttons = new ArrayList<>();

        switch (card.getAlias().replaceAll("\\d", "")) {
            case "blackmarketdealing" ->
                buttons.add(Buttons.green(ffcc + "transaction_BMD", "Start Black Market Transaction"));
            case "brilliance" -> buttons.add(Buttons.green(ffcc + "brilliance", resolve));
            case "crashlanding" -> buttons.add(Buttons.green(ffcc + "crashLandingStart", "Start Crash Landing"));
            case "crisis" -> nop(); // preset
            case "exchangeprogram" ->
                buttons.add(Buttons.green(ffcc + "exchangeProgramStart", "Start Exchange Program"));
            case "extremeduress" -> nop(); // preset
            case "lieinwait" -> buttons.add(Buttons.green(ffcc + "lieInWait", resolve));
            case "mercenarycontract" -> buttons.add(Buttons.green(ffcc + "teMercenaryContract_page0", resolve));
            case "piratecontract" -> buttons.add(Buttons.green(ffcc + "pirateContract", resolve));
            case "piratefleet" -> buttons.add(Buttons.green(ffcc + "pirateFleet", resolve));
            case "puppetsonastring" -> nop(); // preset
            case "rescue" -> buttons.add(Buttons.green(ffcc + "startRescue", "Start Rescue"));
            case "overrule" -> buttons.add(Buttons.green(ffcc + "overruleStart", "Start Overrule"));
            case "strategize" -> buttons.add(Buttons.green(ffcc + "strategize", resolve));
        }

        if (buttons != null && !buttons.isEmpty()) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), introMsg, buttons);
            return true;
        }

        return false;
    }

    public static List<Button> getOverruleButtons(Game game) {
        List<Button> scButtons = new ArrayList<>();
        for (Integer sc : game.getSCList()) {
            if (sc <= 0
                    || (game.getScPlayed().get(sc) != null && game.getScPlayed().get(sc))) continue;
            Button button;
            String label = Helper.getSCName(sc, game);
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(sc);
            if (scEmoji != CardEmojis.SCBackBlank && !game.isHomebrewSCMode()) {
                button = Buttons.gray("winnuHero_" + sc + "_overrule", label, scEmoji);
            } else {
                button = Buttons.gray("winnuHero_" + sc + "_overrule", sc + " " + label);
            }
            scButtons.add(button);
        }
        return scButtons;
    }

    @ButtonHandler("exchangeProgramStart")
    private static void exchangeProgramStart(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            buttons.add(Buttons.gray("exchangeProgramPart2_" + p2.getFaction(), p2.getFactionNameOrColor()));
        }
        String message = "Choose the player who you are trying to have an exchange with.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("concedeToED")
    private static void concedeToED(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String faction = buttonID.split("_")[1];
        Player p2 = game.getPlayerFromColorOrFaction(faction);
        ActionCardHelper.discardRandomAC(event, game, player, player.getAcCount());
        if (player.getTg() > 0) {
            p2.gainTG(player.getTg(), true);
            player.setTg(0);
        }
        if (!player.getSecretsUnscored().isEmpty()) {
            SecretObjectiveHelper.showAll(player, p2, game);
        }
        String message = player.getRepresentation()
                + " lost all their ACs, gave all their tgs to the player who played extreme duress, and showed their secrets to them as well.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("lieInWait")
    private static void lieInWait(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        for (Player p2 : player.getNeighbouringPlayers(true)) {
            if (p2 == player || p2.getAcCount() == 0) {
                continue;
            }
            if (game.isFowMode()) {
                buttons.add(Buttons.gray(
                        player.getFinsFactionCheckerPrefix() + "getACFrom_" + p2.getFaction(), p2.getColor()));
            } else {
                Button button =
                        Buttons.gray(player.getFinsFactionCheckerPrefix() + "getACFrom_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
        }
        String message = player.getRepresentationUnfogged()
                + ", please tell the bot which neighbor of your's did the transaction";
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("crashLandingStart")
    private static void crashLandingStart(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        for (Planet planet : tile.getPlanetUnitHolders()) {
            if (planet.getName().contains("mr")) {
                continue;
            }
            if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Mech, player) > 0) {
                buttons.add(Buttons.blue(
                        "crashLandOn_" + planet.getName() + "_mech",
                        "Crash Mech on " + Helper.getPlanetRepresentation(planet.getName(), game),
                        UnitEmojis.mech));
            }
            if (tile.getSpaceUnitHolder().getUnitCount(UnitType.Infantry, player) > 0) {
                buttons.add(Buttons.green(
                        "crashLandOn_" + planet.getName() + "_infantry",
                        "Crash Infantry on " + Helper.getPlanetRepresentation(planet.getName(), game),
                        UnitEmojis.infantry));
            }
        }
        String message = "Choose the unit and planet you wish to crash land on.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("crashLandOn")
    private static void crashLandOn(Game game, Player player, ButtonInteractionEvent event, String buttonID) {

        String planet = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        UnitType type = UnitType.Mech;
        if (unit.contains("inf")) {
            type = UnitType.Infantry;
        }
        game.setStoredValue("coexistFlag", "yes");
        RemoveUnitService.removeUnit(
                event,
                game.getTileFromPlanet(planet),
                game,
                player,
                game.getTileFromPlanet(planet).getSpaceUnitHolder(),
                type,
                1);
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), unit + " " + planet);
        game.removeStoredValue("coexistFlag");

        String message = player.getRepresentation() + " crashed a " + unit + " onto the planet of "
                + Helper.getPlanetRepresentation(planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
        ButtonHelperAbilities.oceanBoundCheck(game);
    }

    @ButtonHandler("exchangeProgramPart2")
    private static void exchangeProgramPart2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {

        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        List<Button> buttons = new ArrayList<>();
        for (String planet : p2.getPlanets()) {
            if (game.getUnitHolderFromPlanet(planet) != null
                    && game.getUnitHolderFromPlanet(planet).hasGroundForces(p2)) {
                buttons.add(Buttons.gray(
                        player.getFinsFactionCheckerPrefix() + "exchangeProgramPart3_" + planet,
                        Helper.getPlanetRepresentation(planet, game)));
            }
        }
        buttons.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "loseAFleetCultural", "Lose a fleet token"));
        String message =
                player.getRepresentation() + " After reaching agreement, choose the planet that you will coexist on.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);

        buttons = new ArrayList<>();
        for (String planet : player.getPlanets()) {
            if (game.getUnitHolderFromPlanet(planet) != null
                    && game.getUnitHolderFromPlanet(planet).hasGroundForces(player)) {
                buttons.add(Buttons.gray(
                        p2.getFinsFactionCheckerPrefix() + "exchangeProgramPart3_" + planet,
                        Helper.getPlanetRepresentation(planet, game)));
            }
        }
        buttons.add(Buttons.red(p2.getFinsFactionCheckerPrefix() + "loseAFleetCultural", "Lose a fleet token"));
        message = p2.getRepresentation() + " After reaching agreement, choose the planet that you will coexist on.";
        MessageHelper.sendMessageToChannelWithButtons(p2.getCorrectChannel(), message, buttons);

        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("exchangeProgramPart3")
    private static void exchangeProgramPart3(Game game, Player player, ButtonInteractionEvent event, String buttonID) {

        String planet = buttonID.split("_")[1];
        game.setStoredValue("coexistFlag", "yes");
        AddUnitService.addUnits(event, game.getTileFromPlanet(planet), game, player.getColor(), "inf " + planet);
        game.removeStoredValue("coexistFlag");

        String message = player.getRepresentation() + " placed an infantry into coexistence on the planet of "
                + Helper.getPlanetRepresentation(planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.deleteMessage(event);
        ButtonHelperAbilities.oceanBoundCheck(game);
    }

    @ButtonHandler("loseAFleetCultural")
    private static void loseAFleetCultural(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        player.setFleetCC(player.getFleetCC() - 1);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getRepresentation()
                        + " has removed a command token from their fleet pool due to failing to reach an agreement on the cultural exchange.");
        ButtonHelper.checkFleetInEveryTile(player, game);

        event.getMessage().delete().queue();
    }

    @ButtonHandler("overruleStart")
    private static void resolveOverrule(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = getOverruleButtons(game);
        String message = "Choose the SC you wish to do the primary of.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("strategize")
    private static void resolveStrategize(Game game, Player player, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();

        if (game.getScPlayed().get(1) == null || !game.getScPlayed().get(1)) {
            buttons.add(Buttons.green("leadershipGenerateCCButtons", "Spend & Gain CCs", CardEmojis.SC1));
        }
        if (game.getScPlayed().get(2) == null || !game.getScPlayed().get(2)) {
            buttons.add(Buttons.green("diploRefresh2", "Ready 2 Planets", CardEmojis.SC2));
        }
        if (game.getScPlayed().get(3) == null || !game.getScPlayed().get(3)) {
            buttons.add(Buttons.gray("draw2 AC", "Draw 2 Action Cards", CardEmojis.ActionCard));
        }
        if (game.getScPlayed().get(4) == null || !game.getScPlayed().get(4)) {
            buttons.add(Buttons.green("construction_spacedock", "Place A SD", UnitEmojis.spacedock));
            buttons.add(Buttons.green("construction_pds", "Place a PDS", UnitEmojis.pds));
        }
        if (game.getScPlayed().get(5) == null || !game.getScPlayed().get(5)) {
            buttons.add(Buttons.gray("sc_refresh", "Replenish Commodities", MiscEmojis.comm));
        }
        if (game.getScPlayed().get(6) == null || !game.getScPlayed().get(6)) {
            buttons.add(Buttons.green("warfareBuild", "Build At Home", CardEmojis.SC6));
        }
        if (game.getScPlayed().get(7) == null || !game.getScPlayed().get(7)) {
            buttons.add(Buttons.GET_A_TECH.withEmoji(CardEmojis.SC7.asEmoji()));
        }
        if (game.getScPlayed().get(8) == null || !game.getScPlayed().get(8)) {
            buttons.add(Buttons.gray("non_sc_draw_so", "Draw Secret Objective", CardEmojis.SecretObjective));
        }
        buttons.add(Buttons.red("deleteButtons", "Done resolving"));

        String message = "Resolve strategize using the buttons. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        buttons = new ArrayList<>();
        buttons.add(Buttons.gray("spendAStratCC", "Spend a Strategy CC"));
        buttons.add(Buttons.red("deleteButtons", "Done resolving"));
        message = "## Unless you are resolving leadership, please spend a strategy command token. ";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("teMercenaryContract_")
    private static void beginTeMercenaryContract(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
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
                    String id = player.finChecker() + "resolveTeMercenaryContract_" + p.getName();
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

        String prefix = player.finChecker() + "teMercenaryContract_";
        String message = player.getRepresentation() + " choose a planet to place 2 neutral infantry on:";
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        if (neutral == null) {
            List<String> unusedColors =
                    game.getUnusedColors().stream().map(ColorModel::getName).toList();
            String color = new SetupNeutralPlayer().pickNeutralColor(unusedColors);
            game.setupNeutralPlayer(color);
            neutral = game.getPlayerFromColorOrFaction("neutral");
        }
        NewStuffHelper.checkAndHandlePaginationChange(
                event, player.getCorrectChannel(), buttons, message, prefix, buttonID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("resolveTeMercenaryContract_")
    private static void resolveTeMercenaryContract(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "resolveTeMercenaryContract_" + RegexHelper.unitHolderRegex(game, "planet");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String planet = matcher.group("planet");
            Tile tile = game.getTileFromPlanet(planet);
            resolvePiratesGeneric(event, game, player, tile, "2 inf " + planet);
            player.setTg(player.getTg() - 2);
            String message = player.getRepresentation() + " paid some mercenaries 2tg to post up at "
                    + Helper.getPlanetRepresentation(planet, game);
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

    @ButtonHandler("startRescue")
    public static void startRescue(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = game.getActiveSystem();
        String message =
                ", please choose the system that you wish to move the ship from. Reminder that it can't contain a command counter.";
        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = ButtonHelper.getTilesWithShipsInTheSystem(player, game);
        for (Tile tile : tiles) {
            if (tile.getPosition().equalsIgnoreCase(pos) || CommandCounterHelper.hasCC(player, tile)) {
                continue;
            }
            buttons.add(Buttons.gray(
                    player.getFinsFactionCheckerPrefix() + "rescuePart2_" + pos + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons()));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("rescuePart2_")
    public static void rescuePart2(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(pos);
        String pos2 = buttonID.split("_")[1];
        Tile tile2 = game.getTileByPosition(pos2);
        String message = ", please choose the one ship you wish to move from " + tile.getRepresentation() + " to "
                + tile2.getRepresentation() + " (along with any units it transports).";
        List<Button> buttons = ButtonHelperHeroes.getArgentHeroStep3Buttons(game, player, "spoof_" + pos2 + "_" + pos);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), player.getRepresentationUnfogged() + message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pirateContract")
    private static void beginPirateContract(Game game, Player player, ButtonInteractionEvent event) {
        beginPirates(game, player, "resolvePirateContract", 0, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("pirateFleet")
    private static void beginPirateFleet(Game game, Player player, ButtonInteractionEvent event) {
        beginPirates(game, player, "resolvePirateFleet", 3, true);
        ButtonHelper.deleteMessage(event);
    }

    public static void beginPirates(Game game, Player player, String prefix, int cost, boolean allowRes) {
        String message = player.getRepresentation() + " choose a system to place the pirates! 🦜☠";
        if (allowRes) {
            List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
            Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
            buttons.add(DoneExhausting);
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(), "Use buttons to pay the kind pirates", buttons);
        } else {
            if (cost == 0) {
                message += "\n-# This one is free :)";
            } else if (player.getTg() < cost) {
                message += "\n-# Could not automatically deduct TGs, please resolve that manually or undo.";
            } else {
                message += "\n-# Automatically deducted " + cost + " trade good" + (cost > 1 ? "s " : " ");
                message += "(" + player.getTg() + "->" + (player.getTg() - cost) + ")";
                player.setTg(player.getTg() - cost);
            }
        }

        Predicate<Tile> emptyTile =
                Tile.tileHasNoPlayerShips(game).and(tile -> !tile.getTileModel().isHyperlane());
        List<Button> buttons = ButtonHelper.getTilesWithPredicateForAction(player, game, prefix, emptyTile, false);
        BlindSelectionService.filterForBlindPositionSelection(game, player, buttons, player.finChecker() + prefix);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("resolvePirateContract_")
    private static void resolvePirateContract(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "resolvePirateContract_" + RegexHelper.posRegex();
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        if (neutral == null) {
            List<String> unusedColors =
                    game.getUnusedColors().stream().map(ColorModel::getName).toList();
            String color = new SetupNeutralPlayer().pickNeutralColor(unusedColors);
            game.setupNeutralPlayer(color);
            neutral = game.getPlayerFromColorOrFaction("neutral");
        }
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            resolvePiratesGeneric(event, game, player, tile, "dd");

            String message = player.getRepresentation() + " paid a pirate to post up at "
                    + tile.getRepresentationForButtons(game, player);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("resolveNokarBt_")
    private static void resolveNokarBt(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "resolveNokarBt_" + RegexHelper.posRegex();
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        if (neutral == null) {
            List<String> unusedColors =
                    game.getUnusedColors().stream().map(ColorModel::getName).toList();
            String color = new SetupNeutralPlayer().pickNeutralColor(unusedColors);
            game.setupNeutralPlayer(color);
            neutral = game.getPlayerFromColorOrFaction("neutral");
        }
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            resolvePiratesGeneric(event, game, player, tile, "2 dd, cr");

            String message = player.getRepresentation() + " hired 2 neutral destroyers and a cruiser to post up at "
                    + tile.getRepresentationForButtons(game, player);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("resolvePirateFleet_")
    private static void resolvePirateFleet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "resolvePirateFleet_" + RegexHelper.posRegex();
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        if (neutral == null) {
            List<String> unusedColors =
                    game.getUnusedColors().stream().map(ColorModel::getName).toList();
            String color = new SetupNeutralPlayer().pickNeutralColor(unusedColors);
            game.setupNeutralPlayer(color);
            neutral = game.getPlayerFromColorOrFaction("neutral");
        }
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Tile tile = game.getTileByPosition(matcher.group("pos"));
            resolvePiratesGeneric(event, game, player, tile, "cv, ca, dd, 2 ff");

            String message = player.getRepresentation() + " paid a fleet of pirates to post up at "
                    + tile.getRepresentationForButtons(game, player);
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        });
    }

    private static void resolvePiratesGeneric(
            ButtonInteractionEvent event, Game game, Player player, Tile tile, String units) {
        Player neutral = game.getPlayerFromColorOrFaction("neutral");
        tile = FlipTileService.flipTileIfNeeded(event, tile, game);
        AddUnitService.addUnits(event, tile, game, neutral.getColorID(), units);
    }

    @ButtonHandler("brilliance")
    private static void beginBrilliance(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String planet : player.getExhaustedPlanets()) {
            if (ButtonHelper.checkForTechSkips(game, planet)) {
                String id = "biostimsReady_planet_" + planet;
                String label = "Ready " + Helper.getPlanetRepresentation(planet, game);
                buttons.add(Buttons.green(id, label));
            }
        }
        for (Player p : game.getRealPlayers()) {
            int lockedCount = 0;
            for (String btID : p.getBreakthroughIDs()) {
                if (!p.isBreakthroughUnlocked(btID)) {
                    lockedCount++;
                }
            }

            if (lockedCount > 0) {
                String id = "resolveBrillianceUnlock_" + p.getFaction();
                String label = "Unlock " + p.getBreakthroughModel().getName();
                if (lockedCount > 1) {
                    label = "Unlock " + p.getFactionModel().getShortName() + "'s Breakthroughs";
                }
                buttons.add(Buttons.gray(id, label, p.getFactionEmoji()));
            }
        }

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), "", buttons);
    }

    @ButtonHandler("resolveBrillianceUnlock_")
    private static void resolveBrillianceUnlockBreakthrough(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "resolveBrillianceUnlock_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Player p2 = game.getPlayerFromColorOrFaction(matcher.group("faction"));
            BreakthroughCommandHelper.unlockAllBreakthroughs(game, p2);
            ButtonHelper.deleteMessage(event);
        });
    }
}
