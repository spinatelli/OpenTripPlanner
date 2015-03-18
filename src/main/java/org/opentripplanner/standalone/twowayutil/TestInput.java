package org.opentripplanner.standalone.twowayutil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.gtfs.serialization.mappings.LatLonFieldMappingFactory;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.routing.pathparser.BasicPathParser;
import org.opentripplanner.routing.pathparser.NoThruTrafficPathParser;
import org.opentripplanner.routing.pathparser.PathParser;
import org.opentripplanner.routing.spt.DominanceFunction;

public class TestInput {

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
    

    public TestInput() {
        this(0);
    }
    public TestInput(int id) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        departureDate = arrivalDate = dateFormat.format(new Date());
        this.id = id;
        this.initialMode = "CAR";
    }

    public String toString() {
        return "From " + fromLat + "," + fromLon + " to " + toLat + "," + toLon + " arrive by "
                + arrivalTime + " depart from " + departureTime;
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

    public double getFromLat() {
        return fromLat;
    }

    public void setFrom(GenericLocation location) {
        setFromLat(location.lat);
        setFromLon(location.lng);
    }

    public void setTo(GenericLocation location) {
        setToLat(location.lat);
        setToLon(location.lng);
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

    public void setFromLon(double fromLon) {
        this.fromLon = fromLon;
    }

    public double getToLon() {
        return toLon;
    }

    public void setToLon(double toLon) {
        this.toLon = toLon;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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
        rq.locale = Locale.ENGLISH;
        rq.modes = new TraverseModeSet("WALK,TRANSIT,"+getInitialMode());
        if (getInitialMode().equals("BICYCLE"))
            rq.bikeParkAndRide = true;
        else
            rq.parkAndRide = true;
        rq.twoway = true;
        rq.setDateTime(departureDate, departureTime, graph.getTimeZone());
        rq.returnDateTime = rq.dateTime;
        rq.setDateTime(arrivalDate, arrivalTime, graph.getTimeZone());
        if (rq.rctx == null) {
            rq.setRoutingContext(graph);
            rq.rctx.pathParsers = new PathParser[] { new BasicPathParser(),
                    new NoThruTrafficPathParser() };
        }
        rq.numItineraries = 2;
        rq.dominanceFunction = new DominanceFunction.MinimumWeight();
        rq.longDistance = true;
        if (rq.maxWalkDistance == Double.MAX_VALUE)
            rq.maxWalkDistance = 2000;
        if (rq.maxWalkDistance > 15000)
            rq.maxWalkDistance = 15000;
        rq.maxTransfers = 4;
    }
}