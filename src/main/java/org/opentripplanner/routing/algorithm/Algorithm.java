package org.opentripplanner.routing.algorithm;

import org.opentripplanner.routing.algorithm.strategies.SearchTerminationStrategy;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.spt.ShortestPathTree;

public interface Algorithm {

    /**
     * Compute SPT using default timeout and termination strategy.
     */
    public ShortestPathTree getShortestPathTree(RoutingRequest req);

    /**
     * Compute SPT using default termination strategy.
     */
    public ShortestPathTree getShortestPathTree(RoutingRequest req, double relTimeoutSeconds);

    public ShortestPathTree getShortestPathTree(RoutingRequest options, double relTimeoutSeconds,
            SearchTerminationStrategy terminationStrategy);
    public void setTraverseVisitor(TraverseVisitor traverseVisitor);
}
