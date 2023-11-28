package ti4.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Storage;

public class FactionModelTest {
    
    @BeforeAll
    public static void init() {
        TileHelper.init();
        PositionMapper.init();
        Mapper.init();
        AliasHandler.init();
        Storage.init();
    }

    @Test
    public void testFactions() {
        for (FactionModel faction : Mapper.getFactions()) {
            assertTrue(faction.isValid());
            assertTrue(validateAbilities(faction));
            assertTrue(validateFactionTech(faction));
            assertTrue(validateHomeSystem(faction));
            assertTrue(validateHomePlanets(faction));
            assertTrue(validateStartingTech(faction));
            assertTrue(validateLeaders(faction));
            assertTrue(validatePromissoryNotes(faction));
            assertTrue(validateUnits(faction));
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
        if (TileHelper.getAllPlanets().keySet().containsAll(faction.getHomePlanets())) return true;
        List<String> invalidPlanetIDs = new ArrayList<>();
        for (String planetID : faction.getHomePlanets()) {
            if (!TileHelper.getAllPlanets().containsKey(planetID)) invalidPlanetIDs.add(planetID);
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid home planet IDs: `" + invalidPlanetIDs + "`");
        return false;
    }

    private static boolean validateHomeSystem(FactionModel faction) {
        if (TileHelper.getAllTiles().containsKey(faction.getHomeSystem()) || faction.getHomeSystem().isEmpty()) {
            return true;
        }
        System.out.println("Faction **" + faction.getAlias() + "** failed validation due to invalid home system IDs: `" + faction.getHomeSystem() + "`");
        return false;
    }

    private static boolean validateStartingTech(FactionModel faction) {
        if (Mapper.getTechs().keySet().containsAll(faction.getStartingTech())) return true;
        List<String> invalidStartingTechIDs = new ArrayList<>();
        for (String startingTechID : faction.getStartingTech()) {
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
}
