package info.batey.examples.cassandra.lwts;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.IntStream.range;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class LWTTest {

    private final static Logger LOG = LoggerFactory.getLogger(LWTTest.class);

    private final static int CLIENTS = 10;
    private final static int VOUCHERS_EACH = 100;
    private Cluster cluster;
    private Session session;

    @Before
    public void setUp() throws Exception {
        cluster = Cluster.builder().addContactPoint("localhost").build();
        session = cluster.connect();
        setupSchema();
    }

    @Test
    public void testNoLWT() throws Exception {
        VoucherManager vm = new VouchersMutable(session);
        String voucherName = "free tv";
        vm.deleteVoucher(voucherName);
        testVouchers(vm, voucherName);
    }

    @Test
    public void testLWT() throws Exception {
        VoucherManager vm = new VouchersMutableLWT(session);
        String voucherName = "free tv";
        vm.deleteVoucher(voucherName);
        testVouchers(vm, voucherName);
    }

    private void testVouchers(VoucherManager vm, String voucherName) throws InterruptedException {
        vm.createVoucher(voucherName);
        CountDownLatch start = new CountDownLatch(CLIENTS);
        CountDownLatch finish = new CountDownLatch(CLIENTS);
        Stream<Client> clients = range(0, CLIENTS).mapToObj(i ->
                new Client(vm, VOUCHERS_EACH, voucherName, "client " + i, start, finish));

        List<Client> allClients = clients.collect(Collectors.toList());
        allClients.forEach(Thread::start);

        finish.await();
        long bought = allClients.stream().map(Client::getVouchersBought).mapToInt(Integer::intValue).sum();
        long failed = allClients.stream().map(Client::getVouchersFailed).mapToInt(Integer::intValue).sum();
        long commitFailed = allClients.stream().map(Client::getCommitFailed).mapToInt(Integer::intValue).sum();
        long unknown = allClients.stream().map(Client::getUnknowns).mapToInt(Integer::intValue).sum();

        LOG.info("Bought {} Failed {} Commit Failed {} Unknown {}", bought, failed, commitFailed, unknown);

        long vouchersSoldServer = vm.vouchersSold(voucherName);
        assertEquals("We lost updates", CLIENTS * VOUCHERS_EACH, bought + failed + commitFailed + unknown);
        assertThat(vouchersSoldServer, allOf(greaterThan(bought), lessThan(bought + unknown + commitFailed)));
    }


    private void setupSchema() {
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

    }
}
