package ti4.ai.tactical;

import lombok.experimental.UtilityClass;

@UtilityClass
class GatherProgress {

    static final double SHARE_AT_GATE = 0.5;
    static final double COST_PROXY = 0.8;
    static final double LATER_LANDING_SHARE = 0.5;

    static double share(double spaceWin, double gate) {
        if (gate <= 0) return SHARE_AT_GATE;
        double reached = Math.clamp(spaceWin / gate, 0, 1);
        return SHARE_AT_GATE * reached * reached;
    }

    static double share(double spaceWin, double gate, double strengthShare) {
        if (gate <= 0) return SHARE_AT_GATE;
        double reached = Math.clamp(Math.max(spaceWin / gate, COST_PROXY * strengthShare), 0, 1);
        return SHARE_AT_GATE * reached;
    }

    static double strengthShare(double joinedFleetCost, double takingFleetCost) {
        if (takingFleetCost <= 0) return 1;
        return Math.clamp(joinedFleetCost / takingFleetCost, 0, 1);
    }

    static double landingShare(int groundForces, int defenders) {
        if (groundForces <= 0) return 0;
        return Math.min(1, groundForces / (defenders + 1.0));
    }

    static double laterLandingShare(int capacity, int defenders, boolean canFetch) {
        if (!canFetch || capacity <= 0) return 0;
        return LATER_LANDING_SHARE * Math.min(1, capacity / (defenders + 1.0));
    }

    static double value(double prize, double spaceWin, double gate) {
        return Math.max(0, prize) * share(spaceWin, gate);
    }

    static double value(double prize, double spaceWin, double gate, double strengthShare) {
        return Math.max(0, prize) * share(spaceWin, gate, strengthShare);
    }
}
