package org.opentripplanner.standalone.twowayutil;

import org.onebusaway.csv_entities.schema.annotations.CsvField;
import org.onebusaway.gtfs.serialization.mappings.LatLonFieldMappingFactory;
import org.onebusaway.gtfs.serialization.mappings.StopTimeFieldMappingFactory;
import org.opentripplanner.common.model.GenericLocation;
import org.opentripplanner.routing.core.RoutingRequest;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.core.TraverseModeSet;
import org.opentripplanner.routing.graph.Graph;

public class OneWayOutput {
    @CsvField()
    private int id;
    
    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double fromLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double fromLon;

    @CsvField(mapping = StopTimeFieldMappingFactory.class)
    private int arrivalTime;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double toLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double toLon;

    @CsvField(mapping = StopTimeFieldMappingFactory.class)
    private int departureTime;

    //routing time
    @CsvField()
    private int twTime;
    
    //routing time
    @CsvField()
    private int owTime;

    // trip duration (2 way)
    @CsvField()
    private int twDuration;
    
    @CsvField()
    private int owDuration;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double owFirstParkingLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double owFirstParkingLon;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double owSecondParkingLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double owSecondParkingLon;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double twParkingLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double twParkingLon;

    public OneWayOutput() {
    }
    
    public OneWayOutput(TwoWayOutput info, int t) {
        setId(info.getId());
        setFromLat(info.getFromLat());
        setFromLon(info.getFromLon());
        setToLat(info.getToLat());
        setToLon(info.getToLon());
        setTwParkingLon(info.getParkingLon());
        setTwParkingLat(info.getParkingLat());
        setTwTime(info.getTime());
        setTwDuration(info.getDuration());
        setOwTime(t);
    }

    public String toString() {
        return "Experiment " + id + " took ";
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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

    public int getTwTime() {
        return twTime;
    }

    public void setTwTime(int twTime) {
        this.twTime = twTime;
    }

    public int getOwTime() {
        return owTime;
    }

    public void setOwTime(int owTime) {
        this.owTime = owTime;
    }

    public int getTwDuration() {
        return twDuration;
    }

    public void setTwDuration(int twDuration) {
        this.twDuration = twDuration;
    }

    public int getOwDuration() {
        return owDuration;
    }

    public void setOwDuration(int owDuration) {
        this.owDuration = owDuration;
    }

    public double getOwFirstParkingLat() {
        return owFirstParkingLat;
    }

    public void setOwFirstParkingLat(double owFirstParkingLat) {
        this.owFirstParkingLat = owFirstParkingLat;
    }

    public double getOwFirstParkingLon() {
        return owFirstParkingLon;
    }

    public void setOwFirstParkingLon(double owFirstParkingLon) {
        this.owFirstParkingLon = owFirstParkingLon;
    }

    public double getOwSecondParkingLat() {
        return owSecondParkingLat;
    }

    public void setOwSecondParkingLat(double owSecondParkingLat) {
        this.owSecondParkingLat = owSecondParkingLat;
    }

    public double getOwSecondParkingLon() {
        return owSecondParkingLon;
    }

    public void setOwSecondParkingLon(double owSecondParkingLon) {
        this.owSecondParkingLon = owSecondParkingLon;
    }

    public double getTwParkingLat() {
        return twParkingLat;
    }

    public void setTwParkingLat(double twParkingLat) {
        this.twParkingLat = twParkingLat;
    }

    public double getTwParkingLon() {
        return twParkingLon;
    }

    public void setTwParkingLon(double twParkingLon) {
        this.twParkingLon = twParkingLon;
    }
}