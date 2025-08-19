package ti4.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Web payload for committing movement to a target system from multiple sources.
 *
 * Expected JSON shape:
 * {
 *   "targetPosition": "305",
 *   "displacement": {
 *     "101-space": [
 *       { "unitType": "cv", "colorID": "blue", "counts": [2, 0] },
 *       { "unitType": "ff", "colorID": "blue", "counts": [4, 0] }
 *     ],
 *     "202-planetName": [
 *       { "unitType": "gf", "colorID": "blue", "counts": [1, 0] }
 *     ]
 *   }
 * }
 */
public record MovementDisplacementRequest(String targetPosition, Map<String, List<MovementUnitCount>> displacement) {}
