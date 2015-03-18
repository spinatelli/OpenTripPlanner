/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.opentripplanner.routing.algorithm;

import java.util.List;

import org.opentripplanner.common.pqueue.BinHeap;
import org.opentripplanner.routing.algorithm.strategies.RemainingWeightHeuristic;
import org.opentripplanner.routing.algorithm.strategies.SearchTerminationStrategy;
import org.opentripplanner.routing.algorithm.strategies.SkipEdgeStrategy;
import org.opentripplanner.routing.algorithm.strategies.SkipTraverseResultStrategy;
import org.opentripplanner.routing.algorithm.strategies.TrivialRemainingWeightHeuristic;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.edgetype.ParkAndRideLinkEdge;
import org.opentripplanner.routing.edgetype.StreetBikeParkLink;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.BikeParkVertex;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;

/**
 * Find the shortest path between graph vertices using Dijkstra's algorithm.
 */
public class ANDijkstra {

    private RoutingRequest options;

    public SearchTerminationStrategy searchTerminationStrategy;

    public SkipEdgeStrategy skipEdgeStrategy;

    public SkipTraverseResultStrategy skipTraverseResultStrategy;

    public TraverseVisitor traverseVisitor;

    private boolean verbose = false;

    private double heuristicCoeff = 1.0;

    private static final int MAX_DIVISOR = 4;

    private static final int MIN_DIVISOR = 1;

    private RemainingWeightHeuristic heuristic = new TrivialRemainingWeightHeuristic();

    public ANDijkstra(RoutingRequest options) {
        this.options = options;
    }

    public void setSearchTerminationStrategy(SearchTerminationStrategy searchTerminationStrategy) {
        this.searchTerminationStrategy = searchTerminationStrategy;
    }

    public void setHeuristicCoefficient(double coeff) {
        if (coeff > 1.0)
            return;
        heuristicCoeff = coeff;
    }

    public void setSkipEdgeStrategy(SkipEdgeStrategy skipEdgeStrategy) {
        this.skipEdgeStrategy = skipEdgeStrategy;
    }

    public void setSkipTraverseResultStrategy(SkipTraverseResultStrategy skipTraverseResultStrategy) {
        this.skipTraverseResultStrategy = skipTraverseResultStrategy;
    }

    public ShortestPathTree getShortestPathTree(State initialState) {
        return getShortestPathTree(initialState, null, false);
    }

    private double computeDelayCoefficient(double min, double max, double weight) {
        return (MAX_DIVISOR - MIN_DIVISOR) * (weight - min) / (max - min) + MIN_DIVISOR;
    }

    public ShortestPathTree getShortestPathTree(State initialState, List<State> initialStates,
            boolean bikeParkings) {
        ShortestPathTree spt = new DominanceFunction.MinimumWeight()
                .getNewShortestPathTree(options);
        BinHeap<State> queue = new BinHeap<State>(1000);

        if (initialStates != null) {
            double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
            double w = 0;
            for (State s : initialStates) {
                w = s.getWeight();
                if (w > max)
                    max = w;
                if (w < min)
                    min = w;
            }

            for (State s : initialStates) {
                spt.add(s);
                w = s.getWeight();
                double d;
                if (heuristicCoeff < 0) {
                    d = computeDelayCoefficient(min, max, w);
                    d *= d;
                } else {
                    d = heuristicCoeff;
                }
                queue.insert(s, w * d);
            }
        }
        spt.add(initialState);
        queue.insert(initialState, initialState.getWeight());

        while (!queue.empty()) { // Until the priority queue is empty:
            State u = queue.extract_min();
            Vertex u_vertex = u.getVertex();

            if (traverseVisitor != null) {
                traverseVisitor.visitVertex(u);
            }

            if (!spt.getStates(u_vertex).contains(u)) {
                continue;
            }

            if (verbose) {
                System.out.println("min," + u.getWeight());
                System.out.println(u_vertex);
            }

            if (searchTerminationStrategy != null
                    && searchTerminationStrategy.shouldSearchTerminate(initialState.getVertex(),
                            null, u, spt, options)) {
                break;
            }

            if (!(u.getVertex() instanceof ParkAndRideVertex && !bikeParkings)
                    || !(u.getVertex() instanceof BikeParkVertex && bikeParkings)) {

                for (Edge edge : options.arriveBy ? u_vertex.getIncoming() : u_vertex.getOutgoing()) {
                    if (skipEdgeStrategy != null
                            && skipEdgeStrategy.shouldSkipEdge(initialState.getVertex(), null, u,
                                    edge, spt, options)) {
                        continue;
                    }
                    State v = null;
                    if (edge instanceof ParkAndRideLinkEdge)
                        v = ((ParkAndRideLinkEdge) edge).traverse(u, true);
                    else if (edge instanceof StreetBikeParkLink)
                        v = ((StreetBikeParkLink) edge).traverse(u, true);
                    else
                        v = edge.traverse(u);

                    // Iterate over traversal results. When an edge leads nowhere (as indicated by
                    // returning NULL), the iteration is over.
                    for (; v != null; v = v.getNextResult()) {
                        if (skipTraverseResultStrategy != null
                                && skipTraverseResultStrategy.shouldSkipTraversalResult(
                                        initialState.getVertex(), null, u, v, spt, options)) {
                            continue;
                        }
                        if (traverseVisitor != null) {
                            traverseVisitor.visitEdge(edge, v);
                        }
                        if (verbose) {
                            System.out.printf("  w = %f + %f = %f %s", u.getWeight(),
                                    v.getWeightDelta(), v.getWeight(), v.getVertex());
                        }
                        if (v.exceedsWeightLimit(options.maxWeight))
                            continue;
                        if (spt.add(v)) {
                            double estimate = heuristic.estimateRemainingWeight(v);
                            queue.insert(v, v.getWeight() + estimate);
                            if (traverseVisitor != null)
                                traverseVisitor.visitEnqueue(v);
                        }
                    }
                }
            }
        }

        return spt;
    }

    public void setHeuristic(RemainingWeightHeuristic heuristic) {
        this.heuristic = heuristic;
    }
}
