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

package org.opentripplanner.routing.vertextype;

import java.util.List;

import org.opentripplanner.common.MavenVersion;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.graph.Vertex;

import com.beust.jcommander.internal.Lists;

/**
 * A vertex for a park and ride area. Connected to streets by ParkAndRideLinkEdge. Transition for
 * parking the car is handled by ParkAndRideEdge.
 * 
 * @author laurent
 * 
 */
public class ParkAndRideVertex extends Vertex {

    private static final long serialVersionUID = MavenVersion.VERSION.getUID();

    private String id;

    /**
     * The set of access nodes for this street node, needed for 2-way PNR routing
     */
    public List<Vertex> accessNodes = Lists.newArrayList();

    /**
     * The set of backward access nodes for this street node, needed for 2-way PNR routing
     */
    public List<Vertex> backwardAccessNodes = Lists.newArrayList();

    public ParkAndRideVertex(Graph g, String label, String id, double x, double y, String name) {
        super(g, label, x, y, name);
        setId(id);
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }
}
