package ti4.service.combat.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import ti4.game.Player;
import ti4.service.combat.v2.CombatV2RollData.Context;
import ti4.service.combat.v2.CombatV2RollData.Round;

class CombatV2ButtonsTest {

    @Test
    void preservesEstablishedCombatButtonIds() {
        Context context = mock(Context.class);
        Player opponent = mock(Player.class);
        when(context.getTilePosition()).thenReturn("101");
        when(context.holderName()).thenReturn("mecatolrex");
        when(context.unitHolderName()).thenReturn("mecatolrex");
        when(opponent.factionButtonChecker()).thenReturn("FFCC_hacan_");

        assertEquals(
                "startThalnos_101_mecatolrex",
                CombatV2Buttons.thalnos(context).getFirst().getCustomId());
        assertEquals(
                "FFCC_hacan_autoAssignAFBHits_101_2",
                CombatV2Buttons.antiFighterBarrage(context, opponent, 2)
                        .getFirst()
                        .getCustomId());
        assertEquals(
                "combatRoll_101_mecatolrex",
                CombatV2Buttons.nextRound(context, new Round(2, 1, "round"), opponent)
                        .getFirst()
                        .getCustomId());
        assertEquals(
                "getDamageButtons_101_bombardment",
                CombatV2Buttons.bombardmentAssignment(context, 3).getFirst().getCustomId());
        assertEquals(
                "FFCC_hacan_autoAssignGroundHits_mecatolrex_2",
                CombatV2Buttons.groundAssignment(context, opponent, 2, new ArrayList<>())
                        .getFirst()
                        .getCustomId());
        assertEquals(
                "FFCC_hacan_autoAssignSpaceHits_101_2",
                CombatV2Buttons.spaceAssignment(context, opponent, 2, new ArrayList<>())
                        .getFirst()
                        .getCustomId());
        assertEquals(
                "FFCC_hacan_autoAssignSpaceCannonOffenceHits_101_2",
                CombatV2Buttons.spaceCannonOffense(context, opponent, 2)
                        .getFirst()
                        .getCustomId());
        assertEquals(
                "getDamageButtons_102_bombardment",
                CombatV2Buttons.dragonBombardment("102", 2).getFirst().getCustomId());
    }
}
