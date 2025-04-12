package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import software.amazon.awssdk.utils.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.image.TileGenerator;
import ti4.image.TileHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollService;
import ti4.service.combat.CombatRollType;
import ti4.service.combat.StartCombatService;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.FlipTileService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.ParsedUnit;
import ti4.service.unit.RemoveUnitService;

public class ButtonHelperModifyUnits {

    public static int getNumberOfSustainableUnits(Player player, Game game, UnitHolder unitHolder, boolean space, boolean spacecannonoffence) {
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int sustains = 0;
        Player mentak = Helper.getPlayerFromUnit(game, "mentak_mech");
        if (mentak != null && mentak != player && !space && unitHolder.getUnitCount(UnitType.Mech, mentak.getColor()) > 0 && !game.getLaws().containsKey("articles_war")) {
            return 0;
        }
        Player mentakFS = Helper.getPlayerFromUnit(game, "mentak_flagship");
        if (mentakFS != null && mentakFS != player
            && unitHolder.getUnitCount(UnitType.Flagship, mentakFS.getColor()) > 0) {
            return 0;
        }
        mentakFS = Helper.getPlayerFromUnit(game, "sigma_mentak_flagship_2");
        if (mentakFS != null && mentakFS != player) {
            if (unitHolder.getUnitCount(UnitType.Flagship, mentakFS.getColor()) > 0) {
                return 0;
            }
            Tile t = game.getTileFromPlanet(unitHolder.getName());
            for (String adjPos : FoWHelper.getAdjacentTilesAndNotThisTile(game, t.getPosition(), player, false)) {
                if (game.getTileByPosition(adjPos).getUnitHolders().get("space").getUnitCount(UnitType.Flagship, mentakFS.getColor()) > 0) {
                    return 0;
                }
            }
        }

        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                continue;
            UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
            if (unitModel == null)
                continue;

            if (unitModel.getBaseType().equalsIgnoreCase("warsun") && ButtonHelper.isLawInPlay(game, "schematics")) {
                continue;
            }
            if (unitModel.getBaseType().equalsIgnoreCase("mech") && spacecannonoffence) {
                continue;
            }
            UnitKey unitKey = unitEntry.getKey();
            int damagedUnits = 0;
            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }
            int totalUnits = unitEntry.getValue() - damagedUnits;
            if (unitModel.getSustainDamage()) {
                sustains += totalUnits;
            }
        }
        return sustains;
    }

    public static void autoAssignAntiFighterBarrageHits(Player player, Game game, String pos, int hits, ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        StringBuilder msg = new StringBuilder(player.getFactionEmoji() + " assigned " + (hits == 1 ? "the hit" : "hits") + " in the following way:\n");
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, game, unitHolder, true, false);
        Player cabal = Helper.getPlayerFromAbility(game, "devour");
        Player mentakHero = game
            .getPlayerFromColorOrFaction(game.getStoredValue("mentakHero"));
        if (hits > 0 && unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null)
                    continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("fighter") && min > 0) {
                    msg.append("> Destroyed ").append(min).append(" ").append(UnitEmojis.fighter).append("\n");
                    hits -= min;
                    var unit = new ParsedUnit(unitKey, min, unitHolder.getName());
                    RemoveUnitService.removeUnit(event, tile, game, unit, true);

                    if (cabal != null
                        && (!cabal.getFaction().equalsIgnoreCase(player.getFaction())
                            || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                        && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                        ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, min, unitName, event);
                    }
                    if (player.hasAbility("heroism")) {
                        ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, min, unitName, event);
                    }
                    if (mentakHero != null) {
                        ButtonHelperFactionSpecific.mentakHeroProducesUnit(player, game, mentakHero, min,
                            unitName, event, tile);
                    }
                }
            }
        }
        Player argent = Helper.getPlayerFromAbility(game, "raid_formation");
        if (hits > 0 && argent != null && FoWHelper.playerHasShipsInSystem(argent, tile) && argent != player
            && numSustains > 0) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null)
                    continue;
                if (unitModel.getBaseType().equalsIgnoreCase("warsun") && ButtonHelper.isLawInPlay(game, "schematics")) {
                    continue;
                }
                UnitKey unitKey = unitEntry.getKey();
                //String unitName = unitKey.unitName();
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = Math.min(totalUnits, hits);
                if (unitModel.getSustainDamage() && min > 0 && unitModel.getIsShip()) {
                    msg.append("> Made ").append(min).append(" ").append(unitModel.getUnitEmoji()).append(" sustained\n");
                    hits -= min;
                    tile.addUnitDamage("space", unitKey, min);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg.toString());
        event.getMessage().delete().queue();
    }

    public static void automateGroundCombat(Player p1, Player p2, String planet, Game game, ButtonInteractionEvent event) {
        boolean haveGroundForces = true;
        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int count = 0;
        while (haveGroundForces) {
            int hitP1 = CombatRollService.secondHalfOfCombatRoll(p1, game, event, tile, planet, CombatRollType.combatround, true);
            int hitP2 = CombatRollService.secondHalfOfCombatRoll(p2, game, event, tile, planet, CombatRollType.combatround, true);

            if (p1.hasTech("vpw") && hitP2 > 0) {
                hitP1++;
            }
            if (p2.hasTech("vpw") && hitP1 > 0) {
                hitP2++;
            }
            int p1SardakkMechHits;
            int p2SardakkMechHits;
            if (p1.getPlanets().contains(planet)) {
                p2SardakkMechHits = ButtonHelperModifyUnits.autoAssignGroundCombatHits(p2, game, planet, hitP1, event);
                p1SardakkMechHits = ButtonHelperModifyUnits.autoAssignGroundCombatHits(p1, game, planet, hitP2, event);
            } else {
                p1SardakkMechHits = ButtonHelperModifyUnits.autoAssignGroundCombatHits(p1, game, planet, hitP2, event);
                p2SardakkMechHits = ButtonHelperModifyUnits.autoAssignGroundCombatHits(p2, game, planet, hitP1, event);
            }
            if (p2SardakkMechHits > 0) {
                ButtonHelperModifyUnits.autoAssignGroundCombatHits(p1, game, planet, p2SardakkMechHits, event);
            }
            if (p1SardakkMechHits > 0) {
                ButtonHelperModifyUnits.autoAssignGroundCombatHits(p2, game, planet, p1SardakkMechHits, event);
            }

            if (!doesPlayerHaveGfOnPlanet(unitHolder, p2) || !doesPlayerHaveGfOnPlanet(unitHolder, p1)) {
                haveGroundForces = false;
            }
            count++;
            if (count > 100) {
                haveGroundForces = false;
            }
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "## End of Ground Combat");
        String pos = tile.getPosition();
        FileUpload systemWithContext = new TileGenerator(game, event, null, 0, tile.getPosition()).createFileUpload();
        MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
        List<Button> buttons = StartCombatService.getGeneralCombatButtons(game, pos, p1, p2, "ground", event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
    }

    public static String getDamagedUnits(Player player, UnitHolder unitHolder, Game game) {
        StringBuilder duraniumMsg = new StringBuilder();
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                continue;
            UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
            if (unitModel == null)
                continue;
            UnitKey unitKey = unitEntry.getKey();
            String unitName = unitKey.unitName();
            int damagedUnits = 0;
            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }
            if (damagedUnits > 0) {
                duraniumMsg.append(unitName);
            }
        }

        return duraniumMsg.toString();
    }

    private static boolean isCabalMechActive(Game game, UnitHolder unitHolder) {
        Player cabalMechOwner = Helper.getPlayerFromUnit(game, "cabal_mech");
        return cabalMechOwner != null && unitHolder.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0 && !ButtonHelper.isLawInPlay(game, "articles_war");
    }

    private static int calculateMinHits(Player player, int hits, int totalUnits) {
        return player.hasTech("nes") ? Math.min(totalUnits, (hits + 1) / 2) : Math.min(totalUnits, hits);
    }

    public static int autoAssignGroundCombatHits(Player player, Game game, String planet, int hits, ButtonInteractionEvent event) {
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        if (unitHolder == null) {
            MessageHelper.replyToMessage(event, "Unable to determine the planet the ground combat is occurring on. This may be a bug to report?");
            return 0;
        }
        StringBuilder msg = new StringBuilder(player.getFactionEmoji() + " assigned " + (hits == 1 ? "the hit" : "hits") + " in the following way:\n");
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, game, unitHolder, false, false);
        boolean cabalMech = isCabalMechActive(game, unitHolder);
        Tile tile = game.getTileFromPlanet(planet);
        Player cabal = Helper.getPlayerFromAbility(game, "devour");
        String duraniumMsg = getDamagedUnits(player, unitHolder, game);
        boolean usedDuraniumAlready = !player.hasTech("da");
        int sardakkMechHits = 0;
        if (hits < 1 && (usedDuraniumAlready || duraniumMsg.isEmpty())) return 0;

        if (numSustains > 0) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null) continue;

                int damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitEntry.getKey(), 0);
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = calculateMinHits(player, hits, totalUnits);

                if (unitModel.getSustainDamage() && min > 0) {
                    msg.append("> Sustained ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    hits -= min * (player.hasTech("nes") ? 2 : 1);
                    tile.addUnitDamage(planet, unitEntry.getKey(), min);

                    for (int x = 0; x < min; x++) {
                        ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
                    }

                    if (player.hasUnit("sardakk_mech") && unitModel.getUnitType() == UnitType.Mech) {
                        msg.append("> Valkyrie Exoskeleton mech generated ").append(min).append(" hit").append(min == 1 ? "" : "s").append(" \n");
                        sardakkMechHits = min;
                    }
                }
            }
        }
        Map<String, String> unitTypes = new LinkedHashMap<>();
        unitTypes.put("fighter", UnitEmojis.fighter.toString());
        unitTypes.put("infantry", UnitEmojis.infantry.toString());
        unitTypes.put("pds", UnitEmojis.pds.toString());
        for (String unitType : unitTypes.keySet()) {
            if (shouldProcessUnit(player, unitType, unitHolder, hits)) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;

                    UnitKey unitKey = unitEntry.getKey();
                    UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                    if (unitModel == null) continue;

                    String unitName = unitKey.unitName();
                    int totalUnits = unitEntry.getValue();
                    int min = Math.min(totalUnits, hits);

                    if (unitName.equalsIgnoreCase(unitType) && min > 0) {
                        msg.append("> Destroyed ").append(min).append(" ").append(unitTypes.get(unitType)).append("\n");
                        hits -= min;
                        var unit = new ParsedUnit(unitKey, min, planet);
                        RemoveUnitService.removeUnit(event, tile, game, unit);

                        handleCabalConsumption(cabal, player, tile, planet, min, unitName, event, game, cabalMech);
                        handleTechOrAbilityTriggers(player, unitName, min, game);
                    }
                }
            }
        }

        if (hits > 0) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;

                UnitKey unitKey = unitEntry.getKey();
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null) continue;

                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);

                if (unitName.equalsIgnoreCase("mech") && min > 0) {
                    msg.append("> Destroyed ").append(min).append(" ").append(unitName).append(min == 1 ? "" : "s").append("\n");
                    hits -= min;
                    if (min + 1 > totalUnits) {
                        duraniumMsg = duraniumMsg.replace(unitName, "");
                    }
                    var unit = new ParsedUnit(unitKey, min, unitHolder.getName());
                    RemoveUnitService.removeUnit(event, tile, game, unit);

                    handleCabalConsumption(cabal, player, tile, planet, min, unitName, event, game, false);
                    StringBuilder msg3 = new StringBuilder();
                    msg.append(handleMechSpecificTriggers(player, min, game, msg3, unitHolder, tile, event));
                }
            }
        }
        if (!usedDuraniumAlready) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null)
                    continue;
                UnitKey unitKey = unitEntry.getKey();
                String unitName = unitKey.unitName();
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                if (!usedDuraniumAlready && damagedUnits > 0 && duraniumMsg.contains(unitName)) {
                    msg.append("> Repaired 1 ").append(unitModel.getUnitEmoji()).append(" due to _Duranium Armor_\n");
                    tile.removeUnitDamage(unitHolder.getName(), unitKey, 1);
                    usedDuraniumAlready = true;
                }
            }
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg.toString());
        if (!doesPlayerHaveGfOnPlanet(unitHolder, player) && (unitHolder.getUnitCount(UnitType.Pds, player.getColor()) > 0
            || unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)) {
            String msg2 = player.getRepresentation()
                + " you should remove structures if your opponent is not playing _Infiltrate_ or using **Assimilate**. Use buttons to resolve.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(
                Buttons.red(player.getFinsFactionCheckerPrefix() + "removeAllStructures_" + unitHolder.getName(),
                    "Remove Structures"));
            buttons.add(Buttons.gray("deleteButtons", "Don't Remove Structures"));
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg2, buttons);
        }
        event.getMessage();
        event.getMessage().delete().queue();
        return sardakkMechHits;
    }

    private static boolean shouldProcessUnit(Player player, String unitType, UnitHolder units, int hits) {
        return switch (unitType) {
            case "fighter" -> hits > 0 && (player.hasUnit("naalu_flagship") || player.hasUnit("sigma_naalu_flagship_2")) && units.getUnitCount(UnitType.Fighter, player.getColor()) > 0;
            case "infantry" -> hits > 0;
            case "pds" -> hits > 0 && (player.hasUnit("titans_pds") || player.hasUnit("titans_pds2")) && units.getUnitCount(UnitType.Pds, player.getColor()) > 0;
            default -> false;
        };
    }

    private static void handleCabalConsumption(Player cabal, Player player, Tile tile, String planet, int min, String unitName, GenericInteractionCreateEvent event, Game game, boolean cabalMech) {
        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction()) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile) || cabalMech) && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, planet)) {
            ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, min, unitName, event);
        }
    }

    private static void handleTechOrAbilityTriggers(Player player, String unitName, int min, Game game) {
        if (unitName.equalsIgnoreCase("infantry") && (player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))) {
            ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
        }
        if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
            ButtonHelper.resolveInfantryDeath(player, min);
        }
    }

    private static String handleMechSpecificTriggers(Player player, int min, Game game, StringBuilder msg, UnitHolder unitHolder, Tile tile, GenericInteractionCreateEvent event) {
        if (player.hasTech("sar")) {
            for (int x = 0; x < min; x++) {
                player.setTg(player.getTg() + 1);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + " gained 1 trade good from _Self-Assembly Routines_ because of a mech dying.");
                ButtonHelperAbilities.pillageCheck(player, game);
            }
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
        if (player.hasUnit("mykomentori_mech")) {
            for (int x = 0; x < min; x++) {
                ButtonHelper.rollMykoMechRevival(game, player);
            }
        }
        if (player.hasUnit("cheiran_mech")) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), min + " infantry " + unitHolder.getName());
            msg.append("> Added ").append(min).append(" infantry to the planet following a Nauplius (Cheiran mech) being destroyed.\n");
        }
        return msg.toString();
    }

    public static boolean doesPlayerHaveGfOnPlanet(UnitHolder unitHolder, Player player) {
        return !((unitHolder.getUnitCount(UnitType.Pds, player.getColor()) < 1 || (!player.hasUnit("titans_pds") && !player.hasUnit("titans_pds2"))) && unitHolder.getUnitCount(UnitType.Mech, player.getColor()) < 1 && unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) < 1);
    }

    public static String autoAssignSpaceCombatHits(
        Player player,
        Game game,
        Tile tile,
        int hits,
        GenericInteractionCreateEvent event,
        boolean justSummarizing
    ) {
        return autoAssignSpaceCombatHits(player, game, tile, hits, event, justSummarizing, false);
    }

    private static void handleCabalEatsUnit(Player cabal, Player player, String unitName, int min, GenericInteractionCreateEvent event, Tile tile, Game game) {
        if (cabal != null && (!cabal.getFaction().equalsIgnoreCase(player.getFaction())
            || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
            && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
            ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, min, unitName, event);
        }
    }

    private static void handleSelfAssemblyRoutines(Player player, int min, Game game) {
        if (player.hasTech("sar")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                + " you gained " + min + " trade good (" + player.getTg() + "->" + (player.getTg() + min)
                + ") from _Self-Assembly Routines_ because of " + min + " of your mechs dying."
                + " This is not an optional gain" + (min > 1 ? ", and happens 1 trade good at a time" : "") + ".");
            for (int x = 0; x < min; x++) {
                player.setTg(player.getTg() + 1);
                ButtonHelperAbilities.pillageCheck(player, game);
            }
            ButtonHelperAgents.resolveArtunoCheck(player, 1);
        }
    }

    private static void handleLetnevCommanderCheck(Player player, Game game, GenericInteractionCreateEvent event, int min) {
        for (int x = 0; x < min; x++) {
            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
        }
    }

    public static String autoAssignSpaceCombatHits(
        Player player,
        Game game,
        Tile tile,
        int hits,
        GenericInteractionCreateEvent event,
        boolean justSummarizing,
        boolean spaceCannonOffence
    ) {
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        StringBuilder msg = new StringBuilder(player.getFactionEmoji() + " assigned " + (hits == 1 ? "the hit" : "hits") + " in the following way:\n");
        if (justSummarizing) {
            msg = new StringBuilder("The hit" + (hits == 1 ? "" : "s") + " would be assigned in the following way:\n");
        }
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, game, unitHolder, true, spaceCannonOffence);
        boolean noMechPowers = ButtonHelper.isLawInPlay(game, "articles_war");
        Player cabal = Helper.getPlayerFromAbility(game, "devour");

        Player mentakHero = game
            .getPlayerFromColorOrFaction(game.getStoredValue("mentakHero"));
        if (spaceCannonOffence) {
            if (cabal != null && !ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile)) {
                cabal = null;
            }

            mentakHero = null;
        }

        Map<UnitKey, Integer> repairableUnitsByUnitKey = new TreeMap<>(new ShipRepairComparator());
        if (player.hasTech("da") && !spaceCannonOffence) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null) continue;

                int damagedUnits = (unitHolder.getUnitDamage() != null) ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;
                if (damagedUnits > 0) {
                    repairableUnitsByUnitKey.put(unitKey, damagedUnits);
                }
            }
        }

        if (numSustains > 0) {
            //just for dread 2s
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.getSustainDamage() || (!unitModel.getIsShip() && !isNomadMechApplicable(player, noMechPowers, unitKey))) continue;
                if (unitModel.getBaseType().equalsIgnoreCase("warsun") && ButtonHelper.isLawInPlay(game, "schematics")) continue;

                String unitName = unitKey.unitName();
                if (!unitName.equalsIgnoreCase("dreadnought") || !player.hasUpgradedUnit("dn2")) continue;

                int damagedUnits = (unitHolder.getUnitDamage() != null) ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = (player.hasTech("nes")) ? Math.min(totalUnits, (hits + 1) / 2) : Math.min(totalUnits, hits);

                if (min > 0) {
                    hits -= min * (player.hasTech("nes") ? 2 : 1); // Adjust hits based on technology
                    repairableUnitsByUnitKey.computeIfPresent(unitKey, (key, value) -> value += min);

                    if (!justSummarizing) {
                        msg.append("> Sustained ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                        tile.addUnitDamage("space", unitKey, min);
                        for (int x = 0; x < min; x++) {
                            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
                        }
                    } else {
                        msg.append("> Would sustain ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    }
                }
            }
            // Second loop for non dread 2s
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.getSustainDamage() || ((!unitModel.getIsShip() && !(ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_nekro_flagship_2", player, tile))) && !isNomadMechApplicable(player, (noMechPowers || spaceCannonOffence), unitKey))) continue;
                if (!unitModel.getIsShip() && spaceCannonOffence) {
                    continue;
                }
                if (unitModel.getBaseType().equalsIgnoreCase("warsun") && ButtonHelper.isLawInPlay(game, "schematics")) continue;
                String unitName = unitKey.unitName();

                if (unitName.equalsIgnoreCase("dreadnought") && player.hasUpgradedUnit("dn2")) continue;

                // Get damaged units count
                int damagedUnits = (unitHolder.getUnitDamage() != null) ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = (player.hasTech("nes")) ? Math.min(totalUnits, (hits + 1) / 2) : Math.min(totalUnits, hits);

                String stuffNotToSustain = game.getStoredValue("stuffNotToSustainFor" + player.getFaction());
                if (stuffNotToSustain.isEmpty()) {
                    game.setStoredValue("stuffNotToSustainFor" + player.getFaction(), "warsunflagship");
                    stuffNotToSustain = "warsunflagship";
                }

                if (unitModel.getSustainDamage() && min > 0 && !stuffNotToSustain.contains(unitModel.getBaseType().toLowerCase())) {
                    hits -= min * (player.hasTech("nes") ? 2 : 1);
                    repairableUnitsByUnitKey.computeIfPresent(unitKey, (key, value) -> value += min);

                    if (!justSummarizing) {
                        msg.append("> Sustained ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                        tile.addUnitDamage("space", unitKey, min);
                        for (int x = 0; x < min; x++) {
                            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
                        }
                    } else {
                        msg.append("> Would sustain ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    }
                }
            }
        }
        List<String> assignHitOrder = new ArrayList<>(List.of("fighter", "destroyer", "cruiser", "remainingSustains", "nraShenanigans", "carrier", "dreadnought", "flagship", "warsun"));
        if (spaceCannonOffence) {
            assignHitOrder = new ArrayList<>(List.of("fighter", "destroyer", "cruiser", "remainingSustains", "carrier", "dreadnought", "flagship", "warsun"));
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (!game.getStoredValue(p2.getFaction() + "graviton").isEmpty()) {
                    assignHitOrder = new ArrayList<>(List.of("destroyer", "cruiser", "remainingSustains", "dreadnought", "carrier", "flagship", "warsun", "fighter"));
                }
            }
        }
        for (String thingToHit : assignHitOrder) {
            if (hits <= 0) continue;

            boolean isNraShenanigans = thingToHit.equalsIgnoreCase("nraShenanigans");
            boolean isRemainingSustains = thingToHit.equalsIgnoreCase("remainingSustains");

            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null) continue;

                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = unitHolder.getUnitDamage() != null ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;
                int effectiveUnits = totalUnits;
                if (isRemainingSustains) {
                    effectiveUnits -= damagedUnits;
                }
                int min = Math.min(effectiveUnits, hits);
                if (isNraShenanigans && player.getUnitsOwned().contains("naaz_mech_space") && unitName.equalsIgnoreCase("mech") && min > 0) {
                    hits -= min;
                    repairableUnitsByUnitKey.computeIfPresent(unitKey, (key, value) -> value -= min);
                    if (!justSummarizing) {
                        var unit = new ParsedUnit(unitKey, min, unitHolder.getName());
                        RemoveUnitService.removeUnit(event, tile, game, unit);
                        handleCabalEatsUnit(cabal, player, unitName, min, event, tile, game);
                        msg.append("> Destroyed ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                        handleSelfAssemblyRoutines(player, min, game);
                    } else {
                        msg.append("> Would destroy ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    }
                    continue; // Skip to the next unit
                } else if (isRemainingSustains && unitModel.getIsShip() && unitModel.getSustainDamage() && min > 0) {
                    String stuffNotToSustain = game.getStoredValue("stuffNotToSustainFor" + player.getFaction());
                    if (stuffNotToSustain.isEmpty()) {
                        game.setStoredValue("stuffNotToSustainFor" + player.getFaction(), "warsun");
                        stuffNotToSustain = "warsun";
                    }
                    if (!stuffNotToSustain.contains(unitName.toLowerCase()) ||
                        (unitName.equalsIgnoreCase("dreadnought") && player.hasUpgradedUnit("dn2"))) {
                        continue; // Skip to the next unit since these sustains are already handled
                    }
                    hits -= min;
                    if (player.hasTech("nes")) hits -= min;
                    if (!justSummarizing) {
                        tile.addUnitDamage("space", unitKey, min);
                        handleLetnevCommanderCheck(player, game, event, min);
                        msg.append("> Sustained ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    } else {
                        msg.append("> Would sustain ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    }

                    continue; // Skip to the next unit
                }

                // Handle general case of destroying units
                if (unitName.equalsIgnoreCase(thingToHit) && min > 0) {
                    hits -= min;
                    repairableUnitsByUnitKey.computeIfPresent(unitKey, (key, value) -> value -= min);
                    if (!justSummarizing) {
                        var unit = new ParsedUnit(unitKey, min, unitHolder.getName());
                        RemoveUnitService.removeUnit(event, tile, game, unit);
                        handleCabalEatsUnit(cabal, player, unitName, min, event, tile, game);
                        if (player.hasAbility("heroism") && unitModel.getBaseType().equalsIgnoreCase("fighter")) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, min, unitName, event);
                        }
                        msg.append("> Destroyed ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                        if (mentakHero != null) {
                            ButtonHelperFactionSpecific.mentakHeroProducesUnit(player, game, mentakHero, min,
                                unitName, event, tile);
                        }
                    } else {
                        msg.append("> Would destroy ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    }
                }
            }
        }

        // Remove floating infantry and mechs if everything else is dead
        if (hits >= 0 && !FoWHelper.playerHasActualShipsInSystem(player, tile)) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey) || unitEntry.getValue() <= 0)
                    continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null)
                    continue;

                String unitName = unitKey.unitName();

                if ((unitName.equalsIgnoreCase("mech") || unitName.equalsIgnoreCase("infantry")) &&
                    !((ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_nekro_flagship_1", player, tile) || ButtonHelper.doesPlayerHaveFSHere("sigma_nekro_flagship_2", player, tile)) ||
                        unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)) {

                    int min = unitEntry.getValue();
                    if (!justSummarizing) {
                        var unit = new ParsedUnit(unitKey, min, unitHolder.getName());
                        RemoveUnitService.removeUnit(event, tile, game, unit);
                        msg.append("> Removed ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    } else {
                        msg.append("> Would remove ").append(min).append(" ").append(unitModel.getUnitEmoji()).append("\n");
                    }
                }
            }
        }

        // Repair units with Duranium Armor if repairable units still exist
        repairableUnitsByUnitKey.values().removeIf(value -> value == 0);
        if (!repairableUnitsByUnitKey.isEmpty()) {
            for (Map.Entry<UnitKey, Integer> unitEntry : repairableUnitsByUnitKey.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey) || unitEntry.getValue() <= 0)
                    continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null)
                    continue;

                int damagedUnits = unitHolder.getUnitDamage() != null ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;
                if (damagedUnits > 0 && unitModel.getIsShip()) {
                    if (!justSummarizing) {
                        msg.append("> Repaired 1 ").append(unitModel.getUnitEmoji()).append(" due to _Duranium Armor_\n");
                        tile.removeUnitDamage("space", unitKey, 1);
                    } else {
                        msg.append("> Would repair 1 ").append(unitModel.getUnitEmoji()).append(" due to _Duranium Armor_\n");
                    }
                    break;
                }
            }
        }
        if (!justSummarizing && event instanceof ButtonInteractionEvent bevent) {
            bevent.getMessage().delete().queue();
        }
        return msg.toString();

    }

    public static List<Button> getRemoveThisTypeOfUnitButton(Player player, Game game, String unit) {
        List<Button> buttons = new ArrayList<>();
        UnitType type = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID()).getUnitType();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, type)) {
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                if (uH.getUnitCount(type, player.getColor()) > 0) {
                    buttons
                        .add(Buttons.red(
                            "removeThisTypeOfUnit_" + type.humanReadableName() + "_" + tile.getPosition() + "_"
                                + uH.getName(),
                            type.humanReadableName() + " from " +
                                tile.getRepresentation() + " in " + uH.getName()));
                }
            }
        }
        buttons.add(Buttons.gray("deleteButtons", "Done Resolving"));
        return buttons;
    }

    @ButtonHandler("removeThisTypeOfUnit_")
    public static void removeThisTypeOfUnit(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unit = buttonID.split("_")[1].toLowerCase().replace(" ", "").replace("'", "");
        String tilePos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(tilePos);
        String unitH = buttonID.split("_")[3];
        UnitHolder uH = tile.getUnitHolders().get(unitH);

        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 " + unit + " " + unitH.replace("space", ""));
        if (uH.getUnitCount(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID()).getUnitType(), player.getColor()) < 1) {
            ButtonHelper.deleteTheOneButton(event);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getRepresentationNoPing() + " removed 1 " + unit + " from tile "
                + tile.getRepresentationForButtons(game, player) + " location: " + unitH);
    }

    public static void infiltratePlanet(Player player, Game game, UnitHolder uH, ButtonInteractionEvent event) {
        int sdAmount = 0;
        int pdsAmount = 0;
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            sdAmount += uH.getUnitCount(UnitType.Spacedock, p2.getColor());
            if (uH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0) {
                RemoveUnitService.removeUnits(event, game.getTileFromPlanet(uH.getName()), game, p2.getColor(), sdAmount + " sd " + uH.getName());
                RemoveUnitService.removeUnits(event, game.getTileFromPlanet(uH.getName()), game, p2.getColor(), sdAmount + " csd " + uH.getName());
            }
            pdsAmount = uH.getUnitCount(UnitType.Pds, p2.getColor()) + pdsAmount;
            if (uH.getUnitCount(UnitType.Pds, p2.getColor()) > 0) {
                RemoveUnitService.removeUnits(event, game.getTileFromPlanet(uH.getName()), game, p2.getColor(), pdsAmount + " pds " + uH.getName());
            }
        }
        if (pdsAmount > 0) {
            if (player.hasUnit("mirveda_pds") || player.hasUnit("mirveda_pds2")) {
                AddUnitService.addUnits(event, game.getTileFromPlanet(uH.getName()), game, player.getColor(), pdsAmount + " pds");
            } else {
                AddUnitService.addUnits(event, game.getTileFromPlanet(uH.getName()), game, player.getColor(), pdsAmount + " pds " + uH.getName());
            }
        }
        if (sdAmount > 0) {
            if (player.hasUnit("saar_spacedock") || player.hasUnit("saar_spacedock2")) {
                AddUnitService.addUnits(event, game.getTileFromPlanet(uH.getName()), game, player.getColor(), sdAmount + " sd");
            } else {
                AddUnitService.addUnits(event, game.getTileFromPlanet(uH.getName()), game, player.getColor(), sdAmount + " sd " + uH.getName());
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            player.getRepresentationNoPing() + " replaced " + pdsAmount + " PDS and " + sdAmount
                + " space dock" + (sdAmount == 1 ? "" : "s") + " on "
                + Helper.getPlanetRepresentation(uH.getName(), game) + " with their own unit"
                + (pdsAmount + sdAmount == 1 ? "" : "s") + ".");

    }

    public static List<Button> getRetreatSystemButtons(Player player, Game game, String pos1, boolean skilled) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        String skilledS = "";
        if (skilled) {
            skilledS = "_skilled";
        }
        for (String pos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false)) {
            Tile tile = game.getTileByPosition(pos2);
            if (pos1.equalsIgnoreCase(pos2) || (!Mapper.getFrontierTileIds().contains(tile.getTileID()) && tile.getPlanetUnitHolders().isEmpty() && tile.getUnitHolders().size() != 2)) {
                continue; // TODO: Can we name the second half of this boolean to make it clear what it is doing?
                // Pretty sure it's skipping over hyperlanes and the like
            }
            if (canRetreatTo(game, player, tile, skilled)) {
                buttons.add(Buttons.gray(finChecker + "retreatUnitsFrom_" + pos1 + "_" + pos2 + skilledS,
                    "Retreat to " + tile.getRepresentationForButtons(game, player)));
            }
        }
        return buttons;
    }

    private static boolean canRetreatTo(Game game, Player player, Tile tile, boolean skilledRetreat) {
        if ((tile.isAsteroidField() && !player.getTechs().contains("amd")) ||
            (tile.isSupernova() && !player.getTechs().contains("mr")) ||
            FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
            return false;
        }
        if (skilledRetreat) {
            return true;
        }
        if (FoWHelper.playerIsInSystem(game, tile, player, false)) {
            return true;
        }
        return !FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game) && (player.hasTech("det") || player.hasTech("absol_det"));
    }

    public static List<Button> getRetreatingGroundTroopsButtons(Player player, Game game, String pos1, String pos2) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        Map<String, String> planetRepresentations = Mapper.getPlanetRepresentations();
        Tile tile = game.getTileByPosition(pos1);
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            String name = entry.getKey();
            String representation = planetRepresentations.get(name);
            if (representation == null) {
                representation = name;
            }
            UnitHolder unitHolder = entry.getValue();
            if (unitHolder instanceof Planet) {
                int limit = unitHolder.getUnitCount(UnitType.Infantry, player.getColor());
                for (int x = 1; x < limit + 1; x++) {
                    if (x > 2) {
                        break;
                    }
                    String id = finChecker + "retreatGroundUnits_" + pos1 + "_" + pos2 + "_" + x + "infantry_" + representation;
                    String label = "Retreat " + x + " Infantry on " + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                    buttons.add(Buttons.green(id, label, UnitEmojis.infantry));
                }
                limit = unitHolder.getUnitCount(UnitType.Mech, player.getColor());
                for (int x = 1; x < limit + 1; x++) {
                    if (x > 2) {
                        break;
                    }
                    String id = finChecker + "retreatGroundUnits_" + pos1 + "_" + pos2 + "_" + x + "mech_" + representation;
                    String label = "Retreat " + x + " Mech" + (x == 1 ? "" : "s") + " on " + Helper.getPlanetRepresentation(representation.toLowerCase(), game);
                    buttons.add(Buttons.green(id, label, UnitEmojis.mech));
                }

            }
        }
        Button concludeMove = Buttons.gray(finChecker + "deleteButtons", "Done Retreating troops");
        buttons.add(concludeMove);
        CommanderUnlockCheckService.checkPlayer(player, "naaz", "empyrean");
        return buttons;
    }

    @ButtonHandler("doneLanding")
    public static void finishLanding(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        if (!event.getMessage().getContentRaw().contains("Moved all units to the space area.")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        }

        String message = "Landed troops. Use buttons to decide if you wish to build or finish the activation.";
        ButtonHelperFactionSpecific.checkBlockadeStatusOfEverything(player, game, event);
        Tile tile = null;
        if (buttonID.contains("_")) {
            tile = game.getTileByPosition(buttonID.split("_")[1]);
        } else {
            game.getTileByPosition(game.getActiveSystem());
        }
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if ("space".equalsIgnoreCase(unitHolder.getName())) {
                continue;
            }
            List<Player> players = ButtonHelper.getPlayersWithUnitsOnAPlanet(game, tile, unitHolder.getName());
            Player player2 = player;
            for (Player p2 : players) {
                if (p2 != player && !player.getAllianceMembers().contains(p2.getFaction())) {
                    player2 = p2;
                    break;
                }
            }
            if (player != player2 && players.contains(player)) {
                StartCombatService.startGroundCombat(player, player2, game, event, unitHolder, tile);
                int mechCount = unitHolder.getUnitCount(UnitType.Mech, player2.getColor());
                if (player2.ownsUnit("keleres_mech") && mechCount > 0) {
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                    Button DoneExhausting = Buttons.red("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged()
                        + ", you must pay 1 influence" + (mechCount > 1 ? ", " + mechCount + " times," : "") + " due to "
                        + (mechCount == 1 ? "a" : mechCount) + " Omniopiare" + (mechCount == 1 ? "s" : "tis") + " (Keleres mech" + (mechCount == 1 ? "" : "s") + ").");
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        "Click the names of the planets you wish to exhaust.", buttons);
                }
            }
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                List<Button> b2s = new ArrayList<>();
                b2s.add(Buttons.green("returnFFToSpace_" + tile.getPosition(), "Return Fighters to Space"));
                b2s.add(Buttons.red(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true,
                    true)
                    + " you may use this button to return Naalu fighters to space after combat concludes. This only needs to be done once. Reminder you can't take over a planet with only fighters.",
                    b2s);
            }
        }
        List<Button> systemButtons = ButtonHelper.landAndGetBuildButtons(player, game, event, tile);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        CommanderUnlockCheckService.checkPlayer(player, "cheiran");
        event.getMessage().delete().queue();
    }

    public static List<Button> getUnitsToDevote(Player player, Game game, GenericInteractionCreateEvent event, Tile tile, String devoteOrNo) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        Set<UnitType> allowedUnits = Set.of(UnitType.Destroyer, UnitType.Cruiser);

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet)
                continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey))
                    continue;

                if (!allowedUnits.contains(unitKey.getUnitType())) {
                    continue;
                }
                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.getUnitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                Button validTile2 = Buttons.red(finChecker + "resolveDevote_" + tile.getPosition() + "_" + unitName
                    + "_" + devoteOrNo, "Destroy " + " " + prettyName, unitKey.unitEmoji());
                buttons.add(validTile2);

            }
        }
        return buttons;
    }

    @ButtonHandler("startDevotion_")
    public static void startDevotion(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        String msg = player.getRepresentation() + " choose which unit of yours to destroy to";
        List<Button> buttons = getUnitsToDevote(player, game, event, tile, "devote");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("resolveDevote_")
    public static void resolveDevote(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        List<Button> buttons = getOpposingUnitsToHit(player, game, event, tile, false);
        String msg = player.getRepresentation() + " choose which opposing unit to hit";
        String unit = buttonID.split("_")[2];
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        var parsedUnit = new ParsedUnit(unitKey);
        RemoveUnitService.removeUnit(event, tile, game, parsedUnit);
        String msg2 = player.getRepresentation() + "used **Devotion** to destroy one of their " + unitKey.unitEmoji() + " in tile " + tile.getRepresentation();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
        event.getMessage().delete().queue();
        String devoteOrNo = buttonID.split("_")[3];
        if (devoteOrNo.equalsIgnoreCase("devote")) {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
            CommanderUnlockCheckService.checkPlayer(player, "yin");
        }
    }

    public static List<Button> getOpposingUnitsToHit(Player player, Game game, GenericInteractionCreateEvent event, Tile tile, boolean exoHit) {
        String finChecker = "FFCC_" + player.getFaction() + "_";

        String exo = "";
        if (exoHit) {
            exo = "exo";
        }
        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = unitHolder.getUnits();
            if (unitHolder instanceof Planet)
                continue;

            Map<UnitKey, Integer> tileUnits = new HashMap<>(units);
            for (Map.Entry<UnitKey, Integer> unitEntry : tileUnits.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (player.unitBelongsToPlayer(unitKey))
                    continue;
                Player p2 = game.getPlayerFromColorOrFaction(unitKey.getColor());
                if (p2 == null) {
                    continue;
                }
                UnitModel unitModel = p2.getUnitFromUnitKey(unitKey);
                if (!unitModel.getIsShip()) {
                    continue;
                }

                String prettyName = unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null) {
                    damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitKey, 0);
                }

                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = finChecker + "hitOpponent_" + tile.getPosition() + "_" + unitName + "damaged"
                        + "_" + unitKey.getColor() + "_" + exo;
                    Button validTile2 = Buttons.red(buttonID, "Damaged " + prettyName, unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
                totalUnits -= damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Buttons.red(finChecker + "hitOpponent_" + tile.getPosition() + "_" + unitName
                        + "_" + unitKey.getColor() + "_" + exo, prettyName, unitKey.unitEmoji());
                    buttons.add(validTile2);
                }
            }
        }
        return buttons;
    }

    @ButtonHandler("hitOpponent_")
    public static void resolveGettingHit(Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String unit = buttonID.split("_")[2];
        boolean damaged = false;
        boolean exo = buttonID.contains("exo");
        if (unit.contains("damaged")) {
            damaged = true;
            unit = unit.replace("damaged", "");
        }
        String playerColor = buttonID.split("_")[3];
        Player player = game.getPlayerFromColorOrFaction(playerColor);
        MessageChannel channel = event.getChannel();
        if (game.isFowMode()) {
            channel = player.getPrivateChannel();
        }
        String msg = player.getRepresentation() + " you have had one of your units assigned a hit, please cancel the hit somehow (_Shields Holding_, SUSTAIN DAMAGE, etc.), or accept the lost of the unit.";
        List<Button> buttons = new ArrayList<>();

        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID());

        UnitModel unitModel = player.getUnitFromUnitKey(key);

        String unitName = key.unitName();
        if (exo) {
            String dmg = "";
            if (damaged) {
                dmg = "damaged ";
            }
            msg = player.getRepresentation() + " lost a " + dmg + unitModel.getBaseType() + " to your opponent's Exotrireme II ability.";
            var parsedUnit = new ParsedUnit(key, 1, Constants.SPACE);
            RemoveUnitService.removeUnit(event, tile, game, parsedUnit, damaged);
            ButtonHelperFactionSpecific.cabalEatsUnitIfItShould(player, game, player, 1, unitName, event, tile, tile.getSpaceUnitHolder());
            MessageHelper.sendMessageToChannelWithButtons(channel, msg, buttons);
            event.getMessage().delete().queue();
            return;
        }

        int x = 1;
        if (damaged) {
            String id = player.finChecker() + "assignHits_" + tile.getPosition() + "_" + x + unitName + "damaged";
            String label = "Remove " + x + " damaged " + unitModel.getBaseType();
            buttons.add(Buttons.red(id, label, unitModel.getUnitEmoji()));
        } else {
            String id = player.finChecker() + "assignHits_" + tile.getPosition() + "_" + x + unitName;
            String label = "Remove " + x + " " + unitModel.getBaseType();
            buttons.add(Buttons.red(id, label, unitModel.getUnitEmoji()));
        }
        if (!damaged && unitModel.getSustainDamage()) {
            String id = player.finChecker() + "assignDamage_" + tile.getPosition() + "_" + 1 + unitName;
            String label = "Sustain " + 1 + " " + unitModel.getBaseType();
            buttons.add(Buttons.blue(id, label, unitModel.getUnitEmoji()));
        }

        buttons.add(Buttons.gray("deleteButtons", "Cancel The Hit"));
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("retreatGroundUnits_")
    public static void retreatGroundUnits(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        String rest = buttonID.replace("retreatGroundUnits_", "").replace("'", "");
        String pos1 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos1 + "_", "");
        String pos2 = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos2 + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitType;
        String planet = "";
        if (rest.contains("_")) {
            unitType = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase();
        } else {
            unitType = rest;
        }

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitType), player.getColor());
        AddUnitService.addUnits(event, game.getTileByPosition(pos2), game, player.getColor(), amount + " " + unitType);
        boolean damaged = buttonLabel.toLowerCase().contains("damaged");
        if (damaged) {
            game.getTileByPosition(pos2).addUnitDamage("space", unitKey, amount);
        }

        var unit = new ParsedUnit(unitKey, amount, planet);
        RemoveUnitService.removeUnit(event, game.getTileByPosition(pos1), game, unit, damaged);

        List<Button> systemButtons = getRetreatingGroundTroopsButtons(player, game, pos1, pos2);
        String retreatMessage = player.getFactionEmojiOrColor() + " retreated " + amount + " " + unitType + " on " + planet + " to "
            + game.getTileByPosition(pos2).getRepresentationForButtons(game, player) + ".";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), retreatMessage);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void retreatSpaceUnits(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        Tile tile1 = game.getTileByPosition(pos1);
        Tile tile2 = game.getTileByPosition(pos2);
        tile2 = FlipTileService.flipTileIfNeeded(event, tile2, game);
        if (game.playerHasLeaderUnlockedOrAlliance(player, "kollecccommander") && !buttonID.contains("skilled")
            && !CommandCounterHelper.hasCC(event, player.getColor(), tile1)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji()
                + " did not place a command token in system they retreated to due to Kado S'mah-Qar, the Kollecc commander.");
        } else {
            CommandCounterHelper.addCC(event, player, tile2, true);
        }

        for (Map.Entry<String, UnitHolder> entry : tile1.getUnitHolders().entrySet()) {
            UnitHolder unitHolder = entry.getValue();
            Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
            if (unitHolder instanceof Planet)
                continue;
            // retreat capacity units first to avoid false cap flags
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null)
                    continue;
                if (unitModel.getCapacityValue() < 1) {
                    continue;
                }
                UnitKey unitKey = unitEntry.getKey();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                var unit = new ParsedUnit(unitKey, totalUnits, Constants.SPACE);
                RemoveUnitService.removeUnit(event, tile1, game, unit);

                AddUnitService.addUnits(event, tile2, game, player.getColor(), totalUnits + " " + unitName);
                if (damagedUnits > 0) {
                    game.getTileByPosition(pos2).addUnitDamage("space", unitKey, damagedUnits);
                }
            }
            // this will catch all the capacity units left behind in the previous iteration
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                    continue;
                UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
                if (unitModel == null)
                    continue;
                if (unitModel.getCapacityValue() > 0) {
                    continue;
                }
                UnitKey unitKey = unitEntry.getKey();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                var unit = new ParsedUnit(unitKey, totalUnits, Constants.SPACE);
                RemoveUnitService.removeUnit(event, tile1, game, unit);

                AddUnitService.addUnits(event, tile2, game, player.getColor(), totalUnits + " " + unitName);
                if (damagedUnits > 0) {
                    game.getTileByPosition(pos2).addUnitDamage("space", unitKey, damagedUnits);
                }
            }
        }
    }

    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game game, Player player, String ident) {
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos),
            "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    /**
     * Known sources: {@link Helper#getPlanetPlaceUnitButtons}
     */
    @ButtonHandler("place_")
    public static void genericPlaceUnit(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unitNPlanet = buttonID.replace("place_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unitID = AliasHandler.resolveUnit(unitLong.replace("2", ""));
        UnitKey unitKey = Mapper.getUnitKey(unitID, player.getColorID());
        String spaceOrPlanet;

        String successMessage;
        String playerRep = player.getRepresentation();
        Tile tile = game.getTile(AliasHandler.resolveTile(planetName));
        if ("sd".equalsIgnoreCase(unitID)) {
            if (player.hasUnit("absol_saar_spacedock") || player.hasUnit("saar_spacedock") || player.hasTech("ffac2")
                || player.hasTech("absol_ffac2")) {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitID);
                successMessage = "Placed 1 space dock in the space area of the " + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitID + " " + planetName);
                successMessage = "Placed 1 " + UnitEmojis.spacedock + " on " + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
            CommanderUnlockCheckService.checkPlayer(player, "cabal");
            if (player.hasAbility("industrious") && !FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                Button replace = Buttons.green("FFCC_" + player.getFaction() + "_rohdhnaIndustrious_"
                    + tile.getPosition() + "_" + unitID + " "
                    + planetName, "Replace Space Dock with War Sun");

                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(),
                    playerRep + ", you may spend 6 resources to replace 1 space dock with 1 war sun via your **Industrious** ability.", replace);
            }
        } else if ("pds".equalsIgnoreCase(unitLong)) {
            if (player.ownsUnit("mirveda_pds") || player.ownsUnit("mirveda_pds2")) {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitID);
                successMessage = "Placed 1 " + UnitEmojis.pds + " in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitLong + " " + planetName);
                successMessage = "Placed 1 " + UnitEmojis.pds + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
        } else if ("monument".equalsIgnoreCase(unitLong)) {
            if (player.ownsUnit("empyrean_monument")) {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitID);
                successMessage = "Placed 1 " + UnitEmojis.Monument + " in the space area of the " + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitLong + " " + planetName);
                successMessage = "Placed 1 " + UnitEmojis.Monument + " on " + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
        } else {
            String producedOrPlaced = "Produced";
            if ("gf".equalsIgnoreCase(unitID) || "mf".equalsIgnoreCase(unitID)
                || (unitLong.contains("gf") && unitLong.length() > 2)) {
                if ((unitLong.contains("gf") && unitLong.length() > 2)) {
                    if (!planetName.contains("space")) {
                        spaceOrPlanet = planetName;
                        String num = unitLong.substring(0, 1);
                        String producedInput = unitID.replace(num, "") + "_" + tile.getPosition() + "_" + spaceOrPlanet;
                        int nu = Integer.parseInt(num);
                        for (int x = 0; x < nu; x++) {
                            player.produceUnit(producedInput);
                        }
                        AddUnitService.addUnits(event, tile, game, player.getColor(), num + " gf " + planetName);
                        successMessage = producedOrPlaced + " " + num + " " + UnitEmojis.infantry + " on "
                            + Helper.getPlanetRepresentation(planetName, game) + ".";
                    } else {
                        spaceOrPlanet = "space";
                        tile = game.getTileByPosition(planetName.replace("space", ""));
                        String num = unitLong.substring(0, 1);
                        String producedInput = unitID.replace(num, "") + "_" + tile.getPosition() + "_" + spaceOrPlanet;
                        int nu = Integer.parseInt(num);
                        for (int x = 0; x < nu; x++) {
                            player.produceUnit(producedInput);
                        }
                        AddUnitService.addUnits(event, tile, game, player.getColor(), num + " gf");
                        successMessage = producedOrPlaced + " " + num + " " + UnitEmojis.infantry + " in space.";
                    }
                } else {

                    if (!planetName.contains("space")) {
                        spaceOrPlanet = planetName;
                        String producedInput = unitID.replace("2", "") + "_" + tile.getPosition() + "_" + spaceOrPlanet;
                        player.produceUnit(producedInput);
                        AddUnitService.addUnits(event, tile, game, player.getColor(), unitID + " " + planetName);
                        successMessage = producedOrPlaced + " a " + unitKey.unitEmoji() + " on "
                            + Helper.getPlanetRepresentation(planetName, game) + ".";
                    } else {
                        spaceOrPlanet = "space";
                        tile = game.getTileByPosition(planetName.replace("space", ""));
                        String producedInput = unitID.replace("2", "") + "_" + tile.getPosition() + "_" + spaceOrPlanet;
                        player.produceUnit(producedInput);
                        AddUnitService.addUnits(event, tile, game, player.getColor(), unitID);
                        successMessage = producedOrPlaced + " a " + unitKey.unitEmoji() + " in space.";
                    }

                }
            } else {
                spaceOrPlanet = "space";
                tile = game.getTileByPosition(planetName);
                String producedInput = unitID.replace("2", "") + "_" + tile.getPosition() + "_"
                    + spaceOrPlanet;
                player.produceUnit(producedInput);
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    AddUnitService.addUnits(event, game.getTileByPosition(planetName), game, player.getColor(), "2 ff");
                    successMessage = "Produced 2 " + UnitEmojis.fighter + " in tile "
                        + tile.getRepresentationForButtons(game, player) + ".";
                    player.produceUnit(producedInput);
                    Tile tile2 = game.getTileByPosition(planetName);
                    if (player.hasAbility("cloaked_fleets")) {
                        List<Button> shroadedFleets = new ArrayList<>();
                        shroadedFleets.add(
                            Buttons.green("cloakedFleets_" + tile2.getPosition() + "_ff", "Capture 1 fighter"));
                        shroadedFleets.add(Buttons.red("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                            "You may use your **Cloaked Fleets** ability to capture this produced ship.",
                            shroadedFleets);
                    }
                    if (player.hasAbility("cloaked_fleets")) {
                        List<Button> shroadedFleets = new ArrayList<>();
                        shroadedFleets.add(
                            Buttons.green("cloakedFleets_" + tile2.getPosition() + "_ff", "Capture 1 fighter"));
                        shroadedFleets.add(Buttons.red("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                            "You may use your **Cloaked Fleets** ability to capture this produced ship.",
                            shroadedFleets);
                    }
                } else if ("2destroyer".equalsIgnoreCase(unitLong)) {
                    AddUnitService.addUnits(event, game.getTileByPosition(planetName), game, player.getColor(), "2 destroyer");
                    successMessage = "Produced 2 " + UnitEmojis.destroyer + " in tile " + tile.getRepresentationForButtons(game, player) + ".";
                    player.produceUnit(producedInput);
                } else {
                    AddUnitService.addUnits(event, game.getTileByPosition(planetName), game, player.getColor(), unitID);
                    successMessage = "Produced a " + unitKey.unitEmoji() + " in tile " + tile.getRepresentationForButtons(game, player) + ".";
                    if (player.hasAbility("cloaked_fleets")) {
                        List<Button> cloakedFleets = new ArrayList<>();
                        cloakedFleets.add(Buttons.green("cloakedFleets_" + tile.getPosition() + "_" + unitID, "Capture 1 " + StringUtils.capitalize(Mapper.getUnitBaseTypeFromAsyncID(unitID))));
                        cloakedFleets.add(Buttons.red("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "You may use your **Cloaked Fleets** ability to capture this produced ship.", cloakedFleets);
                    }
                }

            }
        }
        if (("sd".equalsIgnoreCase(unitID) || "pds".equalsIgnoreCase(unitLong) || "monument".equalsIgnoreCase(unitLong)) && event.getMessage().getContentRaw().toLowerCase().contains("construction")) {
            if (game.isFowMode() || (!"action".equalsIgnoreCase(game.getPhaseOfGame()) && !"statusScoring".equalsIgnoreCase(game.getPhaseOfGame()))) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), playerRep + successMessage.replace("Produced", " produced"));
            } else {
                ButtonHelper.sendMessageToRightStratThread(player, game, playerRep + successMessage.replace("Produced", " produced"), "construction");
            }

            if (player.hasLeader("mahactagent") || player.hasExternalAccessToLeader("mahactagent")) {
                String message = playerRep + ", please tell the bot if you used Mahact's agent and thus should place the active player's (**Construction** holder) command token"
                    + " or if you followed normally and should place your own command token from reinforcements.";
                Button placeCCInSystem = Buttons.green(player.getFinsFactionCheckerPrefix() + "reinforcements_cc_placement_" + planetName, "Place Token from Reinforcements");
                Button placeConstructionCCInSystem = Buttons.gray(player.getFinsFactionCheckerPrefix() + "placeHolderOfConInSystem_" + planetName, "Place 1 Token of the Active Player");
                Button NoDontWantTo = Buttons.blue(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Don't Place A Command Token");
                List<Button> buttons = List.of(placeCCInSystem, placeConstructionCCInSystem, NoDontWantTo);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            } else {
                boolean hasConstruction = false;
                for (Integer sc : player.getSCs()) {
                    StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
                    if (scModel != null && scModel.getBotSCAutomationID().equalsIgnoreCase("pok4construction") && game.getScPlayed().containsKey(sc)) {
                        hasConstruction = true;
                        break;
                    }
                }
                if (!hasConstruction && ("action".equalsIgnoreCase(game.getPhaseOfGame())
                    || game.getCurrentAgendaInfo().contains("Strategy")) && !game.getStrategyCardSet().getAlias().toLowerCase().contains("anarchy")
                    && !ButtonHelper.isPlayerElected(game, player, "absol_minsindus")) {
                    tile = TileHelper.getTile(event, planetName, game);
                    String msg = playerRep + " placed 1 command token from reinforcements in the "
                        + Helper.getPlanetRepresentation(planetName, game) + " system.";
                    if (!game.playerHasLeaderUnlockedOrAlliance(player, "rohdhnacommander")) {
                        CommandCounterHelper.addCC(event, player, tile);
                        if (!game.isFowMode()) {
                            ButtonHelper.updateMap(game, event);
                        }
                    } else {
                        msg = playerRep
                            + " has B-Unit 205643a, the Roh'Dhna Commander, and is thus doing the primary ability of **Construction**, which does not place a command token.";
                    }
                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
                    } else {
                        ButtonHelper.sendMessageToRightStratThread(player, game, msg, "construction");
                    }
                }
            }
            event.getMessage().delete().queue();
        } else {
            if ("sd".equalsIgnoreCase(unitID) || "pds".equalsIgnoreCase(unitLong) || "monument".equalsIgnoreCase(unitLong)) {
                String producedInput = unitID + "_"
                    + tile.getPosition() + "_"
                    + planetName;
                player.produceUnit(producedInput);
            }
            String editedMessage = event.getMessage().getContentRaw();
            if (editedMessage.contains("Produced")) {
                editedMessage += "\n " + successMessage;
            } else {
                editedMessage = playerRep + " " + successMessage;
            }

            if (editedMessage.contains("place 2 infantry")) {
                successMessage = "Placed " + UnitEmojis.infantry + UnitEmojis.infantry + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                event.getMessage().delete().queue();
            } else {
                event.getMessage().editMessage(Helper.buildProducedUnitsMessage(player, game)).queue(
                    null, (error) -> BotLogger.error(new BotLogger.LogMessageOrigin(event, player), MessageHelper.getRestActionFailureMessage(event.getMessageChannel(), "Failed to edit message", null, error), error));
            }
        }
        if ("sd".equalsIgnoreCase(unitID)) {
            tile = game.getTileFromPlanet(planetName);
            if (tile != null) {
                AgendaHelper.ministerOfIndustryCheck(player, game, tile, event);
            }
        }
        CommanderUnlockCheckService.checkPlayer(player, "titans");
        CommanderUnlockCheckService.checkPlayer(player, "saar");
        CommanderUnlockCheckService.checkPlayer(player, "rohdhna");
        CommanderUnlockCheckService.checkPlayer(player, "cheiran");
        CommanderUnlockCheckService.checkPlayer(player, "celdauri");
        CommanderUnlockCheckService.checkPlayer(player, "gledge");
        if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if ("warsun".equalsIgnoreCase(unitLong)) {
            CommanderUnlockCheckService.checkPlayer(player, "muaat");
        }
        CommanderUnlockCheckService.checkPlayer(player, "mentak", "l1z1x", "tnelis", "cymiae", "kyro", "ghemina", "argent", "naaz", "arborec");
    }

    @ButtonHandler("cloakedFleets_")
    public static void resolveCloakedFleets(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unitID = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit(unitID), player.getColor());
        var unit = new ParsedUnit(key, 1, Constants.SPACE);
        RemoveUnitService.removeUnit(event, tile, game, unit);
        String name = unitID;
        if (Mapper.getUnit(AliasHandler.resolveUnit(unitID)) != null) {
            name = Mapper.getUnit(AliasHandler.resolveUnit(unitID)).getName();
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " captured 1 newly produced " + UnitEmojis.getUnitEmoji(name)
                + " in " + tile.getRepresentationForButtons(game, player) + " using the **Cloaked Fleets** ability (limit of 2 ships may be captured per build).");
        AddUnitService.addUnits(event, player.getNomboxTile(), game, player.getColor(), unitID);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("kolleccMechCapture_")
    public static void resolveKolleccMechCapture(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unit = buttonID.split("_")[2];
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(buttonID.split("_")[1]);
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), unit + " " + planet);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + " captured 1 " + unit + " on "
                + Helper.getPlanetRepresentation(planet, game) + " using a Nightshade Vanguard (Kollecc Mech).");
        AddUnitService.addUnits(event, player.getNomboxTile(), game, player.getColor(), unit);
        UnitHolder uh = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        if (unit.equalsIgnoreCase("mech")) {
            if (uh.getUnitCount(UnitType.Mech, player.getColor()) < 1) {
                ButtonHelper.deleteTheOneButton(event);
            }
        } else {
            if (uh.getUnitCount(UnitType.Infantry, player.getColor()) < 1) {
                ButtonHelper.deleteTheOneButton(event);
            }
        }
    }

    @ButtonHandler("placeOneNDone_")
    public static void placeUnitAndDeleteButton(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String unitNPlanet = buttonID.replace("placeOneNDone_", "");
        String skipbuild = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        unitNPlanet = unitNPlanet.replace(skipbuild + "_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unitID = AliasHandler.resolveUnit(unitLong);
        UnitKey unitKey = Mapper.getUnitKey(unitID, player.getColorID());
        String producedOrPlaced = "Produced";

        boolean willSkipBuild = skipbuild.contains("skipbuild");
        boolean orbitalDrop = skipbuild.contains("orbital");
        if (willSkipBuild) {
            producedOrPlaced = "Placed";
        }
        String successMessage;
        String playerRep = player.getRepresentation();

        Tile tile = game.getTileFromPositionOrAlias(planetName);
        if ("sd".equalsIgnoreCase(unitID)) {
            if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitID);
                successMessage = "Placed 1 space dock in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitID + " " + planetName);
                successMessage = "Placed 1 " + UnitEmojis.spacedock + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
        } else if ("pd".equalsIgnoreCase(unitID)) {
            if (player.ownsUnit("mirveda_pds") || player.ownsUnit("mirveda_pds2")) {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitID);
                successMessage = "Placed 1 " + UnitEmojis.pds + " in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                AddUnitService.addUnits(event, tile, game, player.getColor(), unitLong + " " + planetName);
                successMessage = "Placed 1 " + UnitEmojis.pds + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
        } else {
            if ("gf".equalsIgnoreCase(unitID) || "mf".equalsIgnoreCase(unitID) || ((unitLong.contains("gf") && unitLong.length() > 2))) {
                if (unitLong.contains("gf") && unitLong.length() > 2) {
                    String amount = "" + unitLong.charAt(0);
                    if (!planetName.contains("space")) {
                        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " gf " + planetName);
                        successMessage = producedOrPlaced + " " + amount + " " + UnitEmojis.infantry + " on "
                            + Helper.getPlanetRepresentation(planetName, game) + ".";
                    } else {
                        tile = game.getTileByPosition(planetName.replace("space", ""));
                        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " gf ");
                        successMessage = producedOrPlaced + " " + amount + " " + UnitEmojis.infantry + " in space.";
                    }
                } else {
                    if (!planetName.contains("space")) {
                        AddUnitService.addUnits(event, tile, game, player.getColor(), unitID + " " + planetName);
                        successMessage = producedOrPlaced + " a " + unitKey.unitEmoji() + " on " + Helper.getPlanetRepresentation(planetName, game) + ".";
                    } else {
                        tile = game.getTileByPosition(planetName.replace("space", ""));
                        AddUnitService.addUnits(event, tile, game, player.getColor(), unitID);
                        successMessage = producedOrPlaced + " a " + unitKey.unitEmoji() + " in space.";
                    }
                }
            } else {
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    AddUnitService.addUnits(event, game.getTileByPosition(planetName), game, player.getColor(), "2 ff");
                    successMessage = producedOrPlaced + " 2 " + UnitEmojis.fighter + " in tile " + tile.getRepresentationForButtons(game, player) + ".";
                } else if ("2destroyer".equalsIgnoreCase(unitLong)) {
                    AddUnitService.addUnits(event, game.getTileByPosition(planetName), game, player.getColor(), "2 destroyer");
                    successMessage = producedOrPlaced + " 2 " + UnitEmojis.destroyer + " in tile " + tile.getRepresentationForButtons(game, player) + ".";
                } else {
                    AddUnitService.addUnits(event, game.getTileByPosition(planetName), game, player.getColor(), unitID);
                    successMessage = producedOrPlaced + " a " + unitKey.unitEmoji() + " in tile " + tile.getRepresentationForButtons(game, player) + ".";
                    Tile tile2 = game.getTileByPosition(planetName);
                    if (player.hasAbility("cloaked_fleets")) {
                        List<Button> shroadedFleets = new ArrayList<>();
                        shroadedFleets.add(Buttons.green("cloakedFleets_" + tile2.getPosition() + "_" + unitID, "Capture 1 " + Mapper.getUnit(unitID).getName()));
                        shroadedFleets.add(Buttons.red("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(),
                            "You may use your **Cloaked Fleets** ability to capture this produced ship.",
                            shroadedFleets);
                    }
                    if (tile2 != null && !willSkipBuild && player.hasAbility("rally_to_the_cause")
                        && player.getHomeSystemTile() == tile2
                        && !ButtonHelperAbilities.getTilesToRallyToTheCause(game, player).isEmpty()) {
                        String msg = player.getRepresentation()
                            + " due to your **Rally to the Cause** ability, if you just produced a ship in your home system,"
                            + " you may produce up to 2 ships in a system that contains a planet with a trait,"
                            + " but does not contain a legendary planet or another player's units. Press button to resolve";
                        List<Button> buttons2 = new ArrayList<>();
                        buttons2.add(Buttons.green("startRallyToTheCause", "Rally To The Cause"));
                        buttons2.add(Buttons.red("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg,
                            buttons2);

                    }
                }

            }
            CommanderUnlockCheckService.checkPlayer(player, "l1z1x");
            CommanderUnlockCheckService.checkPlayer(player, "cymiae");
            CommanderUnlockCheckService.checkPlayer(player, "kyro");
            CommanderUnlockCheckService.checkPlayer(player, "ghemina");
            CommanderUnlockCheckService.checkPlayer(player, "tnelis");
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), playerRep + " " + successMessage);
        String message2 = player.getRepresentationUnfogged() + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        if (skipbuild.contains("freelancers")) {
            buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "freelancers");
        }
        if (player.hasTechReady("absol_st")) {
            buttons.add(Buttons.red("useTech_absol_st", "Use Sarween Tools"));
        }
        if (player.hasRelic("boon_of_the_cerulean_god")) {
            buttons.add(Buttons.red("useRelic_boon", "Use Boon Of The Cerulean God Relic"));
        }
        if (player.hasUnexhaustedLeader("ghotiagent")) {
            buttons.add(Buttons.red("exhaustAgent_ghotiagent_" + player.getFaction(), "Use Ghoti Agent", FactionEmojis.ghoti));
        }
        if (player.hasUnexhaustedLeader("mortheusagent")) {
            buttons.add(Buttons.red("exhaustAgent_mortheusagent_" + player.getFaction(), "Use Mortheus Agent", FactionEmojis.mortheus));
        }
        Button DoneExhausting;
        if (!buttonID.contains("deleteButtons")) {
            DoneExhausting = Buttons.red("deleteButtons_" + buttonID, "Done Exhausting Planets");
        } else {
            DoneExhausting = Buttons.red("deleteButtons", "Done Exhausting Planets");
        }
        buttons.add(DoneExhausting);
        if (!willSkipBuild) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2,
                buttons);
        } else {
            if (orbitalDrop) {
                List<Button> orbFollowUp = new ArrayList<>();
                if (player.hasUnit("sol_mech") && !ButtonHelper.isLawInPlay(game, "regulations") && ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "mech", true) < 4) {
                    orbFollowUp.add(Buttons.green("orbitalMechDrop_" + planetName, "Pay 3r for Mech?"));
                }
                orbFollowUp.add(Buttons.red("finishComponentAction_spitItOut", "Finish Orbital Drop"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), player.getRepresentation() +
                    ", you may pay 3 resources to drop a mech on the planet too (if applicable)", orbFollowUp);
            }
        }

        CommanderUnlockCheckService.checkPlayer(player, "titans");
        if ("sd".equalsIgnoreCase(unitID)) {
            tile = game.getTileFromPlanet(planetName);
            if (tile != null) {
                AgendaHelper.ministerOfIndustryCheck(player, game, tile, event);
            }
        }
        CommanderUnlockCheckService.checkPlayer(player, "saar");
        CommanderUnlockCheckService.checkPlayer(player, "rohdhna");
        CommanderUnlockCheckService.checkPlayer(player, "cheiran");
        CommanderUnlockCheckService.checkPlayer(player, "celdauri");
        if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game, Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        CommanderUnlockCheckService.checkPlayer(player, "mentak");
        CommanderUnlockCheckService.checkPlayer(player, "l1z1x");
        CommanderUnlockCheckService.checkPlayer(player, "tnelis");
        CommanderUnlockCheckService.checkPlayer(player, "cymiae");
        CommanderUnlockCheckService.checkPlayer(player, "kyro");
        CommanderUnlockCheckService.checkPlayer(player, "ghemina");
        if ("warsun".equalsIgnoreCase(unitLong)) {
            CommanderUnlockCheckService.checkPlayer(player, "muaat");
        }
        CommanderUnlockCheckService.checkPlayer(player, "argent");
        CommanderUnlockCheckService.checkPlayer(player, "naaz");
        CommanderUnlockCheckService.checkPlayer(player, "arborec");

        event.getMessage().delete().queue();
    }

    @ButtonHandler("spaceUnits_")
    public static void spaceLandedUnits(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        String rest = buttonID.replace("spaceUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("-", "").replace("'", "");
        } else {
            unitName = rest;
        }
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = unitName.replace("damaged", "");
        }
        game.setActiveSystem(pos);
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        AddUnitService.addUnits(event, game.getTileByPosition(pos), game, player.getColor(), amount + " " + unitName);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            game.getTileByPosition(pos).addUnitDamage("space", unitKey, amount);
            game.getTileByPosition(pos).removeUnitDamage(planet, unitKey, amount);
        }

        String planetName = ButtonHelper.getUnitHolderFromPlanetName(planet, game).getName();
        game.getTileByPosition(pos).removeUnit(planet, unitKey, amount);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = "damaged " + unitName;
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmojiOrColor() + " undid landing of " + amount + " " + unitName + " on " + planetName + ".");
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    @ButtonHandler("assCannonNDihmohn_")
    public static void resolveAssaultCannonNDihmohnCommander(String buttonID, ButtonInteractionEvent event, Player player, Game game) {
        String cause = buttonID.split("_")[1];
        String pos = buttonID.split("_")[2];
        Player opponent = null;
        String msg;
        Tile tile = game.getTileByPosition(pos);
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            if (FoWHelper.playerHasShipsInSystem(p2, tile) && !player.getAllianceMembers().contains(p2.getFaction())) {
                opponent = p2;
            }
        }
        if (opponent == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No opponent found");
            return;
        }
        List<Button> buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(opponent, game, tile, "spacecombat");
        if (cause.contains("dihmohn")) {
            msg = opponent.getRepresentationUnfogged() + " " + player.getFactionEmoji()
                + " used Clona Bathru, the Dih-Mohn Commander, to generate a hit against you. Please assign it with buttons.";
        } else if (cause.contains("ds")) {
            buttons = getOpposingUnitsToHit(player, game, event, tile, false);
            msg = player.getRepresentation() + " choose which opposing unit to hit";
        } else if (cause.contains("exo")) {
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 dread");
            ButtonHelperFactionSpecific.cabalEatsUnitIfItShould(player, game, player, 1, "dread", event, tile, tile.getSpaceUnitHolder());
            buttons = getOpposingUnitsToHit(player, game, event, tile, true);
            msg = player.getRepresentation() + " choose which opposing unit to destroy";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getRepresentation(false, false) + " has chosen to destroy one of their dreadnoughts in order to choose 2 opposing ships to destroy. This occurs after any retreats. The dread has been removed.");
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);
        } else {
            msg = opponent.getRepresentationUnfogged() + " your opponent used _Assault Cannon_, forcing you to destroy a non-fighter ship. Please assign it with buttons.";
            buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(opponent, game, tile, "assaultcannoncombat");
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), msg, buttons);

    }

    @ButtonHandler("landUnits_")
    public static void landingUnits(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        String rest = buttonID.replace("landUnits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        rest = rest.replace(pos + "_", "");
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].replace(" ", "").toLowerCase().replace("'", "").replace("-", "");
        } else {
            unitName = rest;
        }
        boolean damaged = false;
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = unitName.replace("damaged", "");
            damaged = true;
        }
        boolean doesUnitExist = true;
        Tile tile = game.getTileByPosition(pos);
        game.setActiveSystem(pos);
        UnitHolder space = tile.getSpaceUnitHolder();
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        if (space.getUnitCount(unitKey.getUnitType(), player.getColor()) < amount) {
            doesUnitExist = false;
        }
        if (damaged) {
            if (space.getUnitDamage().get(unitKey) == null || space.getUnitDamage().get(unitKey) < amount) {
                doesUnitExist = false;
            }
        } else {
            int damagedUnits = 0;
            if (space.getUnitDamage().get(unitKey) != null) {
                damagedUnits = space.getUnitDamage().get(unitKey);
            }
            int undamaged = space.getUnitCount(unitKey.getUnitType(), player.getColor()) - damagedUnits;
            if (undamaged < amount) {
                doesUnitExist = false;
            }
        }
        if (!doesUnitExist) {
            List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                "That unit amount did not exist, attempting to regenerate correct landing buttons. Try now");
            MessageHelper.editMessageButtons(event, systemButtons);
            return;
        }

        AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " " + unitName + " " + planet);
        String planetName = ButtonHelper.getUnitHolderFromPlanetName(planet, game).getName();
        if (buttonLabel.toLowerCase().contains("damaged")) {
            game.getTileByPosition(pos).removeUnitDamage("space", unitKey, amount);
            game.getTileByPosition(pos).addUnitDamage(planetName, unitKey, amount);
        }
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = "damaged " + unitName;
        }

        game.getTileByPosition(pos).removeUnit("space", unitKey, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.fogSafeEmoji() + " landed " + amount + " " + unitName + " on " + planetName + ".");
        String oldMessage = event.getMessage().getContentRaw();
        if (space.getUnitCount(UnitType.Infantry, player.getColor()) < 1 && space.getUnitCount(UnitType.Mech, player.getColor()) < 1) {
            oldMessage = "Remember to click \"Done Landing Troops\" if everything has landed correctly.";
        }
        MessageHelper.editMessageWithButtons(event, oldMessage, systemButtons);
    }

    @ButtonHandler("domnaStepOne_")
    public static void offerDomnaStep2Buttons(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unit : tile.getUnitHolders().get("space").getUnits().keySet()) {
            if (unit.getUnitType() == UnitType.Infantry || unit.getUnitType() == UnitType.Mech) {
                continue;
            }
            String unitName = unit.unitName();
            Button validTile = Buttons.green("domnaStepTwo_" + pos + "_" + unitName, "Move 1 " + unit.unitName());
            buttons.add(validTile);
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select the unit you wish to move.",
            buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("domnaStepTwo_")
    public static void offerDomnaStep3Buttons(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String pos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            Tile tile2 = game.getTileByPosition(pos2);
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, game)) {
                buttons.add(Buttons.gray("domnaStepThree_" + pos1 + "_" + unit + "_" + pos2,
                    "Move " + unit + " to " + tile2.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select the tile you wish to move to.", buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("domnaStepThree_")
    public static void resolveDomnaStep3Buttons(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String pos1 = buttonID.split("_")[1];
        Tile tile1 = game.getTileByPosition(pos1);
        String unit = buttonID.split("_")[2];
        String pos2 = buttonID.split("_")[3];
        Tile tile2 = game.getTileByPosition(pos2);
        AddUnitService.addUnits(event, tile2, game, player.getColor(), unit);
        RemoveUnitService.removeUnits(event, tile1, game, player.getColor(), unit);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " moved 1 " + unit + " from "
                + tile1.getRepresentationForButtons(game, player) + " to "
                + tile2.getRepresentationForButtons(game, player) + " with an ability.");
        event.getMessage().delete().queue();
    }

    @ButtonHandler("combatDrones")
    public static void offerCombatDroneButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        int numff;
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }
            numff = unitHolder.getUnitCount(UnitType.Fighter, player.getColor());
            for (int x = 1; x < numff + 1; x++) {
                buttons.add(Buttons.green("combatDroneConvert_" + x, "" + x));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + ", please choose how many fighters you wish to convert to infantry.",
            buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("offerMirvedaCommander")
    public static void offerMirvedaCommanderButtons(ButtonInteractionEvent event, Game game, Player player) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        int numinf;
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            if (unitHolder instanceof Planet) {
                continue;
            }
            numinf = unitHolder.getUnitCount(UnitType.Infantry, player.getColor());
            for (int x = 1; x < numinf + 1; x++) {
                buttons.add(Buttons.green("resolveMirvedaCommander_" + x, "" + x));
            }
        }
        buttons.add(Buttons.red("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentationUnfogged() + ", please choose how many infantry you wish to convert to fighters.",
            buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler("resolveMirvedaCommander_")
    public static void resolvingMirvedaCommander(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        int numff = Integer.parseInt(buttonID.split("_")[1]);
        if (numff > 0) {
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), numff + " infantry");
            AddUnitService.addUnits(event, tile, game, player.getColor(), numff + " fighters");
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        String msg = player.getFactionEmojiOrColor() + " turned " + numff + " infantry into " + numff + " fighter"
            + (numff == 1 ? "" : "s") + " using Assault Machina, the Mirveda commander.";
        event.getMessage().editMessage(msg)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    @ButtonHandler("combatDroneConvert_")
    public static void resolvingCombatDrones(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        int numff = Integer.parseInt(buttonID.split("_")[1]);
        if (numff > 0) {
            RemoveUnitService.removeUnits(event, tile, game, player.getColor(), numff + " fighters");
            AddUnitService.addUnits(event, tile, game, player.getColor(), numff + " infantry");
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        String msg = player.getFactionEmojiOrColor() + " turned " + numff + " fighter" + (numff == 1 ? "" : "s") + " into "
            + numff + " infantry using their **Combat Drones** ability.";
        event.getMessage().editMessage(msg)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    @ButtonHandler("assignHits_")
    public static void assignHits(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String buttonLabel = event.getButton().getLabel();
        String rest = buttonID.replace("assignHits_", "");
        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");
        Player cabal = Helper.getPlayerFromAbility(game, "devour");
        Player mentakHero = game
            .getPlayerFromColorOrFaction(game.getStoredValue("mentakHero"));
        String assignType = "combat";
        if (!game.getStoredValue(player.getFaction() + "latestAssignHits").isEmpty()) {
            assignType = game.getStoredValue(player.getFaction() + "latestAssignHits");
        }
        boolean heroism = player.hasAbility("heroism");
        if (!assignType.toLowerCase().contains("combat")) {
            heroism = false;
            if (cabal != null && !ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile)) {
                cabal = null;
            }
        }
        if (rest.contains("All")) {
            String cID = Mapper.getColorID(player.getColor());
            for (Map.Entry<String, UnitHolder> entry : tile.getUnitHolders().entrySet()) {
                UnitHolder unitHolder = entry.getValue();
                Map<UnitKey, Integer> units1 = unitHolder.getUnits();
                Map<UnitKey, Integer> units = new HashMap<>(units1);
                if (unitHolder instanceof Planet) {
                    if (!rest.contains("AllShips")) {

                        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                            UnitKey unitKey = unitEntry.getKey();
                            if (!unitKey.getColorID().equals(cID))
                                continue;

                            String unitName = unitKey.unitName();
                            int amount = unitEntry.getValue();
                            Player cabalMechOwner = Helper.getPlayerFromUnit(game, "cabal_mech");
                            boolean cabalMech = cabalMechOwner != null
                                && unitHolder.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0
                                && unitName.toLowerCase().contains("infantry")
                                && !ButtonHelper.isLawInPlay(game, "articles_war");

                            var unit = new ParsedUnit(unitKey, unitEntry.getValue(), unitHolder.getName());
                            RemoveUnitService.removeUnit(event, game.getTileByPosition(pos), game, unit);
                            if (cabal != null && FoWHelper.playerHasUnitsOnPlanet(cabal, tile, unitHolder.getName())
                                && ((!cabal.getFaction().equalsIgnoreCase(player.getFaction())
                                    || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile)
                                    || cabalMech)
                                    || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal,
                                    unitEntry.getValue(), unitName, event);
                            }
                            if (player.hasAbility("heroism") && unitName.toLowerCase().contains("infantry")) {
                                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, unitEntry.getValue(), unitName, event);
                            }
                            if (mentakHero != null) {
                                ButtonHelperFactionSpecific.mentakHeroProducesUnit(player, game, mentakHero,
                                    unitEntry.getValue(), unitName, event, tile);
                            }
                            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))
                                && unitName.toLowerCase().contains("inf")) {
                                ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
                            }
                            if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
                                ButtonHelper.resolveInfantryDeath(player, amount);
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasTech("sar")) {
                                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                                    + " you gained " + amount + " trade good (" + player.getTg() + "->" + (player.getTg() + amount)
                                    + ") from _Self-Assembly Routines_ because of " + amount + " of your mechs dying."
                                    + " This is not an optional gain" + (amount > 1 ? ", and happens 1 trade good at a time" : "") + ".");
                                for (int x = 0; x < amount; x++) {
                                    player.setTg(player.getTg() + 1);
                                    ButtonHelperAbilities.pillageCheck(player, game);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, 1);
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasUnit("mykomentori_mech")) {
                                for (int x = 0; x < amount; x++) {
                                    ButtonHelper.rollMykoMechRevival(game, player);
                                }
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasUnit("cheiran_mech")) {
                                AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " infantry " + unitHolder.getName());
                                String msg = "> Added " + amount + " infantry to the planet following a Nauplius (Cheiran mech) being destroyed.";
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                            }
                        }
                    }
                } else {
                    for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                        UnitKey unitKey = unitEntry.getKey();
                        if (!unitKey.getColorID().equals(cID))
                            continue;
                        String unitName = unitKey.unitName();
                        int amount = unitEntry.getValue();

                        var unit = new ParsedUnit(unitKey, amount, Constants.SPACE);
                        RemoveUnitService.removeUnit(event, game.getTileByPosition(pos), game, unit);
                        if (cabal != null && FoWHelper.playerHasShipsInSystem(cabal, tile)
                            && (!cabal.getFaction().equalsIgnoreCase(player.getFaction())
                                || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, amount, unitName,
                                event);
                        }
                        if (mentakHero != null) {
                            ButtonHelperFactionSpecific.mentakHeroProducesUnit(player, game, mentakHero, amount,
                                unitName, event, tile);
                        }
                    }
                }
            }
            String message2 = player.getFactionEmojiOrColor() + " Removed all units";
            if (rest.contains("AllShips")) {
                message2 = player.getFactionEmojiOrColor() + " Removed all units in space area";
            }
            String message = event.getMessage().getContentRaw();
            assignType = "combat";
            if (!game.getStoredValue(player.getFaction() + "latestAssignHits").isEmpty()) {
                assignType = game.getStoredValue(player.getFaction() + "latestAssignHits");
            }
            List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignType);
            event.getMessage().editMessage(message)
                .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

            return;
        }
        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        String planetName;
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }

        var unit = new ParsedUnit(unitKey, amount, planetName);
        RemoveUnitService.removeUnit(event, game.getTileByPosition(pos), game, unit, buttonLabel.toLowerCase().contains("damaged"));

        if ("".equalsIgnoreCase(planet)) {
            if (cabal != null
                && (!cabal.getFaction().equalsIgnoreCase(player.getFaction())
                    || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))
                && FoWHelper.playerHasShipsInSystem(cabal, tile)) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, amount, unitName, event);
            }
            if (mentakHero != null) {
                ButtonHelperFactionSpecific.mentakHeroProducesUnit(player, game, mentakHero, amount, unitName,
                    event, tile);
            }
            if (heroism && unitKey.getUnitType() == UnitType.Fighter) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, amount, unitName, event);
            }
        } else {
            boolean cabalMech = cabal != null
                && game.getTileByPosition(pos).getUnitHolders().get(planetName).getUnitCount(UnitType.Mech,
                    cabal.getColor()) > 0
                && cabal.hasUnit("cabal_mech")
                && unitName.toLowerCase().contains("infantry") && !ButtonHelper.isLawInPlay(game, "articles_war");
            if (cabal != null
                && (!cabal.getFaction().equalsIgnoreCase(player.getFaction())
                    || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile) || cabalMech)
                && (FoWHelper.playerHasUnitsOnPlanet(cabal, tile, planetName) || ButtonHelper.doesPlayerHaveFSHere("cabal_flagship", cabal, tile))) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, cabal, amount, unitName, event);
            }
            if (unitKey.getUnitType() == UnitType.Mech && player.hasTech("sar")) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentation()
                    + " you gained " + amount + " trade good (" + player.getTg() + "->" + (player.getTg() + amount)
                    + ") from _Self-Assembly Routines_ because of " + amount + " of your mechs dying."
                    + " This is not an optional gain" + (amount > 1 ? ", and happens 1 trade good at a time" : "") + ".");
                for (int x = 0; x < amount; x++) {
                    player.setTg(player.getTg() + 1);
                    ButtonHelperAbilities.pillageCheck(player, game);
                }
                ButtonHelperAgents.resolveArtunoCheck(player, 1);
            }
            if (unitKey.getUnitType() == UnitType.Mech && player.hasUnit("mykomentori_mech")) {
                for (int x = 0; x < amount; x++) {
                    ButtonHelper.rollMykoMechRevival(game, player);
                }
            }
            if (heroism && unitKey.getUnitType() == UnitType.Infantry) {
                ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, amount, unitName, event);
            }
            if (unitKey.getUnitType() == UnitType.Mech && player.hasUnit("cheiran_mech") && !planetName.equalsIgnoreCase("space")) {
                AddUnitService.addUnits(event, tile, game, player.getColor(), amount + " infantry " + planetName);
                String msg = "> Added " + amount + " infantry to the planet following a Nauplius (Cheiran mech) being destroyed.";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            }
            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))
                && unitName.toLowerCase().contains("inf")) {
                ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
            }
            if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
                ButtonHelper.resolveInfantryDeath(player, amount);
            }
        }

        String message = event.getMessage().getContentRaw();
        String message2 = player.getRepresentationNoPing() + " removed " + amount + " " + unitName + " from " + planetName + " in tile "
            + tile.getRepresentationForButtons(game, player) + ".";
        assignType = "combat";
        if (!game.getStoredValue(player.getFaction() + "latestAssignHits").isEmpty()) {
            assignType = game.getStoredValue(player.getFaction() + "latestAssignHits");
        }
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignType);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

    }

    @ButtonHandler("repairDamage_")
    public static void repairDamage(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String rest;
        rest = buttonID.replace("repairDamage_", "");

        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");

        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        String planetName;
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }
        tile.removeUnitDamage(planetName, unitKey, amount);
        String message = event.getMessage().getContentRaw();
        String message2 = player.getFactionEmojiOrColor() + " repaired " + amount + " " + unitName + " from " + planetName + " in tile "
            + tile.getRepresentationForButtons(game, player) + ".";
        List<Button> systemButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game, tile);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
    }

    @ButtonHandler("assignDamage_")
    public static void assignDamage(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String rest;
        rest = buttonID.replace("assignDamage_", "");

        String pos = rest.substring(0, rest.indexOf("_"));
        Tile tile = game.getTileByPosition(pos);
        rest = rest.replace(pos + "_", "");

        int amount = Integer.parseInt(rest.charAt(0) + "");
        rest = rest.substring(1);
        String unitName;
        String planet = "";
        if (rest.contains("_")) {
            unitName = rest.split("_")[0];
            planet = rest.split("_")[1].toLowerCase().replace(" ", "");
        } else {
            unitName = rest;
        }
        String planetName;
        unitName = unitName.replace("damaged", "");
        planet = planet.replace("damaged", "");
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        if ("".equalsIgnoreCase(planet)) {
            planetName = "space";
        } else {
            planetName = planet.replace("'", "");
            planetName = AliasHandler.resolvePlanet(planetName);
        }
        tile.addUnitDamage(planetName, unitKey, amount);
        String message = event.getMessage().getContentRaw();
        String message2 = player.getFactionEmojiOrColor() + " sustained " + amount + " " + unitName + " from " + planetName + " in tile "
            + tile.getRepresentationForButtons(game, player) + ".";

        if (player.hasTech("nes")) {
            message2 += ". These sustains cancel 2 hits due to _Non-Euclidean Shielding_.";
        }
        String assignType = "combat";
        if (!game.getStoredValue(player.getFaction() + "latestAssignHits").isEmpty()) {
            assignType = game.getStoredValue(player.getFaction() + "latestAssignHits");
        }
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignType);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
        for (int x = 0; x < amount; x++) {
            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
        }
    }

    private static boolean isNomadMechApplicable(Player player, boolean noMechPowers, UnitKey unitKey) {
        return unitKey.unitName().equalsIgnoreCase("mech")
            && player.hasUnit("nomad_mech")
            && !noMechPowers;
    }

    public static List<Button> getContractualObligationsButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (Player p : game.getRealPlayers()) {
            if (game.isFowMode()) {
                buttons.add(Buttons.gray("resolveContractual_" + p.getFaction(), p.getColor()));
            } else {
                Button button = Buttons.gray("resolveContractual_" + p.getFaction(), " ");
                buttons.add(button.withEmoji(Emoji.fromFormatted(p.getFactionEmoji())));
            }
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        return buttons;
    }

    @ButtonHandler("resolveContractual_")
    public static void resolveContractualObligations(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        String targetFaction = buttonID.replace("resolveContractual_", "");
        Player targetPlayer = game.getPlayerFromColorOrFaction(targetFaction);

        List<Button> buttons = new ArrayList<>();
        List<Tile> tiles = new ArrayList<>(ButtonHelper.getTilesOfPlayersSpecificUnits(game, targetPlayer,
            Units.UnitType.Spacedock, Units.UnitType.PlenaryOrbital, Units.UnitType.Warsun));
        for (Tile tile : tiles) {
            Button tileButton = Buttons.green("produceOneUnitInTile_" + tile.getPosition() + "_sling",
                tile.getRepresentationForButtons(game, targetPlayer));
            buttons.add(tileButton);
        }
        MessageHelper.sendMessageToChannelWithButtons(targetPlayer.getCorrectChannel(), targetPlayer.getRepresentationUnfogged()
            + " " + (game.isFowMode() ? "Someone" : player.getRepresentationNoPing())
            + " is using _Contractual Obligations_ to force you to produce 1 ship in a system that contains 1 or more of your space docks or war suns.\n"
            + "Select which tile you would like to produce 1 ship in.", buttons);

        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Used _Contractual Obligations_ to force "
                + targetPlayer.getRepresentationNoPing() + " to produce 1 ship.");
        }
        event.getMessage().delete().queue();
    }

}
