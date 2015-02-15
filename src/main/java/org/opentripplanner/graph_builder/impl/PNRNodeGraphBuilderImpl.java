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
import org.opentripplanner.routing.algorithm.GenericDijkstra;
import org.opentripplanner.routing.algorithm.ProfileDijkstra;
import org.opentripplanner.routing.algorithm.strategies.MultiTargetTerminationStrategy;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.StateEditor;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.edgetype.ParkAndRideEdge;
import org.opentripplanner.routing.edgetype.ParkAndRideLinkEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

/**
 * {@link GraphBuilder} plugin that computes access nodes for every street node. Should be called
 * after both the transit network and street network are loaded.
 */
public class PNRNodeGraphBuilderImpl implements GraphBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(PNRNodeGraphBuilderImpl.class);

    private static final int STEP = 5;

    public List<String> provides() {
        return Arrays.asList("pnr nodes");
    }

    public List<String> getPrerequisites() {
        return Arrays.asList("streets", "transit"); // transit yes or no?
    }

    @Override
    public void buildGraph(Graph graph, HashMap<Class<?>, Object> extra) {
        LOG.info("Computing PNR nodes for street nodes...");

        // iterate over a copy of vertex list because it will be modified
        ArrayList<Vertex> vertices = new ArrayList<Vertex>();
        vertices.addAll(graph.getVertices());
        Map<String, Integer> vmap = new HashMap<String, Integer>();
        for (int i = 0; i < vertices.size(); i++) {
            vmap.put(vertices.get(i).getLabel(), i);
        }

        // graph.index(new DefaultStreetVertexIndexFactory());
        int stopsize = Sets.newHashSet(Iterables.filter(vertices, ParkAndRideVertex.class)).size();
        Set<Vertex> parkings = new HashSet<Vertex>(Sets.newHashSet(Iterables.filter(vertices,
                ParkAndRideVertex.class)));
        Set<Vertex> intersections = new HashSet<Vertex>(Sets.newHashSet(Iterables.filter(vertices,
                IntersectionVertex.class)));
        long avgProfile = 0;
        long avgDijkstra = 0;
        int counter = 0;

        Map<Vertex, Map<Vertex, Double>> profiles = new HashMap<Vertex, Map<Vertex, Double>>();
        long time, time2;
        RoutingRequest options = new RoutingRequest();
        LOG.info("Size of PNR candidate set is " + stopsize);
        stopsize = 0;
        for (ParkAndRideVertex ts : Iterables.filter(vertices, ParkAndRideVertex.class)) {
            // find out if the transit stop is linked to the street
            boolean linkedToStreet = false;
            // TODO: heres's the problem
            for (Edge e : ts.getOutgoing()) {
                if (e instanceof ParkAndRideLinkEdge) {
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
            options.to = new GenericLocation(ts.getLat(), ts.getLon());
            options.from = new GenericLocation(ts.getLat(), ts.getLon());
            options.batch = true;
            options.setDummyRoutingContext(graph);
            options.batch = true;
            options.arriveBy = true;
            options.parkAndRide = true;
            options.setNumItineraries(1);
            options.setModes(new TraverseModeSet(TraverseMode.WALK, TraverseMode.TRANSIT));

            ProfileDijkstra profileDijkstra = new ProfileDijkstra(options);
            profileDijkstra.setSearchTerminationStrategy(new MultiTargetTerminationStrategy(
                    parkings));
            State initial = new State(ts, new ParkAndRideEdge(ts), options.getSecondsSinceEpoch(),
                    options);
            // TODO: maybe
            initial.setCarParked(true);

            time = System.currentTimeMillis();
            ShortestPathTree spt = profileDijkstra.getShortestPathTree(initial);
            time2 = System.currentTimeMillis();
            avgProfile += (time2 - time);

            /*
             * uni-modal time-independent many-to-all Dijkstra search, restricted to the road
             * subnetwork The road network is assumed time-independent since in this case it is used
             * on foot
             * 
             * ts is inserted in the priority queue with key 0 and flag "covered"= true, the other
             * access nodes with their upper bound of the profile function above
             */
            // List<State> states = new ArrayList<State>();
            for (State s : spt.getAllStates()) {
                Vertex v = s.getVertex();
                if (v instanceof ParkAndRideVertex && !v.equals(ts)) {
                    if (!profiles.containsKey(v))
                        profiles.put(v, new HashMap<Vertex, Double>());
                    profiles.get(v).put(ts, s.getWeight());
                }
            }
            stopsize++;
        }

        LOG.info("Size of PNR set is " + stopsize);
        for (ParkAndRideVertex ts : Iterables.filter(vertices, ParkAndRideVertex.class)) {
            // find out if the transit stop is linked to the street
            boolean linkedToStreet = false;
            // TODO: heres's the problem
            for (Edge e : ts.getOutgoing()) {
                if (e instanceof ParkAndRideLinkEdge) {
                    linkedToStreet = true;
                    break;
                }
            }

            // if not, we can skip computing access nodes
            if (!linkedToStreet)
                continue;

            List<State> states = new ArrayList<State>();
            for (Vertex v : profiles.keySet()) {
                if (!v.equals(ts) && profiles.get(v).containsKey(ts) && profiles.containsKey(ts)) {
                    State s = new State(v, options);
                    StateEditor s1 = s.edit(null);
                    s1.incrementWeight(profiles.get(ts).get(v));
                    s1.incrementWeight(profiles.get(v).get(ts));
                    states.add(s1.makeState());
                }
            }

            RoutingRequest opt = new RoutingRequest();
            opt.to = new GenericLocation(ts.getLat(), ts.getLon());
            opt.from = new GenericLocation(ts.getLat(), ts.getLon());
            opt.batch = true;
            opt.setDummyRoutingContext(graph);
            opt.parkAndRide = true;
            opt.arriveBy = true;
            opt.setNumItineraries(1);
            opt.setModes(new TraverseModeSet(TraverseMode.WALK, TraverseMode.CAR));
            GenericDijkstra mmdijkstra = new GenericDijkstra(opt, true);
            mmdijkstra.setSearchTerminationStrategy(new MultiTargetTerminationStrategy(
                    intersections));
            time = System.currentTimeMillis();
            State initial = new State(ts, new ParkAndRideEdge(ts), options.getSecondsSinceEpoch(),
                    opt);
            initial.covered = true;
            ShortestPathTree spt = mmdijkstra.getShortestPathTree(initial, states);
            time2 = System.currentTimeMillis();
            avgDijkstra += (time2 - time);

            for (State s : spt.getAllStates()) {
                if (s.covered && s.getVertex() instanceof IntersectionVertex) {
                    ((IntersectionVertex) (graph.getVertex(s.getVertex().getLabel()))).pnrNodes
                            .add(ts);
                }
            }

            counter++;
            if (shouldLog(counter, stopsize)) {
                LOG.info("Upper bound search of " + (int) (counter * 100 / stopsize)
                        + "% of parking nodes");
                LOG.info("Average profiling time " + ((double) avgProfile / counter));
                LOG.info("Average dijkstra time " + ((double) avgDijkstra / counter));
                // break;
            }
        }

        LOG.info("Computed " + counter + " out of " + stopsize);
        counter = 0;
        int fwd = 0;
        for (IntersectionVertex iv : Iterables.filter(vertices, IntersectionVertex.class)) {
            if (iv.pnrNodes != null && iv.pnrNodes.size() > 0)
                fwd++;
            counter++;
        }

        LOG.info(fwd + " out of " + counter + " have PNR nodes");

        LOG.info("Done computing PNR nodes for street nodes...");
    }

    private boolean shouldLog(int counter, int stopsize) {
        return counter % (stopsize * STEP / 100) == 0;
    }

    @Override
    public void checkInputs() {
        // no inputs
    }
}
