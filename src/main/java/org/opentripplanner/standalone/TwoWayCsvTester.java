package org.opentripplanner.standalone;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.onebusaway.csv_entities.CsvEntityReader;
import org.onebusaway.csv_entities.CsvEntityWriter;
import org.onebusaway.csv_entities.CsvInputSource;
import org.onebusaway.csv_entities.EntityHandler;

public class TwoWayCsvTester {

    private class CsvFileInputSource implements CsvInputSource {

        private File _path;

        public CsvFileInputSource(File path) {
            _path = path;
        }

        public boolean hasResource(String name) throws IOException {
            return true;
        }

        public InputStream getResource(String name) throws IOException {
            return new FileInputStream(_path);
        }

        public void close() throws IOException {
        }
    }
    
    private class TestEntityHandler implements EntityHandler {
        List<TestInfo> list = new ArrayList<TestInfo>();
        
        public List<TestInfo> getList() {
            return list;
        }
        
        @Override
        public void handleEntity(Object bean) {
            list.add((TestInfo)bean);
        }
    }

    public TwoWayCsvTester() {
    }

    public List<TestInfo> fromFile(File path) {
        CsvEntityReader reader = new CsvEntityReader();
        reader.setInputSource(new CsvFileInputSource(path));
        reader.setInternStrings(true);
        TestEntityHandler handler = new TestEntityHandler() {
        };
        reader.addEntityHandler(handler);
        try {
            reader.readEntities(TestInfo.class);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return handler.getList();
    }

    public void performTests() {

    }

    public void toFile(File path, List<TestInfo> infos) throws IOException {
        CsvEntityWriter writer = new CsvEntityWriter();
        writer.setOutputLocation(path);
        for(TestInfo info:infos)
            writer.handleEntity(info);
        writer.flush();
        writer.close();
    }
}
