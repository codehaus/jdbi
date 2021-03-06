/* Copyright 2004-2006 Brian McCallister
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.skife.jdbi.v2;

import org.skife.jdbi.v2.exceptions.ResultSetException;
import org.skife.jdbi.v2.exceptions.UnableToCreateStatementException;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.tweak.StatementCustomizer;
import org.skife.jdbi.v2.tweak.StatementLocator;
import org.skife.jdbi.v2.tweak.StatementRewriter;
import org.skife.jdbi.v2.tweak.StatementBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Statement prviding convenience result handling for SQL queries.
 */
public class Query<ResultType> extends SQLStatement<Query<ResultType>> implements Iterable<ResultType>
{
    private final ResultSetMapper<ResultType> mapper;

    Query(Binding params,
          ResultSetMapper<ResultType> mapper,
          StatementLocator locator,
          StatementRewriter statementRewriter,
          Connection connection,
          StatementBuilder cache,
          String sql)
    {
        super(params, locator, statementRewriter, connection, cache, sql);
        this.mapper = mapper;
    }

    /**
     * Executes the select
     * <p/>
     * Will eagerly load all results
     *
     * @return
     * @throws UnableToCreateStatementException
     *                            if there is an error creating the statement
     * @throws UnableToExecuteStatementException
     *                            if there is an error executing the statement
     * @throws ResultSetException if there is an error dealing with the result set
     */
    public List<ResultType> list()
    {
        return this.internalExecute(QueryPreperator.NO_OP, new QueryResultMunger<List<ResultType>>()
        {
            public Pair<List<ResultType>, ResultSet> munge(Statement stmt) throws SQLException
            {
                ResultSet rs = stmt.getResultSet();
                List<ResultType> result_list = new ArrayList<ResultType>();
                int index = 0;
                while (rs.next())
                {
                    result_list.add(mapper.map(index++, rs));
                }
                return new Pair<List<ResultType>, ResultSet>(result_list, rs);
            }
        }, QueryPostMungeCleanup.CLOSE_RESOURCES_QUIETLY);
    }

    /**
     * Obtain a forward-only result set iterator. Note that you must explicitely close
     * the iterator to close the underlying resources.
     */
    public ResultIterator<ResultType> iterator()
    {
        return this.internalExecute(QueryPreperator.NO_OP, new QueryResultMunger<ResultIterator<ResultType>>()
        {
            public Pair<ResultIterator<ResultType>, ResultSet> munge(Statement results) throws SQLException
            {
                ResultSetResultIterator<ResultType> r = new ResultSetResultIterator<ResultType>(mapper,
                                                                                                results,
                                                                                                results.getResultSet());
                return new Pair<ResultIterator<ResultType>, ResultSet>(r, results.getResultSet());
            }
        }, QueryPostMungeCleanup.NO_OP);
    }

    /**
     * Executes the select.
     * <p/>
     * Specifies a maximum of one result on the JDBC statement, and map that one result
     * as the return value, or return null if there is nothing in the results
     *
     * @return first result, mapped, or null if there is no first result
     */
    public ResultType first()
    {
        return this.internalExecute(QueryPreperator.MAX_ROWS_ONE, new QueryResultMunger<ResultType>()
        {
            public final Pair<ResultType, ResultSet> munge(final Statement stt) throws SQLException
            {
                ResultSet rs = stt.getResultSet();
                if (rs.next())
                {
                    return new Pair<ResultType, ResultSet>(mapper.map(0, rs), rs);
                }
                else
                {
                    // no result matches
                    return null;
                }
            }
        }, QueryPostMungeCleanup.CLOSE_RESOURCES_QUIETLY);
    }

    /**
     * Provide basic JavaBean mapping capabilities. Will instantiate an instance of resultType
     * for each row and set the JavaBean properties which match fields in the result set.
     *
     * @param resultType JavaBean class to map result set fields into the properties of, by name
     * @return a Query which provides the bean property mapping
     */
    public <Type> Query<Type> map(Class<Type> resultType)
    {
        return this.map(new BeanMapper<Type>(resultType));
    }

    public <T> Query<T> map(ResultSetMapper<T> mapper)
    {
        return new Query<T>(getParameters(),
                            mapper,
                            getStatementLocator(),
                            getRewriter(),
                            getConnection(),
                            getPreparedStatementCache(),
                            getSql());
    }

    /**
     * Specify the fetch size for the query. This should cause the results to be
     * fetched from the underlying RDBMS in groups of rows equal to the number passed.
     * This is useful for doing chunked streaming of results when exhausting memory
     * could be a problem.
     *
     * @param i the number of rows to fetch in a bunch
     * @return the modified query
     */
    public Query<ResultType> setFetchSize(final int i)
    {
        this.addStatementCustomizer(new StatementCustomizer()
        {
            public void customize(PreparedStatement stmt) throws SQLException
            {
                stmt.setFetchSize(i);
            }
        });
        return this;
    }

    /**
     * Specify the maimum number of rows the query is to return. This uses the underlying JDBC
     * {@link Statement#setMaxRows(int)}}.
     *
     * @param i maximum number of rows to return
     * @return modified query
     */
    public Query<ResultType> setMaxRows(final int i)
    {
        this.addStatementCustomizer(new StatementCustomizer()
        {
            public void customize(PreparedStatement stmt) throws SQLException
            {
                stmt.setMaxRows(i);
            }
        });
        return this;
    }

    /**
     * Specify the maimum field size in the result set. This uses the underlying JDBC
     * {@link Statement#setMaxFieldSize(int)}
     *
     * @param i maximum field size
     * @return modified query
     */
    public Query<ResultType> setMaxFieldSize(final int i)
    {
        this.addStatementCustomizer(new StatementCustomizer()
        {
            public void customize(PreparedStatement stmt) throws SQLException
            {
                stmt.setMaxFieldSize(i);
            }
        });
        return this;
    }

    /**
     * Specify that the fetch order should be reversed, uses the underlying
     * {@link Statement#setFetchDirection(int)}
     *
     * @return the modified query
     */
    public Query<ResultType> fetchReverse()
    {
        this.addStatementCustomizer(new StatementCustomizer()
        {
            public void customize(PreparedStatement stmt) throws SQLException
            {
                stmt.setFetchDirection(ResultSet.FETCH_REVERSE);
            }
        });
        return this;
    }

    /**
     * Specify that the fetch order should be forward, uses the underlying
     * {@link Statement#setFetchDirection(int)}
     *
     * @return the modified query
     */
    public Query<ResultType> fetchForward()
    {
        this.addStatementCustomizer(new StatementCustomizer()
        {
            public void customize(PreparedStatement stmt) throws SQLException
            {
                stmt.setFetchDirection(ResultSet.FETCH_FORWARD);
            }
        });
        return this;
    }
}