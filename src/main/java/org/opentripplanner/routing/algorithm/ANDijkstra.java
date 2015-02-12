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
import org.opentripplanner.routing.edgetype.ParkAndRideEdge;
import org.opentripplanner.routing.edgetype.ParkAndRideLinkEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.spt.BasicShortestPathTree;
import org.opentripplanner.routing.spt.ShortestPathTree;
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

    private RemainingWeightHeuristic heuristic = new TrivialRemainingWeightHeuristic();

    public ANDijkstra(RoutingRequest options) {
        this.options = options;
    }

    public void setSearchTerminationStrategy(SearchTerminationStrategy searchTerminationStrategy) {
        this.searchTerminationStrategy = searchTerminationStrategy;
    }

    public void setSkipEdgeStrategy(SkipEdgeStrategy skipEdgeStrategy) {
        this.skipEdgeStrategy = skipEdgeStrategy;
    }

    public void setSkipTraverseResultStrategy(SkipTraverseResultStrategy skipTraverseResultStrategy) {
        this.skipTraverseResultStrategy = skipTraverseResultStrategy;
    }

    public ShortestPathTree getShortestPathTree(State initialState) {
        return getShortestPathTree(initialState, null);
    }
    
    public ShortestPathTree getShortestPathTree(State initialState, List<State> initialStates) {
        Vertex target = null;
        if (options.rctx != null) {
            target = initialState.getOptions().rctx.target;
        }
        ShortestPathTree spt = new BasicShortestPathTree(options);
        BinHeap<State> queue = new BinHeap<State>(1000);

        if (initialStates != null)
            for (State s : initialStates) {
                spt.add(s);
                queue.insert(s, s.getWeight());
            }

        spt.add(initialState);
        queue.insert(initialState, initialState.getWeight());

//        System.out.println("=======\nSearch\n=======");
        while (!queue.empty()) { // Until the priority queue is empty:
            State u = queue.extract_min();
            Vertex u_vertex = u.getVertex();
//            if(u.covered)
//                System.out.println("COvered");
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

            if (searchTerminationStrategy != null &&
                searchTerminationStrategy.shouldSearchTerminate(initialState.getVertex(), null, u, spt, options)) {
                break;
            }

            if (!(u.getVertex() instanceof ParkAndRideVertex)) {
                for (Edge edge : options.arriveBy ? u_vertex.getIncoming() : u_vertex.getOutgoing()) {
                    if (skipEdgeStrategy != null &&
                        skipEdgeStrategy.shouldSkipEdge(initialState.getVertex(), null, u, edge, spt, options)) {
                        continue;
                    }
                    State v = null;
                    if (edge instanceof ParkAndRideLinkEdge)
                        v = ((ParkAndRideLinkEdge) edge).traverse(u, true);
                    else 
                        v = edge.traverse(u);
                        
    //                if (u.covered)
    //                    System.out.println("EDGE "+edge.getClass().getName());
                    // Iterate over traversal results. When an edge leads nowhere (as indicated by
                    // returning NULL), the iteration is over.
                    for (; v != null; v = v.getNextResult()) {
    //                    if (u.covered)
    //                        System.out.println("STATE "+v);
                        if (skipTraverseResultStrategy != null &&
                            skipTraverseResultStrategy.shouldSkipTraversalResult(initialState.getVertex(), null, u, v, spt, options)) {
                            continue;
                        }
                        if (traverseVisitor != null) {
                            traverseVisitor.visitEdge(edge, v);
                        }
                        if (verbose) {
                            System.out.printf("  w = %f + %f = %f %s", u.getWeight(), v.getWeightDelta(), v.getWeight(), v.getVertex());
                        }
                        if (v.exceedsWeightLimit(options.maxWeight)) continue;
                        if (spt.add(v)) {
                            double estimate = heuristic.computeForwardWeight(v, target);
                            queue.insert(v, v.getWeight() + estimate);
                            if (traverseVisitor != null) traverseVisitor.visitEnqueue(v);
                        }
                    }
                }
            }
            spt.postVisit(u);
        }
        return spt;
    }

    public void setHeuristic(RemainingWeightHeuristic heuristic) {
        this.heuristic = heuristic;
    }
}
