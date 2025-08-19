package ti4.service.tactical;

import java.util.List;
import ti4.service.tactical.postmovement.*;

public final class PostMovementAbilityButtons {
    private PostMovementAbilityButtons() {}

    public static final List<PostMovementAbilityButton> ABILITIES = List.of(
            new SardakkCommanderButton(),
            new RaghsCallButton(),
            new RiftUsedButton(),
            new CombatDronesButton(),
            new ShroudOfLithButton(),
            new MirvedaCommanderButton(),
            new GhostCommanderButton(),
            new KhraskCommanderButton(),
            new NokarAgentButton(),
            new TnelisAgentButton(),
            new ZelianAgentButton(),
            new MuaatHeroButton(),
            new ZelianHeroButton(),
            new SardakkHeroButton(),
            new AtokeraHeroButton(),
            new RohdhnaHeroButton(),
            new BombardmentButton());
}
