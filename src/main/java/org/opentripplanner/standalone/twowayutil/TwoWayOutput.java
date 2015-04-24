package org.opentripplanner.standalone.twowayutil;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.serialization.mappings.LatLonFieldMappingFactory;
import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;
import org.opentripplanner.api.common.Message;
import org.opentripplanner.api.common.ParameterException;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.OptimizeType;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.pathparser.BasicPathParser;
import org.opentripplanner.routing.pathparser.NoThruTrafficPathParser;
import org.opentripplanner.routing.pathparser.PathParser;
import org.opentripplanner.routing.request.BannedStopSet;
import org.opentripplanner.routing.spt.DominanceFunction;
import org.opentripplanner.standalone.Router;

public class TwoWayOutput {
    @CsvField()
    private int id;
    
    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double fromLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double fromLon;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double toLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double toLon;

    @CsvField()
    private String arrivalDate;

    @CsvField()
    private String arrivalTime;

    @CsvField()
    private String departureDate;

    @CsvField()
    private String departureTime;

    @CsvField()
    private String initialMode;

    //routing time
    @CsvField()
    private int time;

    // trip duration (2 way)
    @CsvField()
    private int outwardLeg;
    
    @CsvField()
    private int returnLeg;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double parkingLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double parkingLon;
    
    @CsvField(optional=true)
    private int outwardTransfers;
    @CsvField(optional=true)
    private int returnTransfers;
    @CsvField(optional=true)
    private long outwardTransitTime;
    @CsvField(optional=true)
    private long returnTransitTime;
    @CsvField(optional=true)
    private long outwardWalkTime;
    @CsvField(optional=true)
    private long returnWalkTime;
    @CsvField(optional=true)
    private long outwardCarTime;
    @CsvField(optional=true)
    private long returnCarTime;
    @CsvField(optional=true)
    private long outwardBikeTime;
    @CsvField(optional=true)
    private long returnBikeTime;
    @CsvField(optional=true)
    private long outwardWaitingTime;
    @CsvField(optional=true)
    private long returnWaitingTime;

    public TwoWayOutput() {
    }
    
    public TwoWayOutput(TestInput info, int t) {
        setId(info.getId());
        setFromLat(info.getFromLat());
        setFromLon(info.getFromLon());
        setToLat(info.getToLat());
        setToLon(info.getToLon());
        setDepartureTime(info.getDepartureTime());
        setArrivalTime(info.getArrivalTime());
        setDepartureDate(info.getDepartureDate());
        setArrivalDate(info.getArrivalDate());
        setInitialMode(info.getInitialMode());
        setTime(t);
    }

    public String toString() {
        return "Experiment " + id;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getOutwardLeg() {
        return outwardLeg;
    }

    public void setOutwardLeg(int outwardLeg) {
        this.outwardLeg = outwardLeg;
    }

    public int getReturnLeg() {
        return returnLeg;
    }

    public void setReturnLeg(int returnLeg) {
        this.returnLeg = returnLeg;
    }

    public double getParkingLat() {
        return parkingLat;
    }

    public void setParkingLat(double parkingLat) {
        this.parkingLat = parkingLat;
    }

    public double getParkingLon() {
        return parkingLon;
    }

    public void setParkingLon(double parkingLon) {
        this.parkingLon = parkingLon;
    }

    public double getFromLat() {
        return fromLat;
    }

    public String getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(String arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(String departureDate) {
        this.departureDate = departureDate;
    }

    public void setFromLat(double fromLat) {
        this.fromLat = fromLat;
    }

    public double getToLat() {
        return toLat;
    }

    public void setToLat(double toLat) {
        this.toLat = toLat;
    }

    public double getFromLon() {
        return fromLon;
    }

    public void setFromLon(double fromLon) {
        this.fromLon = fromLon;
    }

    public double getToLon() {
        return toLon;
    }

    public void setToLon(double toLon) {
        this.toLon = toLon;
    }

    public String getInitialMode() {
        return initialMode;
    }
    
    public void setInitialMode(String initialMode) {
        this.initialMode = initialMode;
    }

    public int getOutwardTransfers() {
        return outwardTransfers;
    }

    public void setOutwardTransfers(int outwardTransfers) {
        this.outwardTransfers = outwardTransfers;
    }

    public int getReturnTransfers() {
        return returnTransfers;
    }

    public void setReturnTransfers(int returnTransfers) {
        this.returnTransfers = returnTransfers;
    }

    public long getOutwardTransitTime() {
        return outwardTransitTime;
    }

    public void setOutwardTransitTime(long outwardTransitTime) {
        this.outwardTransitTime = outwardTransitTime;
    }

    public long getReturnTransitTime() {
        return returnTransitTime;
    }

    public void setReturnTransitTime(long returnTransitTime) {
        this.returnTransitTime = returnTransitTime;
    }

    public long getOutwardWalkTime() {
        return outwardWalkTime;
    }

    public void setOutwardWalkTime(long outwardWalkTime) {
        this.outwardWalkTime = outwardWalkTime;
    }

    public long getReturnWalkTime() {
        return returnWalkTime;
    }

    public void setReturnWalkTime(long returnWalkTime) {
        this.returnWalkTime = returnWalkTime;
    }

    public long getOutwardCarTime() {
        return outwardCarTime;
    }

    public void setOutwardCarTime(long outwardCarTime) {
        this.outwardCarTime = outwardCarTime;
    }

    public long getReturnCarTime() {
        return returnCarTime;
    }

    public void setReturnCarTime(long returnCarTime) {
        this.returnCarTime = returnCarTime;
    }

    public long getOutwardBikeTime() {
        return outwardBikeTime;
    }

    public void setOutwardBikeTime(long outwardBikeTime) {
        this.outwardBikeTime = outwardBikeTime;
    }

    public long getReturnBikeTime() {
        return returnBikeTime;
    }

    public void setReturnBikeTime(long returnBikeTime) {
        this.returnBikeTime = returnBikeTime;
    }

    public long getOutwardWaitingTime() {
        return outwardWaitingTime;
    }

    public void setOutwardWaitingTime(long outwardWaitingTime) {
        this.outwardWaitingTime = outwardWaitingTime;
    }

    public long getReturnWaitingTime() {
        return returnWaitingTime;
    }

    public void setReturnWaitingTime(long returnWaitingTime) {
        this.returnWaitingTime = returnWaitingTime;
    }

    public void generateFirstLegRequest(RoutingRequest rq, Graph graph, GenericLocation parking, boolean arriveby) {
        rq.routerId = "default";
        if (arriveby) {
            rq.from = new GenericLocation(getFromLat(), getFromLon());
            rq.to = parking;
        } else {
            rq.from = parking;
            rq.to = new GenericLocation(getFromLat(), getFromLon());
        }
        rq.wheelchairAccessible = false;
        rq.showIntermediateStops = false;
        rq.clampInitialWait = -1;
        rq.setArriveBy(arriveby);
        rq.locale = Locale.ENGLISH;
        rq.modes = new TraverseModeSet(getInitialMode());
        rq.bikeParkAndRide = false;
        rq.parkAndRide = false;
        rq.twoway = false;
        if (rq.rctx == null) {
            rq.setRoutingContext(graph);
            rq.rctx.pathParsers = new PathParser[] { new BasicPathParser(),
                    new NoThruTrafficPathParser() };
        }
        rq.numItineraries = 1;
        rq.dominanceFunction = new DominanceFunction.MinimumWeight(); 
        rq.longDistance = true;
        rq.maxTransfers = 4;
    }

    public void generateSecondLegRequest(RoutingRequest rq, Graph graph, GenericLocation parking, boolean arriveby) {
        rq.routerId = "default";
        if (arriveby) {
            rq.setDateTime(arrivalDate, arrivalTime, graph.getTimeZone());
            rq.from = parking;
            rq.to = new GenericLocation(getToLat(), getToLon());
        } else {
            rq.setDateTime(departureDate, departureTime, graph.getTimeZone());
            rq.from = new GenericLocation(getToLat(), getToLon());
            rq.to = parking;
        }
        rq.wheelchairAccessible = false;
        rq.showIntermediateStops = false;
        rq.clampInitialWait = -1;
        rq.setArriveBy(arriveby);
        rq.locale = Locale.ENGLISH;
        rq.modes = new TraverseModeSet("WALK,TRANSIT");
        rq.bikeParkAndRide = false;
        rq.parkAndRide = false;
        rq.twoway = false;
        if (rq.rctx == null) {
            rq.setRoutingContext(graph);
            rq.rctx.pathParsers = new PathParser[] { new BasicPathParser(),
                    new NoThruTrafficPathParser() };
        }
        rq.numItineraries = 1;
        rq.dominanceFunction = new DominanceFunction.MinimumWeight(); 
        rq.longDistance = true;
        rq.maxTransfers = 4;
    }

    public void generateRequest(RoutingRequest rq, Graph graph, boolean arriveby) {
        rq.routerId = "default";
        if (arriveby) {
            rq.setDateTime(departureDate, departureTime, graph.getTimeZone());
            rq.returnDateTime = rq.dateTime;
            rq.setDateTime(arrivalDate, arrivalTime, graph.getTimeZone());
            rq.from = new GenericLocation(getFromLat(), getFromLon());
            rq.to = new GenericLocation(getToLat(), getToLon());
        } else {
            rq.setDateTime(arrivalDate, arrivalTime, graph.getTimeZone());
            rq.returnDateTime = rq.dateTime;
            rq.setDateTime(departureDate, departureTime, graph.getTimeZone());
            rq.to = new GenericLocation(getFromLat(), getFromLon());
            rq.from = new GenericLocation(getToLat(), getToLon());
        }
        rq.wheelchairAccessible = false;
        rq.showIntermediateStops = false;
        rq.clampInitialWait = -1;
        rq.setArriveBy(arriveby);
        rq.locale = Locale.ENGLISH;
        rq.modes = new TraverseModeSet("WALK,TRANSIT,"+getInitialMode());
        if (getInitialMode().equals("BICYCLE"))
            rq.bikeParkAndRide = true;
        else
            rq.parkAndRide = true;
        rq.twoway = false;
        if (rq.rctx == null) {
            rq.setRoutingContext(graph);
            rq.rctx.pathParsers = new PathParser[] { new BasicPathParser(),
                    new NoThruTrafficPathParser() };
        }
        rq.numItineraries = 1;
        rq.dominanceFunction = new DominanceFunction.MinimumWeight(); 
        rq.longDistance = true;
        rq.maxTransfers = 4;
    }
}