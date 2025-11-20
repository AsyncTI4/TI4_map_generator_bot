package ti4.model.enums;

public class CombatMod {
    public enum CombatModType {
        result_modifier,
        extra_rolls,
        bonus_hits,
    }

    public enum CombatModPersistence {
        ALWAYS,
        CONDITIONAL,

        // Temporary persistence
        ONE_ROUND,
        ONE_TACTICAL_ACTION,
        ONE_COMBAT,
    }

    public enum CombatModScaling {
        adjacent_mech,
        adjacent_asteroid,
        adjacent_anomaly,
        combat_round,
        damaged_units_same_type,
        destroyers,
        fragment,
        galvanized_unit_count,
        law,
        unique_ships,
        opponent_faction_tech,
        opponent_non_fighter_ship,
        opponent_sftt,
        opponent_ship,
        opponent_unit_tech,
        po_opponent_exclusively_scored,
        raw_rolls_gt_8,
        nonhome_system_with_planet,
        unit_tech,
    }

    public enum CombatModCondition {
        // base/pok
        opponent_tekklar_player_owner,
        opponent_frag,
        opponent_stolen_faction_tech,
        planet_mr_legendary_home,
        has_ability_fragile,
        naazFS,
        nebula_defender,
        thalnosPlusOne,
        solagent,
        letnevagent,

        // te
        opponent_has_sftt,
        galvanized,
        spacecombat,
        fracture_combat,

        // ds
        opponent_no_cc_fleet,
        units_two_matching_not_ff,
        nivyn_commander_damaged,
        lizho_commander_particular,
        vaylerianhero,
        tnelisopponentfs,
        next_to_structure,

        // flagshipping
        sigma_argent_flagship_1,
        sigma_argent_flagship_2,
    }

    public enum CombatModRelatedType {
        ability,
        tech,
        opponent_tech,
        leader,
        relic,
        agenda,
        unit,
        custom,
        action_cards,
        promissory_notes,
        breakthrough,
    }
}
