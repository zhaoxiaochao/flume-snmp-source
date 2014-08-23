/***************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 *  
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ****************************************************************/
package org.apache.flume.source.sql;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.PollableSource;
import org.apache.flume.conf.Configurable;
import org.apache.flume.event.SimpleEvent;
import org.apache.flume.source.AbstractSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A Source for Kafka which reads messages from kafka. I use this in company production environment 
 * and its performance is good. Over 100k messages per second can be read from kafka in one source.<p>
 * <tt>zk.connect: </tt> the zookeeper ip kafka use.<p>
 * <tt>topic: </tt> the topic to read from kafka.<p>
 * <tt>groupid: </tt> the groupid of consumer group.<p>
 */
public class SNMPSource extends AbstractSource implements Configurable, PollableSource {
	private static final Logger log = LoggerFactory.getLogger(SNMPSource.class);
	
	private static MySqlDBEngine mDBEngine;
	private static MySqlDao mDAO;
	
	private String incrementalValue, table, columnsToSelect,incrementalColumnName;
	private int runQueryDelay;
	
	public Status process() throws EventDeliveryException {
		List<Event> eventList = new ArrayList<Event>();
		byte[] message;
		Event event;
		Map<String, String> headers;
			
		try
		{
			mDAO= new MySqlDao(mDBEngine.getConnection());		
			
			String where = " WHERE " + incrementalColumnName + ">" + incrementalValue;
			String query = "SELECT " + columnsToSelect + " FROM " + table + where + " ORDER BY "+ incrementalColumnName + ";";
			
			System.out.println("QUERY: " + query);
			Vector<Vector<String>> queryResult = mDAO.runQuery(query);
			Vector<String> columns = mDAO.getColumns();
			
			boolean columnPosFind;
			String queryResultRow;

			columnPosFind = false;
			int incrementalColumnPosition=0;
			do
			{
				if (columns.get(incrementalColumnPosition).equals(incrementalColumnName))
					columnPosFind=true;
				else
					incrementalColumnPosition++;
			}while(!columnPosFind);
				

			if (!queryResult.isEmpty())
			{
				incrementalValue = queryResult.lastElement().get(incrementalColumnPosition);
				//envio el mensaje al channel
				System.out.println(queryResult);
					
				for (int i=0;i<queryResult.size();i++)
				{
					//Construyo el mensaje
					queryResultRow = queryResult.get(i).toString();
					queryResultRow = queryResultRow.substring(1, queryResultRow.length()-1);
					message = queryResultRow.getBytes();
	                event = new SimpleEvent();
	                headers = new HashMap<String, String>();
	                headers.put("timestamp", String.valueOf(System.currentTimeMillis()));
	                log.debug("Message: {}", new String(message));
	                event.setBody(message);
	                event.setHeaders(headers);
	                eventList.add(event);
				}
				getChannelProcessor().processEventBatch(eventList);
			}
			
			System.out.println("Last Column readed: " + incrementalValue);
			Thread.sleep(runQueryDelay);				
            return Status.READY;
		}
		catch(SQLException e)
		{
			e.printStackTrace();
			System.out.println("EXCEPCION");
			return Status.BACKOFF;
		}
		catch(InterruptedException e)
		{
			e.printStackTrace();
			System.out.println("EXCEPCION");
			return Status.BACKOFF;			
		}
	}


	public void start(Context context) {
	    
	    
	}

	@Override
	public synchronized void stop() {
		try {
			mDBEngine.CloseConnection();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		super.stop();
	}


	@Override
	public void configure(Context context) {
		
	    String connectionURL, user, password;
		
		connectionURL = context.getString("connection.url");
		user = context.getString("user");
		password = context.getString("password");
		table = context.getString("table");
		columnsToSelect = context.getString("columns.to.select");
		incrementalColumnName = context.getString("incremental.column.name");
		incrementalValue = context.getString("incremental.value");
		runQueryDelay = context.getInteger("run.query.delay");
		
		mDBEngine = new MySqlDBEngine(connectionURL, user, password);
		System.out.println("Estableciendo conexion");
		try {
			mDBEngine.EstablishConnection();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
