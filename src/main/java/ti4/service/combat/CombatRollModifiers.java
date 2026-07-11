package ti4.service.combat;

import java.util.List;
import ti4.model.NamedCombatModifierModel;

public record CombatRollModifiers(
        List<NamedCombatModifierModel> combatModifiers,
        List<NamedCombatModifierModel> extraRolls,
        List<NamedCombatModifierModel> temporaryModifiers) {}
