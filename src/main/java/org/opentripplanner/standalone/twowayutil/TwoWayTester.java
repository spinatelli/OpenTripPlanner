package org.opentripplanner.standalone.twowayutil;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.resource.GraphPathToTripPlanConverter;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.algorithm.Algorithm;
import org.opentripplanner.routing.algorithm.PNRDijkstra;
import org.opentripplanner.routing.algorithm.strategies.EuclideanRemainingWeightHeuristic;
import org.opentripplanner.routing.algorithm.strategies.InterleavedBidirectionalHeuristic;
import org.opentripplanner.routing.algorithm.strategies.RemainingWeightHeuristic;
import org.opentripplanner.routing.algorithm.strategies.TrivialRemainingWeightHeuristic;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.State;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.error.PathNotFoundException;
import org.opentripplanner.routing.impl.GraphPathFinder;
import org.opentripplanner.routing.impl.PathWeightComparator;
import org.opentripplanner.routing.impl.GraphPathFinder.Parser;
import org.opentripplanner.routing.pathparser.BasicPathParser;
import org.opentripplanner.routing.pathparser.NoThruTrafficPathParser;
import org.opentripplanner.routing.pathparser.PathParser;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.routing.spt.ShortestPathTree;
import org.opentripplanner.standalone.OTPServer;
import org.opentripplanner.standalone.Router;
import org.opentripplanner.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class TwoWayTester {

    private static final Logger LOG = LoggerFactory.getLogger(TwoWayTester.class);

    long avg = 0;

    long avgDuration = 0;

    long max = 0;

    long min = Long.MAX_VALUE;

    int i = 0;

    int skipped = 0;

    OTPServer server = null;

    Random rand;
    DateFormat dateFormat;

    public TwoWayTester(OTPServer server) {
        this.server = server;
        rand = new Random();
        dateFormat = new SimpleDateFormat("h:mma");
    }

    public void clearStats() {
        avg = 0;
        avgDuration = 0;
        max = 0;
        min = Long.MAX_VALUE;
        i = 0;
        skipped = 0;
    }

    private void newTime(long t) {
        avg += t;
        max = t > max ? t : max;
        min = t < min ? t : min;
    }

    public void twoWayTest(File testInput, File testOutput) {
        LOG.info("Reading test input file");
        TwoWayCsvTestReader csv = new TwoWayCsvTestReader();
        TestEntityHandler handler = new TestEntityHandler();
        csv.fromFile(testInput, TestInput.class, handler);
        List<TestInput> infos = handler.getList();
        LOG.info("Done reading");

        LOG.info("Starting tests");
        List<TwoWayOutput> outputs = new ArrayList<TwoWayOutput>();
        Router router = server.getRouter("default");
        GraphPathFinder gpFinder = new GraphPathFinder(router); // we could also get a persistent
                                                                // router-scoped GraphPathFinder but
                                                                // there's no setup cost here
        for(TestInput info: infos) {
//        TestInput info = infos.get(0);
            LOG.info("Routing " + info);
            RoutingRequest rq = router.defaultRoutingRequest.clone();
    
            info.generateRequest(rq, router.graph);
            try {
                long time, time2, t;
                LOG.info("Planning " + i);
                time = System.currentTimeMillis();
                List<GraphPath> paths = find(router, rq);
                if (paths.isEmpty())
                    continue;
                TripPlan plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
                time2 = System.currentTimeMillis();
                t = time2 - time;
                LOG.info("Took " + t + " millis");
                newTime(t);
                TwoWayOutput out = new TwoWayOutput(info, (int) t);
                t = -1;
                if (plan != null) {
                    t = plan.itinerary.get(0).duration + plan.itinerary.get(1).duration;
                    avgDuration += t;
                    if (plan.itinerary.get(0).pnrNode != null) {
                        LOG.info(plan.itinerary.get(0).pnrNode.toString());
                        out.setParkingLat(plan.itinerary.get(0).pnrNode.y);
                        out.setParkingLon(plan.itinerary.get(0).pnrNode.x);
                    }
                }
                out.setDuration((int) t);
                LOG.info("Iteration " + i + " time " + t);
                outputs.add(out);
                i++;
            } catch (PathNotFoundException e) {
                LOG.error("Path not found");
                skipped++;
            }
        }

        try {
            csv.toFile(testOutput, outputs);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        printStats();
        clearStats();
    }
    
    public List<GraphPath> find(Router router, RoutingRequest options) {

      Algorithm d = new PNRDijkstra();
      return d.getShortestPathTree(options).getPaths();
    }

    public void oneWayTest(File testInput, File testOutput) {
        if (server == null)
            return;
        LOG.info("Reading test input file");

        TwoWayCsvTestReader csv = new TwoWayCsvTestReader();
        TwoWayTestEntityHandler handler = new TwoWayTestEntityHandler();
        csv.fromFile(testInput, TwoWayOutput.class, handler);
        List<TwoWayOutput> infos = handler.getList();
        LOG.info("Done reading");

        LOG.info("Starting tests");
        List<OneWayOutput> outputs = new ArrayList<OneWayOutput>();
        Router router = server.getRouter("default");
        GraphPathFinder gpFinder = new GraphPathFinder(router); // we could also get a persistent
                                                                // router-scoped GraphPathFinder but
                                                                // there's no setup cost here

        for (TwoWayOutput info : infos) {
            LOG.info("Routing " + info);
            RoutingRequest rq = router.defaultRoutingRequest.clone();
            info.generateRequest(rq, router.graph);
            LOG.info("Planning " + i);

            try {
                long dur = 0, time, rtime, t = 0;

                time = System.currentTimeMillis();
                List<GraphPath> paths = gpFinder.graphPathFinderEntryPoint(rq);
                TripPlan plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
                rtime = System.currentTimeMillis() - time;

                OneWayOutput out = new OneWayOutput(info);

                if (plan != null) {
                    dur = plan.itinerary.get(0).duration;
                    avgDuration += dur;
                    if (plan.itinerary.get(0).pnrNode != null) {
                        out.setOwFirstParkingLat(plan.itinerary.get(0).pnrNode.y);
                        out.setOwFirstParkingLon(plan.itinerary.get(0).pnrNode.x);
                    }
                }

                // from destination to parking
                GenericLocation l = rq.from;
                rq.from = rq.to;
                rq.to = new GenericLocation(out.getOwFirstParkingLat(), out.getOwFirstParkingLon());
                rq.dateTime = rq.returnDateTime;
                rq.setArriveBy(false);
                rq.parkAndRide = false;
                rq.setMode(TraverseMode.CAR);

                time = System.currentTimeMillis();
                paths = gpFinder.graphPathFinderEntryPoint(rq);
                plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
                rtime += System.currentTimeMillis() - time;

                if (plan != null) {
                    t = plan.itinerary.get(0).duration;
                    dur += t;
                    avgDuration += t;
                    if (plan.itinerary.get(0).pnrNode != null) {
                        LOG.info(plan.itinerary.get(0).pnrNode.toString());
                        out.setOwSecondParkingLat(plan.itinerary.get(0).pnrNode.y);
                        out.setOwSecondParkingLon(plan.itinerary.get(0).pnrNode.x);
                    }
                }

                // from parking to source
                rq.from = rq.to;
                rq.to = l;
                rq.dateTime = rq.dateTime + t + 2 * 60;
                rq.setArriveBy(false);
                rq.parkAndRide = false;
                rq.setModes(new TraverseModeSet("TRANSIT,WALK"));

                time = System.currentTimeMillis();
                paths = gpFinder.graphPathFinderEntryPoint(rq);
                plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
                rtime += System.currentTimeMillis() - time;
                newTime(rtime);

                if (plan != null) {
                    t = plan.itinerary.get(0).duration;
                    dur += t;
                    avgDuration += t;
                    if (plan.itinerary.get(0).pnrNode != null) {
                        LOG.info(plan.itinerary.get(0).pnrNode.toString());
                        out.setOwSecondParkingLat(plan.itinerary.get(0).pnrNode.y);
                        out.setOwSecondParkingLon(plan.itinerary.get(0).pnrNode.x);
                    }
                }

                out.setOwDuration((int) dur);
                out.setOwTime((int) rtime);
                outputs.add(out);
                LOG.info("Routing " + out);
            } catch (PathNotFoundException e) {
                LOG.error("Path not found");
                skipped++;
            }
        }

        try {
            csv.toFile(testOutput, outputs);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        printStats();
        clearStats();
    }

    public void generateTestData(TestGenerationParameters params, TimeZone tz) {
        LOG.info("Generating test data");
        Random r = new Random();
        List<TestInput> list = new ArrayList<TestInput>();
        
        for (int i = 0; i < params.experimentsNumber; i++) {
            TestInput t = new TestInput(i);
            
            t.setFrom(generateLocation(r, params.bboxSrc, params.bboxSrcExcept));
            t.setTo(generateLocation(r, params.bboxTgt));
            
            long time = generateTime(t.getArrivalDate(), params.arrFromTime, params.arrToTime, tz);
            t.setArrivalTime(dateFormat.format(new Date(time)));
            
//          t.setDepartureTime(dateFormat.format(new Date(time+6*60*60*1000)));
            time = generateTime(t.getArrivalDate(), params.arrFromTime, params.arrToTime, tz);
            t.setDepartureTime(dateFormat.format(new Date(time)));
            
            int car = rand.nextInt(2);
            if (car == 1)
                t.setInitialMode("CAR");
            else
                t.setInitialMode("BICYCLE");
            
            list.add(t);
            LOG.info("Generated "+i);
        }
        
        TwoWayCsvTestReader csv = new TwoWayCsvTestReader();
        try {
            LOG.info("Output file: " + params.outputFile.getAbsolutePath());
            csv.toFile(params.outputFile, list);
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOG.info("Done generating");
    }
    
    private long generateTime(String date, String from, String to, TimeZone tz) {
        Date fromDT = DateUtils.toDate(date, from, tz);
        Date toDT = DateUtils.toDate(date, to, tz);
        int deltaMinutes = (int)((toDT.getTime() / 1000)-(fromDT.getTime() / 1000));
        int randomMinutes = rand.nextInt(deltaMinutes + 1);
        return fromDT.getTime()+randomMinutes*60*1000;
    }

    private GenericLocation generateLocation(Random r, BBox bbox) {
        return generateLocation(r, bbox, null);
    }
    
    private GenericLocation generateLocation(Random r, BBox bbox, BBox except) {
        double lat;
        do {
            lat = bbox.minLat + (bbox.maxLat - bbox.minLat) * r.nextDouble();
        } while (except != null && lat < except.maxLat && lat > except.minLat);
        double lon;
        do {
            lon = bbox.minLon + (bbox.maxLon - bbox.minLon) * r.nextDouble();
        } while (except != null && lon < except.maxLat && lon > except.minLat);
        return new GenericLocation(lat, lon);
    }

    public void printStats() {
        LOG.info("Done routing, skipped " + skipped);
        if (i != skipped) {
            avg = avg / (i - skipped);
            avgDuration = avgDuration / (i - skipped);
        }
        LOG.info("Average routing time " + avg + " ms = " + avg / 1000 + " s");
        LOG.info("Minimum routing time " + min + " ms = " + min / 1000 + " s");
        LOG.info("Maximum routing time " + max + " ms = " + max / 1000 + " s");
        LOG.info("Average duration " + avgDuration + " s = " + avgDuration / 60 + " mins");
    }
}
