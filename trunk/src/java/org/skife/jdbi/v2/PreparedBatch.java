/*
 * Copyright 2004-2006 Brian McCallister
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

package org.skife.jdbi.v2;

import org.skife.jdbi.v2.exceptions.UnableToCreateStatementException;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.tweak.RewrittenStatement;
import org.skife.jdbi.v2.tweak.StatementRewriter;
import org.skife.jdbi.v2.tweak.StatementLocator;
import org.skife.jdbi.v2.tweak.StatementBuilder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Represents a prepared batch statement. That is, a sql statement compiled as a prepared
 * statement, and then executed multiple times in a single batch. This is, generally,
 * a very efficient way to execute large numbers of the same statement where
 * the statement only varies by the arguments bound to it.
 */
public class PreparedBatch
{
    private List<PreparedBatchPart> parts = new ArrayList<PreparedBatchPart>();
    private final StatementLocator locator;
    private final StatementRewriter rewriter;
    private final Connection connection;
    private final StatementBuilder preparedStatementCache;
    private final String sql;
    private final StatementContext context = new StatementContext();

    PreparedBatch(StatementLocator locator,
                  StatementRewriter rewriter,
                  Connection connection,
                  StatementBuilder preparedStatementCache,
                  String sql)
    {
        this.locator = locator;
        this.rewriter = rewriter;
        this.connection = connection;
        this.preparedStatementCache = preparedStatementCache;
        this.sql = sql;
    }

    /**
     * Specify a value on the statement context for this batch
     *
     * @return self
     */
    public PreparedBatch define(String key, Object value)
    {
        context.setAttribute(key, value);
        return this;
    }

    /**
     * Execute the batch
     *
     * @return the number of rows modified or inserted per batch part.
     */
    public int[] execute()
    {
        // short circuit empty batch
        if (parts.size() == 0) return new int[]{};

        PreparedBatchPart current = parts.get(0);
        final RewrittenStatement rewritten = rewriter.rewrite(sql, current.getParameters(), context);
        PreparedStatement stmt = null;
        try {
            try {
                stmt = connection.prepareStatement(rewritten.getSql());
            }
            catch (SQLException e) {
                throw new UnableToCreateStatementException(e);
            }

            try {
                for (PreparedBatchPart part : parts) {
                    rewritten.bind(part.getParameters(), stmt);
                    stmt.addBatch();
                }
            }
            catch (SQLException e) {
                throw new UnableToExecuteStatementException("Unable to configure JDBC statement to 1", e);
            }

            try {
                return stmt.executeBatch();
            }
            catch (SQLException e) {
                throw new UnableToExecuteStatementException(e);
            }
        }
        finally {
            QueryPostMungeCleanup.CLOSE_RESOURCES_QUIETLY.cleanup(null, stmt, null);
        }
    }

    /**
     * Add a statement (part) to this batch. You'll need to bindBinaryStream any arguments to the
     * part.
     *
     * @return A part which can be used to bindBinaryStream parts to the statement
     */
    public PreparedBatchPart add()
    {
        PreparedBatchPart part = new PreparedBatchPart(this, locator, rewriter, connection, preparedStatementCache, sql, context);
        parts.add(part);
        return part;
    }

    /**
     * Create a new batch part by binding properties from <code>bean</code> to
     * named parameters on the statement
     *
     * @param bean JavaBean to lookup properties on
     *
     * @return the new batch part
     */
    public PreparedBatchPart add(Object bean)
    {
        PreparedBatchPart part = new PreparedBatchPart(this, locator, rewriter, connection, preparedStatementCache, sql, context);
        parts.add(part);
        part.bindFromProperties(bean);
        return part;
    }

    /**
     * Create a new batch part by binding values looked up in <code>args</code> to
     * named parameters on the statement.
     *
     * @param args map to bind arguments from for named parameters on the statement
     *
     * @return the new batch part
     */
    public PreparedBatchPart add(Map<String, ? extends Object> args)
    {
        PreparedBatchPart part = new PreparedBatchPart(this, locator, rewriter, connection, preparedStatementCache, sql, context);
        parts.add(part);
        part.bindFromMap(args);
        return part;
    }
}
