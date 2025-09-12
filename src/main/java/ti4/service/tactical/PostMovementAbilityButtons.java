package ti4.service.tactical;

import java.util.List;
import ti4.service.tactical.postmovement.AtokeraHeroButton;
import ti4.service.tactical.postmovement.BombardmentButton;
import ti4.service.tactical.postmovement.CombatDronesButton;
import ti4.service.tactical.postmovement.GhostCommanderButton;
import ti4.service.tactical.postmovement.KhraskCommanderButton;
import ti4.service.tactical.postmovement.MirvedaCommanderButton;
import ti4.service.tactical.postmovement.MoveAvernusButton;
import ti4.service.tactical.postmovement.MuaatHeroButton;
import ti4.service.tactical.postmovement.NokarAgentButton;
import ti4.service.tactical.postmovement.RaghsCallButton;
import ti4.service.tactical.postmovement.RiftUsedButton;
import ti4.service.tactical.postmovement.RohdhnaHeroButton;
import ti4.service.tactical.postmovement.SardakkCommanderButton;
import ti4.service.tactical.postmovement.SardakkHeroButton;
import ti4.service.tactical.postmovement.ShroudOfLithButton;
import ti4.service.tactical.postmovement.TnelisAgentButton;
import ti4.service.tactical.postmovement.ZelianAgentButton;
import ti4.service.tactical.postmovement.ZelianHeroButton;

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
            new MoveAvernusButton(),
            new MuaatHeroButton(),
            new ZelianHeroButton(),
            new SardakkHeroButton(),
            new AtokeraHeroButton(),
            new RohdhnaHeroButton(),
            new BombardmentButton());
}
