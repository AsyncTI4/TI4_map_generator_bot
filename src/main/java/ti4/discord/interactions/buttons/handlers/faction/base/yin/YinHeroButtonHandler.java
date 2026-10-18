package ti4.discord.interactions.buttons.handlers.faction.base.yin;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.UnitEmojis;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.planet.FlipTileService;
import ti4.service.unit.AddUnitService;

@UtilityClass
class YinHeroButtonHandler {

    @ButtonHandler("yinHeroStart")
    public static void yinHeroStart(ButtonInteractionEvent event, Game game, Player player) {
        yinHeroStart(event, game, player, false);
    }

    @ButtonHandler("yinBrHeroStart")
    public static void yinBrHeroStart(ButtonInteractionEvent event, Game game, Player player) {
        yinHeroStart(event, game, player, true);
    }

    private static void yinHeroStart(ButtonInteractionEvent event, Game game, Player player, boolean blueReverie) {
        String planetPrefix = player.factionButtonChecker() + (blueReverie ? "yinBrHeroPlanet" : "yinHeroPlanet");
        if (game.isFowMode()) {
            // Both branches below disclose holdings: picking a player then listing their planets, and the
            // "unowned" branch walking the whole map (which also reveals, by omission, which planets ARE
            // owned). In fog we offer exactly the planets this player knows exist, whoever holds them.
            List<Button> fogButtons = PlanetTargetService.targetButtons(
                    game,
                    player,
                    PlanetTargetSpec.of(planetPrefix)
                            .where(p -> !p.isSpaceStation(game)
                                    && game.getTileFromPlanet(p.getName()) != null
                                    && !game.getTileFromPlanet(p.getName()).isHomeSystem(game)
                                    && (!blueReverie || !p.isLegendary()))
                            .withPageNavPrefix(planetPrefix + "_"),
                    new ArrayList<>());
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(), "Please choose which planet to invade.", fogButtons);
            return;
        }
        List<Button> buttons = AgendaRiderHelper.getPlayerOutcomeButtons(game, null, "yinHeroTarget", null);
        if (blueReverie) {
            buttons = AgendaRiderHelper.getPlayerOutcomeButtons(game, null, "yinBrHeroTarget", null);
        }
        if (!blueReverie
                && game.getTileByPosition("tl") != null
                && "82a".equalsIgnoreCase(game.getTileByPosition("tl").getTileID())) {
            buttons.add(Buttons.green(planetPrefix + "_lockedmallice", "Invade Mallice"));
        }
        buttons.add(
                Buttons.green((blueReverie ? "yinBrHeroTarget" : "yinHeroTarget") + "_unowned", "Take Unowned Planet"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), "Please choose the player that owns the planet you wish to land on.", buttons);
    }

    // Non-fog only - see yinHeroStart, which skips this step entirely in fog.
    @ButtonHandler("yinHeroTarget_")
    public static void yinHeroTarget(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        yinHeroTarget(event, buttonID, game, player, false);
    }

    @ButtonHandler("yinBrHeroTarget_")
    public static void yinBrHeroTarget(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        yinHeroTarget(event, buttonID, game, player, true);
    }

    private static void yinHeroTarget(
            ButtonInteractionEvent event, String buttonID, Game game, Player player, boolean blueReverie) {
        String targetPrefix = blueReverie ? "yinBrHeroTarget_" : "yinHeroTarget_";
        String planetPrefix = player.factionButtonChecker() + (blueReverie ? "yinBrHeroPlanet_" : "yinHeroPlanet_");
        String faction = buttonID.replace(targetPrefix, "");
        List<Button> buttons = new ArrayList<>();
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target != null && !"unowned".equalsIgnoreCase(faction)) {
            for (String planet : target.getPlanets()) {
                if (game.getTileFromPlanet(planet) == null
                        || game.getTileFromPlanet(planet).isHomeSystem(game)
                        || (blueReverie && game.getUnitHolderFromPlanet(planet).isLegendary())
                        || Helper.getPlanetRepresentation(planet, game)
                                .toLowerCase()
                                .contains("dmz")) {
                    continue;
                }
                buttons.add(Buttons.green(planetPrefix + planet, Helper.getPlanetRepresentation(planet, game)));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(), "Please choose which planet to invade.", buttons);
            ButtonHelper.deleteMessage(event);
        } else {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder instanceof Planet planet) {
                        if (planet.isSpaceStation(game) || (blueReverie && planet.isLegendary())) {
                            continue;
                        }
                        boolean owned = false;
                        for (Player p2 : game.getRealPlayersNNeutral()) {
                            if (p2.getPlanets().contains(planet.getName())) {
                                owned = true;
                                break;
                            }
                        }
                        if (!owned) {
                            buttons.add(Buttons.green(
                                    planetPrefix + planet.getName(),
                                    Helper.getPlanetRepresentation(planet.getName(), game)));
                        }
                    }
                }
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(), "Please choose which planet to invade.", buttons);
        }
    }

    @ButtonHandler("yinHeroPlanet_")
    public static void yinHeroPlanet(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        yinHeroPlanet(event, buttonID, game, player, false);
    }

    @ButtonHandler("yinBrHeroPlanet_")
    public static void yinBrHeroPlanet(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        yinHeroPlanet(event, buttonID, game, player, true);
    }

    private static void yinHeroPlanet(
            ButtonInteractionEvent event, String buttonID, Game game, Player player, boolean blueReverie) {
        String planetButtonPrefix = blueReverie ? "yinBrHeroPlanet" : "yinHeroPlanet";
        String planetPrefix = player.factionButtonChecker() + planetButtonPrefix;
        var spec = PlanetTargetSpec.of(planetPrefix)
                .where(p -> !p.isSpaceStation(game)
                        && game.getTileFromPlanet(p.getName()) != null
                        && !game.getTileFromPlanet(p.getName()).isHomeSystem(game)
                        && (!blueReverie || !p.isLegendary()))
                .withPageNavPrefix(planetPrefix + "_");
        if (PlanetTargetService.handlePlanetPage(event, game, player, buttonID, spec)) return;
        String planet =
                FlipTileService.unlockMalliceIfNeeded(event, buttonID.replace(planetButtonPrefix + "_", ""), game);
        Tile targetTile = game.getTileFromPlanet(planet);
        Planet targetPlanet = game.getUnitHolderFromPlanet(planet);
        boolean mallice = planet.toLowerCase().contains("mallice");
        // Existence is checked in both modes - it only replaces a crash.
        if (targetTile == null || targetPlanet == null || (blueReverie && targetPlanet.isLegendary())) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        // The rules checks apply in fog only. A blind-typed planet never passed the fog spec's filters, so
        // they have to be re-applied here - but outside fog the legacy builder is authoritative, and it
        // deliberately offers a *controlled* space station (only its "unowned planets" branch excludes them).
        // Applying the fog rules unconditionally would silently remove that non-fog capability.
        // Mallice is exempt either way: it is reached by its own dedicated button.
        if (game.isFowMode()
                && !mallice
                && (targetTile.isHomeSystem(game)
                        || targetPlanet.isSpaceStation(game)
                        || targetPlanet.getTokenList().stream().anyMatch(token -> token.contains("dmz")))) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        // The actor's own channel, not wherever the button happened to be pressed - this names a planet and
        // the player invading it, which in fog is not for whoever else can read that channel.
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " is invading " + Helper.getPlanetRepresentation(planet, game)
                        + ".");
        List<Button> buttons = new ArrayList<>();
        for (int x = 1; x < 4; x++) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + (blueReverie ? "yinBrHeroInfantry_" : "yinHeroInfantry_") + planet
                            + "_" + x,
                    "Land " + x + " infantry",
                    UnitEmojis.infantry));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Please choose how many infantry you wish to land on the planet.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("yinHeroInfantry_")
    public static void lastStepOfYinHero(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        lastStepOfYinHero(buttonID, event, game, player, false);
    }

    @ButtonHandler("yinBrHeroInfantry_")
    public static void lastStepOfYinBrHero(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        lastStepOfYinHero(buttonID, event, game, player, true);
    }

    private static void lastStepOfYinHero(
            String buttonID, ButtonInteractionEvent event, Game game, Player player, boolean blueReverie) {
        String planetNInf = buttonID.replace(blueReverie ? "yinBrHeroInfantry_" : "yinHeroInfantry_", "");
        String planet = planetNInf.split("_")[0];
        String amount = planetNInf.split("_")[1];
        Tile tile = game.getTileFromPlanet(planet);
        if (tile == null) {
            tile = game.getTile(AliasHandler.resolveTile(planet));
        }
        if (tile == null || tile.getUnitHolders().get(planet) == null) {
            PlanetTargetService.fizzle(event, player);
            return;
        }
        Player defender = game.getPlayerThatControlsPlanet(planet, true);
        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " inf " + planet);
        String landed = player.getFactionEmojiOrColor() + " Chose to land " + amount + " infantry on "
                + Helper.getPlanetRepresentation(planet, game);
        if (defender != null && defender != player) {
            // The defender is entitled to know their planet was landed on; nobody else is.
            FoWHelper.notifyActorAndAffectedElsePublic(
                    game,
                    player,
                    landed,
                    defender,
                    defender.getRepresentationUnfogged() + ", infantry landed on your planet "
                            + Helper.getPlanetRepresentation(planet, game) + ".",
                    landed);
        } else {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), landed);
        }
        UnitHolder unitHolder = tile.getUnitHolders().get(planet);
        boolean groundCombatStarted = StartCombatService.groundCombatCheck(game, unitHolder, tile, event);
        if (groundCombatStarted) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + ", reminder that Dannel of the Tenth, the Yin hero, skips the space cannon defense step.");
        }
        ButtonHelper.deleteMessage(event);
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event, "Yin hero landing on " + Helper.getPlanetRepresentation(planet, game));
        }
    }
}
