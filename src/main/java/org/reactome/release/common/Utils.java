package org.reactome.release.common;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;

import java.util.Properties;

public class Utils {

    public static Driver getDriver(String user, String password, String host, long port) {
        String neo4jUri = "bolt://" + host + ":" + port;

        return GraphDatabase.driver(neo4jUri, AuthTokens.basic(user, password));
    }

    public static Driver getDriver(Properties props) {
        String neo4jUser = props.getProperty("user", "neo4j");
        String neo4jPass = props.getProperty("password", "neo4j");
        String neo4jHost = props.getProperty("host", "localhost");
        long neo4jPort = Long.parseLong(props.getProperty("port", "7687"));

        return getDriver(neo4jUser, neo4jPass, neo4jHost, neo4jPort);
    }
}
