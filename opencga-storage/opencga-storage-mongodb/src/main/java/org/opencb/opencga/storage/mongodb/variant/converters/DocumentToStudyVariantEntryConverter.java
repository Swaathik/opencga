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

package org.opencb.opencga.storage.mongodb.variant.converters;

import org.bson.Document;
import org.opencb.biodata.models.variant.StudyEntry;
import org.opencb.biodata.models.variant.avro.AlternateCoordinate;
import org.opencb.biodata.models.variant.avro.FileEntry;
import org.opencb.biodata.models.variant.avro.VariantType;
import org.opencb.commons.datastore.core.ComplexTypeConverter;
import org.opencb.commons.datastore.core.QueryResult;
import org.opencb.opencga.storage.core.metadata.StudyConfiguration;
import org.opencb.opencga.storage.core.variant.StudyConfigurationManager;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Cristina Yenyxe Gonzalez Garcia <cyenyxe@ebi.ac.uk>
 */
public class DocumentToStudyVariantEntryConverter implements ComplexTypeConverter<StudyEntry, Document> {

    public static final String FILEID_FIELD = "fid";
    public static final String STUDYID_FIELD = "sid";
    public static final String ATTRIBUTES_FIELD = "attrs";
    //    public static final String FORMAT_FIELD = "fm";
    public static final String GENOTYPES_FIELD = "gt";

    public static final String FILES_FIELD = "files";
    public static final String SAMPLE_DATA_FIELD = "sampleData";
    public static final String ORI_FIELD = "_ori";

    public static final String ALTERNATES_FIELD = "alts";
    public static final String ALTERNATES_CHR = "chr";
    public static final String ALTERNATES_ALT = "alt";
    public static final String ALTERNATES_REF = "ref";
    public static final String ALTERNATES_START = "start";
    public static final String ALTERNATES_END = "end";
    public static final String ALTERNATES_TYPE = "type";

    private boolean includeSrc;
    private Set<Integer> returnedFiles;

    //    private Integer fileId;
    private DocumentToSamplesConverter samplesConverter;
    private StudyConfigurationManager studyConfigurationManager = null;
    private Map<Integer, String> studyIds = new HashMap<>();

    /**
     * Create a converter between VariantSourceEntry and Document entities when
     * there is no need to provide a list of samples or statistics.
     *
     * @param includeSrc If true, will include and gzip the "src" attribute in the Document
     */
    public DocumentToStudyVariantEntryConverter(boolean includeSrc) {
        this.includeSrc = includeSrc;
        this.samplesConverter = null;
        this.returnedFiles = null;
    }


    /**
     * Create a converter from VariantSourceEntry to Document entities. A
     * samples converter and a statistics converter may be provided in case those
     * should be processed during the conversion.
     *
     * @param includeSrc       If true, will include and gzip the "src" attribute in the Document
     * @param samplesConverter The object used to convert the samples. If null, won't convert
     */
    public DocumentToStudyVariantEntryConverter(boolean includeSrc, DocumentToSamplesConverter samplesConverter) {
        this(includeSrc);
        this.samplesConverter = samplesConverter;
    }

    /**
     * Create a converter from VariantSourceEntry to Document entities. A
     * samples converter and a statistics converter may be provided in case those
     * should be processed during the conversion.
     *
     * @param includeSrc       If true, will include and gzip the "src" attribute in the Document
     * @param returnedFiles    If present, reads the information of this files from FILES_FIELD
     * @param samplesConverter The object used to convert the samples. If null, won't convert
     */
    public DocumentToStudyVariantEntryConverter(boolean includeSrc, Collection<Integer> returnedFiles,
                                                DocumentToSamplesConverter samplesConverter) {
        this(includeSrc);
        this.returnedFiles = (returnedFiles != null) ? new HashSet<>(returnedFiles) : null;
        this.samplesConverter = samplesConverter;
    }


    public DocumentToStudyVariantEntryConverter(boolean includeSrc, Integer returnedFile,
                                                DocumentToSamplesConverter samplesConverter) {
        this(includeSrc, Collections.singletonList(returnedFile), samplesConverter);
    }

    public void setStudyConfigurationManager(StudyConfigurationManager studyConfigurationManager) {
        this.studyConfigurationManager = studyConfigurationManager;
    }

    public void addStudyName(int studyId, String studyName) {
        this.studyIds.put(studyId, studyName);
    }

    @Override
    public StudyEntry convertToDataModelType(Document document) {
        int studyId = ((Number) document.get(STUDYID_FIELD)).intValue();
//        String fileId = this.fileId == null? null : String.valueOf(this.fileId);
//        String fileId = returnedFiles != null && returnedFiles.size() == 1? returnedFiles.iterator().next().toString() : null;
        StudyEntry study = new StudyEntry(getStudyName(studyId));

//        String fileId = (String) object.get(FILEID_FIELD);
        Document fileObject = null;
        if (document.containsKey(FILES_FIELD)) {
            List<FileEntry> files = new ArrayList<>(((List) document.get(FILES_FIELD)).size());
            for (Document fileDocument : (List<Document>) document.get(FILES_FIELD)) {
                Integer fid = ((Integer) fileDocument.get(FILEID_FIELD));

                if (returnedFiles != null && !returnedFiles.contains(fid)) {
                    continue;
                }
                HashMap<String, String> attributes = new HashMap<>();
                FileEntry fileEntry = new FileEntry(fid.toString(), null, attributes);
                files.add(fileEntry);

                fileObject = fileDocument;
                // Attributes
                if (fileObject.containsKey(ATTRIBUTES_FIELD)) {
                    Map<String, Object> attrs = ((Document) fileObject.get(ATTRIBUTES_FIELD));
                    for (Map.Entry<String, Object> entry : attrs.entrySet()) {
                        // Unzip the "src" field, if available
                        if (entry.getKey().equals("src")) {
                            if (includeSrc) {
                                byte[] o = (byte[]) entry.getValue();
                                try {
                                    attributes.put(entry.getKey(), org.opencb.commons.utils.StringUtils.gunzip(o));
                                } catch (IOException ex) {
                                    Logger.getLogger(DocumentToStudyVariantEntryConverter.class.getName()).log(Level.SEVERE, null, ex);
                                }
                            }
                        } else {
                            attributes.put(entry.getKey().replace(DocumentToStudyConfigurationConverter.TO_REPLACE_DOTS, "."),
                                    entry.getValue().toString());
                        }
                    }
                }
                if (fileObject.containsKey(ORI_FIELD)) {
                    Document ori = (Document) fileObject.get(ORI_FIELD);
                    fileEntry.setCall(ori.get("s") + ":" + ori.get("i"));
                } else {
                    fileEntry.setCall("");
                }
            }
            study.setFiles(files);
        }

        // Alternate alleles
//        if (fileObject != null && fileObject.containsKey(ALTERNATES_COORDINATES_FIELD)) {
            List<Document> list = (List<Document>) document.get(ALTERNATES_FIELD);
            if (list != null && !list.isEmpty()) {
                for (Document alternateDocument : list) {
                    VariantType variantType = null;
                    String type = (String) alternateDocument.get(ALTERNATES_TYPE);
                    if (type != null && !type.isEmpty()) {
                        variantType = VariantType.valueOf(type);

                    }
                    AlternateCoordinate alternateCoordinate = new AlternateCoordinate(
                            (String) alternateDocument.get(ALTERNATES_CHR),
                            (Integer) alternateDocument.get(ALTERNATES_START),
                            (Integer) alternateDocument.get(ALTERNATES_END),
                            (String) alternateDocument.get(ALTERNATES_REF),
                            (String) alternateDocument.get(ALTERNATES_ALT),
                            variantType);
                    if (study.getSecondaryAlternates() == null) {
                        study.setSecondaryAlternates(new ArrayList<>(list.size()));
                    }
                    study.getSecondaryAlternates().add(alternateCoordinate);
                }
            }
//            String[] alternatives = new String[list.size()];
//            int i = 0;
//            for (Object o : list) {
//                alternatives[i] = o.toString();
//                i++;
//            }
//            study.setSecondaryAlternates(list);
//        }


//        if (fileObject != null && fileObject.containsKey(FORMAT_FIELD)) {
//            study.setFormat((String) fileObject.get(FORMAT_FIELD));
//        } else {

//        }

        // Samples
        if (samplesConverter != null) {
            samplesConverter.convertToDataModelType(document, study, studyId);
        }

        return study;
    }

    public String getStudyName(int studyId) {
        if (!studyIds.containsKey(studyId)) {
            if (studyConfigurationManager == null) {
                studyIds.put(studyId, Integer.toString(studyId));
            } else {
                QueryResult<StudyConfiguration> queryResult = studyConfigurationManager.getStudyConfiguration(studyId, null);
                if (queryResult.getResult().isEmpty()) {
                    studyIds.put(studyId, Integer.toString(studyId));
                } else {
                    studyIds.put(studyId, queryResult.first().getStudyName());
                }
            }
        }
        return studyIds.get(studyId);
    }

    @Override
    public Document convertToStorageType(StudyEntry object) {

        if (object.getFiles().size() != 1) {
            throw new IllegalArgumentException("Expected just one file in the study to convert");
        }
        FileEntry file = object.getFiles().get(0);

        return convertToStorageType(object, file, object.getSamplesName());
    }

    public Document convertToStorageType(StudyEntry studyEntry, FileEntry file, Set<String> sampleNames) {

        int studyId = Integer.parseInt(studyEntry.getStudyId());
        int fileId = Integer.parseInt(file.getFileId());
        Document studyObject = new Document(STUDYID_FIELD, studyId);
        Document fileObject = new Document(FILEID_FIELD, fileId);

        // Alternate alleles
        List<Document> alternates = new LinkedList<>();
        if (studyEntry.getSecondaryAlternates().size() > 0) {   // assuming secondaryAlternates doesn't contain the primary alternate
//            fileObject.append(ALTERNATES_FIELD, studyEntry.getSecondaryAlternatesAlleles());
            for (AlternateCoordinate coordinate : studyEntry.getSecondaryAlternates()) {
                Document alt = new Document();
                if (coordinate.getChromosome() != null) {
                    alt.put(ALTERNATES_CHR, coordinate.getChromosome());
                }
                if (coordinate.getReference() != null) {
                    alt.put(ALTERNATES_REF, coordinate.getReference());
                }
                alt.put(ALTERNATES_ALT, coordinate.getAlternate());
                if (coordinate.getStart() != null) {
                    alt.put(ALTERNATES_START, coordinate.getStart());
                }
                if (coordinate.getStart() != null) {
                    alt.put(ALTERNATES_END, coordinate.getEnd());
                }
                if (coordinate.getType() != null) {
                    alt.put(ALTERNATES_TYPE, coordinate.getType().toString());
                }
                alternates.add(alt);
            }
        }

        // Attributes
        if (file.getAttributes().size() > 0) {
            Document attrs = null;
            for (Map.Entry<String, String> entry : file.getAttributes().entrySet()) {
                String stringValue = entry.getValue();
                String key = entry.getKey().replace(".", DocumentToStudyConfigurationConverter.TO_REPLACE_DOTS);
                Object value = stringValue;
                if (key.equals("src")) {
                    if (includeSrc) {
                        try {
                            value = org.opencb.commons.utils.StringUtils.gzip(stringValue);
                        } catch (IOException ex) {
                            Logger.getLogger(DocumentToStudyVariantEntryConverter.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        continue;
                    }
                } else {
                    try {
                        value = Integer.parseInt(stringValue);
                    } catch (NumberFormatException notAnInt) {
                        try {
                            value = Long.parseLong(stringValue);
                        } catch (NumberFormatException notALong) {
                            try {
                                value = Double.parseDouble(stringValue);
                            } catch (NumberFormatException notADouble) {
                                // leave it as a String
                            }
                        }
                    }
                }

                if (attrs == null) {
                    attrs = new Document(key, value);
                } else {
                    attrs.append(key, value);
                }
            }

            if (attrs != null) {
                fileObject.put(ATTRIBUTES_FIELD, attrs);
            }
        }
        String call = studyEntry.getFile(Integer.toString(fileId)).getCall();
        if (call != null && !call.isEmpty()) {
            int indexOf = call.lastIndexOf(":");
            fileObject.append(ORI_FIELD,
                    new Document("s", call.substring(0, indexOf))
                            .append("i", Integer.parseInt(call.substring(indexOf + 1))));
        }

        studyObject.append(FILES_FIELD, Collections.singletonList(fileObject));
        if (alternates != null && !alternates.isEmpty()) {
            studyObject.append(ALTERNATES_FIELD, alternates);
        }

//        if (samples != null && !samples.isEmpty()) {
        if (samplesConverter != null) {
            Document otherFields = new Document();
            fileObject.append(SAMPLE_DATA_FIELD, otherFields);
            studyObject.putAll(samplesConverter.convertToStorageType(studyEntry, studyId, fileId, otherFields, sampleNames));

        }


        return studyObject;
    }

    public DocumentToSamplesConverter getSamplesConverter() {
        return samplesConverter;
    }

    public void setIncludeSrc(boolean includeSrc) {
        this.includeSrc = includeSrc;
    }
}
