package org.opentripplanner.standalone.twowayutil;

import java.util.ArrayList;
import java.util.List;

import org.onebusaway.csv_entities.EntityHandler;

public class TwoWayTestEntityHandler implements EntityHandler {
    List<TwoWayOutput> list = new ArrayList<TwoWayOutput>();
    
    public List<TwoWayOutput> getList() {
        return list;
    }
    
    @Override
    public void handleEntity(Object bean) {
        list.add((TwoWayOutput)bean);
    }
}
