package ti4.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.model.AbilityModel;
import ti4.model.AgendaModel;
import ti4.model.CombatModifierModel;
import ti4.model.NamedCombatModifierModel;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;
import ti4.model.TileModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;
import ti4.service.emoji.CardEmojis;

public class CombatModHelper {

    private static Boolean IsModInScopeForUnits(
            List<UnitModel> units, CombatModifierModel modifier, CombatRollType rollType, Game game, Player player) {
        for (UnitModel unit : units) {
            if (modifier.isInScopeForUnit(unit, units, rollType, game, player)) {
                return true;
            }
        }
        return false;
    }

    public static List<NamedCombatModifierModel> getModifiers(
            Player player,
            Player opponent,
            Map<UnitModel, Integer> unitsByQuantity,
            Map<UnitModel, Integer> opponentUnitsByQuantity,
            TileModel tile,
            Game game,
            CombatRollType rollType,
            String modifierType) {
        List<NamedCombatModifierModel> modifiers = new ArrayList<>();
        Map<String, CombatModifierModel> combatModifiers = new HashMap<>(Mapper.getCombatModifiers());
        combatModifiers = new HashMap<>(combatModifiers.entrySet().stream()
                .filter(entry -> entry.getValue().getForCombatAbility() == rollType)
                .filter(entry -> entry.getValue().getType().equals(modifierType))
                .filter(entry -> !entry.getValue().getApplyToOpponent())
                .filter(entry -> IsModInScopeForUnits(
                        new ArrayList<>(unitsByQuantity.keySet()), entry.getValue(), rollType, game, player))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));

        for (String ability : player.getAbilities()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.ABILITY, ability))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(
                            relevantMod.get(),
                            tile,
                            player,
                            opponent,
                            unitsByQuantity,
                            opponentUnitsByQuantity,
                            game)) {
                AbilityModel abilityModel = Mapper.getAbility(ability);
                modifiers.add(new NamedCombatModifierModel(relevantMod.get(), abilityModel.getRepresentation()));
            }
        }

        for (String tech : player.getTechs()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.TECH, tech))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(
                            relevantMod.get(),
                            tile,
                            player,
                            opponent,
                            unitsByQuantity,
                            opponentUnitsByQuantity,
                            game)) {
                TechnologyModel technologyModel = Mapper.getTech(tech);
                modifiers.add(new NamedCombatModifierModel(relevantMod.get(), technologyModel.getRepresentation(true)));
            }
        }

        if (opponent != null
                && opponent != player
                && ((player != game.getActivePlayer() && opponent == game.getActivePlayer())
                        || player == game.getActivePlayer())) {
            for (String tech : opponent.getTechs()) {
                Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                        .filter(modifier -> modifier.isRelevantTo("opponent_tech", tech))
                        .findFirst();

                if (relevantMod.isPresent()
                        && checkModPassesCondition(
                                relevantMod.get(),
                                tile,
                                player,
                                opponent,
                                unitsByQuantity,
                                opponentUnitsByQuantity,
                                game)) {
                    TechnologyModel technologyModel = Mapper.getTech(tech);
                    modifiers.add(
                            new NamedCombatModifierModel(relevantMod.get(), technologyModel.getRepresentation(true)));
                }
            }
        }

        for (String relic : player.getRelics()) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.RELIC, relic))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(
                            relevantMod.get(),
                            tile,
                            player,
                            opponent,
                            opponentUnitsByQuantity,
                            unitsByQuantity,
                            game)) {
                RelicModel relicModel = Mapper.getRelic(relic);
                modifiers.add(new NamedCombatModifierModel(relevantMod.get(), relicModel.getSimpleRepresentation()));
            }
        }

        List<AgendaModel> lawAgendasTargetingPlayer = game.getLawsInfo().entrySet().stream()
                .filter(entry -> entry.getValue().equals(player.getFaction())
                        || entry.getValue().equals(player.getColor()))
                .map(entry -> Mapper.getAgenda(entry.getKey()))
                .toList();
        for (AgendaModel agenda : lawAgendasTargetingPlayer) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.AGENDA, agenda.getAlias()))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(
                            relevantMod.get(),
                            tile,
                            player,
                            opponent,
                            unitsByQuantity,
                            opponentUnitsByQuantity,
                            game)) {
                modifiers.add(
                        new NamedCombatModifierModel(relevantMod.get(), CardEmojis.Agenda + " " + agenda.getName()));
            }
        }

        List<UnitModel> unitsInCombat = new ArrayList<>(unitsByQuantity.keySet());
        for (UnitModel unit : unitsInCombat) {
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.UNIT, unit.getAlias()))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(
                            relevantMod.get(),
                            tile,
                            player,
                            opponent,
                            unitsByQuantity,
                            opponentUnitsByQuantity,
                            game)) {
                modifiers.add(new NamedCombatModifierModel(
                        relevantMod.get(), unit.getUnitEmoji() + " " + unit.getName() + " " + unit.getAbility()));
            }
        }

        for (Leader leader : game.playerUnlockedLeadersOrAlliance(player)) {
            if (leader.isExhausted() || leader.isLocked()) {
                continue;
            }
            Optional<CombatModifierModel> relevantMod = combatModifiers.values().stream()
                    .filter(modifier -> modifier.isRelevantTo(Constants.LEADER, leader.getId()))
                    .findFirst();

            if (relevantMod.isPresent()
                    && checkModPassesCondition(
                            relevantMod.get(),
                            tile,
                            player,
                            opponent,
                            unitsByQuantity,
                            opponentUnitsByQuantity,
                            game)) {
                modifiers.add(
                        new NamedCombatModifierModel(relevantMod.get(), Helper.getLeaderFullRepresentation(leader)));
            }
        }

        List<CombatModifierModel> customAlwaysRelveantMods = combatModifiers.values().stream()
                .filter(modifier -> modifier.isRelevantTo(Constants.CUSTOM, Constants.CUSTOM))
                .toList();
        for (CombatModifierModel relevantMod : customAlwaysRelveantMods) {
            if (checkModPassesCondition(
                    relevantMod, tile, player, opponent, unitsByQuantity, opponentUnitsByQuantity, game)) {
                modifiers.add(new NamedCombatModifierModel(
                        relevantMod, relevantMod.getRelated().getFirst().getMessage()));
            }
        }
        Set<NamedCombatModifierModel> set = new HashSet<>(modifiers);

        return new ArrayList<>(set);
    }

    public static Integer getCombinedModifierForUnit(
            UnitModel unit,
            Integer numOfUnit,
            List<NamedCombatModifierModel> modifiers,
            Player player,
            Player opponent,
            Game game,
            List<UnitModel> playerUnits,
            CombatRollType rollType,
            Tile tile,
            UnitHolder unitHolder) {
        int modsValue = 0;
        for (NamedCombatModifierModel namedModifier : modifiers) {
            CombatModifierModel modifier = namedModifier.getModifier();
            if (modifier.isInScopeForUnit(unit, playerUnits, rollType, game, player)) {
                Integer modValue = getVariableModValue(modifier, player, opponent, game, unit, tile, unitHolder);
                Integer perUnitCount = 1;
                if (modifier.getApplyEachForQuantity()) {
                    perUnitCount = numOfUnit;
                }
                modsValue += (modValue * perUnitCount);
            }
        }
        return modsValue;
    }

    private static Boolean checkModPassesCondition(
            CombatModifierModel modifier,
            TileModel onTile,
            Player player,
            Player opponent,
            Map<UnitModel, Integer> unitsByQuantity,
            Map<UnitModel, Integer> opponentUnitsByQuantity,
            Game game) {
        boolean meetsCondition = false;

        Tile tile = null;
        if (onTile != null) {
            tile = game.getTile(onTile.getId());
        }
        String condition = "";
        if (modifier != null && modifier.getCondition() != null) {
            condition = modifier.getCondition();
        }
        switch (condition) {
            case Constants.MOD_OPPONENT_TEKKLAR_PLAYER_OWNER -> {
                if (opponent != null
                        && (player.getPromissoryNotesOwned().stream().anyMatch("tekklar"::equals)
                                || player.getPromissoryNotesOwned().stream()
                                        .anyMatch("sigma_tekklar_legion"::equals))) {
                    meetsCondition = opponent.getTempCombatModifiers().stream()
                                    .anyMatch(mod -> "tekklar".equals(mod.getRelatedID())
                                            && mod.getRelatedType().equals(Constants.PROMISSORY_NOTES))
                            || opponent.getNewTempCombatModifiers().stream()
                                    .anyMatch(mod -> "tekklar".equals(mod.getRelatedID())
                                            && mod.getRelatedType().equals(Constants.PROMISSORY_NOTES));
                }
            }
            case Constants.MOD_OPPONENT_FRAG -> {
                if (opponent != null) {
                    meetsCondition = !opponent.getFragments().isEmpty();
                }
            }
            case Constants.MOD_OPPONENT_STOLEN_TECH -> {
                if (ButtonHelper.isLawInPlay(game, "articles_war")) {
                    return false;
                }
                if (opponent != null) {
                    String opponentFaction = opponent.getFaction();
                    if (opponentFaction.contains("keleres")) {
                        meetsCondition = player.getTechs().stream()
                                .map(Mapper::getTech)
                                .anyMatch(tech ->
                                        "keleres".equals(tech.getFaction().orElse("")));
                    } else {
                        meetsCondition = player.getTechs().stream()
                                .map(Mapper::getTech)
                                .anyMatch(tech -> tech.getFaction().orElse("").equals(opponentFaction));
                    }
                }
            }
            case Constants.MOD_PLANET_MR_LEGEND_HOME -> {
                if (player.getHomeSystemTile() != null
                        && onTile.getId().equals(player.getHomeSystemTile().getTileID())) {
                    meetsCondition = true;
                }
                if (onTile.getPlanets().stream()
                        .anyMatch(planetId -> StringUtils.isNotBlank(
                                Mapper.getPlanet(planetId).getLegendaryAbilityName()))) {
                    meetsCondition = true;
                }
                if (onTile.getPlanets().contains(Constants.MR)) {
                    meetsCondition = true;
                }
                if (game.getTile(onTile.getId()) != null) {
                    if (ButtonHelper.isTileLegendary(game.getTile(onTile.getId()))) {
                        meetsCondition = true;
                    }
                }
            }
            case Constants.MOD_HAS_FRAGILE ->
                meetsCondition = player.getAbilities().contains("fragile");
            case Constants.MOD_OPPONENT_NO_CC_FLEET ->
                meetsCondition = !player.getMahactCC().contains(opponent.getColor());
            case "next_to_structure" ->
                meetsCondition = (!ButtonHelperAgents.getAdjacentTilesWithStructuresInThem(player, game, tile)
                                .isEmpty()
                        || ButtonHelperAgents.doesTileHaveAStructureInIt(player, tile));
            case Constants.MOD_UNITS_TWO_MATCHING_NOT_FF -> {
                meetsCondition = false;
                if (unitsByQuantity.size() == 1) {
                    Entry<UnitModel, Integer> unitByQuantity = new ArrayList<>(unitsByQuantity.entrySet()).getFirst();
                    meetsCondition = unitByQuantity.getValue() == 2
                            && !"fighter"
                                    .equalsIgnoreCase(unitByQuantity.getKey().getBaseType());
                } else if (unitsByQuantity.size() == 2) {
                    Entry<UnitModel, Integer> unitByQuantity = new ArrayList<>(unitsByQuantity.entrySet()).get(0);
                    Entry<UnitModel, Integer> unitByQuantity2 = new ArrayList<>(unitsByQuantity.entrySet()).get(1);
                    String baseType1 = unitByQuantity.getKey().getBaseType();
                    String baseType2 = unitByQuantity2.getKey().getBaseType();
                    if ("fighter".equalsIgnoreCase(baseType1) || "fighter".equalsIgnoreCase(baseType2)) {
                        if (!"fighter".equalsIgnoreCase(baseType1)) {
                            meetsCondition = unitByQuantity.getValue() == 2;
                        } else {
                            meetsCondition = unitByQuantity2.getValue() == 2;
                        }
                    } else if (("flagship".equalsIgnoreCase(baseType1) || "lady".equalsIgnoreCase(baseType1))
                            && ("flagship".equalsIgnoreCase(baseType2) || "lady".equalsIgnoreCase(baseType2))) {
                        meetsCondition = true;
                    }
                } else if (unitsByQuantity.size() == 3) {
                    List<Entry<UnitModel, Integer>> entries = new ArrayList<>(unitsByQuantity.entrySet());
                    meetsCondition = entries.stream().limit(3).allMatch(entry -> {
                        String baseType = entry.getKey().getBaseType();
                        return "fighter".equalsIgnoreCase(baseType)
                                || "flagship".equalsIgnoreCase(baseType)
                                || "lady".equalsIgnoreCase(baseType);
                    });
                }
            }
            case Constants.MOD_NEBULA_DEFENDER -> {
                if ((onTile.isNebula() || tile.isNebula())
                        && !game.getActivePlayerID().equals(player.getUserID())
                        && !game.getStoredValue("mahactHeroTarget").equalsIgnoreCase(player.getFaction())) {
                    meetsCondition = true;
                }
            }
            case "arcane_defender" -> {
                if (tile != null) {
                    for (UnitHolder uH : tile.getPlanetUnitHolders()) {
                        if (uH.getTokenList().contains("attachment_arcane_citadel.png")) {
                            if (player.getPlanets().contains(uH.getName()) && game.getActivePlayer() != player) {
                                boolean allThere = true;
                                for (Entry<UnitModel, Integer> entry : unitsByQuantity.entrySet()) {
                                    if (uH.getUnitCount(entry.getKey().getUnitType(), player) != entry.getValue()) {
                                        allThere = false;
                                    }
                                }
                                if (allThere) {
                                    meetsCondition = true;
                                }
                            }
                        }
                    }
                }
            }
            case "vaylerianhero" -> {
                if (player == game.getActivePlayer()
                        && !game.getStoredValue("vaylerianHeroActive").isEmpty()) {
                    meetsCondition = true;
                }
            }
            case "tnelisopponentfs" -> {
                if (ButtonHelper.doesPlayerHaveFSHere("tnelis_flagship", opponent, tile)
                        && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)
                        && FoWHelper.playerHasShipsInSystem(player, tile)) {
                    meetsCondition = true;
                }
            }
            case "solagent" -> {
                if (game.getStoredValue("solagent").contains(player.getFaction())) {
                    meetsCondition = true;
                }
            }
            case "letnevagent" -> {
                if (game.getStoredValue("letnevagent").contains(player.getFaction())) {
                    meetsCondition = true;
                }
            }
            case "thalnosPlusOne" -> {
                if ("true".equalsIgnoreCase(game.getStoredValue("thalnosPlusOne"))) {
                    meetsCondition = true;
                }
            }
            case "nivyn_commander_damaged" -> {
                if (game.playerHasLeaderUnlockedOrAlliance(player, "nivyncommander")) {
                    meetsCondition = true;
                }
            }
            case "toldar_commander_particular" -> {
                if (game.playerHasLeaderUnlockedOrAlliance(player, "toldarcommander")) {
                    int ownUnits = 0;
                    int opponentUnits = 0;
                    for (Integer i : unitsByQuantity.values()) {
                        ownUnits += i;
                    }
                    for (Integer i : opponentUnitsByQuantity.values()) {
                        opponentUnits += i;
                    }
                    meetsCondition = ownUnits < opponentUnits;
                }
            }
            case "lizho_commander_particular" -> {
                if (game.playerHasLeaderUnlockedOrAlliance(player, "lizhocommander")) {
                    int nonFighter = 0;
                    int infantry = 0;
                    int ships = 0;
                    for (Entry<UnitModel, Integer> entry : unitsByQuantity.entrySet()) {
                        UnitModel unitM = entry.getKey();
                        if (unitM.getIsShip()) {
                            if (!"fighter".equalsIgnoreCase(unitM.getBaseType())) {
                                nonFighter += entry.getValue();
                            }
                            ships += entry.getValue();
                        } else {
                            if ("infantry".equalsIgnoreCase(unitM.getBaseType())) {
                                infantry += entry.getValue();
                            }
                        }
                    }
                    if (ships > 0 && nonFighter < 2) {
                        meetsCondition = true;
                    } else if (ships < 1 && infantry < 2) {
                        meetsCondition = true;
                    }
                }
            }
            case "naazFS" -> {
                if (ButtonHelper.doesPlayerHaveFSHere(
                                "naaz_flagship", player, game.getTileByPosition(game.getActiveSystem()))
                        || ButtonHelper.doesPlayerHaveFSHere(
                                "sigma_naazrokha_flagship_2", player, game.getTileByPosition(game.getActiveSystem()))) {
                    meetsCondition = true;
                }
            }
            case "sigma_argent_flagship_1" ->
                meetsCondition = ButtonHelper.doesPlayerHaveFSHere(
                        "sigma_argent_flagship_1", player, game.getTileByPosition(game.getActiveSystem()));
            case "sigma_argent_flagship_2" -> {
                meetsCondition = ButtonHelper.doesPlayerHaveFSHere("sigma_argent_flagship_2", player, tile);
                for (String adjPos :
                        FoWHelper.getAdjacentTilesAndNotThisTile(game, tile.getPosition(), player, false)) {
                    meetsCondition |= ButtonHelper.doesPlayerHaveFSHere(
                            "sigma_argent_flagship_2", player, game.getTileByPosition(adjPos));
                }
            }
            default -> meetsCondition = true;
        }
        return meetsCondition;
    }

    private static Integer getVariableModValue(
            CombatModifierModel mod,
            Player player,
            Player opponent,
            Game game,
            UnitModel origUnit,
            Tile activeSystem,
            UnitHolder unitHolder) {
        double value = mod.getValue().doubleValue();
        double multiplier = 1.0;
        long scalingCount = 0;
        if (mod.getValueScalingMultiplier() != null) {
            multiplier = mod.getValueScalingMultiplier();
        }
        if (StringUtils.isNotBlank(mod.getValueScalingType())) {
            switch (mod.getValueScalingType()) {
                case Constants.FRAGMENT -> {
                    if (player.isHasFoundCulFrag()) {
                        scalingCount += 1;
                    }
                    if (player.isHasFoundHazFrag()) {
                        scalingCount += 1;
                    }
                    if (player.isHasFoundIndFrag()) {
                        scalingCount += 1;
                    }
                    if (player.isHasFoundUnkFrag()) {
                        scalingCount += 1;
                    }
                }
                case "code" -> {
                    if (player.getHonorCounter() > 1) {
                        scalingCount += 1;
                        if (player.getHonorCounter() > 4) {
                            scalingCount += 1;
                            if (player.getHonorCounter() > 7) {
                                scalingCount += 1;
                            }
                        }
                    }

                    if (player.getDishonorCounter() > 1) {
                        scalingCount += 1;
                        if (player.getDishonorCounter() < 4) {
                            scalingCount += 1;
                            if (player.getDishonorCounter() < 7) {
                                scalingCount += 1;
                            }
                        }
                    }
                }
                case Constants.LAW -> scalingCount = game.getLaws().size();
                case Constants.MOD_OPPONENT_PO_EXCLUSIVE_SCORED -> {
                    if (opponent != null) {
                        var customPublicVPList = game.getCustomPublicVP();
                        List<List<String>> scoredPOUserLists = new ArrayList<>();
                        for (Entry<String, List<String>> entry :
                                game.getScoredPublicObjectives().entrySet()) {
                            // Ensure its actually a revealed PO not imperial or a relic
                            if (!customPublicVPList.containsKey(entry.getKey())) {
                                scoredPOUserLists.add(entry.getValue());
                            }
                        }
                        scalingCount = scoredPOUserLists.stream()
                                .filter(scoredPlayerList -> scoredPlayerList.contains(opponent.getUserID())
                                        && !scoredPlayerList.contains(player.getUserID()))
                                .count();
                    }
                }
                case Constants.UNIT_TECH ->
                    scalingCount = player.getTechs().stream()
                            .map(Mapper::getTech)
                            .filter(TechnologyModel::isUnitUpgrade)
                            .count();
                case Constants.MOD_DESTROYERS ->
                    scalingCount = ButtonHelper.getNumberOfUnitsOnTheBoard(game, player, "destroyer", false);
                case Constants.MOD_OPPONENT_NON_FIGHTER_SHIP ->
                    scalingCount += ButtonHelper.checkNumberNonFighterShips(opponent, activeSystem);
                case Constants.MOD_OPPONENT_SHIP ->
                    scalingCount += ButtonHelper.checkNumberShips(opponent, activeSystem);
                case "combat_round" -> {
                    int round;
                    String combatName = "combatRoundTracker" + player.getFaction() + activeSystem.getPosition()
                            + unitHolder.getName();
                    if (game.getStoredValue(combatName).isEmpty()) {
                        round = 0;
                    } else {
                        round = Integer.parseInt(game.getStoredValue(combatName)) - 1;
                    }
                    scalingCount += round;
                }

                case "adjacent_mech" -> {
                    for (String pos :
                            FoWHelper.getAdjacentTiles(game, activeSystem.getPosition(), player, false, true)) {
                        Tile tile = game.getTileByPosition(pos);
                        for (UnitHolder uH : tile.getUnitHolders().values()) {
                            for (Player p2 : game.getRealPlayers()) {
                                scalingCount += uH.getUnitCount(UnitType.Mech, p2);
                            }
                        }
                    }
                }
                case "adjacent_asteroid" -> {
                    for (String pos :
                            FoWHelper.getAdjacentTiles(game, activeSystem.getPosition(), player, false, true)) {
                        Tile tile = game.getTileByPosition(pos);
                        if (tile.isAsteroidField()) {
                            scalingCount += 1;
                        }
                    }
                }
                case "damaged_units_same_type" -> {
                    UnitHolder space = activeSystem.getUnitHolders().get("space");
                    if (origUnit.getIsGroundForce()
                            && !activeSystem.getPlanetUnitHolders().isEmpty()) {
                        for (UnitHolder planet : activeSystem.getPlanetUnitHolders()) {
                            if (planet.getUnitCount(
                                            Mapper.getUnitKey(
                                                            AliasHandler.resolveUnit(origUnit.getBaseType()),
                                                            player.getColorID())
                                                    .unitType(),
                                            player)
                                    > 0) {
                                space = planet;
                            }
                        }
                    }
                    int count = 0;
                    if (space.getUnitDamage()
                                    .get(Mapper.getUnitKey(
                                            AliasHandler.resolveUnit(origUnit.getBaseType()), player.getColorID()))
                            != null) {
                        count = space.getUnitDamage()
                                .get(Mapper.getUnitKey(
                                        AliasHandler.resolveUnit(origUnit.getBaseType()), player.getColorID()));
                    }
                    scalingCount += count;
                    scalingCount = Math.min(scalingCount, 2);
                }
                case Constants.MOD_OPPONENT_UNIT_TECH -> {
                    if (opponent != null) {
                        scalingCount = opponent.getTechs().stream()
                                .map(Mapper::getTech)
                                .filter(TechnologyModel::isUnitUpgrade)
                                .count();
                    }
                }
                case Constants.MOD_OPPONENT_FACTION_TECH -> {
                    if (opponent != null) {
                        scalingCount = opponent.getTechs().stream()
                                .map(Mapper::getTech)
                                .filter(tech ->
                                        StringUtils.isNotBlank(tech.getFaction().orElse("")))
                                .count();
                    }
                }
                default -> {}
            }
            value *= multiplier * scalingCount;
        }
        value = Math.floor(value); // to make sure eg +1 per 2 destroyer doesn't return 2.5 etc
        return (int) value;
    }
}
