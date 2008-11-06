/*
 * Copyright 2004-2007 Brian McCallister
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.skife.jdbi.derby;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.apache.derby.iapi.error.StandardException;
import org.apache.derby.impl.jdbc.EmbedSQLException;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.skife.jdbi.HandyMapThing;

public class Tools
{
    public static final String CONN_STRING = "jdbc:derby:testing";
    public static Driver driver;
    public static boolean running = false;
    public static EmbeddedDataSource dataSource;

    public static void start() throws SQLException, IOException
    {
        if (!running)
        {
            running = true;
            System.setProperty("derby.system.home", "build/db");
            File db = new File("build/db");
            db.mkdirs();

            dataSource = new EmbeddedDataSource();
            dataSource.setCreateDatabase("create");
            dataSource.setDatabaseName("testing");

            final Connection conn = dataSource.getConnection();
            conn.close();
        }
    }

    public static void stop() throws Exception
    {
        final Connection conn = getConnection();

        deleteData(conn);
        dropTables(conn);
    }

    private static void dropTables(final Connection conn) throws Exception {
    	final String[] drops = {
    			"drop table something",
    			"drop table foo",
    			"drop function do_it",
    			"drop procedure INSERTSOMETHING"
    	};

    	for (String drop : drops)
    	{
    		final Statement stmt = conn.createStatement();
    		try {
    			stmt.execute(drop);
    		} catch (Exception se)
    		{
    			if (se instanceof EmbedSQLException) {
    				final String msg = ((EmbedSQLException) se).getMessageId();
    				if (!msg.contains("42Y55")) {
    					throw se;
    				}
    			} else {
    				throw se;
    			}
    		}
    		stmt.close();
    	}
    }

    private static void deleteData(final Connection conn) throws Exception {
    	final String [] deletes = {
    			"delete from something",
    			"delete from foo"
    	};

    	for(String delete : deletes)
    	{
    		final Statement stmt = conn.createStatement();
		try {
    		stmt.execute(delete);
		} catch (Exception se)
		{
			if (se instanceof EmbedSQLException) {
				final String msg = ((EmbedSQLException) se).getMessageId();
				if (!msg.contains("42X05")) {
					throw se;
				}
			} else {
				throw se;
			}
		}
    		stmt.close();
    	}
    }

    public static Connection getConnection() throws SQLException
    {
        return dataSource.getConnection();
    }

    public static void dropAndCreateSomething() throws SQLException
    {
        final Connection conn = getConnection();

        createTables(conn);
        conn.close();
    }

    private static void createTables(final Connection conn) throws SQLException {
    	final String[] creates = {
    			"create table something ( id integer, name varchar(50), integerValue integer, intValue integer )",
    			"create table foo ( bar VARCHAR(20), baz VARCHAR(20), blo INTEGER )",
    	};
    	for (String create : creates)
    	{
    		final Statement stmt = conn.createStatement();
    		stmt.execute(create);
    		stmt.close();
    	}
    }

    public static String doIt()
    {
        return "it";
    }

    public static DataSource getDataSource()
    {
        return dataSource;
    }

    public static <K> HandyMapThing<K> map(K k, Object v)
    {
        HandyMapThing<K>s =  new HandyMapThing<K>();
        return s.add(k, v);
    }
}
