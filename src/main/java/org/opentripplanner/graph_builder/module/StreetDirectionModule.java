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
import java.util.HashMap;
import java.util.List;

import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.graph_builder.services.GraphBuilderModule;
import org.opentripplanner.routing.edgetype.StreetEdge;
import org.opentripplanner.routing.graph.Edge;
import org.opentripplanner.routing.graph.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

/**
 * {@link GraphBuilder} plugin that computes access nodes for every street node. Should be called
 * after both the transit network and street network are loaded.
 */
public class StreetDirectionModule implements GraphBuilderModule {

    private static final Logger LOG = LoggerFactory.getLogger(StreetDirectionModule.class);

    private GenericLocation reference;

    public List<String> provides() {
        return Arrays.asList("street directions");
    }

    public List<String> getPrerequisites() {
        return Arrays.asList("streets"); // transit yes or no?
    }
    
    public StreetDirectionModule(GenericLocation reference) {
        this.reference = reference;
    }

    @Override
    public void buildGraph(Graph graph, HashMap<Class<?>, Object> extra) {
        LOG.info("Computing direction of street edges...");

        // iterate over a copy of vertex list because it will be modified
        List<Edge> edges = new ArrayList<Edge>();
        edges.addAll(graph.getEdges());
        // if distance of arc head is within [-10%,+10%] of the length of the arc
        // arc has "circular" direction, less is entering, more is exiting
        double epsilon = 0.1;
        for (StreetEdge edge : Iterables.filter(edges, StreetEdge.class)) {
            // TODO: find epsilon
            edge.computeStreetType(reference, epsilon);
        }

        LOG.info("Done computing direction of street edges...");
    }

    @Override
    public void checkInputs() {
        // no inputs
    }
}
