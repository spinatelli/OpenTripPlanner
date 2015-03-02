package org.opentripplanner.standalone;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.gtfs.serialization.mappings.LatLonFieldMappingFactory;
import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;

public class TestInfo {
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

    public TestInfo() {
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

    public RoutingRequest generateRequest(RoutingRequest rq, Graph graph) {
        rq.numItineraries = 1;
        rq.arriveBy = true;
        rq.dateTime = System.currentTimeMillis() / 1000;
        rq.returnDateTime = System.currentTimeMillis() / 1000;
        rq.modes = new TraverseModeSet(TraverseMode.WALK);
        rq.parkAndRide = true;
        rq.twoway = true;
        rq.setRoutingContext(graph);
        return rq;
    }
}