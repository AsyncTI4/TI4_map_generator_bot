package ti4.ai.secrets;

import ti4.image.Mapper;
import ti4.model.SecretObjectiveModel;

public enum SecretPhase {
    STATUS,
    ACTION,
    AGENDA,
    UNKNOWN;

    public static SecretPhase of(String secretId) {
        SecretObjectiveModel model = Mapper.getSecretObjective(secretId);
        String phase = model == null || model.getPhase() == null ? "" : model.getPhase();
        if ("status".equalsIgnoreCase(phase)) return STATUS;
        if ("action".equalsIgnoreCase(phase)) return ACTION;
        if ("agenda".equalsIgnoreCase(phase)) return AGENDA;
        return UNKNOWN;
    }
}
