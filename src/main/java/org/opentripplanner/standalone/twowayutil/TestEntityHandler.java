package org.opentripplanner.standalone.twowayutil;

import java.util.ArrayList;
import java.util.List;

import org.onebusaway.csv_entities.EntityHandler;

public class TestEntityHandler implements EntityHandler {
    List<TestInput> list = new ArrayList<TestInput>();
    
    public List<TestInput> getList() {
        return list;
    }
    
    @Override
    public void handleEntity(Object bean) {
        list.add((TestInput)bean);
    }
}
