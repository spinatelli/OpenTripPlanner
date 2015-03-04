package org.opentripplanner.standalone.twowayutil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public TwoWayCsvTester() {
    }

    public void fromFile(File path, Class<?> type, EntityHandler handler) {
        CsvEntityReader reader = new CsvEntityReader();
        reader.setInputSource(new CsvFileInputSource(path));
        reader.setInternStrings(true);
        reader.addEntityHandler(handler);
        try {
            reader.readEntities(type);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void performTests() {

    }

    public void toFile(File path, List<? extends Object> infos) throws IOException {
        CsvEntityWriter writer = new CsvEntityWriter();
        writer.setOutputLocation(path);
        for(Object info:infos)
            writer.handleEntity(info);
        writer.flush();
        writer.close();
    }
}
