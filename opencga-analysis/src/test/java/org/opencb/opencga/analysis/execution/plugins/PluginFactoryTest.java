package org.opencb.opencga.analysis.execution.plugins;

import org.junit.Test;
import org.opencb.commons.datastore.core.ObjectMap;
import org.opencb.opencga.analysis.beans.Analysis;
import org.opencb.opencga.analysis.execution.plugins.test.TestAnalysis;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.junit.Assert.assertEquals;

/**
 * Created on 27/11/15
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class PluginFactoryTest {

    public static class TestAnalysis2 extends TestAnalysis {

        public static final String ID = "test_plugin2";

        @Override
        public String getIdentifier() {
            return ID;
        }

        @Override
        public Analysis getManifest() {
            try {
                return loadManifest(getIdentifier());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Test
    public void testGetPlugin() throws Exception {
        Class<? extends OpenCGAAnalysis> testClass = PluginFactory.get().getPluginClass(TestAnalysis.PLUGIN_ID);
        assertEquals(TestAnalysis.class, testClass);

        OpenCGAAnalysis testPlugin = PluginFactory.get().getPlugin(TestAnalysis.PLUGIN_ID);
        assertEquals(TestAnalysis.class, testPlugin.getClass());
        testPlugin.init(LoggerFactory.getLogger(OpenCGAAnalysis.class), new ObjectMap(TestAnalysis.PARAM_1, "Hello").append(TestAnalysis.ERROR, false), null, null, -1, null);
        int run = testPlugin.run();
        assertEquals(run, 0);
    }

    @Test
    public void testGetPlugin2() throws Exception {
        Class<? extends OpenCGAAnalysis> testClass = PluginFactory.get().getPluginClass(TestAnalysis2.ID);
        assertEquals(TestAnalysis2.class, testClass);

        OpenCGAAnalysis testPlugin = PluginFactory.get().getPlugin(TestAnalysis2.ID);
        assertEquals(TestAnalysis2.class, testPlugin.getClass());
        testPlugin.init(LoggerFactory.getLogger(OpenCGAAnalysis.class), new ObjectMap(TestAnalysis.PARAM_1, "Hello World!").append(TestAnalysis.ERROR, false), null, null, -1, null);
        int run = testPlugin.run();
        assertEquals(run, 0);
        assertEquals(TestAnalysis2.ID, testPlugin.getManifest().getId());
    }

}