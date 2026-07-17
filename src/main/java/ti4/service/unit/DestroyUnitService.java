package ti4.service.unit;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.ResourceHelper;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.DreamButtonHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ashen.AshenUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumPromissoryHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.crystellum.CrystellumUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.xan.XanUnitHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.zephyrion.ZephyrionBountyHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperActionCards;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.StringHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.BreakthroughCommandHelper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.FactionEmojis;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class DestroyUnitService {

    public static void destroyAllUnitsInSystem(
            GenericInteractionCreateEvent event, Tile tile, Game game, boolean combat) {
        List<RemovedUnit> units = new ArrayList<>();
        for (UnitHolder uh : tile.getUnitHolders().values())
            units.addAll(RemoveUnitService.removeAllUnits(event, tile, game, uh));
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyAllUnits(
            GenericInteractionCreateEvent event, Tile tile, Game game, UnitHolder unitHolder, boolean combat) {
        List<RemovedUnit> units = RemoveUnitService.removeAllUnits(event, tile, game, unitHolder);
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyAllPlayerUnitsInSystem(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, boolean combat) {
        List<RemovedUnit> units = new ArrayList<>();
        for (UnitHolder uh : tile.getUnitHolders().values())
            units.addAll(RemoveUnitService.removeAllPlayerUnits(event, game, player, tile, uh));
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyAllPlayerUnits(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder unitHolder,
            boolean combat) {
        List<RemovedUnit> units = RemoveUnitService.removeAllPlayerUnits(event, game, player, tile, unitHolder);
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyAllPlayerNonStructureUnits(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            Tile tile,
            UnitHolder unitHolder,
            boolean combat) {
        List<RemovedUnit> units =
                RemoveUnitService.removeAllPlayerNonStructureUnits(event, game, player, tile, unitHolder);
        handleDestroyedUnits(event, game, units, combat);
    }

    public static void destroyUnits(
            GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList, boolean combat) {
        destroyUnits(event, tile, game, color, unitList, combat, true);
    }

    private static void destroyUnits(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            String color,
            String unitList,
            boolean combat,
            boolean prioritizeDamagedUnits) {
        List<RemovedUnit> destroyedMap =
                RemoveUnitService.removeUnits(event, tile, game, color, unitList, prioritizeDamagedUnits);
        handleDestroyedUnits(event, game, destroyedMap, combat);
    }

    public static void destroyUnit(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            UnitKey key,
            int amt,
            UnitHolder unitHolder,
            boolean combat) {
        ParsedUnit unit = new ParsedUnit(key, amt, unitHolder.getName());
        destroyUnit(event, tile, game, unit, combat);
    }

    public static void destroyUnit(
            GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit, boolean combat) {
        destroyUnit(event, tile, game, parsedUnit, combat, true);
    }

    public static void destroyUnit(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            ParsedUnit parsedUnit,
            boolean combat,
            boolean prioritizeDamagedUnits) {
        var destroyedUnit = RemoveUnitService.removeUnit(event, tile, game, parsedUnit, prioritizeDamagedUnits);
        handleDestroyedUnits(event, game, destroyedUnit, combat);
    }

    public static void destroyUnit(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            ParsedUnit parsedUnit,
            boolean combat,
            UnitState preferredState) {
        var destroyedUnit = RemoveUnitService.removeUnit(event, tile, game, parsedUnit, preferredState);
        handleDestroyedUnits(event, game, destroyedUnit, combat);
    }

    private static void handleDestroyedUnits(
            GenericInteractionCreateEvent event, Game game, List<RemovedUnit> units, boolean combat) {
        // batch up infantry for INF2-ish effects
        for (Player player : game.getRealPlayersNNeutral()) {
            if (AshenUnitHandler.resolveAshenInfDestroy(game, player, units, event)) {
                continue;
            }

            int numInfantry = 0;
            for (RemovedUnit u : units) {
                if (player.unitBelongsToPlayer(u.unitKey()) && u.unitKey().unitType() == UnitType.Infantry) {
                    numInfantry += u.getTotalRemoved();
                }
            }

            if (numInfantry > 0) {
                ButtonHelper.resolveInfantryDestroy(
                        player, numInfantry, units.getFirst().tile());
            }
        }

        // Would normally gate the hook, but I loop and check for ability in the handler
        CrystellumAbilityHandler.offerFragmentationForBatchIfRelevant(event, game, units, combat);

        // Handle other destroyed units individually
        for (RemovedUnit u : units) handleDestroyedUnit(event, game, units, u, combat);
    }

    // TODO: Jazz add the rest of the destroy code here
    private static void handleDestroyedUnit(
            GenericInteractionCreateEvent event,
            Game game,
            List<RemovedUnit> allUnits,
            RemovedUnit unit,
            boolean combat) {
        int totalAmount = unit.getTotalRemoved();
        Player player = game.getPlayerFromColorOrFaction(unit.unitKey().colorID());

        if (combat && player != null) {
            if (player.hasAbility("beauty_in_destruction")) {
                AshenAbilityHandler.offerBeautyInDestruction(game, player, unit, event);
            }
            if (player.hasUnit("ashen_dreadnought") || player.hasUnit("ashen_dreadnought2")) {
                AshenUnitHandler.offerAshfallEngineOnDestroy(event, game, player, unit);
            }
        }

        List<Player> capturing = CaptureUnitService.listCapturingFlagshipPlayers(game, allUnits, unit);
        List<Player> devours = CaptureUnitService.listCapturingCombatPlayers(game, unit);
        if (combat) {
            capturing.addAll(devours);
        }

        if (game.isTwilightsFallMode() && (unit.unitKey().unitType() == UnitType.Fighter)) {
            for (Player p2 : game.getRealPlayersExcludingThis(player)) {
                if (p2.ownsUnit("tf-vortexer")) {
                    for (String pos :
                            FoWHelper.getAdjacentTiles(game, unit.tile().getPosition(), p2, false, true)) {
                        if (game.getTileByPosition(pos).getSpaceUnitHolder().getUnitCount(UnitType.Carrier, p2) > 0) {
                            capturing.add(p2);
                            break;
                        }
                    }
                }
            }
        }

        List<Player> killers = CaptureUnitService.listProbableKiller(game, unit);

        switch (unit.unitKey().unitType()) {
            case Infantry -> {
                capturing.addAll(CaptureUnitService.listCapturingMechPlayers(game, allUnits, unit));
                AshenUnitHandler.resolveFlagshipBombardmentInfantryDeath(event, game, player, unit);
            }
            case Mech -> {
                handleSelfAssemblyRoutines(player, totalAmount, game);
                if (player != null && player.hasUnit("ashen_mech")) {
                    AshenUnitHandler.resolveAshenMechDestroy(game, player, unit);
                }
                if (player.hasUnit("iron_mech") || player.hasUnit("iron_mech2")) {
                    IronUnitsHandler.resolveRiptideDestroy(event, game, player, unit);
                }
                if (combat
                        && player.getPromissoryNotes().containsKey("bepniron")
                        && !player.getPromissoryNotesOwned().contains("bepniron")) {
                    IronUnitsHandler.resolveEjectionDestroy(event, game, player, unit, killers);
                }
                if (player.hasUnit("dream_mech")) {
                    DreamButtonHandler.offerRecurringMechButtons(
                            event, game, player, totalAmount, unit.uh().getName(), unit.unitKey());
                }
                if (player.hasUnit("mykomentori_mech") || player.hasTech("tf-specops")) {
                    for (int x = 0; x < totalAmount; x++) {
                        ButtonHelper.rollMykoMechRevival(game, player);
                    }
                }
                if (player.hasUnit("cheiran_mech")) {
                    AddUnitService.addUnits(
                            event,
                            unit.tile(),
                            game,
                            player.getColor(),
                            totalAmount + " infantry " + unit.uh().getName());
                    String message = "> Added " + totalAmount + " infantry to the planet following " + totalAmount
                            + " Nauplius (Cheiran mech) being destroyed.\n";
                    MessageHelper.sendMessageToEventChannel(event, message);
                }
            }
            case Warsun -> {
                if (player != null && player.hasUnit("xan_flagship")) {
                    XanUnitHandler.offerFlagshipReplace(event, game, player);
                }
            }
            case Flagship -> {
                if (player != null && player.hasUnit("ta_flagship")) {
                    TaUnitHandler.clearWorldshaperOnFlagshipDestroy(player, unit);
                }
                if (player != null && player.hasUnit("yin_flagship")) {
                    String message1 = "Moments before disaster in game " + game.getName() + ".";
                    DisasterWatchHelper.postTileInDisasterWatch(game, event, unit.tile(), 0, message1);
                    UnitHolder uh = unit.tile().getSpaceUnitHolder();
                    for (Player player_ : game.getPlayers().values()) {
                        destroyAllPlayerNonStructureUnits(event, game, player_, unit.tile(), uh, combat);
                    }
                    int randomJokeChance = ThreadLocalRandom.current().nextInt(1, 3);
                    File audioFile = ResourceHelper.getFile("voices/yin/", "Bomb" + randomJokeChance + ".mp3");
                    if (audioFile.exists()) {
                        MessageHelper.sendFileToChannel(event.getMessageChannel(), audioFile);
                    }
                    DisasterWatchHelper.postTileInDisasterWatch(
                            game, event, unit.tile(), 0, player.getRepresentation() + " has detonated the bomb.");
                }
                if (player != null && player.hasUnit("crystellum_flagship")) {
                    CrystellumUnitHandler.resolveCrystFlagDestroy(event, player, game, unit);
                }
            }
            default -> Consumers.nop();
        }

        Set<String> counted = new HashSet<>();
        for (Player cabal : capturing) {
            if (!counted.add(cabal.getColorID())) continue;
            CaptureUnitService.executeCapture(event, game, cabal, unit);
        }
        if (player != null
                && combat
                && player.hasAbility("heroism")
                && (unit.unitKey().unitType() == UnitType.Infantry
                        || unit.unitKey().unitType() == UnitType.Fighter)) {
            ButtonHelperFactionSpecific.cabalEatsUnit(
                    player, game, player, totalAmount, unit.unitKey().unitName(), event);
        }
        Player mentakHero = game.getPlayerFromColorOrFaction(game.getStoredValue("mentakHero"));
        if (mentakHero != null && combat) {
            ButtonHelperFactionSpecific.mentakHeroProducesUnit(
                    player, game, mentakHero, totalAmount, unit.unitKey().unitName(), event, unit.tile());
        }
        if (player != null
                && player.hasTech("nekroc4y")
                && !combat
                && unit.tile() != player.getHomeSystemTile()
                && player.getHomeSystemTile() != null) {
            UnitModel uni = player.getUnitFromUnitKey(unit.unitKey());
            if (uni != null && uni.getIsShip()) {
                if (player.hasUnit("ghoti_flagship")
                        || CheckUnitContainmentService.getTilesContainingPlayersUnits(game, player, UnitType.Spacedock)
                                .contains(player.getHomeSystemTile())) {
                    List<Button> buttons = new ArrayList<>();
                    buttons.add(Buttons.green(
                            player.factionButtonChecker() + "useNekroNullRef",
                            "Use Null Reference (Upon Each Destroy)",
                            FactionEmojis.Nekro));
                    buttons.add(Buttons.red("deleteButtons", "Decline", FactionEmojis.Nekro));
                    MessageHelper.sendMessageToChannelWithButtons(
                            player.getCorrectChannel(),
                            player.getRepresentation()
                                    + ", you may produce one of your recently destroyed ships in your home system.",
                            buttons);
                }
            }
        }
        if (game.isTotalWarMode() && player != null) {
            UnitModel uni = player.getUnitFromUnitKey(unit.unitKey());
            int cost = (int) Math.ceil(uni.getCost());
            int winnings = cost * unit.getTotalRemoved();
            if (killers.isEmpty()) {
                List<Button> buttons = new ArrayList<>();
                for (Player p2 : game.getRealPlayers()) {
                    buttons.add(Buttons.gray(
                            "totalWarCommGain_" + winnings + "_" + p2.getFaction(), p2.getFactionNameOrColor()));
                }
                buttons.add(Buttons.red("deleteButtons", "No one"));
                String msg =
                        player.getRepresentation() + ", please tell the bot who killed your " + unit.getTotalRemoved()
                                + " " + unit.unitKey().unitType().getUnitTypeEmoji() + ".";
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
            } else {

                Player killer = killers.getFirst();
                if (killer.isRealPlayer()) {
                    String planet = ButtonHelperActionCards.getBestResPlanetInHomeSystem(killer, game);
                    if (planet.isEmpty()) {
                        MessageHelper.sendMessageToChannel(
                                player.getCorrectChannel(), "Could not find a planet to place commodities on.");

                    } else {
                        int newAmount = game.changeCommsOnPlanet(winnings, planet);
                        MessageHelper.sendMessageToChannel(
                                killer.getCorrectChannel(),
                                killer.getRepresentationNoPing() + " added " + winnings
                                        + " commodities to the planet of "
                                        + Helper.getPlanetRepresentation(planet, game)
                                        + " (which has " + newAmount + " commodities on it now) by destroying "
                                        + unit.getTotalRemoved() + " of "
                                        + player.getRepresentationNoPing() + "'s "
                                        + unit.unitKey().unitType().getUnitTypeEmoji()
                                        + "\nIf this was a mistake, adjust the commodities with `/ds set_planet_comms`.");
                    }
                }
            }
        }
        if (game.isAgeOfFightersMode() && player != null) {
            UnitModel uni = player.getUnitFromUnitKey(unit.unitKey());
            if (uni != null && uni.getIsShip() && uni.getUnitType() != UnitType.Fighter) {
                String unitID = AliasHandler.resolveUnit(uni.getBaseType());
                player.setUnitCap(unitID, player.getUnitCap(unitID) - 1);
                MessageHelper.sendMessageToChannel(
                        player.getCorrectChannel(),
                        player.getRepresentationNoPing() + " purged 1 "
                                + unit.unitKey().unitType().getUnitTypeEmoji() + " due to _Age of Fighters_."
                                + " You now have a total of " + player.getUnitCap(unitID)
                                + " available to you  (on the game board or in your reinforcements)."
                                + "\n-# If this was a mistake, readjust the limit with `/game set_unit_cap`.");
            }
        }
        if (player != null && CrystellumPromissoryHandler.canUseFracture(game, player, unit, combat, killers)) {
            CrystellumPromissoryHandler.sendFractureButtons(event, game, player, unit);
        }
        if (player != null) {
            String unitTypeString =
                    unit.unitKey().unitType().humanReadableName().toLowerCase();
            Player activePlayer = game.getActivePlayer();
            if (!game.getStoredValue("bounties" + player.getFaction() + unitTypeString)
                            .isEmpty()
                    && activePlayer != null
                    && activePlayer.hasAbility("marked_prey")
                    && !activePlayer.equals(player)) {
                ZephyrionBountyHandler.claimBounty(
                        game, activePlayer, player, unit.unitKey().unitType(), combat);
            }
        }
    }

    private static void handleSelfAssemblyRoutines(Player player, int min, Game game) {
        if (player.hasActiveBreakthrough("naazbt")) {
            BreakthroughCommandHelper.deactivateBreakthrough(player, "naazbt");
        }
        if (player.hasTech("sar")) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation()
                            + " you gained " + StringHelper.pluralize(min, "trade good") + " (" + player.getTg()
                            + "->" + (player.getTg() + min)
                            + ") from _Self-Assembly Routines_ because of " + min + " of your mechs dying."
                            + " This is a mandatory gain" + (min > 1 ? ", and happens 1 trade good at a time" : "")
                            + ".");
            for (int x = 0; x < min; x++) {
                player.setTg(player.getTg() + 1);
                ButtonHelperAbilities.pillageCheck(player, game);
            }
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
    }
}
