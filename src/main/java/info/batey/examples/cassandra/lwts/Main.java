package info.batey.examples.cassandra.lwts;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;

public class Main {
    public static void main(String[] args) {
        Cluster cluster = Cluster.builder()
                .addContactPoint("localhost")
                .build();

        Session session = cluster.connect("lwts");
        session.execute("CREATE KEYSPACE IF NOT EXISTS lwts with replication = {'class': 'SimpleStrategy', 'replication_factor': 3 };");
        session.execute("USE lwts");

        session.execute("CREATE TABLE IF NOT EXISTS vouchers_mutable (\n" +
                "    name text PRIMARY KEY,\n" +
                "    sold int\n" +
                ")");

        session.execute("CREATE TABLE IF NOT EXISTS vouchers (\n" +
                "    name text,\n" +
                "    when timeuuid,\n" +
                "    sold int static,\n" +
                "    who text,\n" +
                "    PRIMARY KEY (name, when)\n" +
                ")");

        session.execute("CREATE TABLE IF NOT EXISTS users (\n" +
                "    user_name text PRIMARY KEY,\n" +
                "    email text,\n" +
                "    password text\n" +
                ")");

//        sellSomeVouchers(new VouchersMutable(session));
//        sellSomeVouchers(new VouchersMutableLWT(session));
        sellSomeVouchers(new VouchersBatches(session));

        session.close();
        cluster.close();
    }

    private static void sellSomeVouchers(VoucherManager vm) {
        String freeTv = "free tv";
        String who = "chbatey";

        boolean voucherCreated = vm.createVoucher(freeTv);
        System.out.println("Voucher created? " + voucherCreated);

        boolean sold = vm.sellVoucher(freeTv, who);
        System.out.println("Voucher sold? " + sold);
    }
}
