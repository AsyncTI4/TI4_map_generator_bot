package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatReplayHouseTest {

    @Test
    void roleNamesMatchDiscordDelegationRoles() {
        assertEquals("NaaluDelegation", CombatReplayHouse.NAALU.roleName());
        assertEquals("MentakDelegation", CombatReplayHouse.MENTAK.roleName());
        assertEquals("HacanDelegation", CombatReplayHouse.HACAN.roleName());
    }
}
