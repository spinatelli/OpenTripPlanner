package org.opentripplanner.standalone;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jersey.repackaged.com.google.common.collect.Lists;

import org.opentripplanner.routing.services.GraphService;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;

/**
 * This is a JCommander-annotated class that holds parameters for OTP stand-alone mode.
 * These parameters can be parsed from the command line, or provided in a file using Jcommander's
 * at-symbol syntax (see http://jcommander.org/#Syntax). When stand-alone OTP is started as a 
 * daemon, parameters are loaded from such a file, located by default in '/etc/opentripplanner.cfg'.
 * 
 * Note that JCommander-annotated parameters can be any type that can be constructed from a string.
 * This module also contains classes for validating parameters. 
 * See: http://jcommander.org/#Parameter_validation
 * 
 * Some parameter fields are not initialized so when inferring other parameters, we can check for 
 * null and see whether they were specified on the command line.
 * 
 * @author abyrd
 */
public class CommandLineParameters implements Cloneable {

    private static final int    DEFAULT_PORT        = 8080;
    private static final int    DEFAULT_SECURE_PORT = 8081;
    private static final String DEFAULT_BASE_PATH   = "/var/otp";
    private static final String DEFAULT_ROUTER_ID   = "";

    /* Options for the command itself, rather than build or server sub-tasks. */

    @Parameter(names = {"--help"}, help = true,
            description = "Print this help message and exit.")
    public boolean help;

    @Parameter(names = {"--verbose"},
            description = "Verbose output.")
    public boolean verbose;

    @Parameter(names = {"--basePath"}, validateWith = ReadWriteDirectory.class,
            description = "Set the path under which graphs, caches, etc. are stored by default.")
    public String basePath = DEFAULT_BASE_PATH;

    /* Options for the graph builder sub-task. */

    @Parameter(names = {"--build"}, validateWith = ReadableDirectory.class,
            description = "Build graphs at specified paths.", variableArity = true)
    public File build;

    @Parameter(names = {"--cache"}, validateWith = ReadWriteDirectory.class,
            description = "The directory under which to cache OSM and NED tiles. Default is BASE_PATH/cache.")
    public File cacheDirectory;

    @Parameter(names = {"--inMemory"},
            description = "Pass the graph to the server in-memory after building it, without saving to disk.")
    public boolean inMemory;

    @Parameter(names = {"--preFlight"},
            description = "Pass the graph to the server in-memory after building it, and saving to disk.")
    public boolean preFlight;

    @Parameter(names = {"--noTransit"},
            description = "Skip all transit input files (GTFS).")
    public boolean noTransit;

    @Parameter(names = {"--useTransfersTxt"},
            description = "Create direct transfer edges from transfers.txt in GTFS, instead of based on distance.")
    public boolean useTransfersTxt;

    @Parameter(names = {"--noParentStopLinking"},
            description = "Skip linking of stops to parent stops (GTFS).")
    public boolean noParentStopLinking;

    @Parameter(names = {"--parentStationTransfers"},
            description = "Create direct transfers between the constituent stops of each parent station.")
    public boolean parentStationTransfers = false;

    @Parameter(names = {"--noStreets"},
            description = "Skip all street input files (OSM/PBF).")
    public boolean noStreets;

    @Parameter(names = {"--noEmbedConfig"},
            description = "Skip embedding config in graph (Embed.properties).")
    public boolean noEmbedConfig = false;

    @Parameter(names = {"--skipVisibility"},
            description = "Skip area visibility calculations, which are often time consuming.")
    public boolean skipVisibility;

    @Parameter(names = {"--matchBusRoutesToStreets"},
            description = "Based on GTFS data, guess which OSM streets each bus runs on to improve stop linking.")
    public boolean matchBusRoutesToStreets = false;

    @Parameter(names = {"--computeAccessNodes"},
            description = "Precompute access nodes for road nodes.")
    boolean computeAccessNodes;
    
    @Parameter(names = {"--printAccessNodes"},
            description = "Print computed access node stats")
    public boolean anStats = false;
    
    @Parameter(names = {"--cityCenter"},
            description = "Coordinates representing the city center. Street directions are computed wrt this location.")
    public String cityCenter = null;
    
    @Parameter(names = {"--twoWayTest"},
            description = "Perform 2-way PNR routing tests")
    public boolean twoWayTest = false;
    
    @Parameter(names = {"--oneWayTest"},
            description = "Perform one-way routing tests starting from 2-way test data")
    public boolean oneWayTest = false;
    
    @Parameter(names = {"--generateTestData"},
            description = "Generate test data for 2-way PNR routing tests")
    public boolean generateTestData = false;
    
    @Parameter(names = {"--testInput"},
            description = "Input CSV file for 2-way PNR routing tests")
    public File testInput;
    
    @Parameter(names = {"--testOutput"},
            description = "Output CSV file of 2-way PNR routing tests")
    public File testOutput;
    
    @Parameter(names = {"--twoWayRouting"},
            description = "Two way routing with PNR nodes")
    public boolean twoWayRouting = false;
    
    @Parameter(names = {"--bboxSrc"},
            description = "Source location bounding box")
    public String bboxSrc = "";
    
    @Parameter(names = {"--bboxTgt"},
            description = "Target location bounding box")
    public String bboxTgt = "";

    /* Options for the server sub-task. */

    @Parameter(names = {"--analyst"},
            description = "Enable OTP Analyst extensions.")
    public boolean analyst;

    @Parameter(names = {"--bindAddress"},
            description = "Specify which network interface to bind to by address. 0.0.0.0 means all interfaces.")
    public String bindAddress = "0.0.0.0";

    @Parameter(names = {"--securePort"}, validateWith = AvailablePort.class,
            description = "Server port for HTTPS.")
    public Integer securePort;

    @Parameter(names = {"--autoScan"}, description = "Auto-scan for graphs to register in graph directory.")
    public boolean autoScan = false;

    @Parameter(names = {"--autoReload"}, description = "Auto-reload registered graphs when source data is modified.")
    public boolean autoReload = false;

    @Parameter(names = {"--port"}, validateWith = AvailablePort.class,
            description = "Server port for plain HTTP.")
    public Integer port;

    @Parameter(names = {"--graphs"}, validateWith = ReadableDirectory.class,
            description = "Path to directory containing graphs. Defaults to BASE_PATH/graphs.")
    public File graphDirectory;

    @Parameter(names = {"--pointSets"}, validateWith = ReadableDirectory.class,
            description = "Path to directory containing PointSets. Defaults to BASE_PATH/pointsets.")
    public File pointSetDirectory;

    @Parameter(names = {"--clientFiles"}, validateWith = ReadableDirectory.class,
            description = "Path to directory containing local client files to serve.")
    public File clientDirectory = null;

    @Parameter(names = {"--router"}, validateWith = RouterId.class,
            description = "One or more router IDs to build and/or serve, first one being the default.")
    public List<String> routerIds = new ArrayList<String>();

    @Parameter(names = {"--server"},
            description = "Run an OTP API server.")
    public boolean server = false;

    @Parameter(names = {"--visualize"},
            description = "Open a graph visualizer window for debugging.")
    public boolean visualize;

    // TODO should these replace the files auto-discovered in the router directory?
    @Parameter(validateWith = ReadableFile.class, // the remaining parameters in one array
            description = "Files for graph build.")
    public List<File> files = new ArrayList<File>();

    @Parameter(names = {"--insecure"},
            description = "Allow unauthenticated access to sensitive API resources, e.g. /routers")
    public boolean insecure = false;

    @Parameter(names = { "--script" }, description = "run the specified OTP script (groovy, python)")
    public File scriptFile = null;

    @Parameter(names = { "--enableScriptingWebService" }, description = "enable scripting through a web-service (Warning! Very unsafe for public facing servers)")
    boolean enableScriptingWebService = false;

    /** Set some convenience parameters based on other parameters' values. */
    public void infer() {
        server |= (inMemory || preFlight || port != null);
        if (basePath == null) basePath = DEFAULT_BASE_PATH;
        /* If user has not overridden these paths, use default locations under the base path. */
        if (cacheDirectory == null) cacheDirectory = new File(basePath, "cache");
        if (graphDirectory == null) graphDirectory = new File(basePath, "graphs");
        if (pointSetDirectory == null) pointSetDirectory = new File(basePath, "pointsets");
        if (server && port == null) {
            port = DEFAULT_PORT;
            new AvailablePort().validate(port);
        }
        if (server && securePort == null) {
            securePort = DEFAULT_SECURE_PORT;
            new AvailablePort().validate(securePort);
        }
    }

    public CommandLineParameters clone() {
        CommandLineParameters ret;
        try {
            ret = (CommandLineParameters) super.clone();
        } catch (CloneNotSupportedException e) {
            return null;
        }

        if (this.routerIds != null) {
            ret.routerIds = Lists.newArrayList();
            ret.routerIds.addAll(this.routerIds);
        }
        
        return ret;
    }
    
    public static class ReadableFile implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            File file = new File(value);
            if ( ! file.isFile()) {
                String msg = String.format("%s: '%s' is not a file.", name, value);
                throw new ParameterException(msg);
            }
            if ( ! file.canRead()) {
                String msg = String.format("%s: file '%s' is not readable.", name, value);
                throw new ParameterException(msg);
            }
        }
    }
    
    public static class ReadableDirectory implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            File file = new File(value);
            if ( ! file.isDirectory()) {
                String msg = String.format("%s: '%s' is not a directory.", name, value);
                throw new ParameterException(msg);
            }
            if ( ! file.canRead()) {
                String msg = String.format("%s: directory '%s' is not readable.", name, value);
                throw new ParameterException(msg);
            }
        }
    }
    
    public static class ReadWriteDirectory implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            new ReadableDirectory().validate(name, value);
            File file = new File(value);
            if ( ! file.canWrite()) {
                String msg = String.format("%s: directory '%s' is not writable.", name, value);
                throw new ParameterException(msg);
            }
        }
    }

    public static class PositiveInteger implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            Integer i = Integer.parseInt(value);
            if ( i <= 0 ) {
                String msg = String.format("%s must be a positive integer.", name);
                throw new ParameterException(msg);
            }
        }
    }

    public static class AvailablePort implements IParameterValidator {

        @Override
        public void validate(String name, String value) throws ParameterException {
            new PositiveInteger().validate(name, value);
            int port = Integer.parseInt(value);
            this.validate(port);
        }
        
        public void validate(int port) throws ParameterException {
            ServerSocket socket = null;
            boolean portUnavailable = false;
            String reason = null;
            try {
                socket = new ServerSocket(port);
            } catch (IOException e) {
                portUnavailable = true;
                reason = e.getMessage();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) { 
                        // will not be thrown
                    }
                }
            }
            if ( portUnavailable ) {
                String msg = String.format(": port %d is not available. %s.", port, reason);
                throw new ParameterException(msg);
            }
        }
    }
    
    public static class RouterId implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            if (!GraphService.routerIdLegal(value)) {
                String msg = String.format("%s: '%s' is not a valid router ID.", name, value);
                throw new ParameterException(msg);
            }
        }
    }
}

