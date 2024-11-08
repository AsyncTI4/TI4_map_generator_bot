package ti4.model;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.testUtils.BaseTi4Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionModelTest extends BaseTi4Test {
    @Test
    void testFactions() {
        for (FactionModel faction : Mapper.getFactions()) {
            assertTrue(faction.isValid(), faction.getAlias() + ": invalid");
            assertTrue(validateAbilities(faction), faction.getAlias() + ": invalid Abilities");
            assertTrue(validateFactionTech(faction), faction.getAlias() + ": invalid FactionTech: " + faction.getFactionTech());
            assertTrue(validateHomeSystem(faction), faction.getAlias() + ": invalid HomeSystem");
            assertTrue(validateHomePlanets(faction), faction.getAlias() + ": invalid HomePlanets");
            assertTrue(validateStartingTech(faction), faction.getAlias() + ": invalid StartingTech");
            assertTrue(validateLeaders(faction), faction.getAlias() + ": invalid Leaders");
            assertTrue(validatePromissoryNotes(faction), faction.getAlias() + ": invalid PromissoryNotes");
            assertTrue(validateUnits(faction), faction.getAlias() + ": invalid Units");
            assertTrue(validateHomebrewReplacesID(faction), faction.getAlias() + ": invalid HomebrewReplacesID");
        }
    }

    private static boolean validateLeaders(FactionModel faction) {
        if (Mapper.getLeaders().keySet().containsAll(faction.getLeaders())) return true;
        List<String> invalidLeaderIDs = new ArrayList<>();
        for (String leaderID : faction.getLeaders()) {
            if (!Mapper.getLeaders().containsKey(leaderID)) invalidLeaderIDs.add(leaderID);
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid leader IDs: `" + invalidLeaderIDs + "`");
        return false;
    }

    private static boolean validateUnits(FactionModel faction) {
        if (Mapper.getUnits().keySet().containsAll(faction.getUnits())) return true;
        List<String> invalidUnitIDs = new ArrayList<>();
        for (String unitID : faction.getUnits()) {
            if (!Mapper.getUnits().containsKey(unitID)) invalidUnitIDs.add(unitID);
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid unit IDs: `" + invalidUnitIDs + "`");
        return false;
    }

    private static boolean validatePromissoryNotes(FactionModel faction) {
        if (Mapper.getPromissoryNotes().keySet().containsAll(faction.getPromissoryNotes())) return true;
        List<String> invalidPromissoryNoteIDs = new ArrayList<>();
        for (String promissoryNoteID : faction.getPromissoryNotes()) {
            if (!Mapper.getPromissoryNotes().containsKey(promissoryNoteID)) invalidPromissoryNoteIDs.add(promissoryNoteID);
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid promissory note IDs: `" + invalidPromissoryNoteIDs + "`");
        return false;
    }

    private static boolean validateAbilities(FactionModel faction) {
        if (Mapper.getAbilities().keySet().containsAll(faction.getAbilities())) return true;
        List<String> invalidAbilityIDs = new ArrayList<>();
        for (String abilityID : faction.getAbilities()) {
            if (!Mapper.getAbilities().containsKey(abilityID)) invalidAbilityIDs.add(abilityID);
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid ability IDs: `" + invalidAbilityIDs + "`");
        return false;
    }

    private static boolean validateHomePlanets(FactionModel faction) {
        if (TileHelper.getPlanetIdsToPlanetModels().keySet().containsAll(faction.getHomePlanets())) return true;
        List<String> invalidPlanetIDs = new ArrayList<>();
        for (String planetID : faction.getHomePlanets()) {
            if (!TileHelper.getPlanetIdsToPlanetModels().containsKey(planetID)) invalidPlanetIDs.add(planetID);
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid home planet IDs: `" + invalidPlanetIDs + "`");
        return false;
    }

    private static boolean validateHomeSystem(FactionModel faction) {
        if (TileHelper.getTileIdsToTileModels().containsKey(faction.getHomeSystem()) || faction.getHomeSystem().isEmpty()) {
            return true;
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid home system IDs: `" + faction.getHomeSystem() + "`");
        return false;
    }

    private static boolean validateStartingTech(FactionModel faction) {
        List<String> testTechIDs = new ArrayList<>();
        if (faction.getStartingTech() != null) testTechIDs.addAll(faction.getStartingTech());
        if (faction.getStartingTechOptions() != null) testTechIDs.addAll(faction.getStartingTechOptions());
        if (Mapper.getTechs().keySet().containsAll(testTechIDs)) return true;

        List<String> invalidStartingTechIDs = new ArrayList<>();
        for (String startingTechID : testTechIDs) {
            if (!Mapper.getTechs().containsKey(startingTechID)) invalidStartingTechIDs.add(startingTechID);
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid starting tech IDs: `" + invalidStartingTechIDs + "`");
        return false;
    }

    private static boolean validateFactionTech(FactionModel faction) {
        if (Mapper.getTechs().keySet().containsAll(faction.getFactionTech())) return true;
        List<String> invalidFactionTechIDs = new ArrayList<>();
        for (String factionTechID : faction.getFactionTech()) {
            if (!Mapper.getTechs().containsKey(factionTechID)) invalidFactionTechIDs.add(factionTechID);
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid faction tech IDs: `" + invalidFactionTechIDs + "`");
        return false;
    }

    private boolean validateHomebrewReplacesID(FactionModel faction) {
        if (faction.getHomebrewReplacesID().isEmpty()) return true;
        if (Mapper.isValidFaction(faction.getHomebrewReplacesID().get())) return true;
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid HomebrewReplacesID: `" + faction.getHomebrewReplacesID().get() + "`");
        return false;
    }
}
