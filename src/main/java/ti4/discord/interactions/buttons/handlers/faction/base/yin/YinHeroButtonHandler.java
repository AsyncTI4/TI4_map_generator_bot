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
        if (game.isFowMode()) {
            // Both branches below disclose holdings: picking a player then listing their planets, and the
            // "unowned" branch walking the whole map (which also reveals, by omission, which planets ARE
            // owned). In fog we offer exactly the planets this player knows exist, whoever holds them.
            List<Button> fogButtons = PlanetTargetService.targetButtons(
                    game,
                    player,
                    PlanetTargetSpec.of(player.factionButtonChecker() + "yinHeroPlanet")
                            .where(p -> !p.isSpaceStation(game)
                                    && game.getTileFromPlanet(p.getName()) != null
                                    && !game.getTileFromPlanet(p.getName()).isHomeSystem(game)),
                    new ArrayList<>());
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(), "Please choose which planet to invade.", fogButtons);
            return;
        }
        List<Button> buttons = AgendaRiderHelper.getPlayerOutcomeButtons(game, null, "yinHeroTarget", null);
        if (game.getTileByPosition("tl") != null
                && "82a".equalsIgnoreCase(game.getTileByPosition("tl").getTileID())) {
            buttons.add(Buttons.green("yinHeroPlanet_lockedmallice", "Invade Mallice"));
        }
        buttons.add(Buttons.green("yinHeroTarget_unowned", "Take Unowned Planet"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(), "Please choose the player that owns the planet you wish to land on.", buttons);
    }

    // Non-fog only - see yinHeroStart, which skips this step entirely in fog.
    @ButtonHandler("yinHeroTarget_")
    public static void yinHeroTarget(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String faction = buttonID.replace("yinHeroTarget_", "");
        List<Button> buttons = new ArrayList<>();
        Player target = game.getPlayerFromColorOrFaction(faction);
        if (target != null && !"unowned".equalsIgnoreCase(faction)) {
            for (String planet : target.getPlanets()) {
                if (game.getTileFromPlanet(planet) == null
                        || game.getTileFromPlanet(planet).isHomeSystem(game)
                        || Helper.getPlanetRepresentation(planet, game)
                                .toLowerCase()
                                .contains("dmz")) {
                    continue;
                }
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "yinHeroPlanet_" + planet,
                        Helper.getPlanetRepresentation(planet, game)));
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    event.getChannel(), "Please choose which planet to invade.", buttons);
            ButtonHelper.deleteMessage(event);
        } else {
            for (Tile tile : game.getTileMap().values()) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (unitHolder instanceof Planet planet) {
                        if (planet.isSpaceStation(game)) {
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
                                    player.factionButtonChecker() + "yinHeroPlanet_" + planet.getName(),
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
        String planet = buttonID.replace("yinHeroPlanet_", "");
        if ("lockedmallice".equalsIgnoreCase(planet)) {
            planet = "mallice";
            FlipTileService.flipTileIfNeeded(event, game.getTileFromPlanet("lockedmallice"), game);
        } else if ("hexlockedmallice".equalsIgnoreCase(planet)) {
            planet = "hexmallice";
            FlipTileService.flipTileIfNeeded(event, game.getTileFromPlanet("hexlockedmallice"), game);
        }
        Tile targetTile = game.getTileFromPlanet(planet);
        Planet targetPlanet = game.getUnitHolderFromPlanet(planet);
        boolean mallice = planet.toLowerCase().contains("mallice");
        // Existence is checked in both modes - it only replaces a crash.
        if (targetTile == null || targetPlanet == null) {
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
                    player.factionButtonChecker() + "yinHeroInfantry_" + planet + "_" + x,
                    "Land " + x + " infantry",
                    UnitEmojis.infantry));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Please choose how many infantry you wish to land on the planet.", buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("yinHeroInfantry_")
    public static void lastStepOfYinHero(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String planetNInf = buttonID.replace("yinHeroInfantry_", "");
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
