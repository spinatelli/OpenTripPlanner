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

        if (graphBuilder == null && graphVisualizer == null && grizzlyServer == null) {
            LOG.info("Nothing to do. Use --help to see available tasks.");
            ;
        }

        configurator.showANStats();
        if (params.pnrRoute) {
            LOG.info("Routing");
            GraphService gs = configurator.getGraphService();

            RoutingRequest rq = configurator.getServer().routingRequest.clone();
            rq.numItineraries = 2;
            rq.arriveBy = true;
            // marelli -> s babila
            rq.from = new GenericLocation(45.506309, 9.226168);
            rq.to = new GenericLocation(45.471710, 9.201963);
//          rq.from = new GenericLocation(45.489654, 9.215640);
//          rq.to = new GenericLocation(45.475893, 9.206444);
            rq.dateTime = System.currentTimeMillis() / 1000;
            rq.modes = new TraverseModeSet(TraverseMode.WALK);
            rq.parkAndRide = true;
            rq.twoway = true;
            rq.setRoutingContext(gs.getRouter().graph);
            Router router = configurator.getServer().getRouter(rq.routerId);
//            for (int i=0;i<10;i++) {
            long time = System.currentTimeMillis();
            TripPlan plan = router.planGenerator.generate(rq);
            long time2 = System.currentTimeMillis();
            LOG.info("Took " + (time2 - time) + " millis");
//            }
            if (plan != null) {
                for (Itinerary itinerary : plan.itinerary) {
                    LOG.info("======");
                    LOG.info("Itinerary " + itinerary.duration + " " + itinerary.transfers);
                    LOG.info("======");
                    for (Leg leg : itinerary.legs) {
                        SimpleDateFormat formatter = new SimpleDateFormat("dd MM yyyy HH:mm:ss");
                        LOG.info("Leg From " + leg.from.lat + "," + leg.from.lon + " at "
                                + formatter.format(leg.startTime.getTime()));
                        LOG.info("Leg To " + leg.to.lat + "," + leg.to.lon + " at "
                                + formatter.format(leg.endTime.getTime()));
                        LOG.info("By " + leg.mode);
                        if (leg.routeType == null || leg.routeType < 0) {
                            // non transit
                            for (WalkStep step : leg.walkSteps) {
                                LOG.info(leg.mode + " " + step.absoluteDirection.toString()
                                        + " on " + step.streetName);
                            }
                        } else {
                            // transit
                        }
                        // LOG.info(leg.toString());
                    }
                    // assertEquals(AbsoluteDirection.NORTH, step0.absoluteDirection);
                    // assertEquals("NE 43RD AVE", step0.streetName);
                    // assertEquals("NE 43RD AVE", step2.streetName);
                    // assertEquals(RelativeDirection.LEFT, step2.relativeDirection);
                    // assertTrue(step2.stayOn);
                    // assertEquals("From", response.getPlan().from.orig);
                    // assertEquals("From", leg.from.orig);
                    // leg = itinerary.legs.get(itinerary.legs.size() - 1);
                    // assertEquals("To", leg.to.orig);
                    // assertEquals("To", response.getPlan().to.orig);
                }
            }
            LOG.info("Done routing");
        }

    }
}
