package org.opentripplanner.standalone.twowayutil;

public class BBox {
    public double minLat = -90;
    public double minLon = -180;
    public double maxLat = 90;
    public double maxLon = 180;
    
    public BBox(String s) {
        String[] parts = s.split(",");
        minLon = Double.parseDouble(parts[0]);
        minLat = Double.parseDouble(parts[1]);
        maxLon = Double.parseDouble(parts[2]);
        maxLat = Double.parseDouble(parts[3]);
    }
    
    public String toString() {
        return minLat+","+minLon+","+maxLat+","+maxLon;
    }
}