package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.commands.combat.CombatRoll;
import ti4.commands.combat.StartCombat;
import ti4.commands.tokens.AddCC;
import ti4.commands.units.AddUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveUnits;
import ti4.generator.GenerateTile;
import ti4.generator.Mapper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.StrategyCardModel;
import ti4.model.UnitModel;

public class ButtonHelperModifyUnits {

    public static int getNumberOfSustainableUnits(Player player, Game game, UnitHolder unitHolder) {
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int sustains = 0;
        Player mentak = Helper.getPlayerFromUnit(game, "mentak_mech");
        if (mentak != null && mentak != player && unitHolder.getUnitCount(UnitType.Mech, mentak.getColor()) > 0) {
            return 0;
        }
        Player mentakFS = Helper.getPlayerFromUnit(game, "mentak_flagship");
        if (mentakFS != null && mentakFS != player
            && unitHolder.getUnitCount(UnitType.Flagship, mentakFS.getColor()) > 0) {
            return 0;
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
            UnitKey unitKey = unitEntry.getKey();
            int damagedUnits = 0;
            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }
            int totalUnits = unitEntry.getValue() - damagedUnits;
            if (unitModel.getSustainDamage()) {
                sustains = sustains + totalUnits;
            }
        }
        return sustains;
    }

    public static void autoAssignAntiFighterBarrageHits(Player player, Game game, String pos, int hits,
        ButtonInteractionEvent event) {
        Tile tile = game.getTileByPosition(pos);
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        String msg = player.getFactionEmoji() + " assigned " + (hits == 1 ? "the hit" : "hits") + " in the following way:\n";
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, game, unitHolder);
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
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);
                if (unitName.equalsIgnoreCase("fighter") && min > 0) {
                    msg = msg + "> Destroyed " + min + " " + Emojis.fighter + "\n";
                    hits = hits - min;
                    new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(),
                        false, game);

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
                //String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = Math.min(totalUnits, hits);
                if (unitModel.getSustainDamage() && min > 0 && unitModel.getIsShip()) {
                    msg = msg + "> Made " + min + " " + unitModel.getUnitEmoji() + " sustained\n";
                    hits = hits - min;
                    tile.addUnitDamage("space", unitKey, min);
                }
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        event.getMessage().delete().queue();
    }

    public static void autoMateGroundCombat(Player p1, Player p2, String planet, Game game, ButtonInteractionEvent event) {
        boolean haveGroundForces = true;
        Tile tile = game.getTileFromPlanet(planet);
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        int count = 0;
        while (haveGroundForces) {
            int hitP1 = new CombatRoll().secondHalfOfCombatRoll(p1, game, event, tile, planet, CombatRollType.combatround, true);
            int hitP2 = new CombatRoll().secondHalfOfCombatRoll(p2, game, event, tile, planet, CombatRollType.combatround, true);

            if (p1.hasTech("vpw") && hitP2 > 0) {
                hitP1++;
            }
            if (p2.hasTech("vpw") && hitP1 > 0) {
                hitP2++;
            }
            int p1SardakkMechHits = 0;
            int p2SardakkMechHits = 0;
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
        FileUpload systemWithContext = GenerateTile.getInstance().saveImage(game, 0, pos, event);
        MessageHelper.sendMessageWithFile(event.getMessageChannel(), systemWithContext, "Picture of system", false);
        List<Button> buttons = StartCombat.getGeneralCombatButtons(game, pos, p1, p2, "ground", event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", buttons);
    }

    public static String getDamagedUnits(Player player, UnitHolder unitHolder, Game game) {
        String duraniumMsg = "";
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
            if (!player.unitBelongsToPlayer(unitEntry.getKey()))
                continue;
            UnitModel unitModel = player.getUnitFromUnitKey(unitEntry.getKey());
            if (unitModel == null)
                continue;
            UnitKey unitKey = unitEntry.getKey();
            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
            int damagedUnits = 0;
            if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                damagedUnits = unitHolder.getUnitDamage().get(unitKey);
            }
            if (damagedUnits > 0) {
                duraniumMsg = duraniumMsg + unitName;
            }
        }

        return duraniumMsg;
    }

    private static boolean isCabalMechActive(Game game, UnitHolder unitHolder) {
        Player cabalMechOwner = Helper.getPlayerFromUnit(game, "cabal_mech");
        return cabalMechOwner != null && unitHolder.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0 && !ButtonHelper.isLawInPlay(game, "articles_war");
    }

    private static int calculateMinHits(Player player, int hits, int totalUnits) {
        return player.hasTech("nes") ? Math.min(totalUnits, (hits + 1) / 2) : Math.min(totalUnits, hits);
    }

    public static int autoAssignGroundCombatHits(Player player, Game game, String planet, int hits,
        ButtonInteractionEvent event) {
        UnitHolder unitHolder = ButtonHelper.getUnitHolderFromPlanetName(planet, game);
        StringBuilder msg = new StringBuilder(player.getFactionEmoji() + " assigned " + (hits == 1 ? "the hit" : "hits") + " in the following way:\n");
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, game, unitHolder);
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

                    if (player.hasUnit("sardakk_mech")) {
                        msg.append("> Valkyrie Exoskeleton mech generated ").append(min).append(" hit").append(min == 1 ? "" : "s").append(" \n");
                        sardakkMechHits = min;
                    }
                }
            }
        }
        Map<String, String> unitTypes = Map.of(
            "fighter", Emojis.fighter,
            "infantry", Emojis.infantry,
            "pds", Emojis.pds);

        for (String unitType : unitTypes.keySet()) {
            if (shouldProcessUnit(player, unitType, unitHolder, hits)) {
                for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                    if (!player.unitBelongsToPlayer(unitEntry.getKey())) continue;

                    UnitKey unitKey = unitEntry.getKey();
                    UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                    if (unitModel == null) continue;

                    String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                    int totalUnits = unitEntry.getValue();
                    int min = Math.min(totalUnits, hits);

                    if (unitName.equalsIgnoreCase(unitType) && min > 0) {
                        msg.append("> Destroyed ").append(min).append(" ").append(unitTypes.get(unitType)).append("\n");
                        hits -= min;
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, game);

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

                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int min = Math.min(totalUnits, hits);

                if (unitName.equalsIgnoreCase("mech") && min > 0) {
                    msg.append("> Destroyed ").append(min).append(" ").append(unitName).append(min == 1 ? "" : "s").append("\n");
                    hits -= min;
                    if (min + 1 > totalUnits) {
                        duraniumMsg = duraniumMsg.replace(unitName, "");
                    }
                    new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey, player.getColor(), false, game);

                    handleCabalConsumption(cabal, player, tile, planet, min, unitName, event, game, cabalMech);
                    msg.append(handleMechSpecificTriggers(player, min, game, msg, unitHolder, tile, event));
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
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int damagedUnits = 0;
                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }
                if (!usedDuraniumAlready && damagedUnits > 0 && duraniumMsg.contains(unitName)) {
                    msg.append("> Repaired 1 " + unitModel.getUnitEmoji() + " due to Duranium Armor\n");
                    tile.removeUnitDamage(unitHolder.getName(), unitKey, 1);
                    usedDuraniumAlready = true;
                }
            }
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg.toString());
        if (!doesPlayerHaveGfOnPlanet(unitHolder, player) && (unitHolder.getUnitCount(UnitType.Pds, player.getColor()) > 0
            || unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)) {
            String msg2 = player.getRepresentation()
                + " you should remove structures if your opponent is not playing Infiltrate or using Assimilate. Use buttons to resolve.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(
                Button.danger(player.getFinsFactionCheckerPrefix() + "removeAllStructures_" + unitHolder.getName(),
                    "Remove Structures"));
            buttons.add(Button.secondary("deleteButtons", "Don't Remove Structures"));
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2, buttons);
        }
        if (event.getMessage() != null) {
            event.getMessage().delete().queue();
        }
        return sardakkMechHits;
    }

    private static boolean shouldProcessUnit(Player player, String unitType, UnitHolder units, int hits) {
        switch (unitType) {
            case "fighter":
                return hits > 0 && player.hasUnit("naalu_flagship") && units.getUnitCount(UnitType.Fighter, player.getColor()) > 0;
            case "infantry":
                return hits > 0;
            case "pds":
                return hits > 0 && (player.hasUnit("titans_pds") || player.hasUnit("titans_pds2")) && units.getUnitCount(UnitType.Pds, player.getColor()) > 0;
            default:
                return false;
        }
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
            ButtonHelper.resolveInfantryDeath(game, player, min);
        }
    }

    private static String handleMechSpecificTriggers(Player player, int min, Game game, StringBuilder msg, UnitHolder unitHolder, Tile tile, GenericInteractionCreateEvent event) {
        if (player.hasTech("sar")) {
            for (int x = 0; x < min; x++) {
                player.setTg(player.getTg() + 1);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + " gained 1TG from a mech dying while owning Self-Assembly Routines.");
                ButtonHelperAbilities.pillageCheck(player, game);
            }
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        }
        if (player.hasUnit("mykomentori_mech")) {
            for (int x = 0; x < min; x++) {
                ButtonHelper.rollMykoMechRevival(game, player);
            }
        }
        if (player.hasUnit("cheiran_mech")) {
            new AddUnits().unitParsing(event, player.getColor(), tile, min + " infantry " + unitHolder.getName(), game);
            msg.append("> Added ").append(min).append(" infantry to the planet due to Cheiran mech trigger\n");
        }
        return msg.toString();
    }

    public static boolean doesPlayerHaveGfOnPlanet(UnitHolder unitHolder, Player player) {
        return !((unitHolder.getUnitCount(UnitType.Pds, player.getColor()) < 1 || (!player.hasUnit("titans_pds") && !player.hasUnit("titans_pds2"))) && unitHolder.getUnitCount(UnitType.Mech, player.getColor()) < 1 && unitHolder.getUnitCount(UnitType.Infantry, player.getColor()) < 1);
    }

    public static String autoAssignSpaceCombatHits(Player player, Game game, Tile tile, int hits,
        GenericInteractionCreateEvent event, boolean justSummarizing) {

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
            for (int x = 0; x < min; x++) {
                player.setTg(player.getTg() + 1);
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                    player.getRepresentation() + " you gained 1TG (" + (player.getTg() - 1) + "->" + player.getTg()
                        + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                ButtonHelperAbilities.pillageCheck(player, game);
            }
            ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
        }
    }

    private static void handleLetnevCommanderCheck(Player player, Game game, GenericInteractionCreateEvent event, int min) {
        for (int x = 0; x < min; x++) {
            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
        }
    }

    public static String autoAssignSpaceCombatHits(Player player, Game game, Tile tile, int hits,
        GenericInteractionCreateEvent event, boolean justSummarizing, boolean spaceCannonOffence) {
        UnitHolder unitHolder = tile.getUnitHolders().get("space");
        String msg = player.getFactionEmoji() + " assigned " + (hits == 1 ? "the hit" : "hits") + " in the following way:\n";
        if (justSummarizing) {
            msg = player.getFactionEmoji() + " would assign " + (hits == 1 ? "the hit" : "hits") + " in the following way:\n";
        }
        Map<UnitKey, Integer> units = new HashMap<>(unitHolder.getUnits());
        int numSustains = getNumberOfSustainableUnits(player, game, unitHolder);
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
        boolean usedDuraniumAlready = !player.hasTech("da") || spaceCannonOffence;
        StringBuilder duraniumMsgBuilder = new StringBuilder();

        if (!usedDuraniumAlready) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null) continue;

                // Get damaged units count  
                int damagedUnits = (unitHolder.getUnitDamage() != null) ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;

                // If the unit is damaged, add its name to duranium message  
                if (damagedUnits > 0) {
                    duraniumMsgBuilder.append(ButtonHelper.getUnitName(unitKey.asyncID()));
                    usedDuraniumAlready = false; // Mark that we can use Duranium  
                }
            }
        }

        // Process sustains if necessary  
        if (numSustains > 0) {
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.getSustainDamage() || (!unitModel.getIsShip() && !isNomadMechApplicable(player, noMechPowers, unitKey))) continue;
                if (unitModel.getBaseType().equalsIgnoreCase("warsun") && ButtonHelper.isLawInPlay(game, "schematics")) continue;

                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                if (!unitName.equalsIgnoreCase("dreadnought") || !player.hasUpgradedUnit("dn2")) continue;

                // Get damaged units count  
                int damagedUnits = (unitHolder.getUnitDamage() != null) ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;
                int totalUnits = unitEntry.getValue() - damagedUnits;
                int min = (player.hasTech("nes")) ? Math.min(totalUnits, (hits + 1) / 2) : Math.min(totalUnits, hits);

                if (min > 0) {
                    hits -= min * (player.hasTech("nes") ? 2 : 1); // Adjust hits based on technology  

                    // Message building based on condition  
                    if (!justSummarizing) {
                        msg += "> Sustained " + min + " " + unitModel.getUnitEmoji() + "\n";
                        tile.addUnitDamage("space", unitKey, min);
                        for (int x = 0; x < min; x++) {
                            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
                        }
                    } else {
                        msg += "> Would sustain " + min + " " + unitModel.getUnitEmoji() + "\n";
                    }
                }
            }
            // Second loop for sustaining damage  
            for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey)) continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null || !unitModel.getSustainDamage() || (!unitModel.getIsShip() && !isNomadMechApplicable(player, noMechPowers, unitKey))) continue;
                if (unitModel.getBaseType().equalsIgnoreCase("warsun") && ButtonHelper.isLawInPlay(game, "schematics")) continue;
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
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

                    if (!justSummarizing) {
                        msg += "> Sustained " + min + " " + unitModel.getUnitEmoji() + "\n";
                        tile.addUnitDamage("space", unitKey, min);
                        for (int x = 0; x < min; x++) {
                            ButtonHelperCommanders.resolveLetnevCommanderCheck(player, game, event);
                        }
                    } else {
                        msg += "> Would sustain " + min + " " + unitModel.getUnitEmoji() + "\n";
                    }
                }
            }
        }
        List<String> assignHitOrder = new ArrayList<String>(List.of("fighter", "destroyer", "cruiser", "remainingSustains", "nraShenanigans", "dreadnought", "carrier", "flagship", "warsun"));
        if (spaceCannonOffence) {
            assignHitOrder = new ArrayList<String>(List.of("fighter", "destroyer", "cruiser", "remainingSustains", "dreadnought", "carrier", "flagship", "warsun"));
            for (Player p2 : game.getRealPlayers()) {
                if (p2 == player) {
                    continue;
                }
                if (!game.getStoredValue(p2.getFaction() + "graviton").isEmpty()) {
                    assignHitOrder = new ArrayList<String>(List.of("destroyer", "cruiser", "remainingSustains", "dreadnought", "carrier", "flagship", "warsun", "fighter"));
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

                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int damagedUnits = unitHolder.getUnitDamage() != null ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;
                int effectiveUnits = totalUnits - damagedUnits;
                int min = Math.min(effectiveUnits, hits);
                if (isNraShenanigans && unitName.equalsIgnoreCase("mech") && min > 0) {
                    hits -= min;
                    if (!justSummarizing) {
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey,
                            player.getColor(), false, game);
                        handleCabalEatsUnit(cabal, player, unitName, min, event, tile, game);
                        msg += "> Destroyed " + min + " " + unitModel.getUnitEmoji() + "\n";
                        handleSelfAssemblyRoutines(player, min, game);
                    } else {
                        msg += "> Would destroy " + min + " " + unitModel.getUnitEmoji() + "\n";
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
                        continue; // Skip to the next unit if not sustaining  
                    }
                    hits -= min;
                    if (player.hasTech("nes")) hits -= min;
                    if (!justSummarizing) {
                        tile.addUnitDamage("space", unitKey, min);
                        handleLetnevCommanderCheck(player, game, event, min);
                    }
                    msg += "> Sustained " + min + " " + unitModel.getUnitEmoji() + "\n";
                    continue; // Skip to the next unit  
                }

                // Handle general case of destroying units  
                if (unitName.equalsIgnoreCase(thingToHit) && min > 0) {
                    hits -= min;
                    if (!justSummarizing) {
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey,
                            player.getColor(), false, game);
                        handleCabalEatsUnit(cabal, player, unitName, min, event, tile, game);
                        if (player.hasAbility("heroism") && unitModel.getBaseType().equalsIgnoreCase("fighter")) {
                            ButtonHelperFactionSpecific.cabalEatsUnit(player, game, player, min, unitName, event);
                        }
                        msg += "> Destroyed " + min + " " + unitModel.getUnitEmoji() + "\n";
                        if (mentakHero != null) {
                            ButtonHelperFactionSpecific.mentakHeroProducesUnit(player, game, mentakHero, min,
                                unitName, event, tile);
                        }
                    } else {
                        msg += "> Would destroy " + min + " " + unitModel.getUnitEmoji() + "\n";
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

                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());

                if ((unitName.equalsIgnoreCase("mech") || unitName.equalsIgnoreCase("infantry")) &&
                    !(ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, tile) ||
                        unitHolder.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)) {

                    int min = unitEntry.getValue();
                    if (!justSummarizing) {
                        new RemoveUnits().removeStuff(event, tile, min, unitHolder.getName(), unitKey,
                            player.getColor(), false, game);
                        msg += "> Removed " + min + " " + unitModel.getUnitEmoji() + "\n";
                    } else {
                        msg += "> Would remove " + min + " " + unitModel.getUnitEmoji() + "\n";
                    }
                }
            }
        }

        // Repair units with Duranium Armor if not already used  
        if (!usedDuraniumAlready) {
            for (Map.Entry<UnitKey, Integer> unitEntry : new HashMap<>(unitHolder.getUnits()).entrySet()) {
                UnitKey unitKey = unitEntry.getKey();
                if (!player.unitBelongsToPlayer(unitKey) || unitEntry.getValue() <= 0)
                    continue;

                UnitModel unitModel = player.getUnitFromUnitKey(unitKey);
                if (unitModel == null)
                    continue;

                int damagedUnits = unitHolder.getUnitDamage() != null ? unitHolder.getUnitDamage().getOrDefault(unitKey, 0) : 0;
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());

                if (damagedUnits > 0 && duraniumMsgBuilder.toString().contains(unitName)) {
                    if (!justSummarizing) {
                        msg += "> Repaired 1 " + unitModel.getUnitEmoji() + " due to Duranium Armor\n";
                        tile.removeUnitDamage("space", unitKey, 1);
                    } else {
                        msg += "> Would repair 1 " + unitModel.getUnitEmoji() + " due to Duranium Armor\n";
                    }
                    usedDuraniumAlready = true;
                }
            }
        }
        if (!justSummarizing && event instanceof ButtonInteractionEvent bevent) {
            bevent.getMessage().delete().queue();
        }
        return msg;

    }

    public static List<Button> getRemoveThisTypeOfUnitButton(Player player, Game game, String unit) {
        List<Button> buttons = new ArrayList<>();
        UnitType type = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID()).getUnitType();
        for (Tile tile : ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, type)) {
            for (UnitHolder uH : tile.getUnitHolders().values()) {
                if (uH.getUnitCount(type, player.getColor()) > 0) {
                    buttons
                        .add(Button.danger(
                            "removeThisTypeOfUnit_" + type.humanReadableName() + "_" + tile.getPosition() + "_"
                                + uH.getName(),
                            type.humanReadableName() + " from " +
                                tile.getRepresentation() + " in " + uH.getName()));
                }
            }
        }
        buttons.add(Button.secondary("deleteButtons", "Done Resolving"));
        return buttons;
    }

    public static void removeThisTypeOfUnit(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String unit = buttonID.split("_")[1].toLowerCase().replace(" ", "").replace("'", "");
        String tilePos = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(tilePos);
        String unitH = buttonID.split("_")[3];
        UnitHolder uH = tile.getUnitHolders().get(unitH);

        new RemoveUnits().unitParsing(event, player.getColor(), tile, "1 " + unit + " " + unitH.replace("space", ""),
            game);
        if (uH.getUnitCount(Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID()).getUnitType(),
            player.getColor()) < 1) {
            ButtonHelper.deleteTheOneButton(event);
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " removed 1 " + unit + " from tile "
                + tile.getRepresentationForButtons(game, player) + " location: " + unitH);
    }

    public static void infiltratePlanet(Player player, Game game, UnitHolder uH, ButtonInteractionEvent event) {
        int sdAmount = 0;
        int pdsAmount = 0;
        for (Player p2 : game.getRealPlayers()) {
            if (p2 == player) {
                continue;
            }
            sdAmount = uH.getUnitCount(UnitType.CabalSpacedock, p2.getColor()) + sdAmount
                + uH.getUnitCount(UnitType.Spacedock, p2.getColor());
            if (uH.getUnitCount(UnitType.Spacedock, p2.getColor()) > 0) {
                new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(uH.getName()),
                    sdAmount + " sd " + uH.getName(), game);
                new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(uH.getName()),
                    sdAmount + " csd " + uH.getName(), game);
            }
            pdsAmount = uH.getUnitCount(UnitType.Pds, p2.getColor()) + pdsAmount;
            if (uH.getUnitCount(UnitType.Pds, p2.getColor()) > 0) {
                new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(uH.getName()),
                    pdsAmount + " pds " + uH.getName(), game);
            }
        }
        if (pdsAmount > 0) {
            if (player.hasUnit("mirveda_pds") || player.hasUnit("mirveda_pds2")) {
                new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(uH.getName()),
                    pdsAmount + " pds", game);
            } else {
                new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(uH.getName()),
                    pdsAmount + " pds " + uH.getName(), game);
            }
        }
        if (sdAmount > 0) {
            if (player.hasUnit("saar_spacedock") || player.hasUnit("saar_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(uH.getName()),
                    sdAmount + " sd", game);
            } else {
                new AddUnits().unitParsing(event, player.getColor(), game.getTileFromPlanet(uH.getName()),
                    sdAmount + " sd " + uH.getName(), game);
            }
        }
        MessageHelper.sendMessageToChannel(event.getChannel(),
            ButtonHelper.getIdentOrColor(player, game) + " replaced " + pdsAmount + " PDS and " + sdAmount
                + " space dock" + (sdAmount == 1 ? "" : "s") + " on "
                + Helper.getPlanetRepresentation(uH.getName(), game) + " with their own units");

    }

    public static List<Button> getRetreatSystemButtons(Player player, Game game, String pos1, boolean skilled) {
        String finChecker = "FFCC_" + player.getFaction() + "_";
        List<Button> buttons = new ArrayList<>();
        String skilledS = "";
        if (skilled) {
            skilledS = "_skilled";
        }
        HashSet<String> adjTiles = new HashSet<>();
        adjTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(game, pos1, player, false));
        if (game.playerHasLeaderUnlockedOrAlliance(player, "nokarcommander")) {
            Tile hs = player.getHomeSystemTile();
            if (hs != null) {
                adjTiles.addAll(FoWHelper.getAdjacentTilesAndNotThisTile(game, hs.getPosition(), player, false));
            }
        }
        List<String> checked = new ArrayList<>();
        for (String pos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false)) {
            Tile tile = game.getTileByPosition(pos2);
            if (pos1.equalsIgnoreCase(pos2) || checked.contains(pos2) || (!Mapper.getFrontierTileIds().contains(tile.getTileID()) && tile.getPlanetUnitHolders().size() == 0)) {
                continue;
            }
            if ((tile.isAsteroidField() && !player.getTechs().contains("amd")) || (tile.isSupernova() && !player.getTechs().contains("mr"))) {
                continue;
            }
            checked.add(pos2);
            Tile tile2 = game.getTileByPosition(pos2);
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, game)) {
                if (!FoWHelper.otherPlayersHaveUnitsInSystem(player, tile2, game) || skilled
                    || FoWHelper.playerHasShipsInSystem(player, tile2)) {
                    if (FoWHelper.playerIsInSystem(game, tile2, player) || player.hasTech("det")
                        || player.hasTech("absol_det") || skilled) {
                        buttons.add(Button.secondary(finChecker + "retreatUnitsFrom_" + pos1 + "_" + pos2 + skilledS,
                            "Retreat to " + tile2.getRepresentationForButtons(game, player)));
                    }
                }
            }

        }
        return buttons;
    }

    public static List<Button> getRetreatingGroundTroopsButtons(Player player, Game game,
        ButtonInteractionEvent event, String pos1, String pos2) {
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
                    Button validTile2 = Button
                        .success(
                            finChecker + "retreatGroundUnits_" + pos1 + "_" + pos2 + "_" + x + "infantry_"
                                + representation,
                            "Retreat " + x + " Infantry on "
                                + Helper.getPlanetRepresentation(representation.toLowerCase(), game))
                        .withEmoji(Emoji.fromFormatted(Emojis.infantry));
                    buttons.add(validTile2);
                }
                limit = unitHolder.getUnitCount(UnitType.Mech, player.getColor());
                for (int x = 1; x < limit + 1; x++) {
                    if (x > 2) {
                        break;
                    }
                    Button validTile2 = Button.primary(
                        finChecker + "retreatGroundUnits_" + pos1 + "_" + pos2 + "_" + x + "mech_" + representation,
                        "Retreat " + x + " Mech" + (x == 1 ? "" : "s") + " on "
                            + Helper.getPlanetRepresentation(representation.toLowerCase(), game))
                        .withEmoji(Emoji.fromFormatted(Emojis.mech));
                    buttons.add(validTile2);
                }

            }
        }
        Button concludeMove = Button.secondary(finChecker + "deleteButtons", "Done Retreating troops");
        buttons.add(concludeMove);
        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "naaz", event);
        }
        if (player.getLeaderIDs().contains("empyreancommander") && !player.hasLeaderUnlocked("empyreancommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "empyrean", event);
        }
        return buttons;
    }

    public static void finishLanding(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        if (!event.getMessage().getContentRaw().contains("Moved all units to the space area.")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), event.getMessage().getContentRaw());
        }

        String message = "Landed troops. Use buttons to decide if you want to build or finish the activation";
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
                StartCombat.startGroundCombat(player, player2, game, event, unitHolder, tile);
                int mechCount = unitHolder.getUnitCount(UnitType.Mech, player2.getColor());
                if (player2.ownsUnit("keleres_mech") && mechCount > 0) {
                    List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "inf");
                    Button DoneExhausting = Button.danger("deleteButtons_spitItOut", "Done Exhausting Planets");
                    buttons.add(DoneExhausting);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
                        player.getRepresentation(true, true) + " you must pay influence due to Keleres mech" + (mechCount == 1 ? "" : "s"));
                    MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
                        "Click the names of the planets you wish to exhaust", buttons);
                }
            }
            if (unitHolder.getUnitCount(UnitType.Fighter, player.getColor()) > 0) {
                List<Button> b2s = new ArrayList<>();
                b2s.add(Button.success("returnFFToSpace_" + tile.getPosition(), "Return Fighters to Space"));
                b2s.add(Button.danger(player.getFinsFactionCheckerPrefix() + "deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), player.getRepresentation(true,
                    true)
                    + " you may use this button to return Naalu fighters to space after combat concludes. This only needs to be done once. Reminder you can't take over a planet with only fighters.",
                    b2s);
            }
        }
        List<Button> systemButtons = ButtonHelper.landAndGetBuildButtons(player, game, event, tile);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "cheiran", event);
        event.getMessage().delete().queue();
    }

    public static List<Button> getUnitsToDevote(Player player, Game game, GenericInteractionCreateEvent event,
        Tile tile, String devoteOrNo) {
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
                Player p2 = player;
                UnitModel unitModel = p2.getUnitFromUnitKey(unitKey);
                String prettyName = unitModel == null ? unitKey.getUnitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                Button validTile2 = Button.danger(finChecker + "resolveDevote_" + tile.getPosition() + "_" + unitName
                    + "_" + devoteOrNo, "Destroy " + " " + prettyName);
                validTile2 = validTile2.withEmoji(emoji);
                buttons.add(validTile2);

            }
        }
        return buttons;
    }

    public static void startDevotion(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        String msg = player.getRepresentation() + " choose which unit of yours to destroy to";
        List<Button> buttons = getUnitsToDevote(player, game, event, tile, "devote");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    public static void resolveDevote(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        List<Button> buttons = getOpposingUnitsToHit(player, game, event, tile);
        String msg = player.getRepresentation() + " choose which opposing unit to hit";
        String unit = buttonID.split("_")[2];
        Player p2 = player;
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), p2.getColor());
        new RemoveUnits().removeStuff(event, tile, 1, "space", unitKey, p2.getColor(), false, game);
        String msg2 = player.getRepresentation() + "used devotion to destroy one of their " + Emojis.getEmojiFromDiscord(unit.toLowerCase()) + " in tile " + tile.getRepresentation();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg2);
        event.getMessage().delete().queue();
        String devoteOrNo = buttonID.split("_")[3];
        if (devoteOrNo.equalsIgnoreCase("devote")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
            if (player.getLeaderIDs().contains("yincommander") && !player.hasLeaderUnlocked("yincommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "yin", event);
            }
        }
    }

    public static List<Button> getOpposingUnitsToHit(Player player, Game game, GenericInteractionCreateEvent event, Tile tile) {
        String finChecker = "FFCC_" + player.getFaction() + "_";

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

                String prettyName = unitModel == null ? unitKey.getUnitType().humanReadableName() : unitModel.getName();
                String unitName = unitKey.unitName();
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null) {
                    damagedUnits = unitHolder.getUnitDamage().getOrDefault(unitKey, 0);
                }

                EmojiUnion emoji = Emoji.fromFormatted(unitKey.unitEmoji());
                for (int x = 1; x < damagedUnits + 1 && x < 2; x++) {
                    String buttonID = finChecker + "hitOpponent_" + tile.getPosition() + "_" + unitName + "damaged"
                        + "_" + unitKey.getColor();
                    Button validTile2 = Button.danger(buttonID, "Damaged " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
                totalUnits = totalUnits - damagedUnits;
                for (int x = 1; x < totalUnits + 1 && x < 2; x++) {
                    Button validTile2 = Button.danger(finChecker + "hitOpponent_" + tile.getPosition() + "_" + unitName
                        + "_" + unitKey.getColor(), "Hit " + prettyName);
                    validTile2 = validTile2.withEmoji(emoji);
                    buttons.add(validTile2);
                }
            }
        }
        return buttons;
    }

    public static void resolveGettingHit(Game game, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        String unit = buttonID.split("_")[2];
        boolean damaged = false;
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
        String msg = player.getRepresentation() + " you have had one of your units assigned a hit, please cancel the hit (Shields Holding, Titan's agent, Sustain Damage) somehow or accept the lost of the unit";
        List<Button> buttons = new ArrayList<>();

        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColorID());

        UnitModel unitModel = player.getUnitFromUnitKey(key);

        String unitName = ButtonHelper.getUnitName(key.asyncID());

        int x = 1;
        EmojiUnion emoji = Emoji.fromFormatted(unitModel.getUnitEmoji());
        String finChecker = player.getFinsFactionCheckerPrefix();
        if (damaged) {
            Button validTile2 = Button.danger(
                finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName + "damaged",
                "Remove " + x + " damaged " + unitModel.getBaseType());
            validTile2 = validTile2.withEmoji(emoji);
            buttons.add(validTile2);
        } else {
            Button validTile2 = Button.danger(
                finChecker + "assignHits_" + tile.getPosition() + "_" + x + unitName,
                "Remove " + x + " " + unitModel.getBaseType());
            validTile2 = validTile2.withEmoji(emoji);
            buttons.add(validTile2);
        }

        if (!damaged && unitModel.getSustainDamage()) {
            Button validTile2 = Button
                .primary(finChecker + "assignDamage_" + tile.getPosition() + "_" + 1 + unitName,
                    "Sustain " + 1 + " " + unitModel.getBaseType());
            validTile2 = validTile2.withEmoji(emoji);
            buttons.add(validTile2);
        }

        buttons.add(Button.secondary("deleteButtons", "Cancel the hit"));
        MessageHelper.sendMessageToChannel(channel, msg, buttons);
        event.getMessage().delete().queue();
    }

    public static void retreatGroundUnits(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident, String buttonLabel) {
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
        if (buttonLabel.toLowerCase().contains("damaged")) {
            new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos2),
                amount + " " + unitType, game);
            game.getTileByPosition(pos2).addUnitDamage("space", unitKey, amount);
        } else {
            new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos2),
                amount + " " + unitType, game);
        }

        // activeMap.getTileByPosition(pos1).removeUnit(planet,key, amount);
        new RemoveUnits().removeStuff(event, game.getTileByPosition(pos1), amount, planet, unitKey,
            player.getColor(), false, game);

        List<Button> systemButtons = getRetreatingGroundTroopsButtons(player, game, event, pos1, pos2);
        String retreatMessage = ident + " Retreated " + amount + " " + unitType + " on " + planet + " to "
            + game.getTileByPosition(pos2).getRepresentationForButtons(game, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), retreatMessage);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void retreatSpaceUnits(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String both = buttonID.replace("retreatUnitsFrom_", "");
        String pos1 = both.split("_")[0];
        String pos2 = both.split("_")[1];
        Tile tile1 = game.getTileByPosition(pos1);
        Tile tile2 = game.getTileByPosition(pos2);
        tile2 = MoveUnits.flipMallice(event, tile2, game);
        if (game.playerHasLeaderUnlockedOrAlliance(player, "kollecccommander") && !buttonID.contains("skilled")
            && !AddCC.hasCC(event, player.getColor(), tile1)) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), player.getFactionEmoji()
                + " did not place a CC in the retreat system due to Kado S'mah-Qar, the Kollecc commander.");
        } else {
            AddCC.addCC(event, player.getColor(), tile2, true);
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
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitKey, player.getColor(), false,
                    game);
                new AddUnits().unitParsing(event, player.getColor(), tile2, totalUnits + " " + unitName, game);
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
                String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                int totalUnits = unitEntry.getValue();
                int damagedUnits = 0;

                if (unitHolder.getUnitDamage() != null && unitHolder.getUnitDamage().get(unitKey) != null) {
                    damagedUnits = unitHolder.getUnitDamage().get(unitKey);
                }

                new RemoveUnits().removeStuff(event, tile1, totalUnits, "space", unitKey, player.getColor(), false,
                    game);
                new AddUnits().unitParsing(event, player.getColor(), tile2, totalUnits + " " + unitName, game);
                if (damagedUnits > 0) {
                    game.getTileByPosition(pos2).addUnitDamage("space", unitKey, damagedUnits);
                }
            }
        }
    }

    public static void umbatTile(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
        String pos = buttonID.replace("umbatTile_", "");
        List<Button> buttons = Helper.getPlaceUnitButtons(event, player, game, game.getTileByPosition(pos),
            "muaatagent", "place");
        String message = player.getRepresentation() + " Use the buttons to produce units. ";
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        event.getMessage().delete().queue();
    }

    public static void genericPlaceUnit(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident, String trueIdentity, String finsFactionCheckerPrefix) {
        String unitNPlanet = buttonID.replace("place_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong.replace("2", ""));
        String spaceOrPlanet;

        String successMessage;
        String playerRep = player.getRepresentation();
        if ("sd".equalsIgnoreCase(unit)) {
            if (player.hasUnit("absol_saar_spacedock") || player.hasUnit("saar_spacedock") || player.hasTech("ffac2")
                || player.hasTech("absol_ffac2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    game.getTile(AliasHandler.resolveTile(planetName)), unit, game);
                successMessage = "Placed 1 space dock in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                    game.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                    game);
                successMessage = "Placed 1 " + Emojis.spacedock + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
            if (player.getLeaderIDs().contains("cabalcommander") && !player.hasLeaderUnlocked("cabalcommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "cabal", event);
            }
            if (player.hasAbility("industrious") && !FoWHelper.otherPlayersHaveShipsInSystem(player,
                game.getTile(AliasHandler.resolveTile(planetName)), game)) {
                Button replace = Button.success("FFCC_" + player.getFaction() + "_rohdhnaIndustrious_"
                    + game.getTile(AliasHandler.resolveTile(planetName)).getPosition() + "_" + unit + " "
                    + planetName, "Replace Space Dock with War Sun");

                MessageHelper.sendMessageToChannelWithButton(player.getCardsInfoThread(),
                    playerRep + "Industrious: You may spend 6 resources to replace 1 space dock with 1 war sun.", replace);
            }
        } else if ("pds".equalsIgnoreCase(unitLong)) {
            if (player.ownsUnit("mirveda_pds") || player.ownsUnit("mirveda_pds2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    game.getTile(AliasHandler.resolveTile(planetName)), unit, game);
                successMessage = "Placed 1 " + Emojis.pds + " in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                    game.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                    game);
                successMessage = "Placed 1 " + Emojis.pds + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
        } else {
            Tile tile;
            String producedOrPlaced = "Produced";
            if ("gf".equalsIgnoreCase(unit) || "mf".equalsIgnoreCase(unit)
                || (unitLong.contains("gf") && unitLong.length() > 2)) {
                if ((unitLong.contains("gf") && unitLong.length() > 2)) {
                    if (!planetName.contains("space")) {
                        spaceOrPlanet = planetName;
                        tile = game.getTile(AliasHandler.resolveTile(planetName));
                        String num = unitLong.substring(0, 1);
                        String producedInput = unit.replace(num, "") + "_" + tile.getPosition() + "_" + spaceOrPlanet;
                        int nu = Integer.parseInt(num);
                        for (int x = 0; x < nu; x++) {
                            player.produceUnit(producedInput);
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                            game.getTile(AliasHandler.resolveTile(planetName)), num + " gf " + planetName,
                            game);
                        successMessage = producedOrPlaced + " " + num + " " + Emojis.infantry + " on "
                            + Helper.getPlanetRepresentation(planetName, game) + ".";
                    } else {
                        spaceOrPlanet = "space";
                        tile = game.getTileByPosition(planetName.replace("space", ""));
                        String num = unitLong.substring(0, 1);
                        String producedInput = unit.replace(num, "") + "_" + tile.getPosition() + "_" + spaceOrPlanet;
                        int nu = Integer.parseInt(num);
                        for (int x = 0; x < nu; x++) {
                            player.produceUnit(producedInput);
                        }
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, num + " gf",
                            game);
                        successMessage = producedOrPlaced + " " + num + " " + Emojis.infantry + " in space.";
                    }
                } else {

                    if (!planetName.contains("space")) {
                        spaceOrPlanet = planetName;
                        tile = game.getTile(AliasHandler.resolveTile(planetName));
                        String producedInput = unit.replace("2", "") + "_" + tile.getPosition() + "_" + spaceOrPlanet;
                        player.produceUnit(producedInput);
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            game);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " on "
                            + Helper.getPlanetRepresentation(planetName, game) + ".";
                    } else {
                        spaceOrPlanet = "space";
                        tile = game.getTileByPosition(planetName.replace("space", ""));
                        String producedInput = unit.replace("2", "") + "_" + tile.getPosition() + "_" + spaceOrPlanet;
                        player.produceUnit(producedInput);
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            game);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in space.";
                    }

                }
            } else {
                spaceOrPlanet = "space";
                tile = game.getTileByPosition(planetName);
                String producedInput = unit.replace("2", "") + "_" + tile.getPosition() + "_"
                    + spaceOrPlanet;
                player.produceUnit(producedInput);
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(planetName),
                        "2 ff", game);
                    successMessage = "Produced 2 " + Emojis.fighter + " in tile "
                        + game.getTileByPosition(AliasHandler.resolveTile(planetName)).getRepresentationForButtons(game, player) + ".";
                    player.produceUnit(producedInput);
                    Tile tile2 = game.getTileByPosition(planetName);
                    if (player.hasAbility("cloaked_fleets")) {
                        List<Button> shroadedFleets = new ArrayList<>();
                        shroadedFleets.add(
                            Button.success("cloakedFleets_" + tile2.getPosition() + "_ff", "Capture 1 fighter"));
                        shroadedFleets.add(Button.danger("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            "You may use your cloaked fleets ability to capture this produced ship.",
                            shroadedFleets);
                    }
                    if (player.hasAbility("cloaked_fleets")) {
                        List<Button> shroadedFleets = new ArrayList<>();
                        shroadedFleets.add(
                            Button.success("cloakedFleets_" + tile2.getPosition() + "_ff", "Capture 1 fighter"));
                        shroadedFleets.add(Button.danger("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            "You may use your cloaked fleets ability to capture this produced ship.",
                            shroadedFleets);
                    }
                } else if ("2destroyer".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(planetName),
                        "2 destroyer", game);
                    successMessage = "Produced 2 " + Emojis.destroyer + " in tile "
                        + game.getTileByPosition(AliasHandler.resolveTile(planetName)).getRepresentationForButtons(game, player) + ".";
                    player.produceUnit(producedInput);

                } else {
                    new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(planetName),
                        unit, game);
                    successMessage = "Produced a " + Emojis.getEmojiFromDiscord(unitLong) + " in tile "
                        + game.getTileByPosition(AliasHandler.resolveTile(planetName)).getRepresentationForButtons(game, player) + ".";
                    if (player.hasAbility("cloaked_fleets")) {
                        List<Button> shroadedFleets = new ArrayList<>();
                        shroadedFleets.add(Button.success("cloakedFleets_" + tile.getPosition() + "_" + unit + "",
                            "Capture 1 " + ButtonHelper.getUnitName(unit)));
                        shroadedFleets.add(Button.danger("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            "You may use your cloaked fleets ability to capture this produced ship.",
                            shroadedFleets);
                    }

                }

            }
        }
        if (("sd".equalsIgnoreCase(unit) || "pds".equalsIgnoreCase(unitLong))
            && event.getMessage().getContentRaw().contains("for construction")) {

            if (game.isFowMode() || (!"action".equalsIgnoreCase(game.getPhaseOfGame()) && !"statusScoring".equalsIgnoreCase(game.getPhaseOfGame()))) {
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), playerRep + " " + successMessage);
            } else {
                ButtonHelper.sendMessageToRightStratThread(player, game, playerRep + " " + successMessage,
                    "construction");
            }
            if (player.hasLeader("mahactagent") || player.hasExternalAccessToLeader("mahactagent")) {
                String message = playerRep + " Please tell the bot if you used Mahact's agent and should place the active player's (Construction holder) CC or if you followed normally and should place your own CC from reinforcements.";
                Button placeCCInSystem = Button.success(
                    finsFactionCheckerPrefix + "reinforcements_cc_placement_" + planetName,
                    "Place 1 CC from reinforcements");
                Button placeConstructionCCInSystem = Button.secondary(
                    finsFactionCheckerPrefix + "placeHolderOfConInSystem_" + planetName,
                    "Place 1 CC of the active player");
                Button NoDontWantTo = Button.primary(finsFactionCheckerPrefix + "deleteButtons",
                    "Don't Place A CC");
                List<Button> buttons = List.of(placeCCInSystem, placeConstructionCCInSystem, NoDontWantTo);
                MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
            } else {
                boolean hasConstruction = false;
                for (Integer sc : player.getSCs()) {
                    StrategyCardModel scModel = game.getStrategyCardModelByInitiative(sc).orElse(null);
                    if (scModel != null && scModel.getBotSCAutomationID().equalsIgnoreCase("pok4construction")) {
                        hasConstruction = true;
                        break;
                    }
                }
                if (!player.getSCs().contains(Integer.parseInt("4"))
                    && !hasConstruction && ("action".equalsIgnoreCase(game.getPhaseOfGame())
                        || game.getCurrentAgendaInfo().contains("Strategy"))
                    && !ButtonHelper.isPlayerElected(game, player, "absol_minsindus")) {
                    String color = player.getColor();
                    String tileID = AliasHandler.resolveTile(planetName.toLowerCase());
                    Tile tile = game.getTile(tileID);
                    if (tile == null) {
                        tile = game.getTileByPosition(tileID);
                    }
                    String msg = playerRep + " Placed 1 CC From Reinforcements In The "
                        + Helper.getPlanetRepresentation(planetName, game) + " system";
                    if (!game.playerHasLeaderUnlockedOrAlliance(player, "rohdhnacommander")) {
                        if (Mapper.isValidColor(color)) {
                            AddCC.addCC(event, color, tile);
                        }
                    } else {
                        msg = playerRep
                            + " has B-Unit 205643a, the Roh'Dhna Commander and is thus doing the Primary which does not place a CC.";
                    }

                    if (game.isFowMode()) {
                        MessageHelper.sendMessageToChannel(event.getChannel(), msg);
                    } else {
                        ButtonHelper.sendMessageToRightStratThread(player, game, msg, "construction");
                    }
                }
            }

            event.getMessage().delete().queue();

        } else {
            if ("sd".equalsIgnoreCase(unit) || "pds".equalsIgnoreCase(unitLong)) {
                String producedInput = unit + "_"
                    + game.getTile(AliasHandler.resolveTile(planetName)).getPosition() + "_"
                    + planetName;
                player.produceUnit(producedInput);
            }
            String editedMessage = event.getMessage().getContentRaw();
            if (editedMessage.contains("Produced")) {
                editedMessage = editedMessage + "\n " + successMessage;
            } else {
                editedMessage = playerRep + " " + successMessage;
            }

            if (editedMessage.contains("place 2 infantry")) {
                successMessage = "Placed 2 " + Emojis.infantry + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), successMessage);
                event.getMessage().delete().queue();
            } else {
                event.getMessage().editMessage(Helper.buildProducedUnitsMessage(player, game)).queue(
                    null, (error) -> {
                        BotLogger.log(MessageHelper.getRestActionFailureMessage(event.getMessageChannel(), "Failed to edit message", null, error));
                    });
            }

        }
        if ("sd".equalsIgnoreCase(unit)) {
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile != null) {
                AgendaHelper.ministerOfIndustryCheck(player, game, tile, event);
            }
        }
        if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "titans", event);
        }
        if (player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "saar", event);
        }
        ButtonHelper.fullCommanderUnlockCheck(player, game, "rohdhna", event);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "cheiran", event);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "celdauri", event);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "gledge", event);
        if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "mentak", event);
        }
        if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "l1z1x", event);
        }
        if (player.getLeaderIDs().contains("tneliscommander") && !player.hasLeaderUnlocked("tneliscommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "tnelis", event);
        }
        if (player.getLeaderIDs().contains("cymiaecommander") && !player.hasLeaderUnlocked("cymiaecommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "cymiae", event);
        }
        if (player.getLeaderIDs().contains("kyrocommander") && !player.hasLeaderUnlocked("kyrocommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "kyro", event);
        }
        if (player.getLeaderIDs().contains("gheminacommander") && !player.hasLeaderUnlocked("gheminacommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "ghemina", event);
        }
        if (player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander")
            && "warsun".equalsIgnoreCase(unitLong)) {
            ButtonHelper.commanderUnlockCheck(player, game, "muaat", event);
        }
        if (player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "argent", event);
        }

        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "naaz", event);
        }
        if (player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "arborec", event);
        }
    }

    public static void resolveCloakedFleets(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String unit = buttonID.split("_")[2];
        Tile tile = game.getTileByPosition(buttonID.split("_")[1]);
        UnitKey key = Mapper.getUnitKey(AliasHandler.resolveUnit(unit), player.getColor());
        new RemoveUnits().removeStuff(event, tile, 1, "space", key, player.getColor(), false, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " captured 1 newly produced " + ButtonHelper.getUnitName(unit)
                + " in " + tile.getRepresentationForButtons(game, player)
                + " using the Cloaked Fleets ability (limit of 2 ships may be captured per build).");
        new AddUnits().unitParsing(event, player.getColor(), player.getNomboxTile(), unit, game);
        event.getMessage().delete().queue();
    }

    public static void resolveKolleccMechCapture(String buttonID, ButtonInteractionEvent event, Game game,
        Player player) {
        String unit = buttonID.split("_")[2];
        String planet = buttonID.split("_")[1];
        Tile tile = game.getTileFromPlanet(buttonID.split("_")[1]);
        new RemoveUnits().unitParsing(event, player.getColor(), tile, unit + " " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " captured 1 " + unit + " on "
                + Helper.getPlanetRepresentation(planet, game) + " using the Kollecc Mech abiility");
        new AddUnits().unitParsing(event, player.getColor(), player.getNomboxTile(), unit, game);
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

    public static void placeUnitAndDeleteButton(String buttonID, ButtonInteractionEvent event, Game game,
        Player player, String ident, String trueIdentity) {
        String unitNPlanet = buttonID.replace("placeOneNDone_", "");
        String skipbuild = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        unitNPlanet = unitNPlanet.replace(skipbuild + "_", "");
        String unitLong = unitNPlanet.substring(0, unitNPlanet.indexOf("_"));
        String planetName = unitNPlanet.replace(unitLong + "_", "");
        String unit = AliasHandler.resolveUnit(unitLong);
        String producedOrPlaced = "Produced";
        if ("skipbuild".equalsIgnoreCase(skipbuild)) {
            producedOrPlaced = "Placed";
        }
        String successMessage;
        String playerRep = player.getRepresentation();
        if ("sd".equalsIgnoreCase(unit)) {
            if (player.ownsUnit("saar_spacedock") || player.ownsUnit("saar_spacedock2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    game.getTile(AliasHandler.resolveTile(planetName)), unit, game);
                successMessage = "Placed 1 space dock in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                    game.getTile(AliasHandler.resolveTile(planetName)), unit + " " + planetName,
                    game);
                successMessage = "Placed 1 " + Emojis.spacedock + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
        } else if ("pds".equalsIgnoreCase(unit)) {
            if (player.ownsUnit("mirveda_pds") || player.ownsUnit("mirveda_pds2")) {
                new AddUnits().unitParsing(event, player.getColor(),
                    game.getTile(AliasHandler.resolveTile(planetName)), unit, game);
                successMessage = "Placed 1 " + Emojis.pds + " in the space area of the "
                    + Helper.getPlanetRepresentation(planetName, game) + " system.";
            } else {
                new AddUnits().unitParsing(event, player.getColor(),
                    game.getTile(AliasHandler.resolveTile(planetName)), unitLong + " " + planetName,
                    game);
                successMessage = "Placed 1 " + Emojis.pds + " on "
                    + Helper.getPlanetRepresentation(planetName, game) + ".";
            }
        } else {
            Tile tile;
            if ("gf".equalsIgnoreCase(unit) || "mf".equalsIgnoreCase(unit)
                || ((unitLong.contains("gf") && unitLong.length() > 2))) {
                if (unitLong.contains("gf") && unitLong.length() > 2) {
                    String amount = "" + unitLong.charAt(0);
                    if (!planetName.contains("space")) {
                        new AddUnits().unitParsing(event, player.getColor(),
                            game.getTile(AliasHandler.resolveTile(planetName)), amount + " gf " + planetName,
                            game);
                        successMessage = producedOrPlaced + " " + amount + " " + Emojis.infantry + " on "
                            + Helper.getPlanetRepresentation(planetName, game) + ".";
                    } else {
                        tile = game.getTileByPosition(planetName.replace("space", ""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, amount + " gf ",
                            game);
                        successMessage = producedOrPlaced + " " + amount + " " + Emojis.infantry + " in space.";
                    }
                } else {

                    if (!planetName.contains("space")) {
                        tile = game.getTile(AliasHandler.resolveTile(planetName));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit + " " + planetName,
                            game);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " on "
                            + Helper.getPlanetRepresentation(planetName, game) + ".";
                    } else {
                        tile = game.getTileByPosition(planetName.replace("space", ""));
                        new AddUnits().unitParsing(event, player.getColor(),
                            tile, unit,
                            game);
                        successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in space.";
                    }

                }
            } else {
                if ("2ff".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(planetName),
                        "2 ff", game);
                    successMessage = producedOrPlaced + " 2 " + Emojis.fighter + " in tile "
                        + game.getTileByPosition(AliasHandler.resolveTile(planetName)).getRepresentationForButtons(game, player) + ".";
                } else if ("2destroyer".equalsIgnoreCase(unitLong)) {
                    new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(planetName),
                        "2 destroyer", game);
                    successMessage = producedOrPlaced + " 2 " + Emojis.destroyer + " in tile "
                        + game.getTileByPosition(AliasHandler.resolveTile(planetName)).getRepresentationForButtons(game, player) + ".";
                } else {
                    new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(planetName), unit,
                        game);
                    successMessage = producedOrPlaced + " a " + Emojis.getEmojiFromDiscord(unitLong) + " in tile "
                        + game.getTileByPosition(AliasHandler.resolveTile(planetName)).getRepresentationForButtons(game, player) + ".";
                    Tile tile2 = game.getTileByPosition(planetName);
                    if (player.hasAbility("cloaked_fleets")) {
                        List<Button> shroadedFleets = new ArrayList<>();
                        shroadedFleets.add(Button.success("cloakedFleets_" + tile2.getPosition() + "_" + unit + "",
                            "Capture 1 " + ButtonHelper.getUnitName(unit)));
                        shroadedFleets.add(Button.danger("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannel(event.getChannel(),
                            "You may use your cloaked fleets ability to capture this produced ship.",
                            shroadedFleets);
                    }
                    if (tile2 != null && !"skipbuild".equalsIgnoreCase(skipbuild) && player.hasAbility("rally_to_the_cause")
                        && player.getHomeSystemTile() == tile2
                        && ButtonHelperAbilities.getTilesToRallyToTheCause(game, player).size() > 0) {
                        String msg = player.getRepresentation()
                            + " due to your Rally to the Cause ability, if you just produced a ship in your HS, you may produce up to 2 ships in a system that contains a planet with a trait but no legendary planets and no opponent units."
                            + " Press button to resolve.";
                        List<Button> buttons2 = new ArrayList<>();
                        buttons2.add(Button.success("startRallyToTheCause", "Rally To The Cause"));
                        buttons2.add(Button.danger("deleteButtons", "Decline"));
                        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg,
                            buttons2);

                    }
                }

            }
            if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "l1z1x", event);
            }
            if (player.getLeaderIDs().contains("cymiaecommander") && !player.hasLeaderUnlocked("cymiaecommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "cymiae", event);
            }
            if (player.getLeaderIDs().contains("kyrocommander") && !player.hasLeaderUnlocked("kyrocommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "kyro", event);
            }
            if (player.getLeaderIDs().contains("gheminacommander") && !player.hasLeaderUnlocked("gheminacommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "ghemina", event);
            }
            if (player.getLeaderIDs().contains("tneliscommander") && !player.hasLeaderUnlocked("tneliscommander")) {
                ButtonHelper.commanderUnlockCheck(player, game, "tnelis", event);
            }
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(),
            playerRep + " " + successMessage);
        String message2 = trueIdentity + " Click the names of the planets you wish to exhaust.";
        List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "res");
        if (skipbuild.contains("freelancers")) {
            buttons = ButtonHelper.getExhaustButtonsWithTG(game, player, "freelancers");
        }
        if (player.hasTechReady("absol_st")) {
            Button sarweenButton = Button.danger("useTech_absol_st", "Use Sarween Tools");
            buttons.add(sarweenButton);
        }
        if (player.hasRelic("boon_of_the_cerulean_god")) {
            Button sarweenButton = Button.danger("useRelic_boon", "Use Boon Of The Cerulean God Relic");
            buttons.add(sarweenButton);
        }
        if (player.hasUnexhaustedLeader("ghotiagent")) {
            Button winnuButton = Button.danger("exhaustAgent_ghotiagent_" + player.getFaction(),
                "Use Ghoti Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.ghoti));
            buttons.add(winnuButton);
        }
        if (player.hasUnexhaustedLeader("mortheusagent")) {
            Button winnuButton = Button
                .danger("exhaustAgent_mortheusagent_" + player.getFaction(),
                    "Use Mortheus Agent")
                .withEmoji(Emoji.fromFormatted(Emojis.mortheus));
            buttons.add(winnuButton);
        }
        Button DoneExhausting;
        if (!buttonID.contains("deleteButtons")) {
            DoneExhausting = Button.danger("deleteButtons_" + buttonID, "Done Exhausting Planets");
        } else {
            DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
        }
        buttons.add(DoneExhausting);
        if (!"skipbuild".equalsIgnoreCase(skipbuild)) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message2,
                buttons);
        }

        if (player.getLeaderIDs().contains("titanscommander") && !player.hasLeaderUnlocked("titanscommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "titans", event);
        }
        if ("sd".equalsIgnoreCase(unit)) {
            Tile tile = game.getTileFromPlanet(planetName);
            if (tile != null) {
                AgendaHelper.ministerOfIndustryCheck(player, game, tile, event);
            }
        }
        if (player.getLeaderIDs().contains("saarcommander") && !player.hasLeaderUnlocked("saarcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "saar", event);
        }
        ButtonHelper.fullCommanderUnlockCheck(player, game, "rohdhna", event);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "cheiran", event);
        ButtonHelper.fullCommanderUnlockCheck(player, game, "celdauri", event);
        if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
            player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
        }
        if (player.getLeaderIDs().contains("mentakcommander") && !player.hasLeaderUnlocked("mentakcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "mentak", event);
        }
        if (player.getLeaderIDs().contains("l1z1xcommander") && !player.hasLeaderUnlocked("l1z1xcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "l1z1x", event);
        }
        if (player.getLeaderIDs().contains("tneliscommander") && !player.hasLeaderUnlocked("tneliscommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "tnelis", event);
        }
        if (player.getLeaderIDs().contains("cymiaecommander") && !player.hasLeaderUnlocked("cymiaecommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "cymiae", event);
        }
        if (player.getLeaderIDs().contains("kyrocommander") && !player.hasLeaderUnlocked("kyrocommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "kyro", event);
        }
        if (player.getLeaderIDs().contains("gheminacommander") && !player.hasLeaderUnlocked("gheminacommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "ghemina", event);
        }
        if (player.getLeaderIDs().contains("muaatcommander") && !player.hasLeaderUnlocked("muaatcommander")
            && "warsun".equalsIgnoreCase(unitLong)) {
            ButtonHelper.commanderUnlockCheck(player, game, "muaat", event);
        }
        if (player.getLeaderIDs().contains("argentcommander") && !player.hasLeaderUnlocked("argentcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "argent", event);
        }
        if (player.getLeaderIDs().contains("naazcommander") && !player.hasLeaderUnlocked("naazcommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "naaz", event);
        }
        if (player.getLeaderIDs().contains("arboreccommander") && !player.hasLeaderUnlocked("arboreccommander")) {
            ButtonHelper.commanderUnlockCheck(player, game, "arborec", event);
        }

        event.getMessage().delete().queue();
    }

    public static void spaceLandedUnits(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident, String buttonLabel) {
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
        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos), amount + " " + unitName,
            game);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            game.getTileByPosition(pos).addUnitDamage("space", unitKey, amount);
            game.getTileByPosition(pos).removeUnitDamage(planet, unitKey, amount);
        }

        game.getTileByPosition(pos).removeUnit(planet, unitKey, amount);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = "damaged " + unitName;
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ident + "Undid landing of " + amount + " " + unitName + " on " + planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void resolveAssaultCannonNDihmohnCommander(String buttonID, ButtonInteractionEvent event,
        Player player, Game game) {
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
            msg = opponent.getRepresentation(true, true) + " " + player.getFactionEmoji()
                + " used Clona Bathru, the Dih-Mohn Commander, to generate a hit against you. Please assign it with buttons.";
        } else if (cause.contains("ds")) {
            buttons = getOpposingUnitsToHit(player, game, event, tile);
            msg = player.getRepresentation() + " choose which opposing unit to hit";
        } else {
            msg = opponent.getRepresentation(true, true) + " " + player.getFactionEmoji()
                + " used Assault Cannon to force you to destroy a non fighter ship. Please assign it with buttons.";
            buttons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(opponent, game, tile, "assaultcannon");
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);

    }

    public static void landingUnits(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident, String buttonLabel) {
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
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = unitName.replace("damaged", "");
        }

        UnitKey unitKey = Mapper.getUnitKey(AliasHandler.resolveUnit(unitName), player.getColor());
        new AddUnits().unitParsing(event, player.getColor(), game.getTileByPosition(pos),
            amount + " " + unitName + " " + planet, game);
        if (buttonLabel.toLowerCase().contains("damaged")) {
            game.getTileByPosition(pos).removeUnitDamage("space", unitKey, amount);
            game.getTileByPosition(pos).addUnitDamage(
                ButtonHelper.getUnitHolderFromPlanetName(planet, game).getName(), unitKey, amount);

        }
        if (buttonLabel.toLowerCase().contains("damaged")) {
            unitName = "damaged " + unitName;
        }

        game.getTileByPosition(pos).removeUnit("space", unitKey, amount);
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            ident + " Landed " + amount + " " + unitName + " on " + planet);
        event.getMessage().editMessage(event.getMessage().getContentRaw())
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void offerDomnaStep2Buttons(ButtonInteractionEvent event, Game game, Player player,
        String buttonID) {
        String pos = buttonID.split("_")[1];
        Tile tile = game.getTileByPosition(pos);
        List<Button> buttons = new ArrayList<>();
        for (UnitKey unit : tile.getUnitHolders().get("space").getUnits().keySet()) {
            if (unit.getUnitType() == UnitType.Infantry || unit.getUnitType() == UnitType.Mech) {
                continue;
            }
            String unitName = ButtonHelper.getUnitName(unit.asyncID());
            Button validTile = Button.success("domnaStepTwo_" + pos + "_" + unitName, "Move 1 " + unit.unitName());
            buttons.add(validTile);
        }

        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select unit you want to move",
            buttons);
        event.getMessage().delete().queue();
    }

    public static void offerDomnaStep3Buttons(ButtonInteractionEvent event, Game game, Player player,
        String buttonID) {
        String pos1 = buttonID.split("_")[1];
        String unit = buttonID.split("_")[2];
        List<Button> buttons = new ArrayList<>();
        for (String pos2 : FoWHelper.getAdjacentTiles(game, pos1, player, false)) {
            if (pos1.equalsIgnoreCase(pos2)) {
                continue;
            }
            Tile tile2 = game.getTileByPosition(pos2);
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile2, game)) {
                buttons.add(Button.secondary("domnaStepThree_" + pos1 + "_" + unit + "_" + pos2,
                    "Move " + unit + " to " + tile2.getRepresentationForButtons(game, player)));
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "Select tile you want to move to", buttons);
        event.getMessage().delete().queue();
    }

    public static void resolveDomnaStep3Buttons(ButtonInteractionEvent event, Game game, Player player,
        String buttonID) {
        String pos1 = buttonID.split("_")[1];
        Tile tile1 = game.getTileByPosition(pos1);
        String unit = buttonID.split("_")[2];
        String pos2 = buttonID.split("_")[3];
        Tile tile2 = game.getTileByPosition(pos2);
        new AddUnits().unitParsing(event, player.getColor(), tile2, unit, game);
        new RemoveUnits().unitParsing(event, player.getColor(), tile1, unit, game);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            player.getFactionEmoji() + " moved 1 " + unit + " from "
                + tile1.getRepresentationForButtons(game, player) + " to "
                + tile2.getRepresentationForButtons(game, player) + "an ability.");
        event.getMessage().delete().queue();
    }

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
                buttons.add(Button.success("combatDroneConvert_" + x, "" + x));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " choose how many fighters you want to convert to infantry",
            buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

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
                buttons.add(Button.success("resolveMirvedaCommander_" + x, "" + x));
            }
        }
        buttons.add(Button.danger("deleteButtons", "Delete These"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(),
            player.getRepresentation(true, true) + " choose how many infantry you wish to convert to fighters",
            buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    public static void resolvingMirvedaCommander(ButtonInteractionEvent event, Game game, Player player,
        String ident, String buttonID) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        int numff = Integer.parseInt(buttonID.split("_")[1]);
        if (numff > 0) {
            new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " infantry", game);
            new AddUnits().unitParsing(event, player.getColor(), tile, numff + " fighters", game);
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        String msg = ident + " Turned " + numff + " infantry into " + numff + " fighter" + (numff == 1 ? "" : "s") + " using the combat drone ability";
        event.getMessage().editMessage(msg)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void resolvingCombatDrones(ButtonInteractionEvent event, Game game, Player player, String ident,
        String buttonID) {
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        int numff = Integer.parseInt(buttonID.split("_")[1]);
        if (numff > 0) {
            new RemoveUnits().unitParsing(event, player.getColor(), tile, numff + " fighters", game);
            new AddUnits().unitParsing(event, player.getColor(), tile, numff + " infantry", game);
        }
        List<Button> systemButtons = ButtonHelper.moveAndGetLandingTroopsButtons(player, game, event);
        String msg = ident + " Turned " + numff + " fighter" + (numff == 1 ? "" : "s") + " into " + numff + " infantry using the combat drone ability";
        event.getMessage().editMessage(msg)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
    }

    public static void assignHits(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident, String buttonLabel) {
        String rest;
        rest = buttonID.replace("assignHits_", "");
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

                            String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                            int amount = unitEntry.getValue();
                            Player cabalMechOwner = Helper.getPlayerFromUnit(game, "cabal_mech");
                            boolean cabalMech = cabalMechOwner != null
                                && unitHolder.getUnitCount(UnitType.Mech, cabalMechOwner.getColor()) > 0
                                && unitName.toLowerCase().contains("infantry")
                                && !ButtonHelper.isLawInPlay(game, "articles_war");

                            new RemoveUnits().removeStuff(event, game.getTileByPosition(pos),
                                unitEntry.getValue(), unitHolder.getName(), unitKey, player.getColor(), false,
                                game);
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
                                ButtonHelper.resolveInfantryDeath(game, player, amount);
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasTech("sar")) {
                                for (int x = 0; x < amount; x++) {
                                    player.setTg(player.getTg() + 1);
                                    MessageHelper.sendMessageToChannel(
                                        player.getCorrectChannel(),
                                        player.getRepresentation() + " you gained 1TG (" + (player.getTg() - 1)
                                            + "->" + player.getTg()
                                            + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain.");
                                    ButtonHelperAbilities.pillageCheck(player, game);
                                }
                                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasUnit("mykomentori_mech")) {
                                for (int x = 0; x < amount; x++) {
                                    ButtonHelper.rollMykoMechRevival(game, player);
                                }
                            }
                            if (unitKey.getUnitType() == UnitType.Mech && player.hasUnit("cheiran_mech")) {
                                new AddUnits().unitParsing(event, player.getColor(), tile, amount + " infantry " + unitHolder.getName(), game);
                                String msg = "> Added " + amount + " infantry to the planet due to Cheiran mech trigger";
                                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
                            }
                        }
                    }
                } else {
                    for (Map.Entry<UnitKey, Integer> unitEntry : units.entrySet()) {
                        UnitKey unitKey = unitEntry.getKey();
                        if (!unitKey.getColorID().equals(cID))
                            continue;
                        String unitName = ButtonHelper.getUnitName(unitKey.asyncID());
                        int amount = unitEntry.getValue();

                        new RemoveUnits().removeStuff(event, game.getTileByPosition(pos), amount, "space",
                            unitKey, player.getColor(), false, game);
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
            String message2 = ident + " Removed all units";
            if (rest.contains("AllShips")) {
                message2 = ident + " Removed all units in space area";
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

        new RemoveUnits().removeStuff(event, game.getTileByPosition(pos), amount, planetName, unitKey,
            player.getColor(), buttonLabel.toLowerCase().contains("damaged"), game);

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
                for (int x = 0; x < amount; x++) {
                    player.setTg(player.getTg() + 1);
                    MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player
                        .getRepresentation() + " you gained 1TG (" + (player.getTg() - 1) + "->"
                        + player.getTg()
                        + ") from 1 of your mechs dying while you own Self-Assembly Routines. This is not an optional gain");
                    ButtonHelperAbilities.pillageCheck(player, game);
                }
                ButtonHelperAgents.resolveArtunoCheck(player, game, 1);
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
                new AddUnits().unitParsing(event, player.getColor(), tile, amount + " infantry " + planetName, game);
                String msg = "> Added " + amount + " infantry to the planet due to Cheiran mech trigger";
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            }
            if ((player.getUnitsOwned().contains("mahact_infantry") || player.hasTech("cl2"))
                && unitName.toLowerCase().contains("inf")) {
                ButtonHelperFactionSpecific.offerMahactInfButtons(player, game);
            }
            if (player.hasInf2Tech() && unitName.toLowerCase().contains("inf")) {
                ButtonHelper.resolveInfantryDeath(game, player, amount);
            }
        }

        String message = event.getMessage().getContentRaw();
        String message2 = ident + " Removed " + amount + " " + unitName + " from " + planetName + " in tile "
            + tile.getRepresentationForButtons(game, player);
        assignType = "combat";
        if (!game.getStoredValue(player.getFaction() + "latestAssignHits").isEmpty()) {
            assignType = game.getStoredValue(player.getFaction() + "latestAssignHits");
        }
        List<Button> systemButtons = ButtonHelper.getButtonsForRemovingAllUnitsInSystem(player, game, tile, assignType);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);

    }

    public static void repairDamage(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
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
        String message2 = ident + " Repaired " + amount + " " + unitName + " from " + planetName + " in tile "
            + tile.getRepresentationForButtons(game, player);
        List<Button> systemButtons = ButtonHelper.getButtonsForRepairingUnitsInASystem(player, game, tile);
        event.getMessage().editMessage(message)
            .setComponents(ButtonHelper.turnButtonListIntoActionRowList(systemButtons)).queue();
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2);
    }

    public static void assignDamage(String buttonID, ButtonInteractionEvent event, Game game, Player player,
        String ident) {
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
        String message2 = ident + " Sustained " + amount + " " + unitName + " from " + planetName + " in tile "
            + tile.getRepresentationForButtons(game, player);

        if (player.hasTech("nes")) {
            message2 = message2 + ". These sustains cancel 2 hits due to Non-Euclidean Shielding";
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
        return ButtonHelper.getUnitName(unitKey.asyncID()).equalsIgnoreCase("mech") && player.hasUnit("nomad_mech")
            && !noMechPowers;
    }
}
