/*
 * Copyright 2015 OpenCB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opencb.opencga.storage.datamanagers;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.opencb.cellbase.core.common.Region;
import org.opencb.commons.bioformats.variant.vcf4.VcfRecord;
import org.opencb.opencga.core.SgeManager;
import org.opencb.opencga.core.common.Config;
import org.opencb.opencga.core.common.IOUtils;
import org.opencb.opencga.core.common.StringUtils;
import org.opencb.opencga.core.common.XObject;
import org.opencb.opencga.storage.TabixReader;
import org.opencb.opencga.storage.indices.SqliteManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VcfManager {
//    private Gson gson;

    protected static ObjectMapper jsonObjectMapper;
    protected static ObjectWriter jsonObjectWriter;

    protected static Logger logger = LoggerFactory.getLogger(VcfManager.class);
    private static Path indexerManagerScript = Paths.get(Config.getGcsaHome(),
            Config.getAnalysisProperties().getProperty("OPENCGA.ANALYSIS.BINARIES.PATH"), "indexer", "indexerManager.py");

    XObject vcfColumns;

    public VcfManager() throws IOException {
//        gson = new Gson();
        jsonObjectMapper = new ObjectMapper();
        jsonObjectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        jsonObjectWriter = jsonObjectMapper.writer();

        vcfColumns = new XObject();
        vcfColumns.put("chromosome", 0);
        vcfColumns.put("position", 1);
        vcfColumns.put("id", 2);
        vcfColumns.put("ref", 3);
        vcfColumns.put("alt", 4);
        vcfColumns.put("qual", 5);
        vcfColumns.put("filter", 6);
        vcfColumns.put("info", 7);
    }

    private static Path getMetaDir(Path file) {
        String inputName = file.getFileName().toString();
        return file.getParent().resolve(".meta_" + inputName);
    }

    public static String createIndex(Path inputPath) throws IOException, InterruptedException {

        Path metaDir = getMetaDir(inputPath);

        if (Files.exists(metaDir)) {
            IOUtils.deleteDirectory(metaDir);
        }

        String jobId = StringUtils.randomString(8);
        String commandLine = indexerManagerScript + " -t vcf -i " + inputPath + " --outdir " + metaDir;
        try {
            SgeManager.queueJob("indexer", jobId, 0, inputPath.getParent().toString(), commandLine);
        } catch (Exception e) {
            logger.error(e.toString());
//            throw new AnalysisExecutionException("ERROR: sge execution failed.");
        }
        return "indexer_" + jobId;
    }

    private static File checkVcfIndex(Path inputPath) {
        Path metaDir = getMetaDir(inputPath);
        String fileName = inputPath.getFileName().toString();
        //name.vcf.gz
        //name.vcf.tbi
        Path inputCompressedFile = metaDir.resolve(Paths.get(fileName + ".gz"));
        Path inputIndexFile = metaDir.resolve(Paths.get(fileName + ".gz.tbi"));
        if (Files.exists(inputIndexFile) && Files.exists(inputCompressedFile)) {
            return inputIndexFile.toFile();
        }
        return null;
    }

    public static boolean checkIndex(Path filePath) {
        Path metaDir = getMetaDir(filePath);
        String fileName = filePath.getFileName().toString();
        return Files.exists(metaDir.resolve(Paths.get(fileName + ".db")));
    }


    public String queryRegion(Path filePath, String regionStr, Map<String, List<String>> params) throws SQLException, IOException, ClassNotFoundException {

        Path metaDir = getMetaDir(filePath);
        String fileName = filePath.getFileName().toString();

        Path gzFilePath = metaDir.resolve(Paths.get(fileName + ".gz"));

        Region region = Region.parseRegion(regionStr);
        String chromosome = region.getChromosome();
        int start = region.getStart();
        int end = region.getEnd();

        SqliteManager sqliteManager = new SqliteManager();
        sqliteManager.connect(metaDir.resolve(Paths.get(fileName)), true);

        Boolean histogram = false;
        if (params.get("histogram") != null) {
            histogram = Boolean.parseBoolean(params.get("histogram").get(0));
        }
        Boolean histogramLogarithm = false;
        if (params.get("histogramLogarithm") != null) {
            histogramLogarithm = Boolean.parseBoolean(params.get("histogramLogarithm").get(0));
        }
        int histogramMax = 500;
        if (params.get("histogramMax") != null) {
            histogramMax = Integer.getInteger(params.get("histogramMax").get(0), 500);
        }

        if (histogram) {
            long tq = System.currentTimeMillis();
            String tableName = "chunk";
            String chrPrefix = "";
            String queryString = "SELECT * FROM " + tableName + " WHERE chromosome='" + chrPrefix + chromosome + "' AND start<=" + end + " AND end>=" + start;
            List<XObject> queryResults = sqliteManager.query(queryString);
            sqliteManager.disconnect(true);
            int queryResultSize = queryResults.size();

            if (queryResultSize > histogramMax) {
                List<XObject> sumList = new ArrayList<>();
                int sumChunkSize = queryResultSize / histogramMax;
                int i = 0, j = 0;
                XObject item = null;
                int features_count = 0;
                for (XObject result : queryResults) {
                    features_count += result.getInt("features_count");
                    if (i == 0) {
                        item = new XObject("chromosome", result.getString("chromosome"));
                        item.put("start", result.getString("start"));
                    } else if (i == sumChunkSize - 1 || j == queryResultSize - 1) {
                        if (histogramLogarithm) {
                            item.put("features_count", (features_count > 0) ? Math.log(features_count) : 0);
                        } else {
                            item.put("features_count", features_count);
                        }
                        item.put("end", result.getString("end"));
                        sumList.add(item);
                        i = -1;
                        features_count = 0;
                    }
                    j++;
                    i++;
                }
                return jsonObjectWriter.writeValueAsString(sumList);
//                return gson.toJson(sumList);
            }

            if (histogramLogarithm) {
                for (XObject result : queryResults) {
                    int features_count = result.getInt("features_count");
                    result.put("features_count", (features_count > 0) ? Math.log(features_count) : 0);
                }
            }

            System.out.println("Query time " + (System.currentTimeMillis() - tq) + "ms");
            return jsonObjectWriter.writeValueAsString(queryResults);
//            return gson.toJson(queryResults);
        }

//        String tableName = "global_stats";
//        String queryString = "SELECT value FROM " + tableName + " WHERE name='CHR_"+chromosome+"_PREFIX'";
//        String chrPrefix = sqliteManager.query(queryString).get(0).getString("value");

        String chrPrefix = "";
        String tableName = "record_query_fields";
        String queryString = "SELECT position FROM " + tableName + " WHERE chromosome='" + chrPrefix + chromosome + "' AND position<=" + end + " AND position>=" + start;
        List<XObject> queryResults = sqliteManager.query(queryString);
        int queryResultsLength = queryResults.size();
        //disconnect
        sqliteManager.disconnect(true);


        HashMap<String, XObject> queryResultsMap = new HashMap<>();
        for (XObject r : queryResults) {
            queryResultsMap.put(r.getString("position"), r);
        }

        System.out.println("queryResultsLength " + queryResultsLength);

        //Query Tabbix
        File inputVcfIndexFile = checkVcfIndex(filePath);
        TabixReader tabixReader = new TabixReader(gzFilePath.toString());
        if (inputVcfIndexFile == null) {
            logger.info("VcfManager: " + "creating vcf index for: " + filePath);
            return null;
        }

        String line;
        logger.info("regionStr: " + regionStr);
        TabixReader.Iterator lines = null;
        try {
            lines = tabixReader.query(regionStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("lines != null: " + (lines == null));
        logger.info("lines: " + lines);
        List<XObject> records = new ArrayList<>();
        while (lines != null && ((line = lines.next()) != null)) {
            String[] fields = line.split("\t",10);

            XObject record = new XObject();
            record.put("chromosome", fields[0]);
            record.put("start", Integer.valueOf(fields[1]));
            record.put("end", Integer.valueOf(fields[1]));
            record.put("id", fields[2]);
            record.put("reference", fields[3]);
            record.put("alternate", fields[4]);
            record.put("quality", fields[5]);
            record.put("filter", fields[6]);
            record.put("info", fields[7]);
            record.put("format", fields[8]);
            record.put("samples", fields[9].split("\\s"));
            if (queryResultsMap.get(String.valueOf(record.get("start"))) != null) {
                records.add(record);
                queryResultsLength--;
            }
            if (queryResultsLength < 0) {
                break;
            }
        }
        return jsonObjectWriter.writeValueAsString(records);
//        return gson.toJson(records);
    }

    @Deprecated
    public String getByRegion(Path fullFilePath, String regionStr, Map<String, List<String>> params) throws IOException {
        TabixReader tabixReader = new TabixReader(fullFilePath.toString());
        StringBuilder sb = new StringBuilder();
        try {
            TabixReader.Iterator lines = tabixReader.query(regionStr);

            String line;
            sb.append("[");
            while ((line = lines.next()) != null) {
                String[] fields = line.split("\t");
                VcfRecord vcfRecord = new VcfRecord(fields[0],Integer.parseInt(fields[1]),fields[2],fields[3],fields[4],fields[5],fields[6],fields[7]);
                sb.append(jsonObjectWriter.writeValueAsString(vcfRecord) + ",");
            }
            // Remove last comma
            int sbLength = sb.length();
            int sbLastPos = sbLength - 1;
            if (sbLength > 1 && sb.charAt(sbLastPos) == ',') {
                sb.replace(sbLastPos, sbLength, "");
            }
            sb.append("]");

        } catch (Exception e) {
            logger.info(e.toString());
            sb.append("[]");
        }

        return sb.toString();
    }
}
