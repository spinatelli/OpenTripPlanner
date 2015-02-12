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

package org.opentripplanner.graph_builder.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.services.GraphBuilder;
import org.opentripplanner.routing.algorithm.ANDijkstra;
import org.opentripplanner.routing.algorithm.ProfileDijkstra;
import org.opentripplanner.routing.algorithm.strategies.MultiTargetTerminationStrategy;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.edgetype.StreetTransitLink;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * {@link GraphBuilder} plugin that computes access nodes for every street node. Should be called
 * after both the transit network and street network are loaded.
 */
public class AccessNodeGraphBuilderImpl implements GraphBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(AccessNodeGraphBuilderImpl.class);

    private static final int STEP = 1;

    public List<String> provides() {
        return Arrays.asList("access nodes");
    }

    public List<String> getPrerequisites() {
        return Arrays.asList("streets", "transit"); // transit yes or no?
    }

    @Override
    public void buildGraph(Graph graph, HashMap<Class<?>, Object> extra) {
        LOG.info("Computing access nodes for street nodes...");

        // iterate over a copy of vertex list because it will be modified
        ArrayList<Vertex> vertices = new ArrayList<Vertex>();
        vertices.addAll(graph.getVertices());
        Map<String, Integer> vmap = new HashMap<String, Integer>();
        for (int i=0; i<vertices.size(); i++) {
            vmap.put(vertices.get(i).getLabel(), i);
        }

        graph.index(new DefaultStreetVertexIndexFactory());
        int stopsize = Sets.newHashSet(Iterables.filter(vertices, TransitStop.class)).size();

        long avgProfile = 0;
        long avgDijkstra = 0;
        // TransitStops are Access Node Candidates
        int counter = 0;
        for (TransitStop ts : Iterables.filter(vertices, TransitStop.class)) {
            // find out if the transit stop is linked to the street
            boolean linkedToStreet = false;
            for (Edge e : ts.getOutgoing()) {
                if (e instanceof StreetTransitLink) {
                    linkedToStreet = true;
                    break;
                }
            }

            // if not, we can skip computing access nodes
            if (!linkedToStreet)
                continue;

            /*
             * Full multi-modal backward profile search on the public transportation subnetwork of G
             * is performed, starting from a. This gives us travel time functions f(b) for each
             * access-node candidate b ∈ A, representing the time needed to get from node b to node
             * a in the subnetwork for any time of the day. The algorithm looks for all road nodes v
             * ∈ V for which a ∈ A(v), i.e. road nodes for which another access-node b ∈ A is
             * reached by entering the public transportation subnetwork in a at least once during
             * the day, which can be found in A^−1 (a).
             */
            RoutingRequest options = new RoutingRequest(TraverseMode.TRANSIT);
            options.to = (new GenericLocation(null, graph.getAgencyIds().iterator().next() + ":"
                    + ts));
            options.batch = true;
            options.parkAndRide = true;
            options.arriveBy = true;
            // options.setDummyRoutingContext(graph);
            options.setRoutingContext(graph);
            options.setNumItineraries(1);
            options.setModes(new TraverseModeSet(TraverseMode.WALK, TraverseMode.TRANSIT));

            Set<Vertex> stops = new HashSet<Vertex>(Sets.newHashSet(Iterables.filter(vertices,
                    TransitStop.class)));
            // LOG.info("SIZE IS "+stops.size());
            //
            ProfileDijkstra profileDijkstra = new ProfileDijkstra(options);
            profileDijkstra.setSearchTerminationStrategy(new MultiTargetTerminationStrategy(stops));

            State initial = new State(options);
            long time = System.currentTimeMillis();
            ShortestPathTree spt = profileDijkstra.getShortestPathTree(initial);
            long time2 = System.currentTimeMillis();
            avgProfile += (time2 - time);

            /*
             * uni-modal time-independent many-to-all Dijkstra search, restricted to the road
             * subnetwork The road network is assumed time-independent since in this case it is used
             * on foot
             * 
             * ts is inserted in the priority queue with key 0 and flag "covered"= true, the other
             * access nodes with their upper bound of the profile function above
             */
            List<State> states = new ArrayList<State>();
            for (State s : spt.getAllStates())
                if (s.getVertex() instanceof TransitStop)
                    states.add(s);

            RoutingRequest opt = new RoutingRequest(TraverseMode.WALK);
            opt.to = (new GenericLocation(null, graph.getAgencyIds().iterator().next() + ":" + ts));
            opt.batch = true;
            opt.setDummyRoutingContext(graph);
            opt.parkAndRide = true;
            opt.arriveBy = true;
            // opt.bikeParkAndRide = true;
            opt.setNumItineraries(1);
            opt.setModes(new TraverseModeSet(TraverseMode.WALK));
            ANDijkstra mmdijkstra = new ANDijkstra(opt);
            mmdijkstra.setSearchTerminationStrategy(new MultiTargetTerminationStrategy(
                    new HashSet<Vertex>(Sets.newHashSet(Iterables.filter(vertices,
                            IntersectionVertex.class)))));
            time = System.currentTimeMillis();
            initial.covered = true;
            spt = mmdijkstra.getShortestPathTree(initial, states);
            time2 = System.currentTimeMillis();
            avgDijkstra += (time2 - time);

            for (State s : spt.getAllStates()) {
                if (s.covered) {
                    if (s.getVertex() instanceof IntersectionVertex)
                        ((IntersectionVertex) (graph.getVertex(s.getVertex().getLabel()))).accessNodes
                                .add(ts);
                    if (s.getVertex() instanceof ParkAndRideVertex)
                        ((ParkAndRideVertex) (graph.getVertex(s.getVertex().getLabel()))).accessNodes
                                .add(ts);
                }
            }

            // BACKWARD?
            options = new RoutingRequest(TraverseMode.TRANSIT);
            options.from = (new GenericLocation(null, graph.getAgencyIds().iterator().next() + ":"
                    + ts));
            options.batch = true;
//            options.arriveBy = true;
//            options.parkAndRide = true;
            // options.setDummyRoutingContext(graph);
            options.setRoutingContext(graph);
            options.setNumItineraries(1);
            options.setModes(new TraverseModeSet(TraverseMode.WALK, TraverseMode.TRANSIT));

            profileDijkstra = new ProfileDijkstra(options);
            profileDijkstra.setSearchTerminationStrategy(new MultiTargetTerminationStrategy(stops));

            initial = new State(options);
            time = System.currentTimeMillis();
            ShortestPathTree bwdSPT = profileDijkstra.getShortestPathTree(initial);
            time2 = System.currentTimeMillis();
            avgProfile += (time2 - time);
            
            //
            states.clear();
            for (State s : bwdSPT.getAllStates())
                if (s.getVertex() instanceof TransitStop) {
                    s.setParkAndRide(true);
                    states.add(s);
                }
            opt = new RoutingRequest(TraverseMode.WALK);
            opt.from = (new GenericLocation(null, graph.getAgencyIds().iterator().next() + ":" + ts));
            opt.batch = true;
//            opt.arriveBy = true;
            opt.setDummyRoutingContext(graph);
            opt.parkAndRide = true;
            // opt.bikeParkAndRide = true;
            opt.setNumItineraries(1);
            opt.setModes(new TraverseModeSet(TraverseMode.WALK));
            mmdijkstra = new ANDijkstra(opt);
            mmdijkstra.setSearchTerminationStrategy(new MultiTargetTerminationStrategy(
                    new HashSet<Vertex>(Sets.newHashSet(Iterables.filter(vertices,
                            IntersectionVertex.class)))));
            time = System.currentTimeMillis();
            initial.covered = true;
            initial.setParkAndRide(true);
            spt = mmdijkstra.getShortestPathTree(initial, states);
            time2 = System.currentTimeMillis();
            avgDijkstra += (time2 - time);

            for (State s : spt.getAllStates()) {
                if (s.covered) {
                    if (s.getVertex() instanceof IntersectionVertex)
                        ((IntersectionVertex) (graph.getVertex(s.getVertex().getLabel()))).backwardAccessNodes
                                .add(ts);
                    if (s.getVertex() instanceof ParkAndRideVertex)
                        ((ParkAndRideVertex) (graph.getVertex(s.getVertex().getLabel()))).backwardAccessNodes
                                .add(ts);
                }
            }

            counter++;
            if (shouldLog(counter, stopsize)) {
                LOG.info("Upper bound search of " + (int) (counter * 100 / stopsize)
                        + "% of transit stops");
                LOG.info("Average profiling time " + ((double) avgProfile / counter));
                LOG.info("Average dijkstra time " + ((double) avgDijkstra / counter));
//                break;
            }
        }

        LOG.info("Computed " + counter + " out of " + stopsize);
        counter = 0;
        int bwd = 0;
        int fwd = 0;
        for (IntersectionVertex iv : Iterables.filter(vertices, IntersectionVertex.class)) {
            if (iv.accessNodes != null && iv.accessNodes.size() > 0) {
                // LOG.info("Access Nodes = " + iv.accessNodes.size());
                fwd++;
            }
            if (iv.backwardAccessNodes != null && iv.backwardAccessNodes.size() > 0) {
                // LOG.info("Access Nodes = " + iv.accessNodes.size());
                bwd++;
            }
            counter++;
        }

        LOG.info(fwd + " out of " + counter + " Intersection vertices have access nodes");
        LOG.info(bwd + " out of " + counter + " Intersection vertices have bwd access nodes");
        
        counter = 0;
        bwd = 0;
        fwd = 0;
        for (ParkAndRideVertex iv : Iterables.filter(vertices, ParkAndRideVertex.class)) {
            if (iv.accessNodes != null && iv.accessNodes.size() > 0) {
                // LOG.info("Access Nodes = " + iv.accessNodes.size());
                fwd++;
            }
            if (iv.backwardAccessNodes != null && iv.backwardAccessNodes.size() > 0) {
                // LOG.info("Access Nodes = " + iv.accessNodes.size());
                bwd++;
            }
            counter++;
        }

        LOG.info(fwd + " out of " + counter + " PNR nodes have access nodes");
        LOG.info(bwd + " out of " + counter + " PNR nodes have bwd access nodes");
        
        LOG.info("Done computing access nodes for street nodes...");
    }

    private boolean shouldLog(int counter, int stopsize) {
        return counter % (stopsize * STEP / 100) == 0;
    }

    @Override
    public void checkInputs() {
        // no inputs
    }
}
