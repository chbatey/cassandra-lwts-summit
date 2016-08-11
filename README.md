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

It is important to understand the trade odds in using LWTs as they are a costly
option and unusably slow in circumstances.

### How they work

Prepare

Accept

Commit

### How you use them

CQL etc

#### Consistency

SERIAL
LOCAL\_SERIAL

#### Write timestamps

Client side time stamps and LWTs

### Advanced use case: Batches

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

### So what?

Examples that where quorum based consistency just won't do:

* Uniqueness
* Finite resource
* Locks

### Now what?

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



### Config


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

### Batches




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

```cql
CREATE TABLE IF NOT EXISTS locks ( name text PRIMARY KEY, owner text );
```

Scenarios:





