package ti4.contest.replay.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CombatReplayHouseTest {

    @Test
    void roleNamesMatchDiscordDelegationRoles() {
        assertEquals("Naalu Delegation", CombatReplayHouse.NAALU.roleName());
        assertEquals("Mentak Delegation", CombatReplayHouse.MENTAK.roleName());
        assertEquals("Hacan Delegation", CombatReplayHouse.HACAN.roleName());
    }
}
