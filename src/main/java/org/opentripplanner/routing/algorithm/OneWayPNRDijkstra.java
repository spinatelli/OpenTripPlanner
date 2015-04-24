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

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opentripplanner.common.geometry.SphericalDistanceLibrary;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.common.pqueue.BinHeap;
import org.opentripplanner.routing.algorithm.strategies.SearchTerminationStrategy;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.RoutingRequest.PNRStatus;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.services.StreetVertexIndexService;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.opentripplanner.routing.spt.MultiShortestPathTree;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.spt.TwoWayMultiShortestPathTree;
import org.opentripplanner.routing.vertextype.BikeParkVertex;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.util.DateUtils;
import org.opentripplanner.util.monitoring.MonitoringStore;
import org.opentripplanner.util.monitoring.MonitoringStoreFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.internal.Lists;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.vividsolutions.jts.geom.Envelope;

public class OneWayPNRDijkstra implements Algorithm {

    private static final Logger LOG = LoggerFactory.getLogger(OneWayPNRDijkstra.class);

    private static final MonitoringStore store = MonitoringStoreFactory.getStore();

    private boolean verbose = false;

    private TraverseVisitor traverseVisitor;

    class RunState {
        public Map<Vertex, State> pnr = new HashMap<Vertex, State>();

        public State u;

        public ShortestPathTree spt;

        Map<Vertex, State> milestone;

        BinHeap<State> pq;

        public RoutingContext rctx;

        public int nVisited;

        private RoutingRequest options;

        public Vertex u_vertex;

        Double foundPathWeight = Double.MAX_VALUE;

        public Vertex currentPNR = null;

        protected IntersectionVertex sourceVertex;

        protected IntersectionVertex targetVertex;

        public Set<Vertex> sourcePNRAccessNodes;
        public Set<Vertex> sourcePNRBwdAccessNodes;

        public TraverseMode initialMode;

        public double minWeight = Double.MIN_VALUE;

        public RunState(RoutingRequest opt) {
            initialMode = opt.modes.getBicycle() ? TraverseMode.BICYCLE : TraverseMode.CAR;
            options = opt.clone();
            if (options.arriveBy)
                options.worstTime = Long.MIN_VALUE;
            else {
                options.worstTime = Long.MAX_VALUE;
//                options.to = options.from;
//                options.from = opt.to;
            }
        }

    }

    private RunState runState;

    public void startSearch(RoutingRequest opt, long abortTime) {
        RoutingRequest options = opt;
        options.numItineraries = 1;//options.numItineraries;

        options.parkAndRide = !options.modes.getBicycle();
        options.bikeParkAndRide = options.modes.getBicycle();
        if (!options.arriveBy) {
            GenericLocation l = options.to;
            options.to = options.from;
            options.from = l;
        }
        runState = new RunState(options);
        runState.rctx = options.getRoutingContext();

        makeSearchVertices(options);

        int initialSize = runState.rctx.graph.getVertices().size();
        initialSize = (int) Math.ceil(2 * (Math.sqrt((double) initialSize + 1)));

        // Outgoing path part
        runState.spt = new MultiShortestPathTree(runState.options,
                new DominanceFunction.MinimumWeight());
        runState.options.pnrStatus = PNRStatus.WALK_LEG;
        runState.options.setMode(TraverseMode.WALK);

        State initialState = new State(runState.targetVertex, runState.options);
        if (runState.initialMode == TraverseMode.BICYCLE)
            initialState.setBikeParked(true);
        else
            initialState.setCarParked(true);
        runState.spt.add(initialState);
        // Priority Queue
        runState.pq = new BinHeap<State>(initialSize);
        runState.pq.insert(initialState, 0);
        
        initialSize = Sets.newHashSet(
                Iterables.filter(runState.rctx.graph.getVertices(), TransitStop.class)).size();
        runState.milestone = new HashMap<Vertex, State>(initialSize);
        runState.sourcePNRAccessNodes = new HashSet<Vertex>();
        runState.sourcePNRBwdAccessNodes = new HashSet<Vertex>();

        if (runState.initialMode == TraverseMode.BICYCLE) {
            for (Vertex pnr : runState.sourceVertex.bikePNRNodes) {
                runState.sourcePNRAccessNodes.addAll(((BikeParkVertex) pnr).accessNodes);
            }
            for (Vertex pnr : runState.sourceVertex.bikePNRNodes) {
                runState.sourcePNRBwdAccessNodes.addAll(((BikeParkVertex) pnr).backwardAccessNodes);
            }
        } else {
            for (Vertex pnr : runState.sourceVertex.pnrNodes) {
                runState.sourcePNRAccessNodes.addAll(((ParkAndRideVertex) pnr).accessNodes);
            }
            for (Vertex pnr : runState.sourceVertex.pnrNodes) {
                runState.sourcePNRBwdAccessNodes
                        .addAll(((ParkAndRideVertex) pnr).backwardAccessNodes);
            }
        }

        runState.nVisited = 0;
    }

    boolean iterate() {
        if (verbose) {
            double w = runState.pq.peek_min_key();
            System.out.println("pqOut min key = " + w);
            w = runState.pq.peek_min_key();
            System.out.println("pqIn min key = " + w);
        }

        // get the lowest-weight state in the queue
        ShortestPathTree spt;
        RoutingRequest options;
        BinHeap<State> queue;
        Map<Vertex, State> milestoneSet;

        spt = runState.spt;
        options = runState.options;
        queue = runState.pq;
        milestoneSet = runState.milestone;

        runState.u = queue.extract_min();
        // check that this state has not been dominated
        // and mark vertex as visited
        if (!spt.visit(runState.u)) {
            // state has been dominated since it was added to the priority queue, so it is
            // not in any optimal path. drop it on the floor and try the next one.
            return false;
        }

        if (traverseVisitor != null) {
            traverseVisitor.visitVertex(runState.u);
        }

        runState.u_vertex = runState.u.getVertex();

        if (verbose)
            System.out.println("   vertex " + runState.u_vertex);

        runState.nVisited += 1;

        if (checkMilestone(runState.u)) {
            addAllFromMilestoneSet(queue, milestoneSet);
            return true;
        }

        Collection<Edge> edges = runState.u.stateData.isArriveBy() ? runState.u_vertex
                .getIncoming() : runState.u_vertex.getOutgoing();
        for (Edge edge : edges) {

            // Iterate over traversal results. When an edge leads nowhere (as indicated by
            // returning NULL), the iteration is over. TODO Use this to board multiple trips.
            for (State v = edge.traverse(runState.u); v != null; v = v.getNextResult()) {
                if (traverseVisitor != null) {
                    traverseVisitor.visitEdge(edge, v);
                }
                double estimate = v.getWeight();

                if (verbose) {
                    LOG.info("      edge " + edge);
                    LOG.info("      " + runState.u.getWeight() + " -> " + v.getWeight() + "(w) "
                            + " vert = " + v.getVertex());
                }

                // avoid enqueuing useless branches
                if (estimate > options.maxWeight) {
                    // too expensive to get here
                    if (verbose)
                        LOG.info("         too expensive to reach, not enqueued. estimated weight = "
                                + estimate);
                    continue;
                }
                if (isWorstTimeExceeded(v, options)) {
                    // too much time to get here
                    if (verbose)
                        LOG.info("         too much time to reach, not enqueued. time = "
                                + v.getTimeSeconds());
                    continue;
                }

                // spt.add returns true if the state is hopeful; enqueue state if it's hopeful
                if (spt.add(v)) {
                    if (traverseVisitor != null)
                        traverseVisitor.visitEnqueue(v);

                    queue.insert(v, estimate);
                }
            }
        }

        return true;
    }

    private boolean checkMilestone(State v) {
        RoutingRequest opt = v.getOptions();
        Vertex v_vertex = v.getVertex();

        if (runState.u.stateData.isArriveBy()) {
            if (opt.pnrStatus == PNRStatus.WALK_LEG && v_vertex instanceof TransitStop
                    && runState.targetVertex.backwardAccessNodes.contains(v_vertex)) {
                if (!runState.milestone.containsKey(v_vertex))
                    runState.milestone.put(v_vertex, v);
                if (runState.milestone.size() == runState.targetVertex.backwardAccessNodes
                        .size())
                    return true;
            } else if (opt.pnrStatus == PNRStatus.TRANSIT_LEG && v_vertex instanceof TransitStop
                    && isPNRAccessNode(runState.sourceVertex, v_vertex)) {
                if (!runState.milestone.containsKey(v_vertex))
                    runState.milestone.put(v_vertex, v);
                if (runState.milestone.size() == runState.sourcePNRAccessNodes.size())
                    return true;
            } else if (opt.pnrStatus == PNRStatus.PNR_TRANSFER
                    && runState.initialMode == TraverseMode.CAR
                    && v_vertex instanceof ParkAndRideVertex
                    && runState.sourceVertex.pnrNodes.contains(v_vertex)) {
                if (!runState.milestone.containsKey(v_vertex))
                    runState.milestone.put(v_vertex, v);
                if (runState.milestone.size() == runState.sourceVertex.pnrNodes.size())
                    return true;
            } else if (opt.pnrStatus == PNRStatus.PNR_TRANSFER
                    && runState.initialMode == TraverseMode.BICYCLE
                    && v_vertex instanceof BikeParkVertex
                    && runState.sourceVertex.bikePNRNodes.contains(v_vertex)) {
                if (!runState.milestone.containsKey(v_vertex))
                    runState.milestone.put(v_vertex, v);
                if (runState.milestone.size() == runState.sourceVertex.bikePNRNodes.size())
                    return true;
            }
        } else {
            if (opt.pnrStatus == PNRStatus.WALK_LEG && v_vertex instanceof TransitStop
                    && runState.targetVertex.accessNodes.contains(v_vertex)) {
                if (!runState.milestone.containsKey(v_vertex))
                    runState.milestone.put(v_vertex, v);
                if (runState.milestone.size() == runState.targetVertex.accessNodes.size())
                    return true;
            } else if (opt.pnrStatus == PNRStatus.TRANSIT_LEG && v_vertex instanceof TransitStop
                    && isPNRBwdAccessNode(runState.sourceVertex, v_vertex)) {
                if (!runState.milestone.containsKey(v_vertex))
                    runState.milestone.put(v_vertex, v);
                if (runState.milestone.size() == runState.sourcePNRBwdAccessNodes.size())
                    return true;
            } else if (opt.pnrStatus == PNRStatus.PNR_TRANSFER
                    && runState.initialMode == TraverseMode.CAR
                    && v_vertex instanceof ParkAndRideVertex
                    && runState.sourceVertex.pnrNodes.contains(v_vertex)) {
                if (!runState.milestone.containsKey(v_vertex))
                    runState.milestone.put(v_vertex, v);
                if (runState.milestone.size() == runState.sourceVertex.pnrNodes.size())
                    return true;
            } else if (opt.pnrStatus == PNRStatus.PNR_TRANSFER
                    && runState.initialMode == TraverseMode.BICYCLE
                    && v_vertex instanceof BikeParkVertex
                    && runState.sourceVertex.bikePNRNodes.contains(v_vertex)) {
                if (!runState.milestone.containsKey(v_vertex))
                    runState.milestone.put(v_vertex, v);
                if (runState.milestone.size() == runState.sourceVertex.bikePNRNodes.size())
                    return true;
            }
        }
        return false;
    }

    private void addAllFromMilestoneSet(BinHeap<State> queue, Map<Vertex, State> milestoneSet) {
        queue.reset();
        PNRStatus status = runState.u.getPNRStatus();
        for (Vertex v : milestoneSet.keySet()) {
            State s = milestoneSet.get(v);
            RoutingRequest opt = s.getOptions().clone();
            if (status == PNRStatus.WALK_LEG) {
                opt.pnrStatus = PNRStatus.TRANSIT_LEG;
                opt.setModes(new TraverseModeSet(TraverseMode.TRAM, TraverseMode.SUBWAY,
                        TraverseMode.RAIL, TraverseMode.BUS, TraverseMode.TRANSIT,
                        TraverseMode.TRAINISH, TraverseMode.BUSISH));
            } else if (status == PNRStatus.TRANSIT_LEG) {
                opt.pnrStatus = PNRStatus.PNR_TRANSFER;
                opt.setMode(TraverseMode.WALK);
            } else if (status == PNRStatus.PNR_TRANSFER && runState.initialMode == TraverseMode.CAR) {
                opt.pnrStatus = PNRStatus.CAR_LEG;
                opt.setMode(TraverseMode.CAR);
                s.setCarParked(false);
                s.pnrNode = s.getVertex();
            } else if (status == PNRStatus.PNR_TRANSFER
                    && runState.initialMode == TraverseMode.BICYCLE) {
                opt.pnrStatus = PNRStatus.BICYCLE_LEG;
                opt.setMode(TraverseMode.BICYCLE);
                s.setBikeParked(false);
                s.pnrNode = s.getVertex();
            }
            s.setOptions(opt);
            queue.insert(s, s.getWeight());
        }
        milestoneSet.clear();
    }

    void runSearch(long abortTime) {
        while (!runState.pq.empty()) {
            /*
             * Terminate based on timeout?
             */
            if (abortTime < Long.MAX_VALUE && System.currentTimeMillis() > abortTime) {
                LOG.warn("Search timeout. origin={} target={}", runState.rctx.origin,
                        runState.rctx.target);
                runState.options.rctx.aborted = true;
                runState.options.rctx.debugOutput.timedOut = true;
                break;
            }

            double key;
            RoutingRequest options;
            Double maxWeight;
            BinHeap<State> queue;
            Map<Vertex, State> milestoneSet;
            key = runState.pq.peek_min_key();
            options = runState.options;
            maxWeight = runState.foundPathWeight - runState.minWeight;
            queue = runState.pq;
            milestoneSet = runState.milestone;

            if (key >= maxWeight)
                break;

            if (!iterate()) {
                if (queue.empty())
                    addAllFromMilestoneSet(queue, milestoneSet);
                continue;
            }

            // TODO: rctx.target has to be set depending on search direction
            if (runState.u_vertex == runState.sourceVertex && runState.u.pnrNode != null
                    && runState.u.getPNRStatus().isFinal()) {
                if (runState.u.getWeight() >= runState.foundPathWeight) {
                    options.rctx.debugOutput.foundPath();
                    break;
                }
                runState.foundPathWeight = runState.u.getWeight();
                runState.currentPNR = runState.u.pnrNode;
            }

            if (queue.empty())
                addAllFromMilestoneSet(queue, milestoneSet);

        }
    }

    /**
     * Compute SPT using default timeout and termination strategy.
     */
    @Override
    public ShortestPathTree getShortestPathTree(RoutingRequest req) {
        return getShortestPathTree(req, -1, null); // negative timeout means no timeout
    }

    /**
     * Compute SPT using default termination strategy.
     */
    @Override
    public ShortestPathTree getShortestPathTree(RoutingRequest req, double relTimeoutSeconds) {
        return this.getShortestPathTree(req, relTimeoutSeconds, null);
    }

    /** @return the shortest path, or null if none is found */
    public ShortestPathTree getShortestPathTree(RoutingRequest options, double relTimeoutSeconds,
            SearchTerminationStrategy terminationStrategy) {
        ShortestPathTree spt = null;
        long abortTime = DateUtils.absoluteTimeout(relTimeoutSeconds);

        startSearch(options, abortTime);
        if (runState != null) {
            runSearch(Long.MAX_VALUE);

            spt = new TwoWayMultiShortestPathTree(options, runState.spt, null,
                    runState.sourceVertex, runState.targetVertex, true);
        }

        storeMemory();
        return spt;
    }

    private void makeSearchVertices(RoutingRequest options) {
        double searchRadiusM = 0;
        // options.from = new GenericLocation(45.497637769622855, 9.223618207688705);
        // options.to = new GenericLocation(45.4720925, 9.1948771);

        // StreetVertexIndexService index = new StreetVertexIndexServiceImpl(runState.rctx.graph);
        StreetVertexIndexService index = runState.rctx.graph.streetIndex;
        double min = Double.MAX_VALUE;
        while (min == Double.MAX_VALUE) {
            searchRadiusM += 50;
            double searchRadiusLat = SphericalDistanceLibrary.metersToDegrees(searchRadiusM);
            Envelope envelope = new Envelope(options.from.getCoordinate());
            double xscale = Math.cos(options.from.getCoordinate().y * Math.PI / 180);
            envelope.expandBy(searchRadiusLat / xscale, searchRadiusLat);
            Collection<Vertex> vertices = index.getVerticesForEnvelope(envelope);
            for (Vertex v : vertices) {
                double d = SphericalDistanceLibrary.distance(v.getCoordinate(),
                        options.from.getCoordinate());
                if (v instanceof IntersectionVertex && d < min) {
                    runState.sourceVertex = (IntersectionVertex) v;
                    min = d;
                }
            }
        }

        searchRadiusM = 0;
        min = Double.MAX_VALUE;
        while (min == Double.MAX_VALUE) {
            searchRadiusM += 50;
            double searchRadiusLat = SphericalDistanceLibrary.metersToDegrees(searchRadiusM);
            Envelope envelope = new Envelope(options.to.getCoordinate());
            double xscale = Math.cos(options.to.getCoordinate().y * Math.PI / 180);
            envelope.expandBy(searchRadiusLat / xscale, searchRadiusLat);
            Collection<Vertex> vertices = index.getVerticesForEnvelope(envelope);
            for (Vertex v : vertices) {
                double d = SphericalDistanceLibrary.distance(v.getCoordinate(),
                        options.from.getCoordinate());
                if (v instanceof IntersectionVertex && d < min) {
                    runState.targetVertex = (IntersectionVertex) v;
                    min = d;
                }
            }
        }
    }

    private void storeMemory() {
        if (store.isMonitoring("memoryUsed")) {
            System.gc();
            long memoryUsed = Runtime.getRuntime().totalMemory()
                    - Runtime.getRuntime().freeMemory();
            store.setLongMax("memoryUsed", memoryUsed);
        }
    }

    private boolean isWorstTimeExceeded(State v, RoutingRequest opt) {
        if (opt.arriveBy)
            return v.getTimeSeconds() < opt.worstTime;
        else
            return v.getTimeSeconds() > opt.worstTime;
    }

    public void setTraverseVisitor(TraverseVisitor traverseVisitor) {
        this.traverseVisitor = traverseVisitor;
    }

    // Returns whether ts is an access node for at least one PNR node of iv
    boolean isPNRAccessNode(IntersectionVertex iv, Vertex ts) {
        return runState.sourcePNRAccessNodes.contains(ts);
    }

    // Returns whether ts is a bwd access node for at least one PNR node of iv
    boolean isPNRBwdAccessNode(IntersectionVertex iv, Vertex ts) {
        return runState.sourcePNRBwdAccessNodes.contains(ts);
    }
}
