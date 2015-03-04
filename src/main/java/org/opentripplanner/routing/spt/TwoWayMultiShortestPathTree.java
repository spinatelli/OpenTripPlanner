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

package org.opentripplanner.routing.spt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TwoWayMultiShortestPathTree implements ShortestPathTree {

    private static final Logger LOG = LoggerFactory.getLogger(TwoWayMultiShortestPathTree.class);

    private ShortestPathTree out;
    private ShortestPathTree in;

    private RoutingRequest options;

    private Vertex from;

    private Vertex to;
    
    @Override
    public List<GraphPath> getPaths() {
        return getPaths(to, true);
    }
    
    private List<GraphPath> getSPTPaths(ShortestPathTree spt, Vertex dest, Map<Vertex, GraphPath> map, boolean optimize) {
        List<? extends State> stateList = spt.getStates(dest);
        if (stateList == null)
            return Collections.emptyList();
        List<GraphPath> ret = new LinkedList<GraphPath>();
        for (State s : stateList) {
            if (s.isFinal(true) && s.allPathParsersAccept() && s.pnrNode != null) {
                GraphPath gp = new GraphPath(s, optimize);
                ret.add(gp);
                map.put(s.pnrNode, gp);
            }
        }
        return ret;
    }
    
    @Override    
    public List<GraphPath> getPaths(Vertex dest, boolean optimize) {
        Map<Vertex, GraphPath> mOut = new HashMap<Vertex, GraphPath>();
        List<GraphPath> pathsOut = getSPTPaths(out, from, mOut, optimize);
        Map<Vertex, GraphPath> mIn = new HashMap<Vertex, GraphPath>();
        List<GraphPath> pathsIn = getSPTPaths(in, from, mIn, optimize);
        
        if (pathsOut.isEmpty() || pathsIn.isEmpty())
            return Collections.emptyList();
        List<GraphPath> ret = new LinkedList<GraphPath>();
        Set<Vertex> intersect = mOut.keySet();
        intersect.retainAll(mIn.keySet());
        for (Vertex v: intersect) {
          ret.add(mOut.get(v));
          ret.add(mIn.get(v));
        }
        return ret;
    }

    @Override
    public GraphPath getPath(Vertex dest, boolean optimize) {
        State s = getState(dest);
        if (s == null)
            return null;
        else
            return new GraphPath(s, optimize);
    }
    
    public GraphPath getPathIn(Vertex dest, boolean optimize) {
        State s = getStateIn(dest);
        if (s == null)
            return null;
        else
            return new GraphPath(s, optimize);
    }

    public TwoWayMultiShortestPathTree(RoutingRequest options, ShortestPathTree out, ShortestPathTree in, Vertex from, Vertex to) {
        this.options = options;
        this.out = out;
        this.in = in;
        this.from = from;
        this.to = to;
    }

    /****
     * {@link ShortestPathTree} Interface
     ****/

    @Override
    public boolean add(State newState) {
        if (newState.stateData.isArriveBy())
            return out.add(newState);
        
        return in.add(newState);
    }

    @Override
    public State getState(Vertex dest) {
        List<? extends State> states = out.getStates(dest);
        if (states == null)
            return null;
        State ret = null;
        for (State s : states) {
            if ((ret == null || s.betterThan(ret)) && s.isFinal(true) && s.allPathParsersAccept()) {
                ret = s;
            }
        }
        return ret;
    }
    
    public State getStateIn(Vertex dest) {
        List<? extends State> states = in.getStates(dest);
        if (states == null)
            return null;
        State ret = null;
        for (State s : states) {
            if ((ret == null || s.betterThan(ret)) && s.isFinal(true) && s.allPathParsersAccept()) {
                ret = s;
            }
        }
        return ret;
    }

    @Override
    public List<? extends State> getStates(Vertex dest) {
        return out.getStates(dest);
    }
    public List<? extends State> getStatesIn(Vertex dest) {
        return in.getStates(dest);
    }

    @Override
    public int getVertexCount() {
        return out.getVertexCount();
    }

    @Override
    public boolean visit(State state) {
        if (state.stateData.isArriveBy())
            return out.visit(state);
        
        return in.visit(state);
    }

    public String toString() {
        return "TwoWayMultiSPT[" + out+" and " + in + "]";
    }

    @Override
    public Collection<State> getAllStates() {
        ArrayList<State> allStates = new ArrayList<State>();
        allStates.addAll(out.getAllStates());
        allStates.addAll(in.getAllStates());
        return allStates;
    }

    @Override
    public void postVisit(State u) {
    }
    
    @Override
    public RoutingRequest getOptions() {
        return options;
    }

}