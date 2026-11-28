package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.PaymentRules;
import ti4.ai.scoring.Wallet;
import ti4.ai.secrets.SecretDrawValue;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
class SecretCards {

    static final String IMPERSONATION = "impersonation";
    private static final int IMPERSONATION_INFLUENCE = 3;

    static AiCard impersonation() {
        return AiActionCards.componentWithResolveHook(
                IMPERSONATION,
                SecretCards::impersonationPlan,
                "resolveImpersonation",
                List.of(),
                null,
                SecretCards::expectPayment);
    }

    static Optional<Plan> impersonationPlan(Game game, Player seat) {
        return InfluencePayments.payment(game, seat, IMPERSONATION_INFLUENCE)
                .map(payment -> Plan.of(SecretDrawValue.of(game, seat) - InfluencePayments.cost(game, seat, payment)));
    }

    private static void expectPayment(AiTurnContext context) {
        Optional<Wallet.Payment> payment =
                InfluencePayments.payment(context.game(), context.seat(), IMPERSONATION_INFLUENCE);
        payment.ifPresent(
                found -> PaymentRules.expect(context, "Impersonation", found, InfluencePayments.EXHAUST_DONE));
    }
}
