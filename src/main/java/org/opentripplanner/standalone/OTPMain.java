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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;

import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.graph_builder.GraphBuilder;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.algorithm.Algorithm;
import org.opentripplanner.routing.algorithm.PNRDijkstra;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.impl.DefaultStreetVertexIndexFactory;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.routing.impl.GraphScanner;
import org.opentripplanner.routing.impl.InputStreamGraphSource;
import org.opentripplanner.routing.impl.MemoryGraphSource;
import org.opentripplanner.routing.pathparser.BasicPathParser;
import org.opentripplanner.routing.pathparser.NoThruTrafficPathParser;
import org.opentripplanner.routing.pathparser.PathParser;
import org.opentripplanner.routing.services.GraphService;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.vertextype.IntersectionVertex;
import org.opentripplanner.routing.vertextype.ParkAndRideVertex;
import org.opentripplanner.routing.vertextype.TransitStop;
import org.opentripplanner.scripting.impl.BSFOTPScript;
import org.opentripplanner.scripting.impl.OTPScript;
import org.opentripplanner.standalone.twowayutil.BBox;
import org.opentripplanner.standalone.twowayutil.TwoWayTester;
import org.opentripplanner.util.DateUtils;
import org.opentripplanner.visualizer.GraphVisualizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * This is the main entry point to OpenTripPlanner. It allows both building graphs and starting up
 * an OTP server depending on command line options. OTPMain is a concrete class making it possible
 * to construct one with custom CommandLineParameters and use its graph builder construction method
 * from web services or scripts, not just from the static main function below.
 *
 * TODO still it seems fairly natural for all of these methods to be static.
 */
public class OTPMain {

    private static final Logger LOG = LoggerFactory.getLogger(OTPMain.class);

    public static final String OTP_CONFIG_FILENAME = "otp-config.json";

    private final CommandLineParameters params;

    public OTPServer otpServer = null;

    public GraphService graphService = null;

    /**
     * ENTRY POINT: This is the main method that is called when running otp.jar from the command
     * line.
     */
    public static void main(String[] args) {

        /* Parse and validate command line parameters. */
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
        if (params.build == null && !params.visualize && !params.server
                && params.scriptFile == null && !params.enableScriptingWebService
                && !params.oneWayTest && !params.twoWayTest && !params.generateTestData) {
            LOG.info("Nothing to do. Use --help to see available tasks.");
            System.exit(-1);
        }

        OTPMain main = new OTPMain(params);
        main.run();

    }

    /* Constructor. */
    public OTPMain(CommandLineParameters params) {
        this.params = params;
    }

    /**
     * Making OTPMain a concrete class and placing this logic an instance method instead of
     * embedding it in the static main method makes it possible to build graphs from web services or
     * scripts, not just from the command line.
     */
    public void run() {

        // TODO do params.infer() here to ensure coherency?

        /* Create the top-level objects that represent the OTP server. */
        makeGraphService();
        otpServer = new OTPServer(params, graphService);

        /* Start graph builder if requested */
        if (params.build != null) {
            GraphBuilder graphBuilder = GraphBuilder.forDirectory(params, params.build); // TODO
                                                                                         // multiple
                                                                                         // directories
            if (graphBuilder != null) {
                graphBuilder.run();
                /*
                 * If requested, hand off the graph to the server as the default graph using an
                 * in-memory GraphSource.
                 */
                if (params.inMemory || params.preFlight) {
                    Graph graph = graphBuilder.getGraph();
                    graph.index(new DefaultStreetVertexIndexFactory());
                    // FIXME set true router IDs
                    graphService.registerGraph("", new MemoryGraphSource("", graph,
                            graphBuilder.routerConfig));
                }
            } else {
                LOG.error("An error occurred while building the graph. Exiting.");
                System.exit(-1);
            }
        }

        /* Scan for graphs to load from disk if requested */
        // FIXME eventually router IDs will be present even when just building a graph.
        if ((params.routerIds != null && params.routerIds.size() > 0) || params.autoScan) {
            /* Auto-register pre-existing graph on disk, with optional auto-scan. */
            GraphScanner graphScanner = new GraphScanner(graphService, params.graphDirectory,
                    params.autoScan);
            graphScanner.basePath = params.graphDirectory;
            if (params.routerIds.size() > 0) {
                graphScanner.defaultRouterId = params.routerIds.get(0);
            }
            graphScanner.autoRegister = params.routerIds;
            graphScanner.startup();
        }

        /* Start visualizer if requested */
        if (params.visualize) {
            Router defaultRouter = graphService.getRouter();
            defaultRouter.graphVisualizer = new GraphVisualizer(defaultRouter);
            defaultRouter.graphVisualizer.run();
            defaultRouter.timeouts = new double[] { 60 }; // avoid timeouts due to search animation
        }

        /* Start script if requested */
        if (params.scriptFile != null) {
            try {
                OTPScript otpScript = new BSFOTPScript(otpServer, params.scriptFile);
                if (otpScript != null) {
                    Object retval = otpScript.run();
                    if (retval != null) {
                        LOG.warn("Your script returned something, no idea what to do with it: {}",
                                retval);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /* Start web server if requested */
        if (params.twoWayRouting)
            otpServer.getRouter("default").defaultRoutingRequest.twoway = true;
        if (params.server) {
            GrizzlyServer grizzlyServer = new GrizzlyServer(params, otpServer);
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

        if (params.oneWayTest) {
            TwoWayTester tester = new TwoWayTester(otpServer);
            tester.oneWayTest(params.testInput, params.testOutput);
            System.exit(0);
        }

        if (params.twoWayTest) {
            // TwoWayTester tester = new TwoWayTester(otpServer);
            // tester.twoWayTest(params.testInput, params.testOutput);
            Router router = otpServer.getRouter("default");
            GraphPathFinder gpFinder = new GraphPathFinder(router);
            RoutingRequest rq = router.defaultRoutingRequest.clone();
            rq.routerId = "default";
            rq.setFromString("45.50674541159436,9.195583462715149");
            rq.setToString("45.46817080880512,9.19528841972351");
            rq.maxWalkDistance = 1207.008;
            rq.wheelchairAccessible = false;
            
            rq.showIntermediateStops = false;
            rq.clampInitialWait = -1;
            rq.arriveBy = false;
            rq.locale = Locale.ENGLISH;
            rq.modes = new TraverseModeSet("WALK,CAR,TRANSIT");
            rq.parkAndRide = true;
            // rq.twoway = true;
            rq.setDateTime("03-07-2015", "9:09am", router.graph.getTimeZone());
            if (rq.rctx == null) {
                rq.setRoutingContext(router.graph);
//                rq.rctx.pathParsers = new PathParser[] { new BasicPathParser(),
//                        new NoThruTrafficPathParser() };
            }
            rq.numItineraries = 2;
            rq.dominanceFunction = new DominanceFunction.MinimumWeight();
            rq.longDistance = true;
            rq.maxTransfers = 4;
            Algorithm d = new PNRDijkstra();
            List<GraphPath> paths = d.getShortestPathTree(rq).getPaths();
            if (paths.isEmpty())
                LOG.info("WTF");
            System.exit(0);
        }

        if (params.generateTestData) {
            TwoWayTester tester = new TwoWayTester(otpServer);
            tester.generateTestData(new BBox(params.bboxSrc), new BBox(params.bboxTgt),
                    params.testOutput, 3);
            System.exit(0);
        }

        if (params.anStats) {
            showANStats();
            System.exit(0);
        }
    }

    public void showANStats() {
        Graph g = otpServer.getRouter("default").graph;

        int counter = 0, an = 0, ban = 0, pnr = 0, ans = 0, bans = 0, pnrs = 0;
        for (IntersectionVertex iv : Iterables.filter(g.getVertices(), IntersectionVertex.class)) {
            if (iv.accessNodes != null && iv.accessNodes.size() >= 0) {
                an++;
                ans += iv.accessNodes.size();
            }
            if (iv.backwardAccessNodes != null && iv.backwardAccessNodes.size() >= 0) {
                ban++;
                bans += iv.backwardAccessNodes.size();
            }
            if (iv.pnrNodes != null && iv.pnrNodes.size() >= 0) {
                pnr++;
                pnrs += iv.pnrNodes.size();
            }
            counter++;
        }
        int s = Lists.newArrayList(Iterables.filter(g.getVertices(), TransitStop.class)).size();
        LOG.info("Transit Stops: " + s);
        LOG.info(an + " Intersection vertices out of " + counter + " have access nodes");
        LOG.info("Intersection vertices with access nodes have " + (ans / an)
                + " access nodes on average");
        LOG.info(ban + " Intersection vertices out of " + counter + " have backward access nodes");
        LOG.info("Intersection vertices with backward access nodes have " + (bans / ban)
                + " backward access nodes on average");
        LOG.info(pnr + " Intersection vertices out of " + counter + " have PNR nodes");
        LOG.info("Intersection vertices with PNR nodes have " + (pnrs / pnr)
                + " PNR nodes on average");

        counter = an = ban = ans = bans = 0;
        for (ParkAndRideVertex iv : Iterables.filter(g.getVertices(), ParkAndRideVertex.class)) {
            if (iv.accessNodes != null && iv.accessNodes.size() >= 0) {
                an++;
                ans += iv.accessNodes.size();
            }
            if (iv.backwardAccessNodes != null && iv.backwardAccessNodes.size() >= 0) {
                ban++;
                bans += iv.backwardAccessNodes.size();
            }
            counter++;
        }

        LOG.info(an + " PNR Nodes out of " + counter + " have access nodes");
        LOG.info("PNR Nodes with access nodes have " + (ans / an) + " access nodes on average");
        LOG.info(ban + " PNR Nodes out of " + counter + " have backward access nodes");
        LOG.info("PNR Nodes with backward access nodes have " + (bans / ban)
                + " backward access nodes on average");
    }

    /**
     * Create a cached GraphService that will be used by all OTP components to resolve router IDs to
     * Graphs. If a graph is supplied (graph parameter is not null) then that graph is also
     * registered. TODO move into OTPServer and/or GraphService itself, eliminate FileFactory and
     * put basePath in GraphService
     */
    public void makeGraphService() {
        graphService = new GraphService(params.autoReload);
        InputStreamGraphSource.FileFactory graphSourceFactory = new InputStreamGraphSource.FileFactory(
                params.graphDirectory);
        graphService.graphSourceFactory = graphSourceFactory;
        if (params.graphDirectory != null) {
            graphSourceFactory.basePath = params.graphDirectory;
        }
    }

    /**
     * Open and parse the JSON file at the given path into a Jackson JSON tree. Comments and
     * unquoted keys are allowed. Returns null if the file does not exist, Returns null if the file
     * contains syntax errors or cannot be parsed for some other reason.
     *
     * We do not require any JSON config files to be present because that would get in the way of
     * the simplest rapid deployment workflow. Therefore we return an empty JSON node when the file
     * is missing, causing us to fall back on all the default values as if there was a JSON file
     * present with no fields defined.
     */
    public static JsonNode loadJson(File file) {
        try (FileInputStream jsonStream = new FileInputStream(file)) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
            JsonNode config = mapper.readTree(jsonStream);
            LOG.info("Found and loaded JSON configuration file '{}'", file);
            return config;
        } catch (FileNotFoundException ex) {
            LOG.info("File '{}' is not present. Using default configuration.", file);
            return MissingNode.getInstance();
        } catch (Exception ex) {
            LOG.error("Error while parsing JSON config file '{}': {}", file, ex.getMessage());
            System.exit(42); // probably "should" be done with an exception
            return null;
        }
    }

}
