package org.opentripplanner.standalone.twowayutil;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.gtfs.serialization.mappings.LatLonFieldMappingFactory;
import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;

public class TestInput {

    @CsvField()
    private int id;
    
    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double fromLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double toLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double fromLon;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double toLon;

    @CsvField(mapping = StopTimeFieldMappingFactory.class)
    private int arrivalTime;

    @CsvField(mapping = StopTimeFieldMappingFactory.class)
    private int departureTime;

    public TestInput() {
    }

    public String toString() {
        return "From " + fromLat + "," + fromLon + " to " + toLat + "," + toLon+" arrive by "+arrivalTime+" depart from "+departureTime;
    }

    public int getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(int arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public int getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(int departureTime) {
        this.departureTime = departureTime;
    }

    public double getFromLat() {
        return fromLat;
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void generateRequest(RoutingRequest rq, Graph graph) {
        rq.numItineraries = 1;
        rq.arriveBy = true;
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(System.currentTimeMillis());
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        java.util.Calendar c2 = java.util.Calendar.getInstance();
        
        c2.setTimeInMillis(c.getTimeInMillis()+arrivalTime*1000);
        rq.dateTime = c2.getTimeInMillis()/1000;
        c2.setTimeInMillis(c.getTimeInMillis()+departureTime*1000);
        rq.returnDateTime = c2.getTimeInMillis()/1000;
        rq.modes = new TraverseModeSet(TraverseMode.WALK);
        rq.parkAndRide = true;
        rq.twoway = true;
        rq.from = new GenericLocation(getFromLat(), getFromLon());
        rq.to = new GenericLocation(getToLat(), getToLon());
        rq.setRoutingContext(graph);
    }
}