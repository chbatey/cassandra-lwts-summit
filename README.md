# LWTs

## Abstract

The Strong Consistency provided by QUORUM reads in Cassandra can still lead to
read-write-modify problems when applications want to do things such as guarantee
uniqueness or sell exactly 300 cinema tickets. Fortunately Light Weight
Transactions (LWT) are designed to solve the problems Strong Consistency can
not.

We will discuss:
* Syntax and semantics: Theoretical use cases 
* How they work under the covers

Then we will go through LWTs in practice:
* How do the number of nodes/replicas/data centres affect performance?
* How does contention (multiple concurrent queries using LWTs) affect
  availability and performance?
  * What consistency guarantees do you get with other LWTs and non-LWTs?
  * How does LWT timeout differ from normal write timeout? 
  * Use case: LWTs as a distributed lock and how it went wrong 5 times.

## What?

LWTs are for when strong consistency just won't do. Let's first understand
Cassandra's regular consistency mode:

* ONE
* QUORUM
* ALL

Why is this not good enough?

* No way to define an order of updates
* Read write race conditions

So what is a LWT?

A majority of the replicas for a partition run a consensus algorithm to decide
on the next value.

It is important to understand the trade offs in using LWTs as they are a costly
option and unusably slow in circumstances.

### How they work

Slides on how normal reads and writes work. Example of read then write race condition.

Then on to how a LWT works:

#### Prepare and Promise

The coordinator sends a prepare message to the replicas for the given partition to say that it wishes to propose a new value;

Versioned a.k.a ballot with a timeuuid.

Replicas respond with a promise not to accept any proposals with a lower ballot.

INote: 

StorageProxy.cas
o.a.c.s.paxos.PrepareVerbHandler
o.a.c.s.paxos.PaxosState.prepare

There is a per partition lock to ensure that there is no parallelism in promises with concurrent accepts. 
 
This means that for a single node, per partition, the paxos state machine transitions are serial.
 
If a replica has already promised a higher ballot then it doesn't send a promise and instead sends the higher ballot so the coordinator can
use that to make sure that a retry has a higher ballot.

If the replicas don't respond with promised then the coordinator waits and tries again, incrementing contention. 

Otherwise we move onto the propose phase with two new bits of information:

* The last committed value for this partition
* The most recent in progress

#### Read (to check CAS)

If your LWT consistency is SERIAL, then QUORUM, if it is LOCAL_SERIAL then it is LOCAL_QUORUM.

Metric:: conditionNotMet

Existing row is returned.

#### Propose and Accept

Before proposing the new value any in progress LWTs are first first finished.

You can see this with the metric unfinishedCommit.

A proposal is now sent. Each replica checks that the proposal is greater than any other LWT that the node has promised
 not to accept anything higher.

INote

o.a.c.s.paxos.ProposeVerbHandler
o.a.c.s.paxos.PaxosState.propose

Same lock as for handling prepares.

#### Commit

Finally if all that works a commit is sent to every replica.

The LWT will block until the right number of replicas have responded to the commit based on your consistency level. 
ANY = fire and forget commit.

### How you use them

CQL etc

#### Consistency

Each LWT has two consistency level:

Serial consistency, either:

* SERIAL
* LOCAL\_SERIAL

For the LWT this decides which replicas for the given partition are used. 
If SERIAL then a majority of all replicas across all DCs are used.
If LOCAL_SERIAL then a majority or replicas for the given DC are used.

Consistency:

Used for the commit phase of 

#### Write timestamps

Client side time stamps and LWTs

### Advanced: SERIAL reads

If data is updated with a LWT consider using SERIAL or LOCAL_SERIAL reads.    

Single partitions only.

Starts a paxos round (prepare) and so finishes off any uncommitted LWTs. As this can produce WTEs they are converted to
read time out exceptions.

This can be a much more expensive read operation and you will see it in the Coordinator Read Latency.

INote

o.a.c.s.StorageProxy.readWithPaxos



### Advanced: Batches

Batch with LWTs

### Operations

CasContentionTimeout - Which phase is this for?

#### Metrics and monitoring

Stages involved

ClientMetrics.CASRead
ClientMetrics.CASWrite

For both of the above:

ConditionNotMet
ContentionHistogram
Failures
UnfinishedCommit

##### Tracing LWTs

Stick in how they work, after the explanation

### So what?

Examples that where quorum based consistency just won't do:

* Uniqueness
* Finite resource
* Locks

### Now what?



#### Internal tables

```
CREATE TABLE system.paxos (
    row_key blob,
    cf_id uuid,
    in_progress_ballot timeuuid,
    most_recent_commit blob,
    most_recent_commit_at timeuuid,
    most_recent_commit_version int,
    proposal blob,
    proposal_ballot timeuuid,
    proposal_version int,
    PRIMARY KEY (row_key, cf_id)
) WITH CLUSTERING ORDER BY (cf_id ASC)

```

## Examples

Local testing with real clusters:

```cql
CREATE KEYSPACE IF NOT EXISTS lwts with replication = {'class': 'SimpleStrategy', 'replication_factor': 3 };
```
Real testing in AWS:

```cql
todo
```

### Lock service

Table:

Mutual exclusion:
```cql
CREATE TABLE IF NOT EXISTS locks ( name text PRIMARY KEY, owner text );
```

Uniqueness
```
CREATE TABLE IF NOT EXISTS users (
    user_name text PRIMARY KEY,
    email text,
    password text
)
```

```
INSERT INTO users (user_name, password, email ) 
    VALUES ( 'chbatey', 'different', 'adifferentchris@gmail.com' ) IF NOT EXISTS;
```

Finite resource:

```
CREATE TABLE IF NOT EXISTS vouchers_mutable (
    name text PRIMARY KEY,
    sold int
)
```

```
INSERT INTO vouchers_mutable (name, sold ) VALUES ( 'free tv', 0);
```

```
UPDATE vouchers_mutable SET sold = 1 WHERE name = 'free tv';
```

```
UPDATE vouchers_mutable SET sold = 1 WHERE name = 'free tv' IF sold = 0;
```

But what if we want to keep track of who has the vouchers?

```
CREATE TABLE IF NOT EXISTS vouchers (
    name text,
    when timeuuid,
    sold int static,
    who text,
    PRIMARY KEY (name, when)
);
```

```
INSERT INTO vouchers (name, sold) VALUES ( 'free tv', 0);
```

```
SELECT sold FROM vouchers WHERE name = 'free tv';
```


```
BEGIN BATCH 
    UPDATE vouchers SET sold = 1 WHERE name = 'free tv' IF sold  = 0
    INSERT INTO vouchers (name, when, who) VALUES ( 'free tv', now(), 'chris') 
APPLY BATCH ;

 [applied]
-----------
      True

```

```
BEGIN BATCH 
    UPDATE vouchers SET sold = 1 WHERE name = 'free tv' IF sold = 0 
    INSERT INTO vouchers (name, when, who) VALUES ( 'free tv', now(), 'chris') 
APPLY BATCH ;

 [applied] | name    | when | sold
-----------+---------+------+------
     False | free tv | null |    1

```

## TODO

Isolation levels
Raft
Ramp
2PC


### Trace example

Execute CQL3 query | 2016-08-22 12:38:44.131000 | 127.0.0.1 |              0
Parsing insert into users (user_name, password, email ) values ( 'chbatey', 'chrisrocks', 'christopher.batey@gmail.com' ) if not exists; [SharedPool-Worker-1] | 2016-08-22 12:38:44.132000 | 127.0.0.1 |           1125
Sending PAXOS_PREPARE message to /127.0.0.3 [MessagingService-Outgoing-/127.0.0.3] | 2016-08-22 12:38:44.141000 | 127.0.0.1 |          10414
Sending PAXOS_PREPARE message to /127.0.0.2 [MessagingService-Outgoing-/127.0.0.2] | 2016-08-22 12:38:44.142000 | 127.0.0.1 |          10908
PAXOS_PREPARE message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.143000 | 127.0.0.3 |            492
PAXOS_PREPARE message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.144000 | 127.0.0.2 |            620
Parsing SELECT * FROM system.paxos WHERE row_key = ? AND cf_id = ? [SharedPool-Worker-1] | 2016-08-22 12:38:44.146000 | 127.0.0.3 |           2645
Promising ballot fb282190-685c-11e6-71a2-e0d2d098d5d6 [SharedPool-Worker-1] | 2016-08-22 12:38:44.147000 | 127.0.0.3 |           4325
Parsing UPDATE system.paxos USING TIMESTAMP ? AND TTL ? SET in_progress_ballot = ? WHERE row_key = ? AND cf_id = ? [SharedPool-Worker-1] | 2016-08-22 12:38:44.147000 | 127.0.0.3 |           4505
Appending to commitlog [SharedPool-Worker-1] | 2016-08-22 12:38:44.149000 | 127.0.0.3 |           6068
Adding to paxos memtable [SharedPool-Worker-1] | 2016-08-22 12:38:44.149000 | 127.0.0.3 |           6521
Sending REQUEST_RESPONSE message to /127.0.0.1 [MessagingService-Outgoing-/127.0.0.1] | 2016-08-22 12:38:44.150000 | 127.0.0.3 |           7429
REQUEST_RESPONSE message received from /127.0.0.3 [MessagingService-Incoming-/127.0.0.3] | 2016-08-22 12:38:44.151000 | 127.0.0.1 |          20506
Processing response from /127.0.0.3 [SharedPool-Worker-3] | 2016-08-22 12:38:44.151000 | 127.0.0.1 |          20694
Sending PAXOS_PREPARE message to /127.0.0.1 [MessagingService-Outgoing-/127.0.0.1] | 2016-08-22 12:38:44.159000 | 127.0.0.1 |          28391
PAXOS_PREPARE message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.162000 | 127.0.0.1 |          31588
Parsing SELECT * FROM system.paxos WHERE row_key = ? AND cf_id = ? [SharedPool-Worker-3] | 2016-08-22 12:38:44.164000 | 127.0.0.1 |          33157
Promising ballot fb282190-685c-11e6-71a2-e0d2d098d5d6 [SharedPool-Worker-3] | 2016-08-22 12:38:44.166000 | 127.0.0.1 |          35282
Parsing UPDATE system.paxos USING TIMESTAMP ? AND TTL ? SET in_progress_ballot = ? WHERE row_key = ? AND cf_id = ? [SharedPool-Worker-3] | 2016-08-22 12:38:44.167000 | 127.0.0.1 |          35949
Parsing SELECT * FROM system.paxos WHERE row_key = ? AND cf_id = ? [SharedPool-Worker-1] | 2016-08-22 12:38:44.167000 | 127.0.0.2 |          23611
Promising ballot fb282190-685c-11e6-71a2-e0d2d098d5d6 [SharedPool-Worker-1] | 2016-08-22 12:38:44.172000 | 127.0.0.2 |          28615
Parsing UPDATE system.paxos USING TIMESTAMP ? AND TTL ? SET in_progress_ballot = ? WHERE row_key = ? AND cf_id = ? [SharedPool-Worker-1] | 2016-08-22 12:38:44.173000 | 127.0.0.2 |          28844
Preparing statement [SharedPool-Worker-1] | 2016-08-22 12:38:44.174000 | 127.0.0.2 |          29954
Appending to commitlog [SharedPool-Worker-1] | 2016-08-22 12:38:44.174000 | 127.0.0.2 |          30703
Adding to paxos memtable [SharedPool-Worker-1] | 2016-08-22 12:38:44.175000 | 127.0.0.2 |          31210
Appending to commitlog [SharedPool-Worker-3] | 2016-08-22 12:38:44.177000 | 127.0.0.1 |          46129
Adding to paxos memtable [SharedPool-Worker-3] | 2016-08-22 12:38:44.177000 | 127.0.0.1 |          46497
Sending REQUEST_RESPONSE message to /127.0.0.1 [MessagingService-Outgoing-/127.0.0.1] | 2016-08-22 12:38:44.178000 | 127.0.0.2 |          34446
REQUEST_RESPONSE message received from /127.0.0.2 [MessagingService-Incoming-/127.0.0.2] | 2016-08-22 12:38:44.179000 | 127.0.0.1 |          48034
Processing response from /127.0.0.2 [SharedPool-Worker-4] | 2016-08-22 12:38:44.179000 | 127.0.0.1 |          48412
Reading existing values for CAS precondition [SharedPool-Worker-1] | 2016-08-22 12:38:44.179000 | 127.0.0.1 |          48632
Executing single-partition query on users [SharedPool-Worker-4] | 2016-08-22 12:38:44.182000 | 127.0.0.1 |          50899
Sending REQUEST_RESPONSE message to /127.0.0.1 [MessagingService-Outgoing-/127.0.0.1] | 2016-08-22 12:38:44.182000 | 127.0.0.1 |          51435
REQUEST_RESPONSE message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.183000 | 127.0.0.1 |          51828
Processing response from /127.0.0.1 [SharedPool-Worker-4] | 2016-08-22 12:38:44.183000 | 127.0.0.1 |          51990
Sending READ message to /127.0.0.3 [MessagingService-Outgoing-/127.0.0.3] | 2016-08-22 12:38:44.183000 | 127.0.0.1 |          52432
Sending READ message to /127.0.0.2 [MessagingService-Outgoing-/127.0.0.2] | 2016-08-22 12:38:44.183000 | 127.0.0.1 |          52722
READ message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.184000 | 127.0.0.2 |             43
READ message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.184000 | 127.0.0.3 |             23
Executing single-partition query on users [SharedPool-Worker-1] | 2016-08-22 12:38:44.184000 | 127.0.0.3 |            320
Enqueuing response to /127.0.0.1 [SharedPool-Worker-1] | 2016-08-22 12:38:44.185000 | 127.0.0.3 |           1401
Sending REQUEST_RESPONSE message to /127.0.0.1 [MessagingService-Outgoing-/127.0.0.1] | 2016-08-22 12:38:44.186000 | 127.0.0.3 |           2374
REQUEST_RESPONSE message received from /127.0.0.3 [MessagingService-Incoming-/127.0.0.3] | 2016-08-22 12:38:44.187000 | 127.0.0.1 |          56655
Processing response from /127.0.0.3 [SharedPool-Worker-3] | 2016-08-22 12:38:44.188000 | 127.0.0.1 |          56984
CAS precondition is met; proposing client-requested updates for fb282190-685c-11e6-71a2-e0d2d098d5d6 [SharedPool-Worker-1] | 2016-08-22 12:38:44.195000 | 127.0.0.1 |          64675
Sending PAXOS_PROPOSE message to /127.0.0.2 [MessagingService-Outgoing-/127.0.0.2] | 2016-08-22 12:38:44.196000 | 127.0.0.1 |          65606
Sending PAXOS_PROPOSE message to /127.0.0.1 [MessagingService-Outgoing-/127.0.0.1] | 2016-08-22 12:38:44.196000 | 127.0.0.1 |          65606
PAXOS_PROPOSE message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.197000 | 127.0.0.1 |          65986
Sending PAXOS_PROPOSE message to /127.0.0.3 [MessagingService-Outgoing-/127.0.0.3] | 2016-08-22 12:38:44.197000 | 127.0.0.1 |          66139
Executing single-partition query on paxos [SharedPool-Worker-2] | 2016-08-22 12:38:44.197000 | 127.0.0.1 |          66505
Accepting proposal Commit(fb282190-685c-11e6-71a2-e0d2d098d5d6, [lwts.users] key=chbatey columns=[[] | [email password]]\n    Row: EMPTY | email=christopher.batey@gmail.com, password=chrisrocks) [SharedPool-Worker-2] | 2016-08-22 12:38:44.199000 | 127.0.0.1 |          67804
Parsing UPDATE system.paxos USING TIMESTAMP ? AND TTL ? SET proposal_ballot = ?, proposal = ?, proposal_version = ? WHERE row_key = ? AND cf_id = ? [SharedPool-Worker-2] | 2016-08-22 12:38:44.199000 | 127.0.0.1 |          68119
Adding to paxos memtable [SharedPool-Worker-2] | 2016-08-22 12:38:44.200000 | 127.0.0.1 |          69548
Sending REQUEST_RESPONSE message to /127.0.0.1 [MessagingService-Outgoing-/127.0.0.1] | 2016-08-22 12:38:44.203000 | 127.0.0.1 |          72456
REQUEST_RESPONSE message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.204000 | 127.0.0.1 |          73603
Processing response from /127.0.0.1 [SharedPool-Worker-4] | 2016-08-22 12:38:44.205000 | 127.0.0.1 |          73808
REQUEST_RESPONSE message received from /127.0.0.3 [MessagingService-Incoming-/127.0.0.3] | 2016-08-22 12:38:44.205000 | 127.0.0.1 |          74127
Processing response from /127.0.0.3 [SharedPool-Worker-4] | 2016-08-22 12:38:44.209000 | 127.0.0.1 |          77869
Sending PAXOS_COMMIT message to /127.0.0.2 [MessagingService-Outgoing-/127.0.0.2] | 2016-08-22 12:38:44.210000 | 127.0.0.1 |          79282
Sending PAXOS_COMMIT message to /127.0.0.3 [MessagingService-Outgoing-/127.0.0.3] | 2016-08-22 12:38:44.210000 | 127.0.0.1 |          79645
Parsing SELECT truncated_at FROM system.local WHERE key = 'local' [SharedPool-Worker-4] | 2016-08-22 12:38:44.211000 | 127.0.0.1 |          80553
Committing proposal Commit(fb282190-685c-11e6-71a2-e0d2d098d5d6, [lwts.users] key=chbatey columns=[[] | [email password]]\n    Row: EMPTY | email=christopher.batey@gmail.com, password=chrisrocks) [SharedPool-Worker-4] | 2016-08-22 12:38:44.214000 | 127.0.0.1 |          83481
Parsing UPDATE system.paxos USING TIMESTAMP ? AND TTL ? SET proposal_ballot = null, proposal = null, most_recent_commit_at = ?, most_recent_commit = ?, most_recent_commit_version = ? WHERE row_key = ? AND cf_id = ? [SharedPool-Worker-4] | 2016-08-22 12:38:44.215000 | 127.0.0.1 |          84388
Preparing statement [SharedPool-Worker-4] | 2016-08-22 12:38:44.216000 | 127.0.0.1 |          84939
Adding to paxos memtable [SharedPool-Worker-4] | 2016-08-22 12:38:44.220000 | 127.0.0.1 |          89358
CAS successful [SharedPool-Worker-1] | 2016-08-22 12:38:44.221000 | 127.0.0.1 |          90657
Executing single-partition query on users [SharedPool-Worker-1] | 2016-08-22 12:38:44.221000 | 127.0.0.2 |          36606
Acquiring sstable references [SharedPool-Worker-1] | 2016-08-22 12:38:44.221000 | 127.0.0.2 |          36739
Enqueuing response to /127.0.0.1 [SharedPool-Worker-1] | 2016-08-22 12:38:44.222000 | 127.0.0.2 |          37599
Sending REQUEST_RESPONSE message to /127.0.0.1 [MessagingService-Outgoing-/127.0.0.1] | 2016-08-22 12:38:44.222000 | 127.0.0.2 |          38001
REQUEST_RESPONSE message received from /127.0.0.2 [MessagingService-Incoming-/127.0.0.2] | 2016-08-22 12:38:44.224000 | 127.0.0.1 |             --
Processing response from /127.0.0.2 [SharedPool-Worker-1] | 2016-08-22 12:38:44.224000 | 127.0.0.1 |             --
PAXOS_PROPOSE message received from /127.0.0.1 [MessagingService-Incoming-/127.0.0.1] | 2016-08-22 12:38:44.228000 | 127.0.0.2 |             35
REQUEST_RESPONSE message received from /127.0.0.2 [MessagingService-Incoming-/127.0.0.2] | 2016-08-22 12:38:44.248000 | 127.0.0.1 |             --
Request complete | 2016-08-22 12:38:44.221892 | 127.0.0.1 |          90892

### Results

#### No LWT:

08:37:45.401 [main] INFO info.batey.examples.cassandra.lwts.LWTTest - Bought 10000 Failed 0 Commit Failed 0 Unknown 0
a value equal to or greater than <10000L> <1332L> was less than <10000L>

       Value     Percentile TotalCount 1/(1-Percentile)

   43679.743 0.000000000000          1           1.00
   44302.335 0.100000000000       1117           1.11
   44466.175 0.200000000000       2131           1.25
   44597.247 0.300000000000       3112           1.43
   44728.319 0.400000000000       4115           1.67
   44859.391 0.500000000000       5183           2.00
   44924.927 0.550000000000       5679           2.22
   44990.463 0.600000000000       6184           2.50
   45055.999 0.650000000000       6632           2.86
   45121.535 0.700000000000       7073           3.33
   45219.839 0.750000000000       7642           4.00
   45252.607 0.775000000000       7802           4.44
   45318.143 0.800000000000       8114           5.00
   45383.679 0.825000000000       8374           5.71
   45449.215 0.850000000000       8578           6.67
   45514.751 0.875000000000       8775           8.00
   45580.287 0.887500000000       8939           8.89
   45613.055 0.900000000000       9016          10.00
   45678.591 0.912500000000       9153          11.43
   45776.895 0.925000000000       9286          13.33
   45875.199 0.937500000000       9401          16.00
   45907.967 0.943750000000       9442          17.78
   46006.271 0.950000000000       9516          20.00
   46104.575 0.956250000000       9585          22.86
   46235.647 0.962500000000       9639          26.67
   46399.487 0.968750000000       9697          32.00
   46497.791 0.971875000000       9733          35.56
   46563.327 0.975000000000       9755          40.00
   46661.631 0.978125000000       9784          45.71
   46858.239 0.981250000000       9814          53.33
   47120.383 0.984375000000       9845          64.00
   47284.223 0.985937500000       9861          71.11
   47611.903 0.987500000000       9875          80.00
   47841.279 0.989062500000       9893          91.43
   48201.727 0.990625000000       9907         106.67
   48398.335 0.992187500000       9922         128.00
   48562.175 0.992968750000       9930         142.22
   48889.855 0.993750000000       9938         160.00
   49512.447 0.994531250000       9946         182.86
   50626.559 0.995312500000       9954         213.33
   51445.759 0.996093750000       9962         256.00
   51970.047 0.996484375000       9965         284.44
   52363.263 0.996875000000       9969         320.00
   52559.871 0.997265625000       9973         365.71
   53116.927 0.997656250000       9977         426.67
   54394.879 0.998046875000       9981         512.00
   55705.599 0.998242187500       9983         568.89
   56721.407 0.998437500000       9985         640.00
   57933.823 0.998632812500       9987         731.43
   58458.111 0.998828125000       9989         853.33
   72417.279 0.999023437500       9991        1024.00
   73138.175 0.999121093750       9992        1137.78
   73465.855 0.999218750000       9994        1280.00
   73465.855 0.999316406250       9994        1462.86
   73924.607 0.999414062500       9995        1706.67
   74186.751 0.999511718750       9996        2048.00
   74186.751 0.999560546875       9996        2275.56
   75431.935 0.999609375000       9997        2560.00
   75431.935 0.999658203125       9997        2925.71
   75563.007 0.999707031250       9998        3413.33
   75563.007 0.999755859375       9998        4096.00
   75563.007 0.999780273438       9998        4551.11
   75759.615 0.999804687500       9999        5120.00
   75759.615 0.999829101563       9999        5851.43
   75759.615 0.999853515625       9999        6826.67
   75759.615 0.999877929688       9999        8192.00
   75759.615 0.999890136719       9999        9102.22
   81723.391 0.999902343750      10000       10240.00
   81723.391 1.000000000000      10000
#[Mean    =    44980.067, StdDeviation   =     1258.432]
#[Max     =    81723.391, Total count    =        10000]
#[Buckets =           32, SubBuckets     =         2048]

#### LWT on mutable 
08:35:52.568 [main] INFO info.batey.examples.cassandra.lwts.LWTTest - Bought 1747 Failed 8153 Commit Failed 0 Unknown 100

#### Batches with LWTs
08:36:59.132 [main] INFO info.batey.examples.cassandra.lwts.LWTTest - Bought 1660 Failed 8256 Commit Failed 0 Unknown 84

#### LWTs no contention
08:38:35.214 [main] INFO info.batey.examples.cassandra.lwts.LWTTest - Bought 10000 Failed 0 Commit Failed 0 Unknown 0