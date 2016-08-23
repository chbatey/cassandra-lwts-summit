package info.batey.examples.cassandra.lwts;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Session;

public class Main {
    public static void main(String[] args) {
        Cluster cluster = Cluster.builder()
                .addContactPoint("localhost")
                .build();

        Session lwts = cluster.connect("lwts");

        ResultSet result = lwts.execute("select * from user");

        System.out.println(result);

        cluster.close();
    }
}
