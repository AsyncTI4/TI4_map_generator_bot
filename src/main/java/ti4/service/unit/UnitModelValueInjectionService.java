package ti4.service.unit;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Aeterna.AeternaUnitsHandler;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.model.UnitModel;
import ti4.service.franken.FrankenUnitService;
import ti4.service.game.MonumentsService;
import ti4.service.game.NekroMonumentService;

@UtilityClass
public class UnitModelValueInjectionService {

    public UnitModel injectPlayerUnitValues(Player player, UnitModel unit) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(unit);

        UnitModel injectedUnit = injectSingleUnitValues(player, unit);
        if (!FrankenUnitService.isDuplicateUnitCombiningEnabled(player)) {
            return injectedUnit;
        }
        List<UnitModel> matchingUnits = player.getUnitsOwned().stream()
                .map(Mapper::getUnit)
                .filter(Objects::nonNull)
                .filter(ownedUnit -> unit.getAsyncId().equalsIgnoreCase(ownedUnit.getAsyncId()))
                .toList();
        if (matchingUnits.size() < 2) {
            return injectedUnit;
        }
        return combineDuplicateUnits(
                injectedUnit,
                matchingUnits.stream()
                        .map(ownedUnit -> injectSingleUnitValues(player, ownedUnit))
                        .toList());
    }

    private UnitModel injectSingleUnitValues(Player player, UnitModel unit) {

        UnitValueInjection values = getPlayerUnitValueInjection(player, unit);
        UnitModel injectedUnit = values.isEmpty() ? unit : injectValues(unit, values);
        if (player.getGame().isMonumentsMode() && "nekro_monument".equals(unit.getId())) {
            if (injectedUnit == unit) {
                injectedUnit = copyUnit(unit);
            }
            List<UnitModel> copiedMonuments = NekroMonumentService.getCopiedMonuments(player.getGame(), player);
            String baseAbility = unit.getAbility().orElse("");
            int copiedAbilityStart = baseAbility.indexOf("\n\n**");
            if (copiedAbilityStart >= 0) {
                baseAbility = baseAbility.substring(0, copiedAbilityStart);
            }
            if (!copiedMonuments.isEmpty()) {
                injectedUnit.setMoveValue(Math.max(
                        unit.getMoveValue(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getMoveValue)
                                .max()
                                .orElse(0)));
                injectedUnit.setProductionValue(Math.max(
                        unit.getProductionValue(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getProductionValue)
                                .max()
                                .orElse(0)));
                injectedUnit.setCapacityValue(Math.max(
                        unit.getCapacityValue(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getCapacityValue)
                                .max()
                                .orElse(0)));
                injectedUnit.setFleetSupplyBonus(Math.max(
                        unit.getFleetSupplyBonus(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getFleetSupplyBonus)
                                .max()
                                .orElse(0)));
                injectedUnit.setCapacityUsed(Math.max(
                        unit.getCapacityUsed(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getCapacityUsed)
                                .max()
                                .orElse(0)));
                injectedUnit.setCost(Math.max(
                        unit.getCost(),
                        copiedMonuments.stream()
                                .map(UnitModel::getCost)
                                .max(Float::compare)
                                .orElse(0F)));
                injectedUnit.setCombatDieCount(Math.max(
                        unit.getCombatDieCount(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getCombatDieCount)
                                .max()
                                .orElse(0)));
                injectedUnit.setCombatHitsOn(copiedMonuments.stream()
                        .filter(monument -> monument.getCombatDieCount() > 0)
                        .mapToInt(UnitModel::getCombatHitsOn)
                        .min()
                        .orElse(unit.getCombatHitsOn()));
                injectedUnit.setAfbDieCount(Math.max(
                        unit.getAfbDieCount(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getAfbDieCount)
                                .max()
                                .orElse(0)));
                injectedUnit.setAfbHitsOn(copiedMonuments.stream()
                        .filter(monument -> monument.getAfbDieCount() > 0)
                        .mapToInt(UnitModel::getAfbHitsOn)
                        .min()
                        .orElse(unit.getAfbHitsOn()));
                injectedUnit.setBombardDieCount(Math.max(
                        unit.getBombardDieCount(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getBombardDieCount)
                                .max()
                                .orElse(0)));
                injectedUnit.setBombardHitsOn(copiedMonuments.stream()
                        .filter(monument -> monument.getBombardDieCount() > 0)
                        .mapToInt(UnitModel::getBombardHitsOn)
                        .min()
                        .orElse(unit.getBombardHitsOn()));
                injectedUnit.setSpaceCannonDieCount(Math.max(
                        unit.getSpaceCannonDieCount(),
                        copiedMonuments.stream()
                                .mapToInt(UnitModel::getSpaceCannonDieCount)
                                .max()
                                .orElse(0)));
                injectedUnit.setSpaceCannonHitsOn(copiedMonuments.stream()
                        .filter(monument -> monument.getSpaceCannonDieCount() > 0)
                        .mapToInt(UnitModel::getSpaceCannonHitsOn)
                        .min()
                        .orElse(unit.getSpaceCannonHitsOn()));
                injectedUnit.setDeepSpaceCannon(Boolean.TRUE.equals(unit.getDeepSpaceCannon())
                        || copiedMonuments.stream()
                                .anyMatch(monument -> Boolean.TRUE.equals(monument.getDeepSpaceCannon())));
                injectedUnit.setPlanetaryShield(Boolean.TRUE.equals(unit.getPlanetaryShield())
                        || copiedMonuments.stream()
                                .anyMatch(monument -> Boolean.TRUE.equals(monument.getPlanetaryShield())));
                injectedUnit.setSustainDamage(Boolean.TRUE.equals(unit.getSustainDamage())
                        || copiedMonuments.stream()
                                .anyMatch(monument -> Boolean.TRUE.equals(monument.getSustainDamage())));
                injectedUnit.setDisablesPlanetaryShield(Boolean.TRUE.equals(unit.getDisablesPlanetaryShield())
                        || copiedMonuments.stream()
                                .anyMatch(monument -> Boolean.TRUE.equals(monument.getDisablesPlanetaryShield())));
                injectedUnit.setCanBeDirectHit(Boolean.TRUE.equals(unit.getCanBeDirectHit())
                        || copiedMonuments.stream()
                                .anyMatch(monument -> Boolean.TRUE.equals(monument.getCanBeDirectHit())));
                injectedUnit.setIsGroundForce(Boolean.TRUE.equals(unit.getIsGroundForce())
                        || copiedMonuments.stream()
                                .anyMatch(monument -> Boolean.TRUE.equals(monument.getIsGroundForce())));
                String copiedAbilityText = copiedMonuments.stream()
                        .map(monument -> "**" + monument.getName() + "**: "
                                + monument.getAbility().orElse(""))
                        .collect(java.util.stream.Collectors.joining("\n"));
                if (copiedAbilityText.length() <= 1024) {
                    injectedUnit.setAbility(copiedAbilityText);
                } else {
                    injectedUnit.setAbility(copiedAbilityText.substring(0, 1021) + "...");
                }
            } else {
                injectedUnit.setAbility(baseAbility);
            }
        }
        return injectedUnit;
    }

    private UnitModel combineDuplicateUnits(UnitModel representative, List<UnitModel> units) {
        UnitModel combined = copyUnit(representative);
        List<UnitModel> displayedUnits =
                units.stream().filter(unit -> unit.getFaction().isPresent()).toList();
        if (displayedUnits.isEmpty()) {
            displayedUnits = units;
        }
        combined.setName(displayedUnits.stream()
                .map(UnitModel::getName)
                .distinct()
                .collect(java.util.stream.Collectors.joining(" / ")));
        combined.setMoveValue(
                units.stream().mapToInt(UnitModel::getMoveValue).max().orElse(combined.getMoveValue()));
        combined.setProductionValue(
                units.stream().mapToInt(UnitModel::getProductionValue).max().orElse(combined.getProductionValue()));
        combined.setBasicProduction(units.stream()
                .map(UnitModel::getBasicProduction)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(combined.getBasicProduction()));
        combined.setCapacityValue(
                units.stream().mapToInt(UnitModel::getCapacityValue).max().orElse(combined.getCapacityValue()));
        combined.setFleetSupplyBonus(
                units.stream().mapToInt(UnitModel::getFleetSupplyBonus).max().orElse(combined.getFleetSupplyBonus()));
        combined.setCapacityUsed(
                units.stream().mapToInt(UnitModel::getCapacityUsed).max().orElse(combined.getCapacityUsed()));
        combined.setCost(
                units.stream().map(UnitModel::getCost).max(Float::compare).orElse(combined.getCost()));
        combined.setCombatDieCount(
                units.stream().mapToInt(UnitModel::getCombatDieCount).max().orElse(combined.getCombatDieCount()));
        combined.setCombatHitsOn(getBestHitsOn(
                units, UnitModel::getCombatDieCount, UnitModel::getCombatHitsOn, combined.getCombatHitsOn()));
        combined.setAfbDieCount(
                units.stream().mapToInt(UnitModel::getAfbDieCount).max().orElse(combined.getAfbDieCount()));
        combined.setAfbHitsOn(
                getBestHitsOn(units, UnitModel::getAfbDieCount, UnitModel::getAfbHitsOn, combined.getAfbHitsOn()));
        combined.setBombardDieCount(
                units.stream().mapToInt(UnitModel::getBombardDieCount).max().orElse(combined.getBombardDieCount()));
        combined.setBombardHitsOn(getBestHitsOn(
                units, UnitModel::getBombardDieCount, UnitModel::getBombardHitsOn, combined.getBombardHitsOn()));
        combined.setSpaceCannonDieCount(units.stream()
                .mapToInt(UnitModel::getSpaceCannonDieCount)
                .max()
                .orElse(combined.getSpaceCannonDieCount()));
        combined.setSpaceCannonHitsOn(getBestHitsOn(
                units,
                UnitModel::getSpaceCannonDieCount,
                UnitModel::getSpaceCannonHitsOn,
                combined.getSpaceCannonHitsOn()));
        combined.setIsUpgrade(units.stream().anyMatch(UnitModel::getIsUpgrade));
        combined.setDeepSpaceCannon(units.stream().anyMatch(UnitModel::getDeepSpaceCannon));
        combined.setPlanetaryShield(units.stream().anyMatch(UnitModel::getPlanetaryShield));
        combined.setSustainDamage(units.stream().anyMatch(UnitModel::getSustainDamage));
        combined.setDisablesPlanetaryShield(units.stream().anyMatch(UnitModel::getDisablesPlanetaryShield));
        combined.setCanBeDirectHit(units.stream().anyMatch(UnitModel::getCanBeDirectHit));
        combined.setIsStructure(units.stream().anyMatch(UnitModel::getIsStructure));
        combined.setIsMonument(units.stream().anyMatch(UnitModel::getIsMonument));
        combined.setIsGroundForce(units.stream().anyMatch(UnitModel::getIsGroundForce));
        combined.setIsShip(units.stream().anyMatch(UnitModel::getIsShip));
        combined.setIsSpaceOnly(units.stream().anyMatch(UnitModel::getIsSpaceOnly));
        combined.setIsPlanetOnly(units.stream().anyMatch(UnitModel::getIsPlanetOnly));
        LinkedHashMap<String, String> abilities = new LinkedHashMap<>();
        for (UnitModel ownedUnit : displayedUnits) {
            ownedUnit
                    .getAbility()
                    .filter(ability -> !ability.isBlank())
                    .ifPresent(
                            ability -> abilities.putIfAbsent(ability, "**" + ownedUnit.getName() + "**: " + ability));
        }
        String combinedAbility = String.join("\n", abilities.values());
        combined.setAbility(
                combinedAbility.length() <= 1024 ? combinedAbility : combinedAbility.substring(0, 1021) + "...");
        return combined;
    }

    private int getBestHitsOn(
            List<UnitModel> units,
            java.util.function.ToIntFunction<UnitModel> dieCount,
            java.util.function.ToIntFunction<UnitModel> hitsOn,
            int fallback) {
        return units.stream()
                .filter(unit -> dieCount.applyAsInt(unit) > 0)
                .mapToInt(hitsOn)
                .min()
                .orElse(fallback);
    }

    // TODO: Add TF Nomad FS, 3 TF Mechs, TK Xxcha flag, Lightrail, PinkTF Flagship
    private UnitValueInjection getPlayerUnitValueInjection(Player player, UnitModel unit) {
        IntegerValueInjection integers = IntegerValueInjection.create();
        FloatValueInjection floats = FloatValueInjection.create();
        BooleanValueInjection booleans = BooleanValueInjection.create();

        if (player.hasTech("tharcanumpmy")
                && player.getPlanets().contains("fabricatestation")
                && unit.getUnitType() == UnitType.Flagship) {
            integers.productionValue(3);
        }

        if ("aeterna_flagship".equals(unit.getId())) {
            int tokenCount = AeternaUnitsHandler.getCryptTokenCount(player.getGame(), player);
            if (tokenCount > 0) {
                integers.capacityValue(2 * tokenCount);
            }
        }

        if (player.getGame().isMonumentsMode()
                && "pinktf_monument".equals(unit.getId())
                && player.getGame().getTileMap().values().stream()
                        .anyMatch(tile -> ButtonHelper.doesPlayerHaveUnitHere("pinktf_monument", player, tile))) {
            booleans.isGroundForce(true).isPlanetOnly(false).isSpaceOnly(false);
            if (player.hasUnit("tf-valefarprime")) {
                floats.cost(-1);
            }
        }

        if (player.hasAbility("evolved_warforms") && unit.getUnitType() == UnitType.Mech) {
            integers.moveValue(1);
            booleans.isShip(true).isPlanetOnly(false).isSpaceOnly(false);
        }

        if (player.hasUnlockedBreakthrough("xytherisbt")
                && player.hasUpgradedUnit("pds2")
                && unit.getUnitType() == UnitType.Pds) {
            integers.combatDieCount(1).combatHitsOn(7).capacityUsed(1);
            booleans.isGroundForce(true)
                    .isShip(true)
                    .isPlanetOnly(false)
                    .isSpaceOnly(false)
                    .sustainDamage(true)
                    .canBeDirectHit(true);
        }

        if (player.getGame().isMonumentsMode()
                && "toldar_monumenthonor".equals(unit.getId())
                && MonumentsService.hasMonument(player.getGame(), player, "toldar_monumenthonor")) {
            if (player.getHonorCounter() >= 2) {
                integers.productionValue(2);
            }
            if (player.getHonorCounter() >= 5) {
                booleans.planetaryShield(true);
            }
            if (player.getHonorCounter() == 8) {
                integers.spaceCannonDieCount(3).spaceCannonHitsOn(4);
            }
        }

        return UnitValueInjection.of(integers, floats, booleans);
    }

    public UnitModel injectValues(UnitModel unit, UnitValueInjection values) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(values);

        UnitModel injectedUnit = copyUnit(unit);
        applyIntegerValues(injectedUnit, values.integerValues());
        applyFloatValues(injectedUnit, values.floatValues());
        applyBooleanValues(injectedUnit, values.booleanValues());
        return injectedUnit;
    }

    /**
     * Applies a combat- or action-local value injection to a copied unit model.
     *
     * <p>The caller is responsible for storing and clearing the condition that makes this temporary injection apply.
     */
    public UnitModel injectTemporaryValues(UnitModel unit, UnitValueInjection values) {
        return injectValues(unit, values);
    }

    public UnitModel injectValues(UnitModel unit, IntegerValueInjection values) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(values);

        UnitModel injectedUnit = copyUnit(unit);
        applyIntegerValues(injectedUnit, values);
        return injectedUnit;
    }

    public UnitModel injectValues(UnitModel unit, FloatValueInjection values) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(values);

        UnitModel injectedUnit = copyUnit(unit);
        applyFloatValues(injectedUnit, values);
        return injectedUnit;
    }

    public UnitModel injectValues(UnitModel unit, BooleanValueInjection values) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(values);

        UnitModel injectedUnit = copyUnit(unit);
        applyBooleanValues(injectedUnit, values);
        return injectedUnit;
    }

    private void applyIntegerValues(UnitModel unit, IntegerValueInjection values) {
        if (values.moveValue != null) unit.setMoveValue(unit.getMoveValue() + values.moveValue);
        if (values.productionValue != null) unit.setProductionValue(unit.getProductionValue() + values.productionValue);
        if (values.capacityValue != null) unit.setCapacityValue(unit.getCapacityValue() + values.capacityValue);
        if (values.fleetSupplyBonus != null)
            unit.setFleetSupplyBonus(unit.getFleetSupplyBonus() + values.fleetSupplyBonus);
        if (values.capacityUsed != null) unit.setCapacityUsed(unit.getCapacityUsed() + values.capacityUsed);
        if (values.combatHitsOn != null) unit.setCombatHitsOn(unit.getCombatHitsOn() + values.combatHitsOn);
        if (values.combatDieCount != null) unit.setCombatDieCount(unit.getCombatDieCount() + values.combatDieCount);
        if (values.afbHitsOn != null) unit.setAfbHitsOn(unit.getAfbHitsOn() + values.afbHitsOn);
        if (values.afbDieCount != null) unit.setAfbDieCount(unit.getAfbDieCount() + values.afbDieCount);
        if (values.bombardHitsOn != null) unit.setBombardHitsOn(unit.getBombardHitsOn() + values.bombardHitsOn);
        if (values.bombardDieCount != null) unit.setBombardDieCount(unit.getBombardDieCount() + values.bombardDieCount);
        if (values.spaceCannonHitsOn != null)
            unit.setSpaceCannonHitsOn(unit.getSpaceCannonHitsOn() + values.spaceCannonHitsOn);
        if (values.spaceCannonDieCount != null)
            unit.setSpaceCannonDieCount(unit.getSpaceCannonDieCount() + values.spaceCannonDieCount);
    }

    private void applyFloatValues(UnitModel unit, FloatValueInjection values) {
        if (values.cost != null) unit.setCost(unit.getCost() + values.cost);
    }

    private void applyBooleanValues(UnitModel unit, BooleanValueInjection values) {
        if (values.isUpgrade != null) unit.setIsUpgrade(values.isUpgrade);
        if (values.deepSpaceCannon != null) unit.setDeepSpaceCannon(values.deepSpaceCannon);
        if (values.planetaryShield != null) unit.setPlanetaryShield(values.planetaryShield);
        if (values.sustainDamage != null) unit.setSustainDamage(values.sustainDamage);
        if (values.disablesPlanetaryShield != null) unit.setDisablesPlanetaryShield(values.disablesPlanetaryShield);
        if (values.canBeDirectHit != null) unit.setCanBeDirectHit(values.canBeDirectHit);
        if (values.isStructure != null) unit.setIsStructure(values.isStructure);
        if (values.isMonument != null) unit.setIsMonument(values.isMonument);
        if (values.isGroundForce != null) unit.setIsGroundForce(values.isGroundForce);
        if (values.isShip != null) unit.setIsShip(values.isShip);
        if (values.isSpaceOnly != null) unit.setIsSpaceOnly(values.isSpaceOnly);
        if (values.isPlanetOnly != null) unit.setIsPlanetOnly(values.isPlanetOnly);
    }

    private UnitModel copyUnit(UnitModel unit) {
        UnitModel copy = new UnitModel();
        for (Field field : UnitModel.class.getDeclaredFields()) {
            try {
                field.setAccessible(true);
                Object value = field.get(unit);
                if (value instanceof List<?> list) {
                    value = new ArrayList<>(list);
                }
                field.set(copy, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not copy unit value " + field.getName(), e);
            }
        }
        return copy;
    }

    public record UnitValueInjection(
            IntegerValueInjection integerValues, FloatValueInjection floatValues, BooleanValueInjection booleanValues) {

        public UnitValueInjection {
            integerValues = integerValues == null ? IntegerValueInjection.empty() : integerValues;
            floatValues = floatValues == null ? FloatValueInjection.empty() : floatValues;
            booleanValues = booleanValues == null ? BooleanValueInjection.empty() : booleanValues;
        }

        public static UnitValueInjection empty() {
            return new UnitValueInjection(null, null, null);
        }

        public static UnitValueInjection of(IntegerValueInjection integerValues) {
            return new UnitValueInjection(integerValues, null, null);
        }

        public static UnitValueInjection of(FloatValueInjection floatValues) {
            return new UnitValueInjection(null, floatValues, null);
        }

        public static UnitValueInjection of(BooleanValueInjection booleanValues) {
            return new UnitValueInjection(null, null, booleanValues);
        }

        public static UnitValueInjection of(
                IntegerValueInjection integerValues,
                FloatValueInjection floatValues,
                BooleanValueInjection booleanValues) {
            return new UnitValueInjection(integerValues, floatValues, booleanValues);
        }

        private boolean isEmpty() {
            return integerValues.isEmpty() && floatValues.isEmpty() && booleanValues.isEmpty();
        }
    }

    public static final class IntegerValueInjection {
        private Integer moveValue;
        private Integer productionValue;
        private Integer capacityValue;
        private Integer fleetSupplyBonus;
        private Integer capacityUsed;
        private Integer combatHitsOn;
        private Integer combatDieCount;
        private Integer afbHitsOn;
        private Integer afbDieCount;
        private Integer bombardHitsOn;
        private Integer bombardDieCount;
        private Integer spaceCannonHitsOn;
        private Integer spaceCannonDieCount;

        private IntegerValueInjection() {}

        public static IntegerValueInjection create() {
            return new IntegerValueInjection();
        }

        private static IntegerValueInjection empty() {
            return new IntegerValueInjection();
        }

        public IntegerValueInjection moveValue(int moveValue) {
            this.moveValue = moveValue;
            return this;
        }

        public IntegerValueInjection productionValue(int productionValue) {
            this.productionValue = productionValue;
            return this;
        }

        public IntegerValueInjection capacityValue(int capacityValue) {
            this.capacityValue = capacityValue;
            return this;
        }

        public IntegerValueInjection fleetSupplyBonus(int fleetSupplyBonus) {
            this.fleetSupplyBonus = fleetSupplyBonus;
            return this;
        }

        public IntegerValueInjection capacityUsed(int capacityUsed) {
            this.capacityUsed = capacityUsed;
            return this;
        }

        public IntegerValueInjection combatHitsOn(int combatHitsOn) {
            this.combatHitsOn = combatHitsOn;
            return this;
        }

        public IntegerValueInjection combatDieCount(int combatDieCount) {
            this.combatDieCount = combatDieCount;
            return this;
        }

        public IntegerValueInjection afbHitsOn(int afbHitsOn) {
            this.afbHitsOn = afbHitsOn;
            return this;
        }

        public IntegerValueInjection afbDieCount(int afbDieCount) {
            this.afbDieCount = afbDieCount;
            return this;
        }

        public IntegerValueInjection bombardHitsOn(int bombardHitsOn) {
            this.bombardHitsOn = bombardHitsOn;
            return this;
        }

        public IntegerValueInjection bombardDieCount(int bombardDieCount) {
            this.bombardDieCount = bombardDieCount;
            return this;
        }

        public IntegerValueInjection spaceCannonHitsOn(int spaceCannonHitsOn) {
            this.spaceCannonHitsOn = spaceCannonHitsOn;
            return this;
        }

        public IntegerValueInjection spaceCannonDieCount(int spaceCannonDieCount) {
            this.spaceCannonDieCount = spaceCannonDieCount;
            return this;
        }

        private boolean isEmpty() {
            return moveValue == null
                    && productionValue == null
                    && capacityValue == null
                    && fleetSupplyBonus == null
                    && capacityUsed == null
                    && combatHitsOn == null
                    && combatDieCount == null
                    && afbHitsOn == null
                    && afbDieCount == null
                    && bombardHitsOn == null
                    && bombardDieCount == null
                    && spaceCannonHitsOn == null
                    && spaceCannonDieCount == null;
        }
    }

    public static final class FloatValueInjection {
        private Float cost;

        private FloatValueInjection() {}

        public static FloatValueInjection create() {
            return new FloatValueInjection();
        }

        private static FloatValueInjection empty() {
            return new FloatValueInjection();
        }

        public FloatValueInjection cost(float cost) {
            this.cost = cost;
            return this;
        }

        private boolean isEmpty() {
            return cost == null;
        }
    }

    public static final class BooleanValueInjection {
        private Boolean isUpgrade;
        private Boolean deepSpaceCannon;
        private Boolean planetaryShield;
        private Boolean sustainDamage;
        private Boolean disablesPlanetaryShield;
        private Boolean canBeDirectHit;
        private Boolean isStructure;
        private Boolean isMonument;
        private Boolean isGroundForce;
        private Boolean isShip;
        private Boolean isSpaceOnly;
        private Boolean isPlanetOnly;

        private BooleanValueInjection() {}

        public static BooleanValueInjection create() {
            return new BooleanValueInjection();
        }

        private static BooleanValueInjection empty() {
            return new BooleanValueInjection();
        }

        public BooleanValueInjection isUpgrade(boolean isUpgrade) {
            this.isUpgrade = isUpgrade;
            return this;
        }

        public BooleanValueInjection deepSpaceCannon(boolean deepSpaceCannon) {
            this.deepSpaceCannon = deepSpaceCannon;
            return this;
        }

        public BooleanValueInjection planetaryShield(boolean planetaryShield) {
            this.planetaryShield = planetaryShield;
            return this;
        }

        public BooleanValueInjection sustainDamage(boolean sustainDamage) {
            this.sustainDamage = sustainDamage;
            return this;
        }

        public BooleanValueInjection disablesPlanetaryShield(boolean disablesPlanetaryShield) {
            this.disablesPlanetaryShield = disablesPlanetaryShield;
            return this;
        }

        public BooleanValueInjection canBeDirectHit(boolean canBeDirectHit) {
            this.canBeDirectHit = canBeDirectHit;
            return this;
        }

        public BooleanValueInjection isStructure(boolean isStructure) {
            this.isStructure = isStructure;
            return this;
        }

        public BooleanValueInjection isMonument(boolean isMonument) {
            this.isMonument = isMonument;
            return this;
        }

        public BooleanValueInjection isGroundForce(boolean isGroundForce) {
            this.isGroundForce = isGroundForce;
            return this;
        }

        public BooleanValueInjection isShip(boolean isShip) {
            this.isShip = isShip;
            return this;
        }

        public BooleanValueInjection isSpaceOnly(boolean isSpaceOnly) {
            this.isSpaceOnly = isSpaceOnly;
            return this;
        }

        public BooleanValueInjection isPlanetOnly(boolean isPlanetOnly) {
            this.isPlanetOnly = isPlanetOnly;
            return this;
        }

        private boolean isEmpty() {
            return isUpgrade == null
                    && deepSpaceCannon == null
                    && planetaryShield == null
                    && sustainDamage == null
                    && disablesPlanetaryShield == null
                    && canBeDirectHit == null
                    && isStructure == null
                    && isMonument == null
                    && isGroundForce == null
                    && isShip == null
                    && isSpaceOnly == null
                    && isPlanetOnly == null;
        }
    }
}
