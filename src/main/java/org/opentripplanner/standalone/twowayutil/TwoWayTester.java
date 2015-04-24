package org.opentripplanner.standalone.twowayutil;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

import org.opentripplanner.api.model.TripPlan;
import org.opentripplanner.api.resource.GraphPathToTripPlanConverter;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.algorithm.AStar;
import org.opentripplanner.routing.algorithm.Algorithm;
import org.opentripplanner.routing.algorithm.OneWayPNRDijkstra;
import org.opentripplanner.routing.algorithm.PNRDijkstra;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.spt.GraphPath;
import org.opentripplanner.standalone.OTPServer;
import org.opentripplanner.standalone.Router;
import org.opentripplanner.util.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        int bike = 0, car = 0;
        for (TestInput info : infos) {
            if ((info.getInitialMode().equals("CAR") && car > 75) || (info.getInitialMode().equals("BICYCLE") && bike > 75))
                continue;
            
            LOG.info("Routing " + info);
            RoutingRequest rq = router.defaultRoutingRequest.clone();

            info.generateRequest(rq, router.graph);
            long by = rq.dateTime, after = rq.returnDateTime;
            long time, t;
            LOG.info("Planning " + i);
            
            time = System.currentTimeMillis();
            List<GraphPath> paths = findTwoWay(router, rq);
            if (paths.isEmpty()) {
                skipped++;
                continue;
            }
            t = System.currentTimeMillis() - time;
//            LOG.info("Took " + t + " millis");
            newTime(t);
            
            TripPlan plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
            TwoWayOutput out = new TwoWayOutput(info, (int) t);
            t = -1;
            if (plan != null) {
                if (plan.itinerary.get(0).pnrNode != null) {
                    out.setParkingLat(plan.itinerary.get(0).pnrNode.y);
                    out.setParkingLon(plan.itinerary.get(0).pnrNode.x);
                    out.setOutwardLeg((int)(by - plan.itinerary.get(0).startTime.getTimeInMillis()/1000));
                    out.setReturnLeg((int)(plan.itinerary.get(1).endTime.getTimeInMillis()/1000 - after));

                    out.setOutwardTransfers(plan.itinerary.get(0).transfers);
                    out.setOutwardTransitTime(plan.itinerary.get(0).transitTime);
                    out.setOutwardWalkTime(plan.itinerary.get(0).walkTime);
                    out.setOutwardCarTime(plan.itinerary.get(0).carTime);
                    out.setOutwardBikeTime(plan.itinerary.get(0).bikeTime);
                    out.setOutwardWaitingTime(plan.itinerary.get(0).waitingTime);

                    out.setReturnTransfers(plan.itinerary.get(1).transfers);
                    out.setReturnTransitTime(plan.itinerary.get(1).transitTime);
                    out.setReturnWalkTime(plan.itinerary.get(1).walkTime);
                    out.setReturnCarTime(plan.itinerary.get(1).carTime);
                    out.setReturnBikeTime(plan.itinerary.get(1).bikeTime);
                    out.setReturnWaitingTime(plan.itinerary.get(1).waitingTime);
//                    LOG.info("before "+by+" start "+plan.itinerary.get(0).startTime.getTimeInMillis()/1000+" end "+plan.itinerary.get(0).endTime.getTimeInMillis()/1000);
//                    LOG.info("after "+after+" start "+plan.itinerary.get(1).startTime.getTimeInMillis()/1000+" end "+plan.itinerary.get(1).endTime.getTimeInMillis()/1000);
//                    LOG.info("out "+out.getOutwardLeg()+" ret "+out.getReturnLeg());
                }
//                LOG.info("Iteration " + i + " time " + t);
                outputs.add(out);
                i++;
            }
            if (info.getInitialMode().equals("CAR")) car++;
            else bike++;
            
            if (car > 75 && bike > 75)
                break;
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

    public void twoOneWayTest(File testInput, File testOutput) {
        LOG.info("Reading test input file");
        TwoWayCsvTestReader csv = new TwoWayCsvTestReader();
        TestEntityHandler handler = new TestEntityHandler();
        csv.fromFile(testInput, TestInput.class, handler);
        List<TestInput> infos = handler.getList();
        LOG.info("Done reading");
        LOG.info("Starting tests");
        List<TwoWayOutput> outputs = new ArrayList<TwoWayOutput>();
        Router router = server.getRouter("default");
        int bike = 0, car = 0;
        
        for (TestInput info : infos) {
            if ((info.getInitialMode().equals("CAR") && car > 75) || (info.getInitialMode().equals("BICYCLE") && bike > 75))
                continue;
            
            LOG.info("Routing " + info);
            RoutingRequest rq = router.defaultRoutingRequest.clone();

            info.generateRequest(rq, router.graph);
            long by = rq.dateTime, after = rq.returnDateTime;
            long time, t;
            LOG.info("Planning " + i);
            
            time = System.currentTimeMillis();
            List<GraphPath> paths = findTwoOneWay(router, rq);
            if (paths.isEmpty()) {
                skipped++;
                continue;
            }
            t = System.currentTimeMillis() - time;
//            LOG.info("Took " + t + " millis");
            newTime(t);
            
            TripPlan plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
            TwoWayOutput out = new TwoWayOutput(info, (int) t);
            t = -1;
            if (plan != null) {
                if (plan.itinerary.get(0).pnrNode != null) {
                    out.setParkingLat(plan.itinerary.get(0).pnrNode.y);
                    out.setParkingLon(plan.itinerary.get(0).pnrNode.x);
                    out.setOutwardLeg((int)(by - plan.itinerary.get(0).startTime.getTimeInMillis()/1000));

                    out.setOutwardTransfers(plan.itinerary.get(0).transfers);
                    out.setOutwardTransitTime(plan.itinerary.get(0).transitTime);
                    out.setOutwardWalkTime(plan.itinerary.get(0).walkTime);
                    out.setOutwardCarTime(plan.itinerary.get(0).carTime);
                    out.setOutwardBikeTime(plan.itinerary.get(0).bikeTime);
                    out.setOutwardWaitingTime(plan.itinerary.get(0).waitingTime);
//                    LOG.info("before "+by+" start "+plan.itinerary.get(0).startTime.getTimeInMillis()/1000+" end "+plan.itinerary.get(0).endTime.getTimeInMillis()/1000);
//                    LOG.info("after "+after+" start "+plan.itinerary.get(1).startTime.getTimeInMillis()/1000+" end "+plan.itinerary.get(1).endTime.getTimeInMillis()/1000);
//                    LOG.info("out "+out.getOutwardLeg()+" ret "+out.getReturnLeg());
                }
            }
            
            rq.dateTime = rq.returnDateTime;
            rq.setArriveBy(false);
            time = System.currentTimeMillis();
            paths = findTwoOneWay(router, rq);
            if (paths.isEmpty()) {
                skipped++;
                continue;
            }
            t = System.currentTimeMillis() - time;
            newTime(t);
            
            plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
            t = -1;

            if (plan != null) {
                if (plan.itinerary.get(0).pnrNode != null) {
                    out.setParkingLat(plan.itinerary.get(1).pnrNode.y);
                    out.setParkingLon(plan.itinerary.get(1).pnrNode.x);
                    out.setReturnLeg((int)(plan.itinerary.get(1).endTime.getTimeInMillis()/1000 - after));

                    out.setReturnTransfers(plan.itinerary.get(1).transfers);
                    out.setReturnTransitTime(plan.itinerary.get(1).transitTime);
                    out.setReturnWalkTime(plan.itinerary.get(1).walkTime);
                    out.setReturnCarTime(plan.itinerary.get(1).carTime);
                    out.setReturnBikeTime(plan.itinerary.get(1).bikeTime);
                    out.setReturnWaitingTime(plan.itinerary.get(0).waitingTime);
//                    LOG.info("before "+by+" start "+plan.itinerary.get(0).startTime.getTimeInMillis()/1000+" end "+plan.itinerary.get(0).endTime.getTimeInMillis()/1000);
//                    LOG.info("after "+after+" start "+plan.itinerary.get(1).startTime.getTimeInMillis()/1000+" end "+plan.itinerary.get(1).endTime.getTimeInMillis()/1000);
//                    LOG.info("out "+out.getOutwardLeg()+" ret "+out.getReturnLeg());
                }
                outputs.add(out);
                i++;
            }
            
            if (info.getInitialMode().equals("CAR")) car++;
            else bike++;
            
            if (car > 75 && bike > 75)
                break;
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

    public List<GraphPath> findTwoOneWay(Router router, RoutingRequest options) {
        Algorithm d = new OneWayPNRDijkstra();
        return d.getShortestPathTree(options).getPaths();
    }

    public List<GraphPath> findTwoWay(Router router, RoutingRequest options) {
        Algorithm d = new PNRDijkstra();
        return d.getShortestPathTree(options).getPaths();
    }

    public List<GraphPath> findOneWay(Router router, RoutingRequest options) {
        Algorithm d = new AStar();
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

        for (TwoWayOutput info : infos) {
            LOG.info("Routing " + info);
            RoutingRequest rq = router.defaultRoutingRequest.clone();
            info.generateRequest(rq, router.graph, true);
            LOG.info("Planning " + i);
            long by = rq.dateTime;
            long time, t = 0, timeOw=0;

            time = System.currentTimeMillis();
            List<GraphPath> paths = findOneWay(router, rq);
            if (paths.isEmpty()) {
                skipped++;
                continue;
            }
            TripPlan plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
            t = System.currentTimeMillis() - time;
            timeOw = t;
            OneWayOutput out = new OneWayOutput(info);
            t = -1;

            if (plan != null) {
                if (plan.itinerary.get(0).pnrNode != null) {
                    out.setOwFirstParkingLat(plan.itinerary.get(0).pnrNode.y);
                    out.setOwFirstParkingLon(plan.itinerary.get(0).pnrNode.x);
                }
                out.setOwOutwardLeg((int)(by - plan.itinerary.get(0).startTime.getTimeInMillis()/1000));
                out.setOwOutwardTransfers(plan.itinerary.get(0).transfers);
                out.setOwOutwardTransitTime(plan.itinerary.get(0).transitTime);
                out.setOwOutwardWalkTime(plan.itinerary.get(0).walkTime);
                out.setOwOutwardCarTime(plan.itinerary.get(0).carTime);
                out.setOwOutwardBikeTime(plan.itinerary.get(0).bikeTime);
                out.setOwOutwardWaitingTime(plan.itinerary.get(0).waitingTime);
            }

            // from destination to parking
            rq = router.defaultRoutingRequest.clone();
            info.generateRequest(rq, router.graph, false);
            long after = rq.dateTime;

            time = System.currentTimeMillis();
            paths = findOneWay(router, rq);
            if (paths.isEmpty()) {
                skipped++;
                continue;
            }
            plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
            t = System.currentTimeMillis() - time;
            timeOw += t;
            newTime(timeOw);

            if (plan != null) {
                if (plan.itinerary.get(0).pnrNode != null) {
                    out.setOwSecondParkingLat(plan.itinerary.get(0).pnrNode.y);
                    out.setOwSecondParkingLon(plan.itinerary.get(0).pnrNode.x);
                }
                out.setOwReturnLeg((int)(plan.itinerary.get(0).endTime.getTimeInMillis()/1000 - after));
                out.setOwReturnTransfers(plan.itinerary.get(0).transfers);
                out.setOwReturnTransitTime(plan.itinerary.get(0).transitTime);
                out.setOwReturnWalkTime(plan.itinerary.get(0).walkTime);
                out.setOwReturnCarTime(plan.itinerary.get(0).carTime);
                out.setOwReturnBikeTime(plan.itinerary.get(0).bikeTime);
                out.setOwReturnWaitingTime(plan.itinerary.get(0).waitingTime);
            }

            out.setOwTime((int) timeOw);
            outputs.add(out);
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

    public void oneWayOptimizedTest(File testInput, File testOutput) {
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

        for (TwoWayOutput info : infos) {
            LOG.info("Routing " + info);
            RoutingRequest rq = router.defaultRoutingRequest.clone();
            info.generateRequest(rq, router.graph, true);
            LOG.info("Planning " + i);

            long by = rq.dateTime, after = rq.returnDateTime;
            long time, t = 0, timeOw=0;

            time = System.currentTimeMillis();
            List<GraphPath> paths = findOneWay(router, rq);
            if (paths.isEmpty()) {
                skipped++;
                continue;
            }
            TripPlan plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
            t = System.currentTimeMillis() - time;
            LOG.info("From "+rq.from+" to "+rq.to+" Took " + t + " millis lasts "+plan.itinerary.get(0).duration);
            timeOw = t;
            OneWayOutput out = new OneWayOutput(info);
            t = -1;

            if (plan != null) {
                if (plan.itinerary.get(0).pnrNode != null) {
                    out.setOwFirstParkingLat(plan.itinerary.get(0).pnrNode.y);
                    out.setOwFirstParkingLon(plan.itinerary.get(0).pnrNode.x);
                }
                out.setOwOutwardLeg((int)(by - plan.itinerary.get(0).startTime.getTimeInMillis()/1000));

                out.setOwOutwardTransfers(plan.itinerary.get(0).transfers);
                out.setOwOutwardTransitTime(plan.itinerary.get(0).transitTime);
                out.setOwOutwardWalkTime(plan.itinerary.get(0).walkTime);
                out.setOwOutwardCarTime(plan.itinerary.get(0).carTime);
                out.setOwOutwardBikeTime(plan.itinerary.get(0).bikeTime);
                out.setOwOutwardWaitingTime(plan.itinerary.get(0).waitingTime);
            }
            out.setOwSecondParkingLat(plan.itinerary.get(0).pnrNode.y);
            out.setOwSecondParkingLon(plan.itinerary.get(0).pnrNode.x);

            // from destination to parking
            rq = router.defaultRoutingRequest.clone();
            GenericLocation parking = new GenericLocation(plan.itinerary.get(0).pnrNode.y, plan.itinerary.get(0).pnrNode.x);
            info.generateSecondLegRequest(rq, router.graph, parking, false);

            time = System.currentTimeMillis();
            paths = findOneWay(router, rq);
            if (paths.isEmpty()) {
                skipped++;
                continue;
            }
            plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
            t = System.currentTimeMillis() - time;
            LOG.info("From "+rq.from+" to "+rq.to+" Took " + t + " millis lasts "+plan.itinerary.get(0).duration);
            timeOw += t;

            if (plan != null) {
                t = plan.itinerary.get(0).duration;
                out.setOwReturnTransfers(plan.itinerary.get(0).transfers);
                out.setOwReturnTransitTime(plan.itinerary.get(0).transitTime);
                out.setOwReturnWalkTime(plan.itinerary.get(0).walkTime);
                out.setOwReturnCarTime(plan.itinerary.get(0).carTime);
                out.setOwReturnBikeTime(plan.itinerary.get(0).bikeTime);
                out.setOwReturnWaitingTime(plan.itinerary.get(0).waitingTime+2*60);
            }

            rq.dateTime = plan.itinerary.get(0).endTime.getTimeInMillis()/1000+2*60;
            info.generateFirstLegRequest(rq, router.graph, parking, false);

            time = System.currentTimeMillis();
            paths = findOneWay(router, rq);
            if (paths.isEmpty()) {
                skipped++;
                continue;
            }
            plan = GraphPathToTripPlanConverter.generatePlan(paths, rq);
            t = System.currentTimeMillis() - time;
            LOG.info("From "+rq.from+" to "+rq.to+" Took " + t + " millis lasts "+plan.itinerary.get(0).duration);
            timeOw += t;
            newTime(timeOw);

            if (plan != null) {
                t = plan.itinerary.get(0).duration;
                out.setOwReturnLeg((int)(plan.itinerary.get(0).endTime.getTimeInMillis()/1000 - after));
                //useless when using my algo
                out.setOwReturnTransfers(out.getOwReturnTransfers()+plan.itinerary.get(0).transfers);
                out.setOwReturnTransitTime(out.getOwReturnTransitTime()+plan.itinerary.get(0).transitTime);
                out.setOwReturnWalkTime(out.getOwReturnWalkTime()+plan.itinerary.get(0).walkTime);
                out.setOwReturnCarTime(out.getOwReturnCarTime()+plan.itinerary.get(0).carTime);
                out.setOwReturnBikeTime(out.getOwReturnBikeTime()+plan.itinerary.get(0).bikeTime);
                out.setOwReturnWaitingTime(out.getOwReturnWaitingTime()+plan.itinerary.get(0).waitingTime);
            }

            out.setOwTime((int) timeOw);
            outputs.add(out);
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
            
            t.setFrom(generateLocation(r, params.bboxSrc.get(r.nextInt(params.bboxSrc.size())), params.bboxSrcExcept));
            t.setTo(generateLocation(r, params.bboxTgt));

            long time = generateTime(t.getArrivalDate(), params.arrFromTime, params.arrToTime, tz);
            t.setArrivalTime(dateFormat.format(new Date(time)));

            long time2 = 0;
            // t.setDepartureTime(dateFormat.format(new Date(time+6*60*60*1000)));
            if (params.depFromTime == null || params.depToTime == null || params.depFromTime.isEmpty() || params.depToTime.isEmpty())
                time2 = time + params.delay * 1000;
            else
                time2 = generateTime(t.getDepartureDate(), params.depFromTime, params.depToTime,
                    tz);
            if (time2 < time)
                time2 += 24 * 60 * 60 * 1000;
            
            t.setDepartureTime(dateFormat.format(new Date(time2)));

            if (i % 2 == 0)
                t.setInitialMode("CAR");
            else
                t.setInitialMode("BICYCLE");
            list.add(t);
            LOG.info("Generated " + i);
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
        if (toDT.compareTo(fromDT) < 0)
            toDT = new Date(toDT.getTime() + 24 * 60 * 60 * 1000);
        int deltaMinutes = (int) ((toDT.getTime() / 1000) - (fromDT.getTime() / 1000)) / 60;
        int randomMinutes = rand.nextInt(deltaMinutes + 1);
        return fromDT.getTime() + randomMinutes * 60 * 1000;
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
