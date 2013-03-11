
DROP TABLE JOBSTATUS;

DROP TABLE STEPSTATUS;

DROP TABLE CHECKPOINTDATA;

DROP TABLE JOBINSTANCEDATA;

DROP TABLE EXECUTIONINSTANCEDATA;

DROP TABLE STEPEXECUTIONINSTANCEDATA;

CREATE TABLE JOBSTATUS (
  id		BIGINT,
  obj		VARBINARY
);

CREATE TABLE STEPSTATUS(
  id		VARCHAR(512),
  obj		VARBINARY
);

CREATE TABLE CHECKPOINTDATA(
  id		VARCHAR(512),
  obj		VARBINARY
);

CREATE TABLE JOBINSTANCEDATA(
  id		VARCHAR(512),
  name		VARCHAR(512), 
  apptag VARCHAR(512)
);

CREATE TABLE EXECUTIONINSTANCEDATA(
  id			VARCHAR(512),
  createtime	DATETIME,
  starttime		DATETIME,
  endtime		DATETIME,
  updatetime	DATETIME,
  parameters	VARBINARY,
  jobinstanceid	VARCHAR(512),
  batchstatus		VARCHAR(512),
  exitstatus		VARCHAR(512)
);

CREATE TABLE STEPEXECUTIONINSTANCEDATA(
	id			VARCHAR(512),
	jobexecid	VARCHAR(512),
	stepexecid			VARCHAR(512),
	batchstatus         VARCHAR(512),
    exitstatus			VARCHAR(512),
    stepname			VARCHAR(512),
	readcount			VARCHAR(512),
	writecount			VARCHAR(512),
	commitcount         VARCHAR(512),
	rollbackcount		VARCHAR(512),
	readskipcount		VARCHAR(512),
	processskipcount	VARCHAR(512),
	filtercount			VARCHAR(512),
	writeskipcount		VARCHAR(512),
	startTime           DATETIME,
	endTime             DATETIME,
	persistentData		VARBINARY
);  
  
