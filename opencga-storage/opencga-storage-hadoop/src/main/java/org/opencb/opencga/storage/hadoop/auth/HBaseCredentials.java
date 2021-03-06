package org.opencb.opencga.storage.hadoop.auth;

import org.opencb.opencga.core.auth.IllegalOpenCGACredentialsException;
import org.opencb.opencga.core.auth.OpenCGACredentials;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Created by mh719 on 16/06/15.
 */
public class HBaseCredentials implements OpenCGACredentials {

    private static final Integer DEFAULT_PORT = 60000;
    private static final String DEFAULT_ZOOKEEPER_PATH = "hbase";
    private final String host;
    private final int hbasePort;
    private final String table;
    private final String pass;
    private final String user;
    private final String zookeeperPath;

    public HBaseCredentials(String host, String table, String user, String pass) {
        this(host, table, user, pass, DEFAULT_PORT, DEFAULT_ZOOKEEPER_PATH);
    }

    public HBaseCredentials(String host, String table, String user, String pass, Integer hbasePort) {
        this(host, table, user, pass, hbasePort, DEFAULT_ZOOKEEPER_PATH);
    }

    public HBaseCredentials(String host, String table, String user, String pass, Integer hbasePort,
                            String zookeeperPath) {
        this.host = host;
        this.hbasePort = hbasePort;
        this.table = table;
        this.user = user;
        this.pass = pass;
        this.zookeeperPath = zookeeperPath;
    }

    public String getZookeeperPath() {
        return this.zookeeperPath;
    }

    public String getPass() {
        return pass;
    }

    public String getUser() {
        return user;
    }

    public String getTable() {
        return table;
    }

    public String getHost() {
        return host;
    }

    public String getHostAndPort() {
        return host + ":" + getHbasePort();
    }

    public int getHbasePort() {
        return hbasePort;
    }

    @Override
    public boolean check() throws IllegalOpenCGACredentialsException {
        return true; // TODO not sure how to check
    }

    @Override
    public String toJson() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public URI toUri() {
        try {
            return new URI("hbase", null, getHost(), getHbasePort(), "/" + getTable(), null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }
}
