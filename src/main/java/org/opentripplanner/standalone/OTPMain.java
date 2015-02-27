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

package org.opentripplanner.standalone;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Random;

import org.opentripplanner.api.model.Itinerary;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.model.WalkStep;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.AnnotationsToHTML;
import org.opentripplanner.graph_builder.GraphBuilderTask;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.error.PathNotFoundException;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.visualizer.GraphVisualizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

/**
 * I originally considered configuring OTP server through Java properties, with one global
 * properties file at /etc/otp.properties for the whole server and one optional properties file per
 * graph, then allowing overriding or entirely replacing these settings with command line options.
 * 
 * However, there are not that many build/server settings. Rather than duplicating everything in
 * properties, a command line options class, and an internal configuration class, I am tentatively
 * just using command line parameters for everything. JCommander lets you use "@filename" notation
 * to load command line parameters from a file, which we can keep in /etc or wherever.
 * 
 * Graphs are sought by default in /var/otp/graphs.
 */
public class OTPMain {

    private static final Logger LOG = LoggerFactory.getLogger(OTPMain.class);

    public static class BBox {
        public double minLat = -90;
        public double minLon = -180;
        public double maxLat = 90;
        public double maxLon = 180;
        
        public BBox(String s) {
            String[] parts = s.split(",");
            minLon = Double.parseDouble(parts[0]);
            minLat = Double.parseDouble(parts[1]);
            maxLon = Double.parseDouble(parts[2]);
            maxLat = Double.parseDouble(parts[3]);
        }
        
        public String toString() {
            return minLat+","+minLon+","+maxLat+","+maxLon;
        }
    }
    
    public static void main(String[] args) {
        /* Parse and validate command line parameters */
        CommandLineParameters params = new CommandLineParameters();
        try {
            JCommander jc = new JCommander(params, args);
            if (params.help) {
                jc.setProgramName("java -Xmx[several]G -jar otp.jar");
                jc.usage();
                System.exit(0);
            }
            params.infer();
        } catch (ParameterException pex) {
            LOG.error("Parameter error: {}", pex.getMessage());
            System.exit(1);
        }

        OTPConfigurator configurator = new OTPConfigurator(params);

        // start graph builder, if asked for
        GraphBuilderTask graphBuilder = configurator.builderFromParameters();
        if (graphBuilder != null) {
            graphBuilder.run();
            // Inform configurator which graph is to be used for in-memory handoff.
            if (params.inMemory || params.preFlight) {
                graphBuilder.getGraph().index(new DefaultStreetVertexIndexFactory());
                configurator.makeGraphService(graphBuilder.getGraph());
            }

            if (params.htmlAnnotations) {
                AnnotationsToHTML annotationsToHTML = new AnnotationsToHTML(
                        graphBuilder.getGraph(), new File(params.build.get(0), "report.html"));
                annotationsToHTML.generateAnnotationsLog();
            }
        }

        // start visualizer, if asked for
        GraphVisualizer graphVisualizer = configurator.visualizerFromParameters();
        if (graphVisualizer != null) {
            graphVisualizer.run();
        }

        // start web server, if asked for
        GrizzlyServer grizzlyServer = configurator.serverFromParameters();
        if (grizzlyServer != null) {
            while (true) { // Loop to restart server on uncaught fatal exceptions.
                try {
                    grizzlyServer.run();
                    return;
                } catch (Throwable throwable) {
                    throwable.printStackTrace();
                    LOG.error("An uncaught {} occurred inside OTP. Restarting server.", throwable
                            .getClass().getSimpleName());
                }
            }
        }

        if (params.twoWayTest) {
            TwoWayCsvTester csv = new TwoWayCsvTester();
            csv.fromFile(params.testInput);
            
            BBox srcBox = new BBox(configurator.getSrcBBox());
            BBox tgtBox = new BBox(configurator.getTgtBBox());
            Random r = new Random();
//            double minLat = 45.46, minLon = 9.18, maxLat = 45.56, maxLon = 9.29;
            GraphService gs = configurator.getGraphService();
            
            LOG.info("Routing");
            long avg = 0;
            long avgDuration = 0;
            long max = 0;
            long min = Long.MAX_VALUE;
            int i=0;
            int skipped = 0;
            
//            for (;i<10;i++) {
                RoutingRequest rq = configurator.getServer().routingRequest.clone();
                rq.numItineraries = 1;
                rq.arriveBy = true;
                rq.dateTime = System.currentTimeMillis() / 1000;
                rq.returnDateTime = System.currentTimeMillis() / 1000;
                rq.modes = new TraverseModeSet(TraverseMode.WALK);
                rq.parkAndRide = true;
                rq.twoway = true;
                rq.from = generateLocation(r, srcBox);
                rq.to = generateLocation(r, tgtBox);
                rq.setRoutingContext(gs.getRouter().graph);
                Router router = configurator.getServer().getRouter(rq.routerId);
                LOG.info("From "+ rq.from.toDescriptiveString());
                LOG.info("To "+ rq.to.toDescriptiveString());
                try {
                    long time = System.currentTimeMillis();
                    TripPlan plan = router.planGenerator.generate(rq);
                    long time2 = System.currentTimeMillis();
                    long t = time2-time;
                    LOG.info("Took " + t + " millis");
                    avg += t;
                    max = t > max ? t : max;
                    min = t < min? t : min;
                    t = -1;
                    if (plan != null) {
                        t = plan.itinerary.get(0).duration + plan.itinerary.get(1).duration;
                        avgDuration += t;
                    }
                    LOG.info("Iteration "+i+" time "+t);
                } catch (PathNotFoundException e) {
                    LOG.error("Path not found");
                    skipped++;
                }
//             }
            LOG.info("Done routing, skipped "+skipped);
            avg = avg/(i-skipped);
            avgDuration = avgDuration/(i-skipped);
            LOG.info("Average routing time "+avg+" ms = "+avg/1000+" s");
            LOG.info("Minimum routing time "+min+" ms = "+min/1000+" s");
            LOG.info("Maximum routing time "+max+" ms = "+max/1000+" s");
            LOG.info("Average duration "+avgDuration+" ms = "+avgDuration/1000+" s");
        }
        
        if (graphBuilder == null && graphVisualizer == null && grizzlyServer == null) {
            LOG.info("Nothing to do. Use --help to see available tasks.");
        }

        if (params.anStats)
            configurator.showANStats();
    }

    private static GenericLocation generateLocation(Random r, BBox bbox) {
        // marelli -> s babila
        // rq.from = new GenericLocation(45.506309, 9.226168);
        // rq.to = new GenericLocation(45.471710, 9.201963);
        // rq.from = new GenericLocation(45.489654, 9.215640);
        // rq.to = new GenericLocation(45.475893, 9.206444);
        double lat = bbox.minLat + (bbox.maxLat - bbox.minLat) * r.nextDouble();
        double lon = bbox.minLon + (bbox.maxLon - bbox.minLon) * r.nextDouble();
        return new GenericLocation(lat, lon);
    }
}
