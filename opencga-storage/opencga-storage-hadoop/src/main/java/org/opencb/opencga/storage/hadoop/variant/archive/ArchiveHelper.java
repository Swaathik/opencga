/**
 *
 */
package org.opencb.opencga.storage.hadoop.variant.archive;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.opencb.biodata.models.variant.VariantSource;
import org.opencb.biodata.models.variant.protobuf.VcfMeta;
import org.opencb.biodata.models.variant.protobuf.VcfSliceProtos.VcfRecord;
import org.opencb.biodata.models.variant.protobuf.VcfSliceProtos.VcfSlice;
import org.opencb.biodata.models.variant.protobuf.VcfSliceProtos.VcfSlice.Builder;
import org.opencb.opencga.storage.hadoop.variant.GenomeHelper;
import org.opencb.opencga.storage.hadoop.variant.adaptors.HadoopVariantSourceDBAdaptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Matthias Haimel mh719+git@cam.ac.uk.
 */
public class ArchiveHelper extends GenomeHelper {

    private final Logger logger = LoggerFactory.getLogger(ArchiveHelper.class);
    private final AtomicReference<VcfMeta> meta = new AtomicReference<>();
    private byte[] column;


    private final VcfRecordComparator vcfComparator = new VcfRecordComparator();


    public ArchiveHelper(Configuration conf) throws IOException {
        this(conf, null);
        int fileId = conf.getInt(ArchiveDriver.CONFIG_ARCHIVE_FILE_ID, 0);
        int studyId = conf.getInt(GenomeHelper.CONFIG_STUDY_ID, 0);
        try (HadoopVariantSourceDBAdaptor metadataManager = new HadoopVariantSourceDBAdaptor(conf)) {
            VcfMeta meta = metadataManager.getVcfMeta(getStudyId(), fileId, null);
            this.meta.set(meta);
        }
        column = Bytes.toBytes(getColumnName(meta.get().getVariantSource()));
    }

    public ArchiveHelper(GenomeHelper helper, VcfMeta meta) {
        super(helper);
        this.meta.set(meta);
        column = Bytes.toBytes(getColumnName(meta.getVariantSource()));
    }

    public ArchiveHelper(Configuration conf, VcfMeta meta) {
        super(conf);
        if (meta != null) {
            this.meta.set(meta);
            VariantSource variantSource = getMeta().getVariantSource();
            column = Bytes.toBytes(getColumnName(variantSource));
        }
    }

    public ArchiveHelper(GenomeHelper helper, VariantSource source) throws IOException {
        super(helper);
        this.meta.set(new VcfMeta(source));
        column = Bytes.toBytes(getColumnName(source));
    }

    /**
     * Get the archive column name for a file given a FileId.
     *
     * @param fileId Numerical file identifier
     * @return Column name or Qualifier
     */
    public static String getColumnName(int fileId) {
        return Integer.toString(fileId);
    }

    /**
     * Get the archive column name for a file given a FileId.
     *
     * @param columnName Column name
     * @return Related fileId
     */
    public static int getFileIdFromColumnName(byte[] columnName) {
        return Integer.parseInt(Bytes.toString(columnName));
    }

    /**
     * Get the archive column name for a file given a VariantSource.
     *
     * @param variantSource VariantSource
     * @return Column name or Qualifier
     */
    public static String getColumnName(VariantSource variantSource) {
        return variantSource.getFileId();
    }

    public VcfMeta getMeta() {
        return meta.get();
    }

    public byte[] getColumn() {
        return column;
    }

    @Deprecated
    public VcfSlice join(byte[] key, Iterable<VcfSlice> input) throws InvalidProtocolBufferException {
        Builder sliceBuilder = VcfSlice.newBuilder();
        boolean isFirst = true;
        List<VcfRecord> vcfRecordLst = new ArrayList<VcfRecord>();
        for (VcfSlice slice : input) {

            byte[] skey = generateBlockIdAsBytes(slice.getChromosome(), slice.getPosition());
            // Consistency check
            if (!Bytes.equals(skey, key)) { // Address doesn't match up -> should never happen
                throw new IllegalStateException(String.format("Row keys don't match up!!! %s != %s", Bytes.toString(key),
                        Bytes.toString(skey)));
            }

            if (isFirst) { // init new slice
                sliceBuilder.setChromosome(slice.getChromosome()).setPosition(slice.getPosition());
                isFirst = false;
            }
            vcfRecordLst.addAll(slice.getRecordsList());
        }

        // Sort records
        try {
            Collections.sort(vcfRecordLst, getVcfComparator());
        } catch (IllegalArgumentException e) {
            logger.error("Issue with comparator: ");
            for (VcfRecord r : vcfRecordLst) {
                logger.error(r.toString());
            }
            throw e;
        }

        // Add all
        sliceBuilder.addAllRecords(vcfRecordLst);
        return sliceBuilder.build();
    }

    private VcfSlice extractSlice(Put put) throws InvalidProtocolBufferException {
        List<Cell> cList = put.get(getColumnFamily(), getColumn());
        if (cList.isEmpty()) {
            throw new IllegalStateException(String.format("No data available for row % in column %s in familiy %s!!!",
                    Bytes.toString(put.getRow()), Bytes.toString(getColumn()), Bytes.toString(getColumnFamily())));
        }
        if (cList.size() > 1) {
            throw new IllegalStateException(String.format("One entry instead of %s expected for row %s column %s in familiy %s!!!",
                    cList.size(), Bytes.toString(put.getRow()), Bytes.toString(getColumn()), Bytes.toString(getColumnFamily())));
        }
        Cell cell = cList.get(0);

        byte[] arr = Arrays.copyOfRange(cell.getValueArray(), cell.getValueOffset(), cell.getValueOffset() + cell.getValueLength());
        VcfSlice slice = VcfSlice.parseFrom(arr);
        return slice;
    }

    private VcfRecordComparator getVcfComparator() {
        return vcfComparator;
    }

    public byte[] wrap(VcfRecord record) {
        return record.toByteArray();
    }

    public Put wrap(VcfSlice slice) {
//        byte[] rowId = generateBlockIdAsBytes(slice.getChromosome(), (long) slice.getPosition() + slice.getRecords(0).getRelativeStart
// () * 100);
        byte[] rowId = generateBlockIdAsBytes(slice.getChromosome(), slice.getPosition());
        return wrapAsPut(getColumn(), rowId, slice);
    }

}
