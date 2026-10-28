package ti4.service.leader.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.routing.AnnotationHandlerTestAccess;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.testUtils.BaseTi4Test;

/**
 * Buttons posted before the agent modules existed are still sitting in live games. Each old prefix must keep a
 * handler, either a shim into the module or a step the module kept.
 */
class LegacyAgentButtonRoutingTest extends BaseTi4Test {

    private static final List<String> LEGACY_PREFIXES = List.of(
            "mahactA_follow_",
            "useNaaluAgent_",
            "useXinAgent",
            "selectXinAgentPlayer_",
            "useKaltrimAgent_",
            "useQhetAgent",
            "useAtokeraAgent",
            "selectBelkoseaAgentSystem_",
            "natauAgentOffer_",
            "arvaxiAgentPickTarget_",
            "toldarAgent_",
            "dream_agent_offer_",
            "useBelkoseaAgent",
            "selectAtokeraAgentPlayer_",
            "arvaxiAgentOffer_",
            "dream_agent_choose_",
            "useEdynAgentBr_",
            "useCrystellumAgent_",
            "selectOtherCrystellumAgentTarget",
            "selectCrystellumAgentTarget_",
            "placeCrystellumAgentFighters_",
            "thronesAgentSelectTarget",
            "thronesAgentUseOn_",
            "thronesAgentChooseShip_",
            "thronesAgentPlaceShip_",
            "ponthousagent_",
            "ponthousAgentTarget_",
            "sendRikkaReminderMessage",
            "useHazorusOnSelf_",
            "selectHazorusTarget",
            "useHazorusOnOther_",
            "purgeHazorusFragment_",
            "useMorphaAgent",
            "morphaAgentTarget_",
            "morphaAgentTakeDiscard_",
            "useThurvialiAgentOnSelf",
            "useThurvialiAgentOnOther",
            "selectThurvialiAgentTarget_",
            "selectThurvialiAgentSource_",
            "selectThurvialiAgentDestination_");

    @Test
    void everyLegacyAgentButtonPrefixStillHasAHandler() {
        Set<String> handled = new TreeSet<>();
        for (Class<?> klass : AnnotationHandlerTestAccess.allHandlerClasses()) {
            for (Method method : klass.getDeclaredMethods()) {
                for (ButtonHandler handler : method.getAnnotationsByType(ButtonHandler.class)) {
                    handled.add(handler.value());
                }
            }
        }

        List<String> unrouted = new ArrayList<>(LEGACY_PREFIXES);
        unrouted.removeAll(handled);

        assertThat(unrouted).isEmpty();
    }
}
