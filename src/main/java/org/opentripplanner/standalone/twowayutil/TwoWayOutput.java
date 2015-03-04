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
import org.opentripplanner.routing.request.BannedStopSet;
import org.opentripplanner.standalone.Router;

public class TwoWayOutput {
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
        setTime(t);
    }

    public String toString() {
        return "Experiment " + id + " took " + time;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
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
        rq.modes = new TraverseModeSet(TraverseMode.CAR);
        rq.parkAndRide = true;
        rq.twoway = false;
        rq.from = new GenericLocation(getFromLat(), getFromLon());
        rq.to = new GenericLocation(getToLat(), getToLon());
        rq.setRoutingContext(graph);
    }
}