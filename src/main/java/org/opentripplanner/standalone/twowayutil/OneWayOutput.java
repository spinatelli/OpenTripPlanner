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
    private int twTime;
    
    //routing time
    @CsvField()
    private int owTime;

    // trip duration (2 way)
    @CsvField()
    private int twOutwardLeg;
    
    @CsvField()
    private int twReturnLeg;
    
    @CsvField()
    private int owOutwardLeg;
    
    @CsvField()
    private int owReturnLeg;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double twParkingLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double twParkingLon;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double owFirstParkingLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double owFirstParkingLon;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double owSecondParkingLat;

    @CsvField(mapping = LatLonFieldMappingFactory.class)
    private double owSecondParkingLon;

    @CsvField()
    private int owOutwardTransfers;
    @CsvField()
    private int owReturnTransfers;
    @CsvField()
    private long owOutwardTransitTime;
    @CsvField()
    private long owReturnTransitTime;
    @CsvField()
    private long owOutwardWalkTime;
    @CsvField()
    private long owReturnWalkTime;
    @CsvField()
    private long owOutwardCarTime;
    @CsvField()
    private long owReturnCarTime;
    @CsvField()
    private long owOutwardBikeTime;
    @CsvField()
    private long owReturnBikeTime;
    @CsvField(optional=true)
    private long owOutwardWaitingTime;
    @CsvField(optional=true)
    private long owReturnWaitingTime;
    @CsvField(optional=true)
    private int twOutwardTransfers;
    @CsvField(optional=true)
    private int twReturnTransfers;
    @CsvField(optional=true)
    private long twOutwardTransitTime;
    @CsvField(optional=true)
    private long twReturnTransitTime;
    @CsvField(optional=true)
    private long twOutwardWalkTime;
    @CsvField(optional=true)
    private long twReturnWalkTime;
    @CsvField(optional=true)
    private long twOutwardCarTime;
    @CsvField(optional=true)
    private long twReturnCarTime;
    @CsvField(optional=true)
    private long twOutwardBikeTime;
    @CsvField(optional=true)
    private long twReturnBikeTime;
    @CsvField(optional=true)
    private long twOutwardWaitingTime;
    @CsvField(optional=true)
    private long twReturnWaitingTime;

    public OneWayOutput() {
    }
    
    public OneWayOutput(TwoWayOutput info) {
        setId(info.getId());
        setArrivalTime(info.getArrivalTime());
        setDepartureTime(info.getDepartureTime());
        setFromLat(info.getFromLat());
        setFromLon(info.getFromLon());
        setToLat(info.getToLat());
        setToLon(info.getToLon());
        setTwParkingLon(info.getParkingLon());
        setTwParkingLat(info.getParkingLat());
        setTwTime(info.getTime());
        setTwOutwardLeg(info.getOutwardLeg());
        setTwReturnLeg(info.getReturnLeg());
        setDepartureDate(info.getDepartureDate());
        setArrivalDate(info.getArrivalDate());
        setInitialMode(info.getInitialMode());
        setTwOutwardTransfers(info.getOutwardTransfers());
        setTwOutwardTransitTime(info.getOutwardTransitTime());
        setTwOutwardWalkTime(info.getOutwardWalkTime());
        setTwOutwardCarTime(info.getOutwardCarTime());
        setTwOutwardBikeTime(info.getOutwardBikeTime());
        setTwOutwardWaitingTime(info.getOutwardBikeTime());
        setTwReturnTransfers(info.getReturnTransfers());
        setTwReturnTransitTime(info.getReturnTransitTime());
        setTwReturnWalkTime(info.getReturnWalkTime());
        setTwReturnCarTime(info.getReturnCarTime());
        setTwReturnBikeTime(info.getReturnBikeTime());
        setTwReturnWaitingTime(info.getReturnBikeTime());
    }

    public String getInitialMode() {
        return initialMode;
    }
    
    public void setInitialMode(String initialMode) {
        this.initialMode = initialMode;
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

    public String toString() {
        return "Experiment " + id + " took "+owTime;
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

    public int getOwOutwardLeg() {
        return owOutwardLeg;
    }

    public void setOwOutwardLeg(int owOutwardLeg) {
        this.owOutwardLeg = owOutwardLeg;
    }

    public int getOwReturnLeg() {
        return owReturnLeg;
    }

    public void setOwReturnLeg(int owReturnLeg) {
        this.owReturnLeg = owReturnLeg;
    }

    public int getTwOutwardLeg() {
        return twOutwardLeg;
    }

    public void setTwOutwardLeg(int twOutwardLeg) {
        this.twOutwardLeg = twOutwardLeg;
    }

    public int getTwReturnLeg() {
        return twReturnLeg;
    }

    public void setTwReturnLeg(int twReturnLeg) {
        this.twReturnLeg = twReturnLeg;
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

    public int getOwOutwardTransfers() {
        return owOutwardTransfers;
    }

    public void setOwOutwardTransfers(int owOutwardTransfers) {
        this.owOutwardTransfers = owOutwardTransfers;
    }

    public int getOwReturnTransfers() {
        return owReturnTransfers;
    }

    public void setOwReturnTransfers(int owReturnTransfers) {
        this.owReturnTransfers = owReturnTransfers;
    }

    public long getOwOutwardTransitTime() {
        return owOutwardTransitTime;
    }

    public void setOwOutwardTransitTime(long owOutwardTransitTime) {
        this.owOutwardTransitTime = owOutwardTransitTime;
    }

    public long getOwReturnTransitTime() {
        return owReturnTransitTime;
    }

    public void setOwReturnTransitTime(long owReturnTransitTime) {
        this.owReturnTransitTime = owReturnTransitTime;
    }

    public long getOwOutwardWalkTime() {
        return owOutwardWalkTime;
    }

    public void setOwOutwardWalkTime(long owOutwardWalkTime) {
        this.owOutwardWalkTime = owOutwardWalkTime;
    }

    public long getOwReturnWalkTime() {
        return owReturnWalkTime;
    }

    public void setOwReturnWalkTime(long owReturnWalkTime) {
        this.owReturnWalkTime = owReturnWalkTime;
    }

    public long getOwOutwardCarTime() {
        return owOutwardCarTime;
    }

    public void setOwOutwardCarTime(long owOutwardCarTime) {
        this.owOutwardCarTime = owOutwardCarTime;
    }

    public long getOwReturnCarTime() {
        return owReturnCarTime;
    }

    public void setOwReturnCarTime(long owReturnCarTime) {
        this.owReturnCarTime = owReturnCarTime;
    }

    public long getOwOutwardBikeTime() {
        return owOutwardBikeTime;
    }

    public void setOwOutwardBikeTime(long owOutwardBikeTime) {
        this.owOutwardBikeTime = owOutwardBikeTime;
    }

    public long getOwReturnBikeTime() {
        return owReturnBikeTime;
    }

    public void setOwReturnBikeTime(long owReturnBikeTime) {
        this.owReturnBikeTime = owReturnBikeTime;
    }

    public int getTwOutwardTransfers() {
        return twOutwardTransfers;
    }

    public void setTwOutwardTransfers(int twOutwardTransfers) {
        this.twOutwardTransfers = twOutwardTransfers;
    }

    public int getTwReturnTransfers() {
        return twReturnTransfers;
    }

    public void setTwReturnTransfers(int twReturnTransfers) {
        this.twReturnTransfers = twReturnTransfers;
    }

    public long getTwOutwardTransitTime() {
        return twOutwardTransitTime;
    }

    public void setTwOutwardTransitTime(long twOutwardTransitTime) {
        this.twOutwardTransitTime = twOutwardTransitTime;
    }

    public long getTwReturnTransitTime() {
        return twReturnTransitTime;
    }

    public void setTwReturnTransitTime(long twReturnTransitTime) {
        this.twReturnTransitTime = twReturnTransitTime;
    }

    public long getTwOutwardWalkTime() {
        return twOutwardWalkTime;
    }

    public void setTwOutwardWalkTime(long twOutwardWalkTime) {
        this.twOutwardWalkTime = twOutwardWalkTime;
    }

    public long getTwReturnWalkTime() {
        return twReturnWalkTime;
    }

    public void setTwReturnWalkTime(long twReturnWalkTime) {
        this.twReturnWalkTime = twReturnWalkTime;
    }

    public long getTwOutwardCarTime() {
        return twOutwardCarTime;
    }

    public void setTwOutwardCarTime(long twOutwardCarTime) {
        this.twOutwardCarTime = twOutwardCarTime;
    }

    public long getTwReturnCarTime() {
        return twReturnCarTime;
    }

    public void setTwReturnCarTime(long twReturnCarTime) {
        this.twReturnCarTime = twReturnCarTime;
    }

    public long getTwOutwardBikeTime() {
        return twOutwardBikeTime;
    }

    public void setTwOutwardBikeTime(long twOutwardBikeTime) {
        this.twOutwardBikeTime = twOutwardBikeTime;
    }

    public long getTwReturnBikeTime() {
        return twReturnBikeTime;
    }

    public void setTwReturnBikeTime(long twReturnBikeTime) {
        this.twReturnBikeTime = twReturnBikeTime;
    }

    public long getOwOutwardWaitingTime() {
        return owOutwardWaitingTime;
    }

    public void setOwOutwardWaitingTime(long owOutwardWaitingTime) {
        this.owOutwardWaitingTime = owOutwardWaitingTime;
    }

    public long getOwReturnWaitingTime() {
        return owReturnWaitingTime;
    }

    public void setOwReturnWaitingTime(long owReturnWaitingTime) {
        this.owReturnWaitingTime = owReturnWaitingTime;
    }

    public long getTwOutwardWaitingTime() {
        return twOutwardWaitingTime;
    }

    public void setTwOutwardWaitingTime(long twOutwardWaitingTime) {
        this.twOutwardWaitingTime = twOutwardWaitingTime;
    }

    public long getTwReturnWaitingTime() {
        return twReturnWaitingTime;
    }

    public void setTwReturnWaitingTime(long twReturnWaitingTime) {
        this.twReturnWaitingTime = twReturnWaitingTime;
    }
}