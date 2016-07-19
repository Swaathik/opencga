package org.opencb.opencga.storage.hadoop.utils;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.hbase.*;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created on 13/11/15.
 *
 * @author Matthias Haimel mh719+git@cam.ac.uk
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class HBaseManager extends Configured implements AutoCloseable {
    protected static final Logger LOGGER = LoggerFactory.getLogger(HBaseManager.class);

    @FunctionalInterface
    public interface HBaseTableConsumer {
        void accept(Table table) throws IOException;
    }

    @FunctionalInterface
    public interface HBaseTableFunction<T> {
        T function(Table table) throws IOException;
    }

    @FunctionalInterface
    public interface HBaseTableAdminFunction<T> {
        T function(Table table, Admin admin) throws IOException;
    }


    @Override
    public void setConf(Configuration conf) {
        super.setConf(conf);
    }

    private final AtomicBoolean closeConnection = new AtomicBoolean(false);
    private final AtomicReference<Connection> connection = new AtomicReference<>(null);

    public HBaseManager(Configuration configuration) {
        this(configuration, null);
    }

    public HBaseManager(Configuration configuration, Connection connection) {
        super(configuration);
        this.closeConnection.set(connection == null);
        this.connection.set(connection);
    }

    public boolean getCloseConnection() {
        return closeConnection.get();
    }

    @Override
    public void close() throws IOException {
        if (this.closeConnection.get()) {
            Connection con = this.connection.getAndSet(null);
            if (null != con) {
                LOGGER.info("Close Hadoop DB connection {}", con);
                con.close();
            }
        }
    }

    public Connection getConnection() {
        Connection con = this.connection.get();
        if (this.closeConnection.get() && null == con) {
            while (null == con) {
                try {
                    con = ConnectionFactory.createConnection(this.getConf());
                    LOGGER.info("Opened Hadoop DB connection {}", con);
                } catch (IOException e) {
                    throw new IllegalStateException("Problems opening connection to DB", e);
                }
                if (!this.connection.compareAndSet(null, con)) {
                    try {
                        con.close();
                    } catch (IOException e) {
                        throw new IllegalStateException("Problems closing connection to DB", e);
                    }
                }
                con = this.connection.get();
            }
        }
        return con;
    }

    /**
     * Performs an action over a table.
     *
     * @param con HBase connection object
     * @param tableName Table name
     * @param func      Action to perform
     * @throws IOException If any IO problem occurs
     */
    public static void act(Connection con, String tableName, HBaseTableConsumer func) throws IOException {
        TableName tname = TableName.valueOf(tableName);
        try (Table table = con.getTable(tname)) {
            func.accept(table);
        }
    }

    /**
     * Performs an action over a table.
     *
     * @param con HBase connection object
     * @param tableName Table name
     * @param func      Action to perform
     * @throws IOException If any IO problem occurs
     */
    public static void act(Connection con, byte[] tableName, HBaseTableConsumer func) throws IOException {
        TableName tname = TableName.valueOf(tableName);
        try (Table table = con.getTable(tname)) {
            func.accept(table);
        }
    }

    /**
     * Performs an action over a table.
     *
     * @param tableName Table name
     * @param func      Action to perform
     * @throws IOException If any IO problem occurs
     */
    public void act(String tableName, HBaseTableConsumer func) throws IOException {
        TableName tname = TableName.valueOf(tableName);
        try (Table table = getConnection().getTable(tname)) {
            func.accept(table);
        }
    }

    /**
     * Performs an action over a table.
     * <p>
     * * This method creates a new connection for the action to perform.
     * * Create a connection is heavy. Do not use for common operations.
     * * Use {@link HBaseManager#act(Connection, byte[], HBaseTableFunction)} when possible
     *
     * @param tableName Table name
     * @param func      Action to perform
     * @param <T>       Return type
     * @return Result of the function
     * @throws IOException If any IO problem occurs
     */
    public <T> T act(String tableName, HBaseTableFunction<T> func) throws IOException {
        return act(Bytes.toBytes(tableName), func);
    }

    /**
     * Performs an action over a table.
     * <p>
     * * This method creates a new connection for the action to perform.
     * * Create a connection is heavy. Do not use for common operations.
     * * Use {@link HBaseManager#act(Connection, byte[], HBaseTableFunction)} when possible
     *
     * @param tableName Table name
     * @param func      Action to perform
     * @param <T>       Return type
     * @return Result of the function
     * @throws IOException If any IO problem occurs
     */
    public <T> T act(byte[] tableName, HBaseTableFunction<T> func) throws IOException {
        return act(getConnection(), tableName, func);
    }

    /**
     * Performs an action over a table.
     *
     * @param con HBase connection object
     * @param tableName Table name
     * @param func      Action to perform
     * @param <T>       Return type
     * @return Result of the function
     * @throws IOException If any IO problem occurs
     */
    public static <T> T act(Connection con, byte[] tableName, HBaseTableFunction<T> func) throws IOException {
        TableName tname = TableName.valueOf(tableName);
        try (Table table = con.getTable(tname)) {
            return func.function(table);
        }
    }

    /**
     * Performs an action over a table.
     *
     * @param con HBase connection object
     * @param tableName Table name
     * @param func      Action to perform
     * @param <T>       Return type
     * @return Result of the function
     * @throws IOException If any IO problem occurs
     */
    public static <T> T act(Connection con, String tableName, HBaseTableFunction<T> func) throws IOException {
        TableName tname = TableName.valueOf(tableName);
        try (Table table = con.getTable(tname)) {
            return func.function(table);
        }
    }

    /**
     * Performs an action over a table.
     * <p>
     * * This method creates a new connection for the action to perform.
     * * Create a connection is heavy. Do not use for common operations.
     * * Use {@link HBaseManager#act(Connection, String, HBaseTableAdminFunction)} when possible
     *
     * @param tableName Table name
     * @param func      Action to perform
     * @param <T>       Return type
     * @return Result of the function
     * @throws IOException If any IO problem occurs
     */
    public <T> T act(String tableName, HBaseTableAdminFunction<T> func) throws IOException {
        return act(getConnection(), tableName, func);
    }

    /**
     * Performs an action over a table.
     *
     * @param con HBase connection object
     * @param tableName Table name
     * @param func      Action to perform
     * @param <T>       Return type
     * @return Result of the function
     * @throws IOException If any IO problem occurs
     */
    public static <T> T act(Connection con, String tableName, HBaseTableAdminFunction<T> func) throws IOException {
        TableName tname = TableName.valueOf(tableName);
        try (Table table = con.getTable(tname); Admin admin = con.getAdmin()) {
            return func.function(table, admin);
        }
    }

    /**
     * Checks if the required table exists.
     *
     * @param tableName    HBase table name
     * @return boolean True if the table exists
     * @throws IOException throws {@link IOException}
     **/
    public boolean tableExists(String tableName) throws IOException {
        return act(tableName, (table, admin) -> admin.tableExists(table.getName()));
    }

    /**
     * Create default HBase table layout with one column family.
     *
     * @param con HBase connection object
     * @param tableName    HBase table name
     * @param columnFamily Column Family
     * @return boolean True if a new table was created
     * @throws IOException throws {@link IOException} from creating a connection / table
     **/
    public static boolean createTableIfNeeded(Connection con, String tableName, byte[] columnFamily) throws IOException {
        return createTableIfNeeded(con, tableName, columnFamily, Compression.Algorithm.SNAPPY);
    }


    public boolean createTableIfNeeded(String tableName, byte[] columnFamily, Compression.Algorithm compressionType) throws IOException {
        return createTableIfNeeded(getConnection(), tableName, columnFamily, compressionType);
    }

    /**
     * Create default HBase table layout with one column family.
     *
     * @param con HBase connection object
     * @param tableName    HBase table name
     * @param columnFamily Column Family
     * @param compressionType Compression Algorithm
     * @return boolean True if a new table was created
     * @throws IOException throws {@link IOException} from creating a connection / table
     **/
    public static boolean createTableIfNeeded(Connection con, String tableName, byte[] columnFamily, Compression.Algorithm compressionType)
            throws IOException {
        TableName tName = TableName.valueOf(tableName);
        return act(con, tableName, (table, admin) -> {
            if (!admin.tableExists(tName)) {
                HTableDescriptor descr = new HTableDescriptor(tName);
                HColumnDescriptor family = new HColumnDescriptor(columnFamily);
                if (compressionType != null) {
                    family.setCompressionType(compressionType);
                }
                descr.addFamily(family);
                try {
                    LOGGER.info("Create New HBASE table {}", tableName);
                    admin.createTable(descr);
                } catch (TableExistsException e) {
                    return false;
                }
                return true;
            }
            return false;
        });
    }

    public static void addHBaseSettings(Configuration conf, String hostPortString) throws URISyntaxException {
        String[] hostPort = hostPortString.split(":");
        String server = hostPort[0];
        String port = hostPort.length > 0 ? hostPort[1] : "60000";
        String master = String.join(":", server, port);
//        conf.set(HBASE_MASTER, master);
        conf.set(HConstants.ZOOKEEPER_QUORUM, master);
    }
}
