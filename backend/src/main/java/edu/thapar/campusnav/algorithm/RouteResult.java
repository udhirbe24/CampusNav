package edu.thapar.campusnav.algorithm;

import java.util.List;
import java.util.Map;

/** A finished route ready to send to the frontend (serialised as snake_case JSON). */
public record RouteResult(
        List<String> pathNodes,
        List<NodeDetail> nodeDetails,
        Map<String, Object> geojson,
        List<Step> steps,
        Summary summary,
        Map<String, Object> emergencyFacility) {

    public record NodeDetail(String nodeKey, String name, double latitude, double longitude,
                             String nodeType, boolean isAccessible, Long buildingId) {
    }

    public record Step(int stepNumber, String instruction, double distanceMeters, String distanceText,
                       double bearing, String fromNode, String toNode, boolean hasStairs, boolean hasRamp,
                       boolean isArrival) {
    }

    /**
     * @param algorithm       the algorithm the engine picked automatically
     * @param algorithmReason one sentence on why it was picked
     * @param nodesExplored   nodes the algorithm settled before finding the answer
     */
    public record Summary(double totalDistanceMeters, String totalDistanceText, long totalTimeSeconds,
                          String totalTimeText, int nodeCount, String algorithm, String algorithmReason,
                          int nodesExplored, String mode, boolean hasStairs, boolean isAccessible) {
    }

    public RouteResult withEmergencyFacility(Map<String, Object> facility) {
        return new RouteResult(pathNodes, nodeDetails, geojson, steps, summary, facility);
    }
}
