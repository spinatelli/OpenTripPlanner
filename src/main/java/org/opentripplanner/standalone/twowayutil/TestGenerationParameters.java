package org.opentripplanner.standalone.twowayutil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class TestGenerationParameters {

    public final List<BBox> bboxSrc;
    public final BBox bboxSrcExcept;
    public final BBox bboxTgt;
    public final String arrFromTime;
    public final String arrToTime;
    public final String depFromTime;
    public final String depToTime;
    public final String initialMode;
    public final File outputFile;
    public final int experimentsNumber;
    public final int delay;

    /**
     * Set all parameters from the given Jackson JSON tree, applying defaults.
     * Supplying MissingNode.getInstance() will cause all the defaults to be applied.
     * This could be done automatically with the "reflective query scraper" but it's less type safe and less clear.
     * Until that class is more type safe, it seems simpler to just list out the parameters by name here.
     */
    public TestGenerationParameters(JsonNode config) {
        bboxSrc = new ArrayList<BBox>();
        JsonNode bsrc = config.get("bboxSrc");
        if (bsrc.isArray())
            for (JsonNode n : bsrc) {
                bboxSrc.add(new BBox(n.asText()));
                System.out.println(n.asText());
            }
        else
            bboxSrc.add(new BBox(config.path("bboxSrc").asText()));
        String s = config.path("bboxSrcExcept").asText(null);
        if (s != null)
            bboxSrcExcept = new BBox(s);
        else
            bboxSrcExcept = null;
        bboxTgt = new BBox(config.path("bboxTgt").asText());
        arrFromTime = config.path("arrFromTime").asText();
        arrToTime = config.path("arrToTime").asText();
        depFromTime = config.path("depFromTime").asText();
        depToTime = config.path("depToTime").asText();
        initialMode = config.path("initialMode").asText();
        outputFile = new File(config.path("outputFile").asText());
        experimentsNumber = config.path("experimentsNumber").asInt(1);
        delay = config.path("delay").asInt(0);
    }

}
