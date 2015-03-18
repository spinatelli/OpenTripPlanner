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

package org.opentripplanner.graph_builder.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.services.GraphBuilderModule;
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
import org.opentripplanner.routing.vertextype.BikeParkVertex;
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
public class AccessNodeModule implements GraphBuilderModule {

    private static final Logger LOG = LoggerFactory.getLogger(AccessNodeModule.class);

    private static final int STEP = 1;
    private double heuristicCoefficient = 1.0;

    private GenericLocation location;

    public List<String> provides() {
        return Arrays.asList("access nodes");
    }

    public List<String> getPrerequisites() {
        return Arrays.asList("streets", "transit"); // transit yes or no?
    }
    
    public void setHeuristicCoefficient(double coeff) {
        if(coeff > 1.0)
            return;
        heuristicCoefficient = coeff;
    }

    @Override
    public void buildGraph(Graph graph, HashMap<Class<?>, Object> extra) {
        LOG.info("Computing access nodes for street/PNR/bikePNR nodes...");

        // iterate over a copy of vertex list because it will be modified
        List<Vertex> vertices = new ArrayList<Vertex>();
        vertices.addAll(graph.getVertices());

        if (graph.streetIndex == null)
            graph.index(new DefaultStreetVertexIndexFactory());
        Set<Vertex> stops = new HashSet<Vertex>(Sets.newHashSet(Iterables.filter(vertices,
                TransitStop.class)));

        computeAccessNodes(graph, vertices, stops, false);
        computeAccessNodes(graph, vertices, stops, true);
        
        printIntersectionStats(vertices);
        printPNRStats(vertices);
        printBikePNRStats(vertices);

        LOG.info("Done computing access nodes for street/PNR/bikePNR nodes...");
    }

    private void computeAccessNodes(Graph graph, List<Vertex> vertices, Set<Vertex> stops,
            boolean bikeParkings) {
        int stopsize = stops.size();
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

            location = new GenericLocation(null, graph.getAgencyIds().iterator().next() + ":" + ts);

            /*
             * Full multi-modal backward profile search on the public transportation subnetwork of G
             * is performed, starting from a. This gives us travel time functions f(b) for each
             * access-node candidate b ∈ A, representing the time needed to get from node b to node
             * a in the subnetwork for any time of the day. The algorithm looks for all road nodes v
             * ∈ V for which a ∈ A(v), i.e. road nodes for which another access-node b ∈ A is
             * reached by entering the public transportation subnetwork in a at least once during
             * the day, which can be found in A^−1 (a).
             */
            // batch, PNR, bike PNR, arriveBy, traversemodes, graph, dummy routing context
            RoutingRequest options = buildOptions(true, !bikeParkings, bikeParkings, true,
                    new TraverseModeSet(TraverseMode.WALK, TraverseMode.TRANSIT), graph, false);

            State initial = new State(options);
            long time = System.currentTimeMillis();
            List<State> states = doProfileSearch(initial, options, stops);
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
            RoutingRequest opt = buildOptions(true, !bikeParkings, bikeParkings, true,
                    new TraverseModeSet(TraverseMode.WALK), graph, true);

            time = System.currentTimeMillis();
            Collection<? extends State> anStates = forwardExploration(initial, opt, vertices,
                    states, bikeParkings);
            time2 = System.currentTimeMillis();
            avgDijkstra += (time2 - time);
            setAccessNodes(graph, anStates, ts, !bikeParkings);

            states.clear();

            // BACKWARD
            options = buildOptions(true, false, bikeParkings, false, new TraverseModeSet(
                    TraverseMode.WALK, TraverseMode.TRANSIT), graph, false);

            initial = new State(options);
            time = System.currentTimeMillis();
            states = doProfileSearch(initial, options, stops);
            time2 = System.currentTimeMillis();
            avgProfile += (time2 - time);

            for (State s : states) {
                if (bikeParkings) {
                    s.setBikeParkAndRide(true);
                    s.setBikeParked(true);
                } else
                    s.setParkAndRide(true);
            }

            opt = buildOptions(true, !bikeParkings, bikeParkings, false, new TraverseModeSet(
                    TraverseMode.WALK), graph, true);
            time = System.currentTimeMillis();
            if (bikeParkings) {
                initial.setBikeParkAndRide(true);
                initial.setBikeParked(true);
            } else
                initial.setParkAndRide(true);
            anStates = forwardExploration(initial, opt, vertices, states, bikeParkings);
            time2 = System.currentTimeMillis();
            avgDijkstra += (time2 - time);
            setBackwardAccessNodes(graph, anStates, ts, !bikeParkings);

            counter++;
            if (shouldLog(counter, stopsize)) {
                LOG.info("Upper bound search of " + (int) (counter * 100 / stopsize)
                        + "% of transit stops");
                LOG.info("Average profiling time " + ((double) avgProfile / counter));
                LOG.info("Average dijkstra time " + ((double) avgDijkstra / counter));
                // break;
            }
        }

        LOG.info("Computed " + counter + " out of " + stopsize);
    }

    private Collection<? extends State> forwardExploration(State initial, RoutingRequest opt,
            List<Vertex> vertices, List<State> states, boolean bikeParkings) {
        ANDijkstra mmdijkstra = new ANDijkstra(opt);
        mmdijkstra.setHeuristicCoefficient(heuristicCoefficient);
        Set<Vertex> term = new HashSet<Vertex>(Sets.newHashSet(Iterables.filter(vertices,
                IntersectionVertex.class)));
        // TODO: are these two needed?
        term.addAll(Sets.newHashSet(Iterables.filter(vertices, ParkAndRideVertex.class)));
        term.addAll(Sets.newHashSet(Iterables.filter(vertices, BikeParkVertex.class)));

        mmdijkstra.setSearchTerminationStrategy(new MultiTargetTerminationStrategy(term));
        initial.covered = true;
        ShortestPathTree spt = mmdijkstra.getShortestPathTree(initial, states, bikeParkings);
        return spt.getAllStates();
    }

    private List<State> doProfileSearch(State initial, RoutingRequest options, Set<Vertex> stops) {

        ProfileDijkstra profileDijkstra = new ProfileDijkstra(options);
        profileDijkstra.setSearchTerminationStrategy(new MultiTargetTerminationStrategy(stops));

        ShortestPathTree spt = profileDijkstra.getShortestPathTree(initial);
        List<State> states = new ArrayList<State>();
        for (State s : spt.getAllStates())
            if (s.getVertex() instanceof TransitStop)
                states.add(s);
        return states;
    }

    private RoutingRequest buildOptions(boolean batch, boolean parkAndRide,
            boolean bikeParkAndRide, boolean arriveBy, TraverseModeSet modes, Graph graph,
            boolean dummy) {
        RoutingRequest options = new RoutingRequest(modes.isTransit() ? TraverseMode.TRANSIT
                : TraverseMode.WALK);
        if (arriveBy)
            options.to = location;
        else
            options.from = location;
        options.batch = batch;
        options.parkAndRide = parkAndRide;
        options.bikeParkAndRide = bikeParkAndRide;
        options.arriveBy = arriveBy;
        if (dummy)
            options.setDummyRoutingContext(graph);
        else
            options.setRoutingContext(graph);
        options.setNumItineraries(1);
        options.setModes(modes);
        return options;
    }

    private void setAccessNodes(Graph graph, Collection<? extends State> anStates, Vertex ts,
            boolean car) {
        for (State s : anStates) {
            if (s.covered) {
                if (s.getVertex() instanceof IntersectionVertex && car) {
                    IntersectionVertex iv = (IntersectionVertex) (graph.getVertex(s.getVertex().getLabel()));
                    if (!iv.accessNodes.contains(ts))
                        iv.accessNodes.add(ts);
                } else if (s.getVertex() instanceof ParkAndRideVertex && car) {
                    ParkAndRideVertex iv = (ParkAndRideVertex) (graph.getVertex(s.getVertex().getLabel()));
                    if (!iv.accessNodes.contains(ts))
                        iv.accessNodes.add(ts);
                } else if (s.getVertex() instanceof BikeParkVertex && !car) {
                    BikeParkVertex iv = (BikeParkVertex) (graph.getVertex(s.getVertex().getLabel()));
                    if (!iv.accessNodes.contains(ts))
                        iv.accessNodes.add(ts);
                }
            }
        }
    }

    private void setBackwardAccessNodes(Graph graph, Collection<? extends State> anStates,
            Vertex ts, boolean car) {
        for (State s : anStates) {
            if (s.covered) {
                if (s.getVertex() instanceof IntersectionVertex && car) {
                    IntersectionVertex iv = (IntersectionVertex) (graph.getVertex(s.getVertex().getLabel()));
                    if (!iv.backwardAccessNodes.contains(ts))
                        iv.backwardAccessNodes.add(ts);
                } else if (s.getVertex() instanceof ParkAndRideVertex && car) {
                    ParkAndRideVertex iv = (ParkAndRideVertex) (graph.getVertex(s.getVertex().getLabel()));
                    if (!iv.backwardAccessNodes.contains(ts))
                        iv.backwardAccessNodes.add(ts);
                } else if (s.getVertex() instanceof BikeParkVertex && !car) {
                    BikeParkVertex iv = (BikeParkVertex) (graph.getVertex(s.getVertex().getLabel()));
                    if (!iv.backwardAccessNodes.contains(ts))
                        iv.backwardAccessNodes.add(ts);
                }
            }
        }
    }

    private void printIntersectionStats(List<Vertex> vertices) {
        int counter = 0;
        int bwd = 0;
        int fwd = 0;
        for (IntersectionVertex iv : Iterables.filter(vertices, IntersectionVertex.class)) {
            if (iv.accessNodes != null && iv.accessNodes.size() > 0)
                fwd++;
            if (iv.backwardAccessNodes != null && iv.backwardAccessNodes.size() > 0)
                bwd++;
            counter++;
        }
        LOG.info(fwd + " out of " + counter + " Intersection vertices have access nodes");
        LOG.info(bwd + " out of " + counter + " Intersection vertices have bwd access nodes");
    }

    private void printPNRStats(List<Vertex> vertices) {
        int counter = 0;
        int bwd = 0;
        int fwd = 0;
        for (ParkAndRideVertex pnr : Iterables.filter(vertices, ParkAndRideVertex.class)) {
            if (pnr.accessNodes != null && pnr.accessNodes.size() > 0)
                fwd++;
            if (pnr.backwardAccessNodes != null && pnr.backwardAccessNodes.size() > 0)
                bwd++;
            counter++;
        }
        LOG.info(fwd + " out of " + counter + " PNR vertices have access nodes");
        LOG.info(bwd + " out of " + counter + " PNR vertices have bwd access nodes");
    }

    private void printBikePNRStats(List<Vertex> vertices) {
        int counter = 0;
        int bwd = 0;
        int fwd = 0;
        for (BikeParkVertex bpnr : Iterables.filter(vertices, BikeParkVertex.class)) {
            if (bpnr.accessNodes != null && bpnr.accessNodes.size() > 0)
                fwd++;
            if (bpnr.backwardAccessNodes != null && bpnr.backwardAccessNodes.size() > 0)
                bwd++;
            counter++;
        }
        LOG.info(fwd + " out of " + counter + " Bike PNR vertices have access nodes");
        LOG.info(bwd + " out of " + counter + " Bike PNR vertices have bwd access nodes");
    }

    private boolean shouldLog(int counter, int stopsize) {
        return counter % (stopsize * STEP / 100) == 0;
    }

    @Override
    public void checkInputs() {
        // no inputs
    }
}
