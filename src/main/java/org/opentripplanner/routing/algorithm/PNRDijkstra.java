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
import org.opentripplanner.common.pqueue.BinHeap;
import org.opentripplanner.routing.algorithm.strategies.SearchTerminationStrategy;
import org.opentripplanner.routing.core.RoutingContext;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.RoutingRequest.PNRStatus;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Vertex;
import org.opentripplanner.routing.impl.StreetVertexIndexServiceImpl;
import org.opentripplanner.routing.services.SPTService;
import org.opentripplanner.routing.services.StreetVertexIndexService;
import org.opentripplanner.routing.spt.MultiShortestPathTree;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.routing.spt.TwoWayMultiShortestPathTree;
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

/**
 * Find the shortest path between graph vertices using A*.
 */
public class PNRDijkstra implements SPTService { // maybe this should be wrapped in a component SPT
                                                 // service

    private static final Logger LOG = LoggerFactory.getLogger(PNRDijkstra.class);

    // FIXME this is not really a factory, it's a way to fake a global variable. This should be
    // stored at the OTPServer level.
    private static final MonitoringStore store = MonitoringStoreFactory.getStore();

    private static final double OVERSEARCH_MULTIPLIER = 4.0;

    private boolean verbose = false;

    private TraverseVisitor traverseVisitor;

    enum RunStatus {
        RUNNING, STOPPED
    }

    /*
     * TODO instead of having a separate class for search state, we should just make one
     * GenericAStar per request.
     */
    class RunState {
        public Map<Vertex, State> pnrOut = new HashMap<Vertex, State>();

        public Map<Vertex, State> pnrIn = new HashMap<Vertex, State>();

        public State u;

        public ShortestPathTree sptIn;

        public ShortestPathTree sptOut;

        Set<State> milestoneOut;

        Set<State> milestoneIn;

        BinHeap<State> pqOut;

        BinHeap<State> pqIn;

        public RoutingContext rctx;

        public int nVisited;

        public List<Object> targetAcceptedStates;

        public RunStatus status;

        private RoutingRequest optionsIn;

        private RoutingRequest optionsOut;

        private SearchTerminationStrategy terminationStrategy;

        public Vertex u_vertex;
        
        public boolean milestoneReached = false;

        Double foundPathWeightIn = Double.MAX_VALUE;

        Double foundPathWeightOut = Double.MAX_VALUE;

        public Vertex currentPNR = null;

        protected IntersectionVertex sourceVertex;

        protected IntersectionVertex targetVertex;
        
        public Set<Vertex> sourcePNRAccessNodes;
        public Set<Vertex> sourcePNRBwdAccessNodes;

        public RunState(RoutingRequest options, SearchTerminationStrategy terminationStrategy) {
            optionsOut = options.clone();
            optionsOut.worstTime = Long.MIN_VALUE;
            optionsIn = options.clone();
            optionsIn.arriveBy = false;
            optionsIn.from = optionsOut.to;
            optionsIn.to = optionsOut.from;
            this.terminationStrategy = terminationStrategy;
        }

    }

    private RunState runState;

    public void startSearch(RoutingRequest options, SearchTerminationStrategy terminationStrategy,
            long abortTime) {

        runState = new RunState(options, terminationStrategy);
        runState.rctx = options.getRoutingContext();

        makeSearchVertices(options);
        // findNearestIntersections(options);
        
        int initialSize = runState.rctx.graph.getVertices().size();
        initialSize = (int) Math.ceil(2 * (Math.sqrt((double) initialSize + 1)));
        
        // Outgoing path part
        runState.sptOut = new MultiShortestPathTree(runState.optionsOut);
        runState.optionsOut.pnrStatus = PNRStatus.WALK_LEG;
        runState.optionsOut.setMode(TraverseMode.WALK);

        State initialState = new State(runState.targetVertex, runState.optionsOut);
        initialState.setCarParked(true);
        runState.sptOut.add(initialState);
        // Priority Queue.
        // NOTE(flamholz): the queue is self-resizing, so we initialize it to have
        // size = O(sqrt(|V|)) << |V|. For reference, a random, undirected search
        // on a uniform 2d grid will examine roughly sqrt(|V|) vertices before
        // reaching its target.
        runState.pqOut = new BinHeap<State>(initialSize);
        runState.pqOut.insert(initialState, 0);
        
        // Incoming path part
        runState.sptIn = new MultiShortestPathTree(runState.optionsIn);
        runState.optionsIn.pnrStatus = PNRStatus.WALK_LEG;
        runState.optionsIn.setMode(TraverseMode.WALK);
        initialState = new State(runState.targetVertex, runState.optionsIn);
        initialState.setCarParked(true);
        runState.sptIn.add(initialState);
        runState.pqIn = new BinHeap<State>(initialSize);
        runState.pqIn.insert(initialState, 0);

        initialSize = Sets.newHashSet(Iterables.filter(runState.rctx.graph.getVertices(), TransitStop.class)).size();
        runState.milestoneIn = new HashSet<State>(initialSize);
        runState.milestoneOut = new HashSet<State>(initialSize);
        runState.sourcePNRAccessNodes = new HashSet<Vertex>();
        for(Vertex pnr: runState.sourceVertex.pnrNodes) {
            runState.sourcePNRAccessNodes.addAll(((ParkAndRideVertex)pnr).accessNodes);
        }
        runState.sourcePNRBwdAccessNodes = new HashSet<Vertex>();
        for(Vertex pnr: runState.sourceVertex.pnrNodes) {
            runState.sourcePNRAccessNodes.addAll(((ParkAndRideVertex)pnr).backwardAccessNodes);
        }

        runState.nVisited = 0;
        runState.targetAcceptedStates = Lists.newArrayList();
    }

    boolean iterate(boolean queueIn) {
        if (verbose) {
            double w = runState.pqOut.peek_min_key();
            System.out.println("pqOut min key = " + w);
            w = runState.pqIn.peek_min_key();
            System.out.println("pqIn min key = " + w);
        }

        // get the lowest-weight state in the queue
        ShortestPathTree spt;
        RoutingRequest options;
        BinHeap<State> queue;
        Set<State> milestoneSet;

        if (queueIn) {
            spt = runState.sptIn;
            options = runState.optionsIn;
            queue = runState.pqIn;
            milestoneSet = runState.milestoneIn;
        } else {
            spt = runState.sptOut;
            options = runState.optionsOut;
            queue = runState.pqOut;
            milestoneSet = runState.milestoneOut;
        }

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
            runState.milestoneReached = false;
            queue.reset();
            for (State s: milestoneSet) {
                RoutingRequest opt = s.getOptions();
                if (s.getPNRStatus() == PNRStatus.WALK_LEG) {
                    opt.pnrStatus = PNRStatus.TRANSIT_LEG;
                    opt.setMode(TraverseMode.TRANSIT);
                } else if (s.getPNRStatus() == PNRStatus.TRANSIT_LEG) {
                    opt.pnrStatus = PNRStatus.PNR_TRANSFER;
                    opt.setMode(TraverseMode.WALK);
                } else if (s.getPNRStatus() == PNRStatus.PNR_TRANSFER) {
                    opt.pnrStatus = PNRStatus.CAR_LEG;
                    opt.setMode(TraverseMode.CAR);
                    s.setCarParked(false);
                }
                queue.insert(s, s.getWeight());
            }
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
                    System.out.println("      edge " + edge);
                    System.out.println("      " + runState.u.getWeight() + " -> " + v.getWeight()
                            + "(w) " + " vert = " + v.getVertex());
                }

                // avoid enqueuing useless branches
                if (estimate > options.maxWeight) {
                    // too expensive to get here
                    if (verbose)
                        System.out
                                .println("         too expensive to reach, not enqueued. estimated weight = "
                                        + estimate);
                    continue;
                }
                if (isWorstTimeExceeded(v, options)) {
                    // too much time to get here
                    if (verbose)
                        System.out.println("         too much time to reach, not enqueued. time = "
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
            if (opt.pnrStatus == PNRStatus.WALK_LEG && v_vertex instanceof TransitStop && runState.targetVertex.backwardAccessNodes.contains(v_vertex)) {
                runState.milestoneOut.add(v);
                if (runState.milestoneOut.size() == runState.targetVertex.backwardAccessNodes.size())
                    runState.milestoneReached = true;
            } else if (opt.pnrStatus == PNRStatus.TRANSIT_LEG && v_vertex instanceof TransitStop && isPNRAccessNode(runState.sourceVertex, v_vertex)) {
                runState.milestoneOut.add(v);
                if (runState.milestoneOut.size() == runState.sourcePNRAccessNodes.size())
                    runState.milestoneReached = true;
            } else if (opt.pnrStatus == PNRStatus.PNR_TRANSFER && v_vertex instanceof ParkAndRideVertex && runState.sourceVertex.pnrNodes.contains(v_vertex)) {
                runState.milestoneOut.add(v);
                if (runState.milestoneOut.size() == runState.sourcePNRBwdAccessNodes.size())
                    runState.milestoneReached = true;
            }
        } else {
            if (opt.pnrStatus == PNRStatus.WALK_LEG && v_vertex instanceof TransitStop && runState.targetVertex.accessNodes.contains(v_vertex)) {
                runState.milestoneIn.add(v);
            } else if (opt.pnrStatus == PNRStatus.TRANSIT_LEG && v_vertex instanceof TransitStop  && isPNRBwdAccessNode(runState.sourceVertex, v_vertex)) {
                runState.milestoneIn.add(v);
            } else if (opt.pnrStatus == PNRStatus.PNR_TRANSFER && v_vertex instanceof ParkAndRideVertex && runState.sourceVertex.pnrNodes.contains(v_vertex)) {
                runState.milestoneIn.add(v);
            }            
        }
        return runState.milestoneReached;
    }

    void runSearch(long abortTime) {
        while (!runState.pqOut.empty() || !runState.pqIn.empty()) {
            /*
             * Terminate based on timeout?
             */
            if (abortTime < Long.MAX_VALUE && System.currentTimeMillis() > abortTime) {
                LOG.warn("Search timeout. origin={} target={}", runState.rctx.origin,
                        runState.rctx.target);
                runState.optionsOut.rctx.aborted = true;
                runState.optionsIn.rctx.aborted = true;
                runState.optionsIn.rctx.debugOutput.timedOut = true;
                runState.optionsOut.rctx.debugOutput.timedOut = true;

                break;
            }

            double key;
            RoutingRequest options;
            Double foundPathWeight;
            boolean queueIn = runState.pqOut.empty()
                    || !runState.pqIn.empty() && runState.pqOut.peek_min_key() > runState.pqIn.peek_min_key();
            if (queueIn) {
                key = runState.pqIn.peek_min_key();
                options = runState.optionsIn;
                foundPathWeight = runState.foundPathWeightIn;
            } else {
                key = runState.pqOut.peek_min_key();
                options = runState.optionsOut;
                foundPathWeight = runState.foundPathWeightOut;
            }

            if (key >= foundPathWeight)
                break;

            if (!iterate(queueIn)) {
                continue;
            }

            // TODO: rctx.target has to be set depending on search direction
            if (runState.u_vertex == runState.sourceVertex && runState.u.pnrNode != null) {
                // bwd search = outgoing path
                if (!queueIn) {
                    // if we're using a pnr node and it has not yet been settled by this search
                    if (!runState.pnrOut.containsKey(runState.u.pnrNode)) {
                        // set as used
                        runState.pnrOut.put(runState.u.pnrNode, runState.u);
                        // it has been settled by the other search too
                        if (runState.pnrIn.containsKey(runState.u.pnrNode)) {
                            // new solution for u.pnrNode

                            // TODO: sum of weights? or rather make the difference between dep/arr
                            // long newWeight = (runState.optionsOut.dateTime -
                            // runState.u.getTimeSeconds())+(runState.pnrIn.get(runState.u.pnrNode).getTimeSeconds()-runState.optionsIn.dateTime);
                            double newWeight = runState.u.getWeight()
                                    + runState.pnrIn.get(runState.u.pnrNode).getWeight();
                            if (newWeight >= foundPathWeight)
                                continue;
                            foundPathWeight = newWeight;
                            runState.currentPNR = runState.u.pnrNode;
                        }
                    }
                } else {
                    // if we're using a pnr node and it has not yet been settled by this search
                    if (!runState.pnrIn.containsKey(runState.u.pnrNode)) {
                        // set as used
                        runState.pnrIn.put(runState.u.pnrNode, runState.u);
                        // it has been settled by the other search too
                        if (runState.pnrOut.containsKey(runState.u.pnrNode)) {
                            // new solution for u.pnrNode
    
                            // TODO: sum of weights? or rather make the difference between dep/arr
                            // long newWeight = (runState.optionsOut.dateTime -
                            // runState.u.getTimeSeconds())+(runState.pnrIn.get(runState.u.pnrNode).getTimeSeconds()-runState.optionsIn.dateTime);
                            double newWeight = runState.u.getWeight()
                                    + runState.pnrOut.get(runState.u.pnrNode).getWeight();
                            if (newWeight >= foundPathWeight) {
                                options.rctx.debugOutput.foundPath();
                                break;
                            }
                            foundPathWeight = newWeight;
                            runState.currentPNR = runState.u.pnrNode;
                        }
                    }
                }
            }

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

        startSearch(options, terminationStrategy, abortTime);

        if (runState != null) {
            runSearch(abortTime);
            
            spt = new TwoWayMultiShortestPathTree(options, runState.sptOut, runState.sptIn);
        }

        storeMemory();
        return spt;
    }

    private void makeSearchVertices(RoutingRequest options) {
        double searchRadiusM = 50;
        double searchRadiusLat = SphericalDistanceLibrary.metersToDegrees(searchRadiusM);

        StreetVertexIndexService index = new StreetVertexIndexServiceImpl(runState.rctx.graph);
        Envelope envelope = new Envelope(options.from.getCoordinate());
        double xscale = Math.cos(options.from.getCoordinate().y * Math.PI / 180);
        envelope.expandBy(searchRadiusLat / xscale, searchRadiusLat);
        Collection<Vertex> vertices = index.getVerticesForEnvelope(envelope);
        for (Vertex v : vertices) {
            if (v instanceof IntersectionVertex) {
                runState.sourceVertex = (IntersectionVertex) v;
                break;
            }
        }
        envelope = new Envelope(options.to.getCoordinate());
        xscale = Math.cos(options.to.getCoordinate().y * Math.PI / 180);
        envelope.expandBy(searchRadiusLat / xscale, searchRadiusLat);
        vertices = index.getVerticesForEnvelope(envelope);
        for (Vertex v : vertices) {
            if (v instanceof IntersectionVertex) {
                runState.targetVertex = (IntersectionVertex) v;
                break;
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
