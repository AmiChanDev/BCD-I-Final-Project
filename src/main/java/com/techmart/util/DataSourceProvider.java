package com.techmart.util;

import jakarta.annotation.Resource;
import jakarta.ejb.Stateless;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

@Stateless
public class DataSourceProvider {

    private static final Logger LOG = Logger.getLogger(DataSourceProvider.class.getName());

    @Resource(lookup = "jdbc/TechMartDS")
    private DataSource dataSource;

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new IllegalStateException(
                "DataSource not injected — verify jdbc/TechMartDS is configured in GlassFish.");
        }
        Connection conn = dataSource.getConnection();
        LOG.fine("Connection borrowed from pool: " + conn);
        return conn;
    }

    public DataSource getDataSource() {
        return dataSource;
    }
}
