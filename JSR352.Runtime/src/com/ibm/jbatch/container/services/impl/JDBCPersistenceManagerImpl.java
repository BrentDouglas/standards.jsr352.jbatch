/*
 * Copyright 2012 International Business Machines Corp.
 * 
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package com.ibm.jbatch.container.services.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.batch.operations.JobOperator.BatchStatus;
import javax.batch.runtime.JobInstance;
import javax.batch.runtime.Metric;
import javax.batch.runtime.StepExecution;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import com.ibm.jbatch.container.context.impl.JobContextImpl;
import com.ibm.jbatch.container.context.impl.MetricImpl;
import com.ibm.jbatch.container.context.impl.StepContextImpl;
import com.ibm.jbatch.container.exception.BatchContainerServiceException;
import com.ibm.jbatch.container.exception.PersistenceException;
import com.ibm.jbatch.container.jobinstance.JobInstanceImpl;
import com.ibm.jbatch.container.jobinstance.JobOperatorJobExecutionImpl;
import com.ibm.jbatch.container.jobinstance.RuntimeJobExecutionHelper;
import com.ibm.jbatch.container.jobinstance.StepExecutionImpl;
import com.ibm.jbatch.container.jsl.Navigator;
import com.ibm.jbatch.container.persistence.CheckpointData;
import com.ibm.jbatch.container.persistence.CheckpointDataKey;
import com.ibm.jbatch.container.services.IJobExecution;
import com.ibm.jbatch.container.status.JobStatus;
import com.ibm.jbatch.container.status.JobStatusKey;
import com.ibm.jbatch.container.status.StepStatus;
import com.ibm.jbatch.container.status.StepStatusKey;
import com.ibm.jbatch.container.util.TCCLObjectInputStream;
import com.ibm.jbatch.spi.services.IBatchConfig;

public class JDBCPersistenceManagerImpl extends AbstractPersistenceManagerImpl {

	private static final String CLASSNAME = JDBCPersistenceManagerImpl.class.getName();

	private final static Logger logger = Logger.getLogger(CLASSNAME);
	
	private static final String JOBSTATUS_TABLE = "JOBSTATUS";
	private static final String STEPSTATUS_TABLE = "STEPSTATUS";
	private static final String CHECKPOINTDATA_TABLE = "CHECKPOINTDATA";
	private static final String JOBINSTANCEDATA_TABLE = "JOBINSTANCEDATA";
	private static final String EXECUTIONINSTANCEDATA_TABLE = "EXECUTIONINSTANCEDATA";
	private static final String STEPEXECUTIONINSTANCEDATA_TABLE = "STEPEXECUTIONINSTANCEDATA";
	
	private static final String CREATE_TAB_JOBSTATUS = "CREATE TABLE JOBSTATUS("
			+ "id BIGINT CONSTRAINT JOBSTATUS_PK PRIMARY KEY," 
			+ "obj BLOB,"
			+ "CONSTRAINT JOBSTATUS_JOBINST_FK FOREIGN KEY (id) REFERENCES JOBINSTANCEDATA (jobinstanceid) ON DELETE CASCADE)";
	private static final String CREATE_TAB_STEPSTATUS = "CREATE TABLE STEPSTATUS("
			+ "id BIGINT CONSTRAINT STEPSTATUS_PK PRIMARY KEY," 
			+ "obj BLOB,"
			+ "CONSTRAINT STEPSTATUS_STEPEXEC_FK FOREIGN KEY (id) REFERENCES STEPEXECUTIONINSTANCEDATA (stepexecid) ON DELETE CASCADE)";
	private static final String CREATE_TAB_CHECKPOINTDATA = "CREATE TABLE CHECKPOINTDATA("
			+ "id VARCHAR(512),obj BLOB)";
	private static final String CREATE_TAB_JOBINSTANCEDATA = "CREATE TABLE JOBINSTANCEDATA("
			+ "jobinstanceid BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1) CONSTRAINT JOBINSTANCE_PK PRIMARY KEY,"
			+ "name VARCHAR(512),"
			+ "apptag VARCHAR(512))";
	private static final String CREATE_TAB_EXECUTIONINSTANCEDATA = "CREATE TABLE EXECUTIONINSTANCEDATA("
			+ "jobexecid BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1) CONSTRAINT JOBEXECUTION_PK PRIMARY KEY,"
			+ "jobinstanceid BIGINT,"
			+ "createtime TIMESTAMP,"
			+ "starttime TIMESTAMP,"
			+ "endtime TIMESTAMP,"
			+ "updatetime TIMESTAMP,"
			+ "parameters BLOB,"
			+ "batchstatus VARCHAR(512),"
			+ "exitstatus VARCHAR(512)," 
			+ "CONSTRAINT JOBINST_JOBEXEC_FK FOREIGN KEY (jobinstanceid) REFERENCES JOBINSTANCEDATA (jobinstanceid))";
	private static final String CREATE_TAB_STEPEXECUTIONINSTANCEDATA = "CREATE TABLE STEPEXECUTIONINSTANCEDATA("
			+ "stepexecid BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1) CONSTRAINT STEPEXECUTION_PK PRIMARY KEY,"
			+ "jobexecid BIGINT,"
			+ "batchstatus VARCHAR(512),"
			+ "exitstatus VARCHAR(512),"
			+ "stepname VARCHAR(512),"
	        + "stepcontainmentcsv VARCHAR(512),"
			+ "readcount INTEGER,"
			+ "writecount INTEGER,"
			+ "commitcount INTEGER,"
			+ "rollbackcount INTEGER,"
			+ "readskipcount INTEGER,"
			+ "processskipcount INTEGER,"
			+ "filtercount INTEGER,"
			+ "writeskipcount INTEGER,"
			+ "startTime TIMESTAMP," 
			+ "endTime TIMESTAMP,"
			+ "persistentData BLOB," 
			+ "CONSTRAINT JOBEXEC_STEPEXEC_FK FOREIGN KEY (jobexecid) REFERENCES EXECUTIONINSTANCEDATA (jobexecid))";
	
	private static final String INSERT_JOBSTATUS = "insert into jobstatus values(?, ?)";
	
	private static final String UPDATE_JOBSTATUS = "update jobstatus set obj = ? where id = ?";

	private static final String SELECT_JOBSTATUS = "select id, obj from jobstatus where id = ?";
	
	private static final String DELETE_JOBSTATUS = "delete from jobstatus where id = ?";

	private static final String INSERT_STEPSTATUS = "insert into stepstatus values(?, ?)";
	
	private static final String UPDATE_STEPSTATUS = "update stepstatus set obj = ? where id = ?";

	private static final String SELECT_STEPSTATUS = "select id, obj from stepstatus where id = ?";
	
	private static final String DELETE_STEPSTATUS = "delete from stepstatus where id = ?";

	private static final String INSERT_CHECKPOINTDATA = "insert into checkpointdata values(?, ?)";

	private static final String UPDATE_CHECKPOINTDATA = "update checkpointdata set obj = ? where id = ?";

	private static final String SELECT_CHECKPOINTDATA = "select id, obj from checkpointdata where id = ?";
	
	private static final String CREATE_CHECKPOINTDATA_INDEX = "create index chk_index on checkpointdata(id)";
	
	private static final String DELETE_CHECKPOINTDATA = "delete from checkpointdata where id = ?";
	
	// JOB OPERATOR QUERIES
	private static final String INSERT_JOBINSTANCEDATA = "insert into jobinstancedata (name, apptag) values(?, ?)";
	
	private static final String INSERT_EXECUTIONDATA = "insert into executionInstanceData (jobinstanceid, parameters) values(?, ?)";
	
	private static final String SELECT_JOBINSTANCEDATA_COUNT = "select count(jobinstanceid) as jobinstancecount from jobinstancedata where name = ?";
	
	private static final String SELECT_JOBINSTANCEDATA_IDS = "select jobinstanceid from jobinstancedata where name = ?";
	
	private static final String SELECT_JOBINSTANCEDATA_NAMES = "select name from jobinstancedata where apptag = ?";
	private static final String SELECT_JOBINSTANCEDATA_APPTAG = "select apptag from jobinstancedata where jobinstanceid = ?";
	
	public static final String START_TIME = "starttime";
	public static final String CREATE_TIME = "createtime";
	public static final String END_TIME = "endtime";
	public static final String UPDATE_TIME = "updatetime";
	public static final String BATCH_STATUS = "batchstatus";
	public static final String EXIT_STATUS = "exitstatus";
	public static final String INSTANCE_ID = "instanceId";
	public static final String JOBEXEC_ID = "jobexecid";
	public static final String STEPEXEC_ID = "stepexecid";
	public static final String STEPCONTEXT = "stepcontext";
	public static final String APPTAG = "apptag";


    protected DataSource dataSource = null;
    protected String jndiName = null;
    
	protected String driver = ""; 
	protected String schema = "";
	protected String url = ""; 
	protected String userId = "";
	protected String pwd = "";
	
	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#init(com.ibm.jbatch.container.IBatchConfig)
	 */
	@Override
	public void init(IBatchConfig batchConfig) throws BatchContainerServiceException {
		super.init(batchConfig);
		
		logger.entering(CLASSNAME, "init", batchConfig);
		
		schema = batchConfig.getDatabaseConfigurationBean().getSchema();
		
		if (!batchConfig.isJ2seMode()) {
			jndiName = batchConfig.getDatabaseConfigurationBean().getJndiName();
			
			logger.log(Level.FINE, "JNDI name is {0}", jndiName);
			
			if (jndiName == null || jndiName.equals("")) {
				throw new BatchContainerServiceException("JNDI name is not defined.");
			}
			
			try {
				Context ctx = new InitialContext();
				dataSource = (DataSource) ctx.lookup(jndiName);
				
			} catch (NamingException e) {
				logger.severe("Lookup failed for JNDI name: " + jndiName + 
						".  One cause of this could be that the batch runtime is incorrectly configured to EE mode when it should be in SE mode.");
				throw new BatchContainerServiceException(e);
			}
			
		} else {
			driver = batchConfig.getDatabaseConfigurationBean().getJdbcDriver();
			url = batchConfig.getDatabaseConfigurationBean().getJdbcUrl();
			userId = batchConfig.getDatabaseConfigurationBean().getDbUser();
			pwd = batchConfig.getDatabaseConfigurationBean().getDbPassword();
			
			logger.log(Level.FINE, "driver: {0}, url: {1}", new Object[]{driver, url});
		}
		
		try {
			// only auto-create on Derby
			if(isDerby()) {	
				if(!isSchemaValid()) {
					createSchema();
				}
				checkAllTables();
			}
		} catch (SQLException e) {
			logger.severe(e.getLocalizedMessage());
			throw new BatchContainerServiceException(e);
		}
		
		logger.exiting(CLASSNAME, "init");
	}
	
	/**
	 * Checks if the default schema JBATCH or the schema defined in batch-config exists.
	 * 
	 * @return true if the schema exists, false otherwise.
	 * @throws SQLException
	 */
	private boolean isSchemaValid() throws SQLException {
		logger.entering(CLASSNAME, "isSchemaValid");
		Connection conn = getConnectionToDefaultSchema();
		DatabaseMetaData dbmd = conn.getMetaData();
		ResultSet rs = dbmd.getSchemas();
		while(rs.next()) {
			if (schema.equalsIgnoreCase(rs.getString("TABLE_SCHEM")) ) {
				cleanupConnection(conn, rs, null);
				logger.exiting(CLASSNAME, "isSchemaValid", true);
				return true;
			}
		}
		cleanupConnection(conn, rs, null);
		logger.exiting(CLASSNAME, "isSchemaValid", false);
		return false;
	}
	
	private boolean isDerby() throws SQLException {
		logger.entering(CLASSNAME, "isDerby");
		Connection conn = getConnectionToDefaultSchema();
		DatabaseMetaData dbmd = conn.getMetaData();
		boolean derby = dbmd.getDatabaseProductName().toLowerCase().indexOf("derby") > 0;
		logger.exiting(CLASSNAME, "isDerby", derby);
		return derby;
	}
	
	/**
	 * Creates the default schema JBATCH or the schema defined in batch-config.
	 * 
	 * @throws SQLException
	 */
	private void createSchema() throws SQLException {
		logger.entering(CLASSNAME, "createSchema");
		Connection conn = getConnectionToDefaultSchema();

		logger.log(Level.WARNING, schema + " schema does not exists. Trying to create it.");
		PreparedStatement ps = null;
		ps = conn.prepareStatement("CREATE SCHEMA " + schema);
		ps.execute();
	      
	    cleanupConnection(conn, null, ps);
	    logger.exiting(CLASSNAME, "createSchema");
	}
	
	/**
	 * Checks if all the runtime batch table exists. If not, it creates them.
	 *  
	 * @throws SQLException
	 */
	private void checkAllTables() throws SQLException {
		logger.entering(CLASSNAME, "checkAllTables");
		
		createIfNotExists(CHECKPOINTDATA_TABLE, CREATE_TAB_CHECKPOINTDATA);
		executeStatement(CREATE_CHECKPOINTDATA_INDEX);
		createIfNotExists(JOBINSTANCEDATA_TABLE, CREATE_TAB_JOBINSTANCEDATA);

		createIfNotExists(EXECUTIONINSTANCEDATA_TABLE,
				CREATE_TAB_EXECUTIONINSTANCEDATA);
		createIfNotExists(STEPEXECUTIONINSTANCEDATA_TABLE,
				CREATE_TAB_STEPEXECUTIONINSTANCEDATA);
		
		createIfNotExists(JOBSTATUS_TABLE, CREATE_TAB_JOBSTATUS);
		createIfNotExists(STEPSTATUS_TABLE, CREATE_TAB_STEPSTATUS);	
		
		logger.exiting(CLASSNAME, "checkAllTables");
	}
	
	/**
	 * Creates tableName using the createTableStatement DDL.
	 * 
	 * @param tableName
	 * @param createTableStatement
	 * @throws SQLException
	 */
	private void createIfNotExists(String tableName, String createTableStatement) throws SQLException {
		logger.entering(CLASSNAME, "createIfNotExists", new Object[] {tableName, createTableStatement});
		
		Connection conn = getConnection();
		DatabaseMetaData dbmd = conn.getMetaData();
		ResultSet rs = dbmd.getTables(null, schema, tableName, null);
		PreparedStatement ps = null;
		if(!rs.next()) {
			logger.log(Level.WARNING, tableName + " table does not exists. Trying to create it.");
			ps = conn.prepareStatement(createTableStatement);
			ps.executeUpdate();
		}
	      
	    cleanupConnection(conn, rs, ps);
	    logger.exiting(CLASSNAME, "createIfNotExists");
	}
	
	/**
	 * Executes the provided SQL statement
	 * 
	 * @param statement
	 * @throws SQLException
	 */
	private void executeStatement(String statement) throws SQLException {
		logger.entering(CLASSNAME, "executeStatement", statement);
		
		Connection conn = getConnection();
		PreparedStatement ps = null;
		
		ps = conn.prepareStatement(statement);
		ps.executeUpdate();
	      
	    cleanupConnection(conn, ps);
	    logger.exiting(CLASSNAME, "executeStatement");
	}

	
    /* (non-Javadoc)
     * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_createJobStatus(com.ibm.jbatch.container.status.JobStatusKey, com.ibm.jbatch.container.status.JobStatus)
     */
    @Override
	protected void _createJobStatus(JobStatusKey key, JobStatus value) {
    	logger.entering(CLASSNAME, "_createJobStatus", new Object[] {key, value});
    	executeInsert(key.getJobInstanceId(), value, INSERT_JOBSTATUS);
    	logger.exiting(CLASSNAME, "_createJobStatus");
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_getJobStatus(com.ibm.jbatch.container.status.JobStatusKey)
	 */
	@Override
	protected List<JobStatus> _getJobStatus(JobStatusKey key) {
		logger.entering(CLASSNAME, "_getJobStatus", key);
		List<JobStatus> jobStatuses = executeQuery(key.getJobInstanceId(), SELECT_JOBSTATUS);
		logger.exiting(CLASSNAME, "_getJobStatus", jobStatuses);
		return jobStatuses;
	}
    
    /* (non-Javadoc)
     * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_updateJobStatus(com.ibm.jbatch.container.status.JobStatusKey, com.ibm.jbatch.container.status.JobStatus)
     */
    @Override
    protected void _updateJobStatus(JobStatusKey key, JobStatus value) {
    	logger.entering(CLASSNAME, "_updateJobStatus", new Object[] {key, value});
   		List<JobStatus> data = executeQuery(key.getJobInstanceId(), SELECT_JOBSTATUS);
   		if(data != null && !data.isEmpty()) {
    		executeUpdate(value, key.getJobInstanceId(), UPDATE_JOBSTATUS);
    	}
   		logger.exiting(CLASSNAME, "_updateJobStatus");
    }

    /* (non-Javadoc)
     * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_deleteJobStatus(com.ibm.jbatch.container.status.JobStatusKey)
     */
    @Override
    protected void _deleteJobStatus(JobStatusKey key) {
    	logger.entering(CLASSNAME, "_deleteJobStatus", key);
    	executeDelete(key.getJobInstanceId(), DELETE_JOBSTATUS);
    	logger.exiting(CLASSNAME, "_deleteJobStatus");
    }

    /* (non-Javadoc)
     * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_createStepStatus(com.ibm.jbatch.container.status.StepStatusKey, com.ibm.jbatch.container.status.StepStatus)
     */
    @Override
	protected void _createStepStatus(StepStatusKey key, StepStatus value) {
    	logger.entering(CLASSNAME, "_createStepStatus", new Object[] {key, value});
        executeInsert(key.getKeyPrimitive(), value, INSERT_STEPSTATUS);
        logger.exiting(CLASSNAME, "_createStepStatus");
	}

    /* (non-Javadoc)
     * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_getStepStatus(com.ibm.jbatch.container.status.StepStatusKey)
     */
    @Override
	protected List<StepStatus> _getStepStatus(StepStatusKey key) {
    	logger.entering(CLASSNAME, "_getStepStatus", key);
    	List<StepStatus> stepStatuses = executeQuery(key.getStepId(), SELECT_STEPSTATUS); 
    	logger.exiting(CLASSNAME, "_getStepStatus", stepStatuses);
    	return stepStatuses;
	}

    /* (non-Javadoc)
     * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_updateStepStatus(com.ibm.jbatch.container.status.StepStatusKey, com.ibm.jbatch.container.status.StepStatus)
     */
    @Override
    protected void _updateStepStatus(StepStatusKey key, StepStatus value) {
    	logger.entering(CLASSNAME, "_updateStepStatus", new Object[] {key, value});
		List<StepStatus> data = executeQuery(key.getKeyPrimitive(), SELECT_STEPSTATUS);
		if(data != null && !data.isEmpty()) {
			executeUpdate(value, key.getKeyPrimitive(), UPDATE_STEPSTATUS);
		}
		logger.exiting(CLASSNAME, "_updateStepStatus");
    }

    /* (non-Javadoc)
     * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_deleteStepStatus(com.ibm.jbatch.container.status.StepStatusKey)
     */
    @Override
    protected void _deleteStepStatus(StepStatusKey key) {
    	logger.entering(CLASSNAME, "_deleteStepStatus", key);
    	executeDelete(key.getKeyPrimitive(), DELETE_STEPSTATUS);
    	logger.exiting(CLASSNAME, "_deleteStepStatus");
    }

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_createCheckpointData(com.ibm.ws.batch.container.checkpoint.CheckpointDataKey, com.ibm.ws.batch.container.checkpoint.CheckpointData)
	 */
	@Override
	protected void _createCheckpointData(CheckpointDataKey key, CheckpointData value) {
		logger.entering(CLASSNAME, "_createCheckpointData", new Object[] {key, value});
		executeInsert(key.getCommaSeparatedKey(), value, INSERT_CHECKPOINTDATA);
		logger.exiting(CLASSNAME, "_createCheckpointData");
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_getCheckpointData(com.ibm.ws.batch.container.checkpoint.CheckpointDataKey)
	 */
	@Override
	protected List<CheckpointData> _getCheckpointData(CheckpointDataKey key) {
		logger.entering(CLASSNAME, "_getCheckpointData", key);
		List<CheckpointData> checkpointData = executeQuery(key.getCommaSeparatedKey(), SELECT_CHECKPOINTDATA);
		logger.exiting(CLASSNAME, "_getCheckpointData", checkpointData);
		return checkpointData;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_updateCheckpointData(com.ibm.ws.batch.container.checkpoint.CheckpointDataKey, com.ibm.ws.batch.container.checkpoint.CheckpointData)
	 */
	@Override
	protected void _updateCheckpointData(CheckpointDataKey key, CheckpointData value) {
		logger.entering(CLASSNAME, "_updateCheckpointData", new Object[] {key, value});
		List<CheckpointData> data = executeQuery(key.getCommaSeparatedKey(), SELECT_CHECKPOINTDATA);
		if(data != null && !data.isEmpty()) {
			executeUpdate(value, key.getCommaSeparatedKey(), UPDATE_CHECKPOINTDATA);
		} else {
		    _createCheckpointData(key, value);
		}
		logger.exiting(CLASSNAME, "_updateCheckpointData");
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.impl.AbstractPersistenceManagerImpl#_deleteCheckpointData(com.ibm.ws.batch.container.checkpoint.CheckpointDataKey)
	 */
	@Override
	protected void _deleteCheckpointData(CheckpointDataKey key) {
		executeDelete(key.getJobInstanceId(), DELETE_CHECKPOINTDATA);
		logger.entering(CLASSNAME, "_deleteCheckpointData", key);
		executeDelete(key.getCommaSeparatedKey(), DELETE_CHECKPOINTDATA);
		logger.exiting(CLASSNAME, "_deleteCheckpointData");
	}

	/**
	 * @return the database connection and sets it to the default schema JBATCH or the schema defined in batch-config.
	 * 
	 * @throws SQLException
	 */
	protected Connection getConnection() throws SQLException {
		logger.entering(CLASSNAME, "getConnection");
		Connection connection = null;
		
		if(!batchConfig.isJ2seMode()) {
			logger.fine("J2EE mode, getting connection from data source");
			connection = dataSource.getConnection();
			logger.fine("autocommit="+connection.getAutoCommit());
		} else {
			try {
				Class.forName(driver);
			} catch (ClassNotFoundException e) {
				throw new PersistenceException(e);
			}
			logger.log(Level.FINE, "JSE mode, getting connection from {0}", url);
			connection = DriverManager.getConnection(url, userId, pwd);
			logger.fine("autocommit="+connection.getAutoCommit());
		}
		setSchemaOnConnection(connection);
		
        logger.exiting(CLASSNAME, "getConnection", connection);
        return connection;
	}
	
	/**
	 * @return the database connection. The schema is set to whatever default its used by the underlying database.
	 * @throws SQLException
	 */
	protected Connection getConnectionToDefaultSchema() throws SQLException {
		logger.entering(CLASSNAME, "getConnection");
		Connection connection = null;
		
		if(!batchConfig.isJ2seMode()) {
			logger.fine("J2EE mode, getting connection from data source");
			connection = dataSource.getConnection();
			logger.fine("autocommit="+connection.getAutoCommit());
		} else {
			try {
				Class.forName(driver);
			} catch (ClassNotFoundException e) {
								
				StringWriter sw = new StringWriter();
				PrintWriter pw = new PrintWriter(sw);
				e.printStackTrace(pw);
				
				logger.log(Level.SEVERE, "ClassNotFoundException: Cannot load driver class: " + driver + "; Exception stack trace: " + sw);
				
				throw new PersistenceException(e);
			}
			logger.log(Level.FINE, "JSE mode, getting connection from {0}", url);
			connection = DriverManager.getConnection(url, userId, pwd);
			logger.fine("autocommit="+connection.getAutoCommit());
		}
		logger.exiting(CLASSNAME, "getConnection", connection);
		return connection;
	}
	
	/**
	 * Set the default schema JBATCH or the schema defined in batch-config on the connection object.
	 * 
	 * @param connection
	 * @throws SQLException
	 */
	private void setSchemaOnConnection(Connection connection) throws SQLException {
		logger.entering(CLASSNAME, "setSchemaOnConnection");
		
		PreparedStatement ps = null;
		ps = connection.prepareStatement("SET SCHEMA ?");
		ps.setString(1, schema);
		ps.executeUpdate(); 
		
		ps.close();
	    
	    logger.exiting(CLASSNAME, "setSchemaOnConnection");
	}
	
	/**
	 * insert data to DB table
	 * 
	 * @param key - the IPersistenceDataKey object
	 * @param value - serializable object to store  
	 * @param query - SQL statement to execute
	 * 
	 * Ex. insert into tablename values(?, ?)
	 */
	private <T> void executeInsert(Object key, T value, String query) {
		logger.entering(CLASSNAME, "executeInsert", new Object[] {key, value, query});
		Connection conn = null;
		PreparedStatement statement = null;
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] b;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			baos = new ByteArrayOutputStream();
			oout = new ObjectOutputStream(baos);
			oout.writeObject(value);

			b = baos.toByteArray();

			statement.setObject(1, key);
			statement.setBytes(2, b);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "executeInsert");
	}
	
	private void executeJobInstanceDataInsert(long key, String jobName, String apptag, String query){
		Connection conn = null;
		PreparedStatement statement = null;
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] b;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			baos = new ByteArrayOutputStream();
			oout = new ObjectOutputStream(baos);
			oout.writeObject(jobName);

			b = baos.toByteArray();

			statement.setLong(1, key);
			statement.setString(2, jobName);
			statement.setString(3, apptag);
			
			statement.executeUpdate();
			
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, null, statement);
		}
	}
	
	private void executeExecutionDataInsert(long key, Timestamp createtime, Timestamp starttime, Timestamp endtime, 
											Timestamp updatetime, Properties parms, long instanceID, String batchstatus, String exitstatus, String query) {
		Connection conn = null;
		PreparedStatement statement = null;
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] b;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			baos = new ByteArrayOutputStream();
			oout = new ObjectOutputStream(baos);
			oout.writeObject(parms);

			b = baos.toByteArray();

			statement.setLong(1, key);
			statement.setTimestamp(2, createtime);
			statement.setTimestamp(3,starttime);
			statement.setTimestamp(4, endtime);
			statement.setTimestamp(5, updatetime);
			statement.setBytes(6, b);
			statement.setLong(7, instanceID);
			statement.setString(8, batchstatus);
			statement.setString(9, exitstatus);
			
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, null, statement);
		}
	}
	
	/**
	 * update data in DB table
	 * 
	 * @param value - serializable object to store 
	 * @param key - the IPersistenceDataKey object
	 * @param query - SQL statement to execute. 
	 * 
	 * Ex. update tablename set obj = ? where id = ?
	 */
	private <T> void executeUpdate(T value, Object key, String query) {
		logger.entering(CLASSNAME, "executeUpdate", new Object[] {key, value, query});
		Connection conn = null;
		PreparedStatement statement = null;
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] b;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			baos = new ByteArrayOutputStream();
			oout = new ObjectOutputStream(baos);
			oout.writeObject(value);

			b = baos.toByteArray();

			statement.setBytes(1, b);
			statement.setObject(2, key);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "executeUpdate");
	}
	
	public void jobExecutionTimestampUpdate(long key, String timestampToUpdate, Timestamp ts) {
		Connection conn = null;
		PreparedStatement statement = null;

		try {
			conn = getConnection();
			statement = conn.prepareStatement("update executioninstancedata set " + timestampToUpdate + " = ? where jobexecid = ?");
			
			statement.setTimestamp(1, ts);	
			statement.setObject(2, key);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, null, statement);
		}
	}
	
	public void jobExecutionStatusStringUpdate(long key, String statusToUpdate, String statusString, Timestamp updatets) {
		Connection conn = null;
		PreparedStatement statement = null;
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] b;

		try {
			conn = getConnection();
			statement = conn.prepareStatement("update executioninstancedata set " + statusToUpdate + " = ?, updatetime = ? where jobexecid = ?");
			

			statement.setString(1, statusString);
			statement.setTimestamp(2, updatets);
			statement.setLong(3, key);
			statement.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, null, statement);
		}
	}
	
	private void executeExecutionDataUpdate(long key, Timestamp createtime,
			Timestamp starttime, Timestamp endtime, Timestamp updatetime,
			Properties parms, long instanceID, String status, String query) {
		Connection conn = null;
		PreparedStatement statement = null;
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] b;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			baos = new ByteArrayOutputStream();
			oout = new ObjectOutputStream(baos);
			oout.writeObject(parms);

			b = baos.toByteArray();

			statement.setObject(1, key);
			statement.setTimestamp(2, createtime);
			statement.setTimestamp(3, starttime);
			statement.setTimestamp(4, endtime);
			statement.setTimestamp(5, updatetime);
			statement.setBytes(6, b);

			statement.setString(7, status);

			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, null, statement);
		}
	}
	
	/**
	 * delete data from DB table
	 * 
	 * @param key - the IPersistenceDataKey object
	 * @param query - SQL statement to execute.
	 * 
	 * Ex. delete from tablename where id = ?
	 */
	private void executeDelete(Object key, String query) {
		logger.entering(CLASSNAME, "executeDelete", new Object[] {key, query});
		Connection conn = null;
		PreparedStatement statement = null;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			statement.setObject(1, key);
			statement.executeUpdate();

		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {

			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "executeDelete");
	}
	
	/**
	 * select data from DB table
	 * 
	 * @param key - the IPersistenceDataKey object
	 * @param query - SQL statement to execute.
	 * @return List of serializable objects store in the DB table
	 * 
	 * Ex. select id, obj from tablename where id = ?
	 */
	private <T> List<T> executeQuery(Object key, String query) {
		logger.entering(CLASSNAME, "executeQuery", new Object[] {key, query});
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		List<T> data = new ArrayList<T>();
		ObjectInputStream objectIn = null;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			statement.setObject(1, key);
			rs = statement.executeQuery();
			while (rs.next()) {
				byte[] buf = rs.getBytes("obj");
				if (buf != null) {
					objectIn = new ObjectInputStream(new ByteArrayInputStream(buf));
					T deSerializedObject = (T) objectIn.readObject();
					data.add(deSerializedObject);
				}
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			if (objectIn != null) {
				try {
					objectIn.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, rs, statement);
		}
		logger.exiting(CLASSNAME, "executeQuery");
		return data;	
	}
	
	private Set<String> executeNameQuery(String query) {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		Set<String> data = new HashSet<String>();
	
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			rs = statement.executeQuery();
			while (rs.next()) {
				String name = rs.getString("name");
				data.add(name);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		}
		finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return data;	
	}



	/**
	 * closes connection, result set and statement
	 * 
	 * @param conn - connection object to close
	 * @param rs - result set object to close
	 * @param statement - statement object to close
	 */
	private void cleanupConnection(Connection conn, ResultSet rs, PreparedStatement statement) {

		logger.entering(CLASSNAME, "cleanupConnection", new Object[] {conn, rs, statement});
		
		if (statement != null) {
			try {
				statement.close();
			} catch (SQLException e) {
				throw new PersistenceException(e);
			}
		}

		if (rs != null) {
			try {
				rs.close();
			} catch (SQLException e) {
				throw new PersistenceException(e);
			}
		}
		
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				throw new PersistenceException(e);
			} finally {
				try {
					conn.close();
				} catch (SQLException e) {
					throw new PersistenceException(e);
				}
			}
		}
		logger.exiting(CLASSNAME, "cleanupConnection");
	}
	
	/**
	 * closes connection and statement
	 * 
	 * @param conn - connection object to close
	 * @param statement - statement object to close
	 */
	private void cleanupConnection(Connection conn, PreparedStatement statement) {

		logger.entering(CLASSNAME, "cleanupConnection", new Object[] {conn,statement});
		
		if (statement != null) {
			try {
				statement.close();
			} catch (SQLException e) {
				throw new PersistenceException(e);
			}
		}
		
		if (conn != null) {
			try {
				conn.close();
			} catch (SQLException e) {
				throw new PersistenceException(e);
			} finally {
				try {
					conn.close();
				} catch (SQLException e) {
					throw new PersistenceException(e);
				}
			}
		}
		logger.exiting(CLASSNAME, "cleanupConnection");
	}


	/**
	 * JOB OPERATOR METHODS
	 */
	
	@Override
    public void jobOperatorCreateJobInstanceData(long key, String Value, String apptag){
		this.executeJobInstanceDataInsert(key, Value, apptag, this.INSERT_JOBINSTANCEDATA);
	}

	
	@Override
	public int jobOperatorGetJobInstanceCount(String jobName) {
		/*
		List<Integer> data = executeQuery(jobName, SELECT_JOBINSTANCEDATA_COUNT);
		if(data != null && !data.isEmpty()) {
			return data.get(0);
		}
		else return 0;
		*/
		
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		String status = null;
		int count;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement(SELECT_JOBINSTANCEDATA_COUNT);
			statement.setString(1, jobName);
			rs = statement.executeQuery();
			rs.next();
			count = rs.getInt("jobinstancecount");
			
		} catch (SQLException e) {
			throw new PersistenceException(e);
	}
		finally {
			cleanupConnection(conn, rs, statement);
		}
		return count;
	}


	@Override
	public List<Long> jobOperatorgetJobInstanceIds(String jobName, int start,
			int count) {

		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		List<Long> data = new ArrayList<Long>();
	
		try {
			conn = getConnection();
			statement = conn.prepareStatement(SELECT_JOBINSTANCEDATA_IDS);
			statement.setObject(1, jobName);
			rs = statement.executeQuery();
			while (rs.next()) {
				long id = rs.getLong("jobinstanceid");
				data.add(id);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		}
		finally {
			cleanupConnection(conn, rs, statement);
		}
	
		if (data.size() > 0){
			try {
				return data.subList(start, start+count);
			}
			catch (IndexOutOfBoundsException oobEx){
				return data;
			}
		}
		else return data;
	}
	
	@Override
	public HashMap jobOperatorGetJobInstanceData() {

		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		HashMap data = new HashMap();
	
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select distinct jobinstanceid, name from jobinstancedata");
			rs = statement.executeQuery();
			while (rs.next()) {
				long id = rs.getLong("jobinstanceid");
				String name = rs.getString("name");
				data.put(id, name);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		}
		finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return data;
	}


	@Override
	public Set<String> jobOperatorgetJobNames() {
		
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		Set<String> data = new HashSet<String>();
		try {
			conn = getConnection();
			statement = conn.prepareStatement(SELECT_JOBINSTANCEDATA_NAMES);
			rs = statement.executeQuery();
			while (rs.next()) {
				String name = rs.getString("name");
				data.add(name);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		}
		finally {
			cleanupConnection(conn, rs, statement);
		}
		return data;
	}


	@Override
	public void jobOperatorCreateExecutionData(long key,
			Timestamp createTime, Timestamp starttime, Timestamp endtime, Timestamp updateTime, Properties parms,
			long instanceID, String batchstatus, String exitstatus) {
		
		executeExecutionDataInsert(key, createTime, starttime, endtime, updateTime, parms, instanceID, batchstatus, exitstatus, INSERT_EXECUTIONDATA);
		
	}
	
	public Timestamp jobOperatorQueryJobExecutionTimestamp(long key, String requestedTimestamp){
		
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		Timestamp timestamp = null;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select " + requestedTimestamp + " from executioninstancedata where jobexecid = ?");
			statement.setObject(1, key);
			rs = statement.executeQuery();
			while (rs.next()) {
				timestamp = rs.getTimestamp(requestedTimestamp);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		}
		finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return timestamp;
	}
	
	// Get Batch or Exit Status
	public String jobOperatorQueryJobExecutionStatus(long key, String requestedStatus){
		
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		String status = null;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select " + requestedStatus + " from executioninstancedata where jobexecid = ?");
			statement.setLong(1, key);
			rs = statement.executeQuery();
			while (rs.next()) {
				status = rs.getString(requestedStatus);
				}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return status;
	}
	
	public long jobOperatorQueryJobExecutionJobInstanceId(long executionID){
		
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		long jobinstanceID = 0;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select jobinstanceid from executioninstancedata where jobexecid = ?");
			statement.setLong(1, executionID);
			rs = statement.executeQuery();
			while (rs.next()) {
				jobinstanceID = rs.getLong("jobinstanceid");
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		}
		finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return jobinstanceID;
	}

	@Override
	public List<IJobExecution> jobOperatorGetJobExecutionsByJobInstanceID(long jobInstanceID){
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		List<IJobExecution> data = new ArrayList<IJobExecution>();
		JobOperatorJobExecutionImpl jobExecutionImpl;
		ObjectInputStream objectIn = null;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select * from executioninstancedata where jobinstanceid = ?");
			statement.setLong(1, jobInstanceID);
			rs = statement.executeQuery();
			
			while (rs.next()) {
				jobExecutionImpl = new JobOperatorJobExecutionImpl(rs.getLong("id"), rs.getLong("jobinstanceid"), null);
				jobExecutionImpl.setCreateTime(rs.getTimestamp("createtime"));
				jobExecutionImpl.setStartTime(rs.getTimestamp("starttime"));
				jobExecutionImpl.setLastUpdateTime(rs.getTimestamp("updatetime"));
				jobExecutionImpl.setEndTime(rs.getTimestamp("endtime"));
				jobExecutionImpl.setBatchStatus(rs.getString("batchstatus"));
				jobExecutionImpl.setExitStatus(rs.getString("exitstatus"));
				
				// get the object based data
				Properties props = null;
				byte[] buf = rs.getBytes("parameters");
				if (buf != null) {
					objectIn = new ObjectInputStream(new ByteArrayInputStream(buf));
					props = (Properties) objectIn.readObject();
				}
				
				jobExecutionImpl.setJobParameters(props);
				
				data.add(jobExecutionImpl);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			if (objectIn != null) {
				try {
					objectIn.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, rs, statement);
		}
		
		return data;

	}

	@Override
	public Properties getParameters(long instanceId){
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		Properties props = null;
		ObjectInputStream objectIn = null;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select parameters from executioninstancedata where jobinstanceid = ?"); 
			statement.setLong(1, instanceId);
			rs = statement.executeQuery();
			while (rs.next()) {
				
				// get the object based data
				byte[] buf = rs.getBytes("parameters");
				if (buf != null) {
					objectIn = new ObjectInputStream(new ByteArrayInputStream(buf));
					props = (Properties) objectIn.readObject();
				}
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			if (objectIn != null) {
				try {
					objectIn.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, rs, statement);
		}
		
		return props;
		
	}


	@Override
	public void stepExecutionCreateStepExecutionData(String stepExecutionKey,
			long jobExecutionID, StepContextImpl stepContext, List<String> stepContainment) {

		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		long stepExecutionID = stepContext.getStepExecutionId();
		String batchStatus = stepContext.getBatchStatus().name();
		String exitStatus = stepContext.getExitStatus();
		String stepName = stepContext.getStepName();
		String stepContainmentCSV = StepExecutionImpl.getStepContainmentCSV(stepContainment);
		Object persistentData = stepContext.getPersistentUserData();
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] pdataBytes;
		
    	long readCnt = 0;
    	long writeCnt = 0;
    	long processSkipCnt = 0;
    	long commitCnt = 0;
    	long rollbackCnt = 0;
    	long readSkipCnt = 0;
    	long filterCnt = 0;
    	long writeSkipCnt = 0;
    	
    	Timestamp startTimeTS = stepContext.getStartTimeTS();
    	Timestamp endTimeTS = stepContext.getEndTimeTS();
    	
		Metric[] metrics = stepContext.getMetrics();
		for (int i = 0; i < metrics.length; i++) {
			
			if (metrics[i].getType().equals(MetricImpl.MetricType.READ_COUNT)) {
				readCnt = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.WRITE_COUNT)) {
				writeCnt = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.PROCESS_SKIP_COUNT)) {
				processSkipCnt = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.COMMIT_COUNT)) {
				commitCnt = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.ROLLBACK_COUNT)) {
				rollbackCnt = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.READ_SKIP_COUNT)) {
				readSkipCnt = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.FILTER_COUNT)) {
				filterCnt = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.WRITE_SKIPCOUNT)) {
				writeSkipCnt = metrics[i].getValue();	
			}
		
		}
		
				
		try {
			conn = getConnection();
			statement = conn.prepareStatement("insert into stepexecutionInstanceData values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			statement.setString(1, stepExecutionKey);
			statement.setLong(2, jobExecutionID);
			statement.setLong(3, stepExecutionID);
			statement.setString(4, batchStatus);
			statement.setString(5, exitStatus);
			statement.setString(6, stepName);
			statement.setString(7, stepContainmentCSV);
			statement.setLong(8, readCnt);
			statement.setLong(9, writeCnt);
			statement.setLong(10, commitCnt);
			statement.setLong(11, rollbackCnt);
			statement.setLong(12, readSkipCnt);
			statement.setLong(13, processSkipCnt);
			statement.setLong(14, filterCnt);
			statement.setLong(15, writeSkipCnt);
            statement.setTimestamp(16, startTimeTS);
            statement.setTimestamp(17, endTimeTS);
            baos = new ByteArrayOutputStream();
			oout = new ObjectOutputStream(baos);
			oout.writeObject(persistentData);

			pdataBytes = baos.toByteArray();
			
			statement.setObject(18,pdataBytes);
            
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, rs, statement);
		}		
	}
	
	@Override
	public List<StepExecution<?>> getStepExecutionIDListQueryByJobID(long execid){
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		
		
		long jobexecid = 0;
		long stepexecid = 0;
		String stepname = null;
		String stepContainmentCSV = null;
		String batchstatus = null;
		String exitstatus = null;
		Exception ex = null;
		long readCount =0;
		long writeCount = 0;
		long commitCount = 0;
		long rollbackCount = 0;
		long readSkipCount = 0;
		long processSkipCount = 0;
		long filterCount = 0;
		long writeSkipCount = 0;
		Timestamp startTS = null;
		Timestamp endTS = null;
		StepExecutionImpl stepEx = null;
		ObjectInputStream objectIn = null;
		
		List<StepExecution<?>> data = new ArrayList<StepExecution<?>>();
		
	
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select * from stepexecutioninstancedata where jobexecid = ?"); 
			statement.setLong(1, execid);
			rs = statement.executeQuery();
			while (rs.next()) {
				jobexecid = rs.getLong("jobexecid");
				stepexecid = rs.getLong("stepexecid");
				stepname = rs.getString("stepname");
				stepContainmentCSV = rs.getString("stepcontainmentcsv");
				batchstatus = rs.getString("batchstatus");
				exitstatus = rs.getString("exitstatus");
				readCount = rs.getLong("readcount");
				writeCount = rs.getLong("writecount");
				commitCount = rs.getLong("commitcount");
				rollbackCount = rs.getLong("rollbackcount");
				readSkipCount = rs.getLong("readskipcount");
				processSkipCount = rs.getLong("processskipcount");
				filterCount = rs.getLong("filtercount");
				writeSkipCount = rs.getLong("writeSkipCount");
				startTS = rs.getTimestamp("startTime");
				endTS = rs.getTimestamp("endTime");
				// get the object based data
				Serializable persistentData = null;
				byte[] pDataBytes = rs.getBytes("persistentData");
				if (pDataBytes != null) {
					objectIn = new TCCLObjectInputStream(new ByteArrayInputStream(pDataBytes));
					persistentData = (Serializable)objectIn.readObject();
				}
				
				stepEx = new StepExecutionImpl(jobexecid, stepexecid);
				
				stepEx.setBatchStatus(BatchStatus.valueOf(batchstatus));
				stepEx.setExitStatus(exitstatus);
				stepEx.setStepName(stepname);
				stepEx.setStepContainment(StepExecutionImpl.split(stepContainmentCSV));
				stepEx.setReadCount(readCount);
				stepEx.setWriteCount(writeCount);
				stepEx.setCommitCount(commitCount);
				stepEx.setRollbackCount(rollbackCount);
				stepEx.setReadSkipCount(readSkipCount);
				stepEx.setProcessSkipCount(processSkipCount);
				stepEx.setFilterCount(filterCount);
				stepEx.setWriteSkipCount(writeSkipCount);
				stepEx.setStartTime(startTS);
				stepEx.setEndTime(endTS);	
				stepEx.setpersistentUserData(persistentData);
				
				data.add(stepEx);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return data;
	}
	
	@Override
	public StepExecution getStepExecutionObjQueryByStepID(long stepExecutionId){
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		long jobexecid = 0;
		long stepexecid = 0;
		String stepname = null;
		String stepContainmentCSV = null;
		String batchstatus = null;
		String exitstatus = null;
		Exception ex = null;
		long readCount =0;
		long writeCount = 0;
		long commitCount = 0;
		long rollbackCount = 0;
		long readSkipCount = 0;
		long processSkipCount = 0;
		long filterCount = 0;
		long writeSkipCount = 0;
		Timestamp startTS = null;
		Timestamp endTS = null;
		StepExecutionImpl stepEx = null;
		ObjectInputStream objectIn = null;
	
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select * from stepexecutioninstancedata where stepexecid = ?"); 
			statement.setLong(1, stepExecutionId);
			rs = statement.executeQuery();
			while (rs.next()) {
				jobexecid = rs.getLong("jobexecid");
				stepexecid = rs.getLong("stepexecid");
				stepname = rs.getString("stepname");
				stepContainmentCSV = rs.getString("stepcontainmentcsv");
				batchstatus = rs.getString("batchstatus");
				exitstatus = rs.getString("exitstatus");
				readCount = rs.getLong("readcount");
				writeCount = rs.getLong("writecount");
				commitCount = rs.getLong("commitcount");
				rollbackCount = rs.getLong("rollbackcount");
				readSkipCount = rs.getLong("readskipcount");
				processSkipCount = rs.getLong("processskipcount");
				filterCount = rs.getLong("filtercount");
				writeSkipCount = rs.getLong("writeSkipCount");
				startTS = rs.getTimestamp("startTime");
				endTS = rs.getTimestamp("endTime");
				// get the object based data
				byte[] pDataBytes = rs.getBytes("persistentData");
				Serializable persistentDataObj = null;
				if (pDataBytes != null) {
					objectIn = new TCCLObjectInputStream(new ByteArrayInputStream(pDataBytes));
					persistentDataObj = (Serializable)objectIn.readObject();
				}
				
				stepEx = new StepExecutionImpl(jobexecid, stepexecid);
				
				stepEx.setBatchStatus(BatchStatus.valueOf(batchstatus));
				stepEx.setExitStatus(exitstatus);
				stepEx.setStepName(stepname);
				stepEx.setStepContainment(StepExecutionImpl.split(stepContainmentCSV));
				stepEx.setReadCount(readCount);
				stepEx.setWriteCount(writeCount);
				stepEx.setCommitCount(commitCount);
				stepEx.setRollbackCount(rollbackCount);
				stepEx.setReadSkipCount(readSkipCount);
				stepEx.setProcessSkipCount(processSkipCount);
				stepEx.setFilterCount(filterCount);
				stepEx.setWriteSkipCount(writeSkipCount);
				stepEx.setStartTime(startTS);
				stepEx.setEndTime(endTS);	
				stepEx.setpersistentUserData(persistentDataObj);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			if (objectIn != null) {
				try {
					objectIn.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, rs, statement);
		}
		
		return stepEx;
	}
	
	/*
	@Override
	public long stepExecutionQueryID(long stepExecutionId){
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		long id = 0;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select stepexecutionid from stepexecutioninstancedata where id = " + key); 
			statement.setLong(1, stepExecutionId);
			rs = statement.executeQuery();
			while (rs.next()) {
				id = rs.getLong(idtype);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		}
		finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return id;
	}
	*/
	
	public void jobOperatorUpdateBatchStatusWithUPDATETSonly(long key, String statusToUpdate, String statusString, Timestamp updatets) {
		Connection conn = null;
		PreparedStatement statement = null;
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] b;

		try {
			conn = getConnection();
			statement = conn.prepareStatement("update executioninstancedata set " + statusToUpdate + " = ?, updatetime = ?, createtime = ? where jobexecid = ?");
			
			statement.setString(1, statusString);
			statement.setTimestamp(2, updatets);
			statement.setTimestamp(3, updatets);
			statement.setLong(4, key);
			
			statement.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, null, statement);
		}
	}
	
	public void jobOperatorUpdateBatchStatusWithSTATUSandUPDATETSonly(long key, String statusToUpdate, String statusString, Timestamp updatets){
		Connection conn = null;
		PreparedStatement statement = null;
		ByteArrayOutputStream baos = null;
		ObjectOutputStream oout = null;
		byte[] b;

		try {
			conn = getConnection();
			statement = conn.prepareStatement("update executioninstancedata set " + statusToUpdate + " = ?, starttime = ?, updatetime = ? where jobexecid = ?");
			
			statement.setString(1, statusString);
			statement.setTimestamp(2, updatets);
			statement.setTimestamp(3, updatets);
			statement.setLong(4, key);
			
			statement.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
			throw new PersistenceException(e);
		} finally {
			if (baos != null) {
				try {
					baos.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			if (oout != null) {
				try {
					oout.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, null, statement);
		}
		
	}


	@Override
	public IJobExecution jobOperatorGetJobExecution(long jobExecutionId) {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		Timestamp createtime = null;
		Timestamp starttime = null;
		Timestamp endtime = null;
		Timestamp updatetime = null;
		long instanceId = 0;
		String batchStatus = null;
		String exitStatus = null;
		JobOperatorJobExecutionImpl jobEx = null;
		ObjectInputStream objectIn = null;
	
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select createtime, starttime, endtime, updatetime, parameters, jobinstanceid, batchstatus, exitstatus from executioninstancedata where jobexecid = ?"); 
			statement.setLong(1, jobExecutionId);
			rs = statement.executeQuery();
			while (rs.next()) {
				createtime = rs.getTimestamp("createtime");
				starttime = rs.getTimestamp("starttime");
				endtime = rs.getTimestamp("endtime");
				updatetime = rs.getTimestamp("updatetime");
				instanceId = rs.getLong("jobinstanceid");
				
				// get the object based data
				batchStatus = rs.getString("batchstatus");
				exitStatus = rs.getString("exitstatus");
				
				// get the object based data
				Properties params = null;
				byte[] buf = rs.getBytes("parameters");
				if (buf != null) {
					objectIn = new ObjectInputStream(new ByteArrayInputStream(buf));
					params = (Properties) objectIn.readObject();
				}
				jobEx = new JobOperatorJobExecutionImpl(jobExecutionId, instanceId, null);
				jobEx.setCreateTime(createtime);
				jobEx.setStartTime(starttime);
				jobEx.setEndTime(endtime);
				jobEx.setJobParameters(params);
				jobEx.setLastUpdateTime(updatetime);
				jobEx.setBatchStatus(batchStatus);
				jobEx.setExitStatus(exitStatus);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			if (objectIn != null) {
				try {
					objectIn.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, rs, statement);
		}
		return jobEx;
	}
	
	@Override
	public List<IJobExecution> jobOperatorGetJobExecutions(long jobInstanceId) {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		Timestamp createtime = null;
		Timestamp starttime = null;
		Timestamp endtime = null;
		Timestamp updatetime = null;
		long jobExecutionId = 0;
		long instanceId = 0;
		String batchStatus = null;
		String exitStatus = null;
		List<IJobExecution> data = new ArrayList<IJobExecution>();
		JobOperatorJobExecutionImpl jobEx = null;
		ObjectInputStream objectIn = null;
	
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select jobexecid, createtime, starttime, endtime, updatetime, parameters, batchstatus, exitstatus  from executioninstancedata where jobinstanceid = ?"); 
			statement.setLong(1, jobInstanceId);
			rs = statement.executeQuery();
			while (rs.next()) {
				jobExecutionId = rs.getLong("jobexecid");
				createtime = rs.getTimestamp("createtime");
				starttime = rs.getTimestamp("starttime");
				endtime = rs.getTimestamp("endtime");
				updatetime = rs.getTimestamp("updatetime");
				
				// get the object based data
				batchStatus = rs.getString("batchstatus");
				exitStatus = rs.getString("exitstatus");
				
				// get the object based data
				byte[] buf = rs.getBytes("parameters");
				Properties params = null;
				if (buf != null) {
					objectIn = new ObjectInputStream(new ByteArrayInputStream(buf));
					params = (Properties) objectIn.readObject();
				}
				
				jobEx = new JobOperatorJobExecutionImpl(jobExecutionId, instanceId, null);
				jobEx.setCreateTime(createtime);
				jobEx.setStartTime(starttime);
				jobEx.setEndTime(endtime);
				jobEx.setLastUpdateTime(updatetime);
				jobEx.setBatchStatus(batchStatus);
				jobEx.setExitStatus(exitStatus);
				
				data.add(jobEx);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			if (objectIn != null) {
				try {
					objectIn.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, rs, statement);
		}
		return data;
	}
	
	@Override
	public Set<Long> jobOperatorGetRunningExecutions(String jobName){
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		Set<Long> executionIds = new HashSet<Long>();
	
		try {
			conn = getConnection();
			statement = conn.prepareStatement("SELECT A.jobexecid FROM executioninstancedata AS A INNER JOIN jobinstancedata AS B ON A.jobinstanceid = B.jobinstanceid WHERE A.batchstatus = ? AND B.name = ?"); 
			statement.setString(1, BatchStatus.STARTED.name());
			statement.setString(2, jobName);
			rs = statement.executeQuery();
			while (rs.next()) {
				executionIds.add(rs.getLong("jobexecid"));
			}

		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
		return executionIds;		
	}
	
	@Override
	public String getJobCurrentTag(long jobInstanceId) {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		String apptag = null;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement(SELECT_JOBINSTANCEDATA_APPTAG); 
			statement.setLong(1, jobInstanceId);
			rs = statement.executeQuery();
			while (rs.next()) {
				apptag = rs.getString(APPTAG);
			}

		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return apptag;
	}
	
	@Override
	public void purge(String apptag) {
		
		logger.entering(CLASSNAME, "purge", apptag);
		String deleteJobs = "DELETE FROM jobinstancedata WHERE apptag = ?";
		String deleteJobExecutions = "DELETE FROM executioninstancedata " 
				+ "WHERE jobexecid IN (" 
				+ "SELECT B.jobexecid FROM jobinstancedata AS A INNER JOIN executioninstancedata AS B " 
				+ "ON A.jobinstanceid = B.jobinstanceid " 
				+ "WHERE A.apptag = ?)";
		String deleteStepExecutions = "DELETE FROM stepexecutioninstancedata " 
				+ "WHERE stepexecid IN ("
				+ "SELECT C.stepexecid FROM jobinstancedata AS A INNER JOIN executioninstancedata AS B "
				+ "ON A.jobinstanceid = B.jobinstanceid INNER JOIN stepexecutioninstancedata AS C "
				+ "ON B.jobexecid = C.jobexecid "
				+ "WHERE A.apptag = ?)";
		
		Connection conn = null;
		PreparedStatement statement = null;
		try {
			conn = getConnection();
			statement = conn.prepareStatement(deleteStepExecutions);
			statement.setString(1, apptag);
			statement.executeUpdate();
			
			statement = conn.prepareStatement(deleteJobExecutions);
			statement.setString(1, apptag);
			statement.executeUpdate();
			
			statement = conn.prepareStatement(deleteJobs);
			statement.setString(1, apptag);
			statement.executeUpdate();

		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {

			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "purge");
		
	}

	@Override
	public List<JobStatus> getJobStatusFromExecution(long executionId) {
		
		long instanceId = 0;
		
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		List<JobStatus> data = new ArrayList<JobStatus>();
		ObjectInputStream objectIn = null;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select jobinstanceid from executioninstancedata where jobexecid = ?");
			statement.setLong(1, executionId);
			rs = statement.executeQuery();
			while (rs.next()) {
				instanceId = rs.getLong("jobinstanceid");
			}
			statement = conn.prepareStatement("select obj from jobstatus where id = ?");
			statement.setLong(1, instanceId);
			rs = statement.executeQuery();
			while (rs.next()){
				byte[] buf = rs.getBytes("obj");
				if (buf != null) {
					objectIn = new ObjectInputStream(new ByteArrayInputStream(buf));
				}
				JobStatus deSerializedObject = (JobStatus) objectIn.readObject();
				data.add(deSerializedObject);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			if (objectIn != null) {
				try {
					objectIn.close();
				} catch (IOException e) {
					throw new PersistenceException(e);
				}
			}
			cleanupConnection(conn, rs, statement);
		}
		logger.exiting(CLASSNAME, "executeQuery");
		return data;	
	}
	
	public long getJobInstanceIdByExecutionId(long executionId){
		long instanceId= 0;
		
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("select jobinstanceid from executioninstancedata where jobexecid = ?");
			statement.setObject(1, executionId);
			rs = statement.executeQuery();
			while (rs.next()) {
				instanceId = rs.getLong("jobinstanceid");
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
		
		return instanceId;
	}

	/**
	 * This method is used to serialized an object saved into a table BLOB field.
	 *  
	 * @param theObject the object to be serialized
	 * @return a object byte array
	 * @throws IOException
	 */
	private byte[] serializeObject(Serializable theObject) throws IOException {

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ObjectOutputStream oout = new ObjectOutputStream(baos);
		oout.writeObject(theObject);
		byte[] data = baos.toByteArray();
		baos.close();
		oout.close();

		return data;
	}

	/**
	 * This method is used to de-serialized a table BLOB field to its original object form.
	 * 
	 * @param buffer the byte array save a BLOB
	 * @return the object saved as byte array
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	private Serializable deserializeObject(byte[] buffer) throws IOException, ClassNotFoundException {

		Serializable theObject = null;
		ObjectInputStream objectIn = null;

		if (buffer != null) {
			objectIn = new ObjectInputStream(new ByteArrayInputStream(buffer));
			theObject = (Serializable)objectIn.readObject();
			objectIn.close();
		}
		return theObject;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#createJobInstance(java.lang.String, java.lang.String, java.lang.String, java.util.Properties)
	 */
	@Override
	public JobInstance createJobInstance(String name, String apptag, String jobXml, Properties jobParameters) {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		JobInstanceImpl jobInstance = null;
		
		try {
			conn = getConnection();
			statement = conn.prepareStatement("INSERT INTO jobinstancedata (name, apptag) VALUES(?, ?)", Statement.RETURN_GENERATED_KEYS);
			statement.setString(1, name);
			statement.setString(2, apptag);
			statement.executeUpdate();
			rs = statement.getGeneratedKeys();
			if(rs.next()) {
				long jobInstanceID = rs.getLong(1);
				jobInstance = new JobInstanceImpl(jobXml, jobParameters, jobInstanceID);
				jobInstance.setJobName(name);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
		return jobInstance;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#createJobExecution(com.ibm.jbatch.container.jsl.Navigator, javax.batch.runtime.JobInstance, java.util.Properties, com.ibm.jbatch.container.context.impl.JobContextImpl)
	 */
	@Override
	public RuntimeJobExecutionHelper createJobExecution(Navigator jobNavigator, JobInstance jobInstance, Properties jobParameters, JobContextImpl jobContext) {
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		RuntimeJobExecutionHelper jobExecution = null;
		try {
			Timestamp now = new Timestamp(System.currentTimeMillis());
			conn = getConnection();
			statement = conn.prepareStatement("INSERT INTO executioninstancedata (jobinstanceid, createtime, updatetime, batchstatus, parameters) VALUES(?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, jobInstance.getInstanceId());
			statement.setTimestamp(2, now);
			statement.setTimestamp(3, now);
			statement.setString(4, jobContext.getBatchStatus().name());
			statement.setObject(5, serializeObject(jobParameters));
			statement.executeUpdate();
			rs = statement.getGeneratedKeys();
			if(rs.next()) {
				long jobExecutionId = rs.getLong(1);
				jobExecution = new RuntimeJobExecutionHelper(jobNavigator, jobInstance, jobExecutionId, jobContext);
				jobExecution.setBatchStatus(jobContext.getBatchStatus().name());
				jobExecution.setCreateTime(now);
				jobExecution.setLastUpdateTime(now);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
		return jobExecution;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#createStepExecution(long, com.ibm.jbatch.container.context.impl.StepContextImpl)
	 */
	@Override
	public StepExecutionImpl createStepExecution(long jobExecId, StepContextImpl stepContext, List<String> containment) {
		StepExecutionImpl stepExecution = null;
		String batchStatus = stepContext.getBatchStatus() == null ? BatchStatus.STARTING.name() : stepContext.getBatchStatus().name();
		String exitStatus = stepContext.getExitStatus();
		String stepName = stepContext.getStepName();
		String stepContainmentCSV = StepExecutionImpl.getStepContainmentCSV(containment);
		
    	long readCount = 0;
    	long writeCount = 0;
    	long commitCount = 0;
    	long rollbackCount = 0;
    	long readSkipCount = 0;
    	long processSkipCount = 0;
    	long filterCount = 0;
    	long writeSkipCount = 0;
    	Timestamp startTime = stepContext.getStartTimeTS();
    	Timestamp endTime = stepContext.getEndTimeTS();
    	
		Metric[] metrics = stepContext.getMetrics();
		for (int i = 0; i < metrics.length; i++) {
			if (metrics[i].getType().equals(MetricImpl.MetricType.READ_COUNT)) {
				readCount = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.WRITE_COUNT)) {
				writeCount = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.PROCESS_SKIP_COUNT)) {
				processSkipCount = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.COMMIT_COUNT)) {
				commitCount = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.ROLLBACK_COUNT)) {
				rollbackCount = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.READ_SKIP_COUNT)) {
				readSkipCount = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.FILTER_COUNT)) {
				filterCount = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.WRITE_SKIPCOUNT)) {
				writeSkipCount = metrics[i].getValue();	
			}		
		}
		Serializable persistentData = stepContext.getPersistentUserData();

		stepExecution = createStepExecution(jobExecId, batchStatus, exitStatus, stepName, stepContainmentCSV, readCount, 
			writeCount, commitCount, rollbackCount, readSkipCount, processSkipCount, filterCount, writeSkipCount, startTime,
			endTime, persistentData);

		return stepExecution;
	}


	private StepExecutionImpl createStepExecution(long jobExecId,  String batchStatus, String exitStatus, String stepName, String stepContainmentCSV, long readCount, 
		long writeCount, long commitCount, long rollbackCount, long readSkipCount, long processSkipCount, long filterCount,
		long writeSkipCount, Timestamp startTime, Timestamp endTime, Serializable persistentData) {

		logger.entering(CLASSNAME, "createStepExecution", new Object[] {jobExecId, batchStatus, exitStatus, stepName, readCount, 
			writeCount, commitCount, rollbackCount, readSkipCount, processSkipCount, filterCount, writeSkipCount, startTime,
			endTime, persistentData});

		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		StepExecutionImpl stepExecution = null;
		String query = "INSERT INTO stepexecutioninstancedata (jobexecid, batchstatus, exitstatus, stepname, stepcontainmentcsv, readcount," 
			+ "writecount, commitcount, rollbackcount, readskipcount, processskipcount, filtercount, writeskipcount, starttime,"
			+ "endtime, persistentdata) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try {
			conn = getConnection();
			statement = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
			statement.setLong(1, jobExecId);
			statement.setString(2, batchStatus);
			statement.setString(3, exitStatus);
			statement.setString(4, stepName);
			statement.setString(5, stepContainmentCSV);
			statement.setLong(6, readCount);
			statement.setLong(7, writeCount);
			statement.setLong(8, commitCount);
			statement.setLong(9, rollbackCount);
			statement.setLong(10, readSkipCount);
			statement.setLong(11, processSkipCount);
			statement.setLong(12, filterCount);
			statement.setLong(13, writeSkipCount);
            statement.setTimestamp(14, startTime);
            statement.setTimestamp(15, endTime);
			statement.setObject(16, serializeObject(persistentData));
            
			statement.executeUpdate();
			rs = statement.getGeneratedKeys();
			if(rs.next()) {
				long stepExecutionId = rs.getLong(1);
				stepExecution = new StepExecutionImpl(jobExecId, stepExecutionId);
        		stepExecution.setStepName(stepName);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "createStepExecution");

		return stepExecution;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#updateStepExecution(long, com.ibm.jbatch.container.context.impl.StepContextImpl)
	 */
	@Override
	public void updateStepExecution(long jobExecId, StepContextImpl stepContext, List<String> containment) {
		long stepExecutionId = stepContext.getStepExecutionId();
		String batchStatus = stepContext.getBatchStatus() == null ? BatchStatus.STARTING.name() : stepContext.getBatchStatus().name();
		String exitStatus = stepContext.getExitStatus();
		String stepName = stepContext.getStepName();
		String stepContainmentCSV = StepExecutionImpl.getStepContainmentCSV(containment);
		
    	long readCount = 0;
    	long writeCount = 0;
    	long commitCount = 0;
    	long rollbackCount = 0;
    	long readSkipCount = 0;
    	long processSkipCount = 0;
    	long filterCount = 0;
    	long writeSkipCount = 0;
    	Timestamp startTime = stepContext.getStartTimeTS();
    	Timestamp endTime = stepContext.getEndTimeTS();
    	
		Metric[] metrics = stepContext.getMetrics();
		for (int i = 0; i < metrics.length; i++) {
			if (metrics[i].getType().equals(MetricImpl.MetricType.READ_COUNT)) {
				readCount = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.WRITE_COUNT)) {
				writeCount = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.PROCESS_SKIP_COUNT)) {
				processSkipCount = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.COMMIT_COUNT)) {
				commitCount = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.ROLLBACK_COUNT)) {
				rollbackCount = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.READ_SKIP_COUNT)) {
				readSkipCount = metrics[i].getValue();
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.FILTER_COUNT)) {
				filterCount = metrics[i].getValue();	
			} else if (metrics[i].getType().equals(MetricImpl.MetricType.WRITE_SKIPCOUNT)) {
				writeSkipCount = metrics[i].getValue();	
			}
		}
		Serializable persistentData = stepContext.getPersistentUserData();

		updateStepExecution(stepExecutionId, jobExecId, batchStatus, exitStatus, stepName, stepContainmentCSV, readCount, 
			writeCount, commitCount, rollbackCount, readSkipCount, processSkipCount, filterCount, 
			writeSkipCount, startTime, endTime, persistentData);

	}
	

	private void updateStepExecution(long stepExecutionId, long jobExecId,  String batchStatus, String exitStatus, String stepName, String stepContainmentCSV, long readCount, 
		long writeCount, long commitCount, long rollbackCount, long readSkipCount, long processSkipCount, long filterCount,
		long writeSkipCount, Timestamp startTime, Timestamp endTime, Serializable persistentData) {

		logger.entering(CLASSNAME, "updateStepExecution", new Object[] {stepExecutionId, jobExecId, batchStatus, exitStatus, stepName, readCount, 
			writeCount, commitCount, rollbackCount, readSkipCount, processSkipCount, filterCount, writeSkipCount, startTime,
			endTime, persistentData});

		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		StepExecutionImpl stepExecution = null;
		String query = "UPDATE stepexecutioninstancedata SET jobexecid = ?, batchstatus = ?, exitstatus = ?, stepname = ?, stepcontainmentcsv = ?, readcount = ?," 
			+ "writecount = ?, commitcount = ?, rollbackcount = ?, readskipcount = ?, processskipcount = ?, filtercount = ?, writeskipcount = ?,"
			+ " starttime = ?, endtime = ?, persistentdata = ? WHERE stepexecid = ?";

		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			statement.setLong(1, jobExecId);
			statement.setString(2, batchStatus);
			statement.setString(3, exitStatus);
			statement.setString(4, stepName);
			statement.setString(5, stepContainmentCSV);
			statement.setLong(6, readCount);
			statement.setLong(7, writeCount);
			statement.setLong(8, commitCount);
			statement.setLong(9, rollbackCount);
			statement.setLong(10, readSkipCount);
			statement.setLong(11, processSkipCount);
			statement.setLong(12, filterCount);
			statement.setLong(13, writeSkipCount);
            statement.setTimestamp(14, startTime);
            statement.setTimestamp(15, endTime);
			statement.setObject(16, serializeObject(persistentData));
          	statement.setLong(17, stepExecutionId); 
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, null, statement);
		}

		logger.exiting(CLASSNAME, "updateStepExecution");
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#createJobStatus(long)
	 */
	@Override
	public JobStatus createJobStatus(long jobInstanceId) {
		logger.entering(CLASSNAME, "createJobStatus", jobInstanceId);
		Connection conn = null;
		PreparedStatement statement = null;
		JobStatus jobStatus = new JobStatus(jobInstanceId);
		try {
			conn = getConnection();
			statement = conn.prepareStatement("INSERT INTO jobstatus (id, obj) VALUES(?, ?)");
			statement.setLong(1, jobInstanceId);
			statement.setBytes(2, serializeObject(jobStatus));
			statement.executeUpdate();
			
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "createJobStatus");
		return jobStatus;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#getJobStatus(long)
	 */
	@Override
	public JobStatus getJobStatus(long instanceId) {
    	logger.entering(CLASSNAME, "getJobStatus", instanceId);
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		RuntimeJobExecutionHelper jobExecution = null;
		String query = "SELECT obj FROM jobstatus WHERE id = ?";
		JobStatus jobStatus = null;

		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			statement.setLong(1, instanceId);
			rs = statement.executeQuery();
			if(rs.next()) {
				jobStatus = (JobStatus)deserializeObject(rs.getBytes(1));
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
    	logger.exiting(CLASSNAME, "getJobStatus", jobStatus);
		return jobStatus;
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#updateJobStatus(long, com.ibm.jbatch.container.status.JobStatus)
	 */
	@Override
	public void updateJobStatus(long instanceId, JobStatus jobStatus) {
    	logger.entering(CLASSNAME, "updateJobStatus", new Object[] {instanceId, jobStatus});
		Connection conn = null;
		PreparedStatement statement = null;
		try {
			conn = getConnection();
			statement = conn.prepareStatement("UPDATE jobstatus SET obj = ? WHERE id = ?");
			statement.setBytes(1, serializeObject(jobStatus));
			statement.setLong(2, instanceId);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "updateJobStatus");
	}	

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#createStepStatus(long)
	 */
	@Override
	public StepStatus createStepStatus(long stepExecId) {
		logger.entering(CLASSNAME, "createStepStatus", stepExecId);
		Connection conn = null;
		PreparedStatement statement = null;
		StepStatus stepStatus = new StepStatus(stepExecId);
		try {
			conn = getConnection();
			statement = conn.prepareStatement("INSERT INTO stepstatus (id, obj) VALUES(?, ?)");
			statement.setLong(1, stepExecId);
			statement.setBytes(2, serializeObject(stepStatus));
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "createStepStatus");
		return stepStatus;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#getStepStatus(long, java.lang.String)
	 */
	@Override
	public StepStatus getStepStatus(long instanceId, String stepName) {
    	logger.entering(CLASSNAME, "getStepStatus", new Object[] {instanceId, stepName});
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		RuntimeJobExecutionHelper jobExecution = null;
		String query = "SELECT obj FROM stepstatus WHERE id IN ("
			+ "SELECT B.stepexecid FROM executioninstancedata A INNER JOIN stepexecutioninstancedata B ON A.jobexecid = B.jobexecid " 
			+ "WHERE A.jobinstanceid = ? and B.stepname = ?)";
		StepStatus stepStatus = null;

		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			statement.setLong(1, instanceId);
			statement.setString(2, stepName);
			rs = statement.executeQuery();
			if(rs.next()) {
				stepStatus = (StepStatus)deserializeObject(rs.getBytes(1));
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} catch (ClassNotFoundException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
    	logger.exiting(CLASSNAME, "getStepStatus", stepStatus);
		return stepStatus;
	}

	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#updateStepStatus(long, com.ibm.jbatch.container.status.StepStatus)
	 */
	@Override
	public void updateStepStatus(long stepExecutionId, StepStatus stepStatus) {
    	logger.entering(CLASSNAME, "updateStepStatus", new Object[] {stepExecutionId, stepStatus});
		Connection conn = null;
		PreparedStatement statement = null;
		try {
			conn = getConnection();
			statement = conn.prepareStatement("UPDATE stepstatus SET obj = ? WHERE id = ?");
			statement.setBytes(1, serializeObject(stepStatus));
			statement.setLong(2, stepExecutionId);
			statement.executeUpdate();
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} catch (IOException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, null, statement);
		}
		logger.exiting(CLASSNAME, "updateStepStatus");
	}
	
	/* (non-Javadoc)
	 * @see com.ibm.jbatch.container.services.IPersistenceManagerService#getTagName(long)
	 */
	@Override
	public String getTagName(long jobExecutionId) {
		logger.entering(CLASSNAME, "getTagName", jobExecutionId);
		String apptag = null;
		Connection conn = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		String query = "SELECT A.apptag FROM jobinstancedata A INNER JOIN executioninstancedata B ON A.jobinstanceid = B.jobinstanceid"
				+ "WHERE B.jobexecid = ?";
		try {
			conn = getConnection();
			statement = conn.prepareStatement(query);
			statement.setLong(1, jobExecutionId);
			rs = statement.executeQuery();
			if(rs.next()) {
				apptag = rs.getString(1);
			}
		} catch (SQLException e) {
			throw new PersistenceException(e);
		} finally {
			cleanupConnection(conn, rs, statement);
		}
		logger.exiting(CLASSNAME, "getTagName");
		return apptag;
	}

}
