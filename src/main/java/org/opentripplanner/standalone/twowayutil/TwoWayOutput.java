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
    private int duration;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double parkingLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double parkingLon;

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

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
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

    public void generateRequest(RoutingRequest rq, Graph graph) {
        rq.routerId = "default";
        rq.from = new GenericLocation(getFromLat(), getFromLon());
        rq.to = new GenericLocation(getToLat(), getToLon());
        rq.maxWalkDistance = 1207.008;
        rq.wheelchairAccessible = false;
        rq.showIntermediateStops = false;
        rq.clampInitialWait = -1;
        rq.setArriveBy(true);
        rq.modes = new TraverseModeSet("WALK,TRANSIT,"+getInitialMode());
        if (getInitialMode().equals("BIKE"))
            rq.bikeParkAndRide = true;
        else
            rq.parkAndRide = true;
        rq.twoway = false;
        rq.setDateTime(departureDate, departureTime, graph.getTimeZone());
        rq.returnDateTime = rq.dateTime;
        rq.setDateTime(arrivalDate, arrivalTime, graph.getTimeZone());
//        if (rq.rctx == null) {
//            rq.setRoutingContext(graph);
//            rq.rctx.pathParsers = new PathParser[] { new BasicPathParser(),
//                    new NoThruTrafficPathParser() };
//        }
        rq.numItineraries = 1;
//        rq.dominanceFunction = new DominanceFunction.MinimumWeight(); 
//        rq.longDistance = true;
//        rq.maxTransfers = 4;
    }
}