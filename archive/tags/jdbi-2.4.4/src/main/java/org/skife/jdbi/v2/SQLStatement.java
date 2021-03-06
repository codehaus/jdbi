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

package org.skife.jdbi.v2;

import org.skife.jdbi.v2.exceptions.ResultSetException;
import org.skife.jdbi.v2.exceptions.UnableToCreateStatementException;
import org.skife.jdbi.v2.exceptions.UnableToExecuteStatementException;
import org.skife.jdbi.v2.tweak.Argument;
import org.skife.jdbi.v2.tweak.RewrittenStatement;
import org.skife.jdbi.v2.tweak.SQLLog;
import org.skife.jdbi.v2.tweak.StatementBuilder;
import org.skife.jdbi.v2.tweak.StatementCustomizer;
import org.skife.jdbi.v2.tweak.StatementLocator;
import org.skife.jdbi.v2.tweak.StatementRewriter;

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

/**
 * This class provides the common functions between <code>Query</code> and
 * <code>Update</code>. It defines most of the argument binding functions
 * used by its subclasses.
 */
public abstract class SQLStatement<SelfType extends SQLStatement<SelfType>>
{
    private final Binding params;
    private final Connection connection;
    private final String sql;
    private final StatementRewriter rewriter;
    private final StatementBuilder statementBuilder;
    private final StatementLocator locator;
    private final Collection<StatementCustomizer> customizers = new ArrayList<StatementCustomizer>();
    private final StatementContext context;

    /**
     * This will be set on execution, not before
     */
    private RewrittenStatement rewritten;
    private PreparedStatement stmt;
    private final SQLLog log;

    SQLStatement(Binding params,
                 StatementLocator locator,
                 StatementRewriter rewriter,
                 Connection conn,
                 StatementBuilder preparedStatementCache,
                 String sql,
                 StatementContext ctx,
                 SQLLog log)
    {
        this.log = log;
        assert (verifyOurNastyDowncastIsOkay());
        this.context = ctx;
        this.statementBuilder = preparedStatementCache;
        this.rewriter = rewriter;
        this.connection = conn;
        this.sql = sql;
        this.params = params;
        this.locator = locator;

        ctx.setConnection(conn);
        ctx.setRawSql(sql);
        ctx.setBinding(params);
    }


    /**
     * Define a value on the {@link StatementContext}
     *
     * @param key Key to acces this value from the StatementContext
     * @param value Value to setAttribute on the StatementContext
     * @return this
     */
    @SuppressWarnings("unchecked")
    public SelfType define(String key, Object value)
    {
        getContext().setAttribute(key, value);
        return (SelfType)this;
    }

    /**
     * Obtain the statement context associated with this statement
     */
    public StatementContext getContext() {
        return context;
    }

    /**
     * Provides a means for custom statement modification. Common cusotmizations
     * have their own methods, such as {@link Query#setMaxRows(int)}
     *
     * @param customizer instance to be used to cstomize a statement
     *
     * @return modified statement
     */
    @SuppressWarnings("unchecked")
    public SelfType addStatementCustomizer(StatementCustomizer customizer)
    {
        this.customizers.add(customizer);
        return (SelfType) this;
    }

    private boolean verifyOurNastyDowncastIsOkay()
    {
        if (this.getClass().getTypeParameters().length == 0) {
            return true;
        }
        else {
            Class parameterized_type = this.getClass().getTypeParameters()[0].getGenericDeclaration();
            return parameterized_type.isAssignableFrom(this.getClass());
        }
    }

    protected StatementBuilder getStatementBuilder()
    {
        return statementBuilder;
    }

    protected StatementLocator getStatementLocator()
    {
        return this.locator;
    }

    protected StatementRewriter getRewriter()
    {
        return rewriter;
    }

    protected Binding getParams()
    {
        return params;
    }

    protected Connection getConnection()
    {
        return connection;
    }

    /**
     * The un-translated SQL used to create this statement
     * @return
     */
    protected String getSql()
    {
        return sql;
    }

    protected Binding getParameters()
    {
        return params;
    }

    /**
     * Used if you need to have some exotic parameter bound.
     *
     * @param position position to bindBinaryStream this argument, starting at 0
     * @param argument exotic argument factory
     *
     * @return the same Query instance
     */
    @SuppressWarnings("unchecked")
    public SelfType bind(int position, Argument argument)
    {
        params.addPositional(position, argument);
        return (SelfType) this;
    }

    /**
     * Used if you need to have some exotic parameter bound.
     *
     * @param name     name to bindBinaryStream this argument
     * @param argument exotic argument factory
     *
     * @return the same Query instance
     */
    @SuppressWarnings("unchecked")
    public SelfType bind(String name, Argument argument)
    {
        params.addNamed(name, argument);
        return (SelfType) this;
    }

    /**
     * Binds named parameters from JavaBean propertues on o
     *
     * @param o source of named parameter values to use as arguments
     *
     * @return modified statement
     */
    @SuppressWarnings("unchecked")
    public SelfType bindFromProperties(Object o)
    {
        params.addLazyNamedArguments(new BeanPropertyArguments(o, context));
        return (SelfType) this;
    }

    /**
     * Binds named parameters from a map of String to Object instances
     *
     * @param args map where keys are matched to named parameters in order to bind arguments
     *
     * @return modified statement
     */
    @SuppressWarnings("unchecked")
    public SelfType bindFromMap(Map<String, ? extends Object> args)
    {
        params.addLazyNamedArguments(new MapArguments(args));
        return (SelfType) this;
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Character value)
    {
        return bind(position, new CharacterArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the parameter to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Character value)
    {
        return bind(name, new CharacterArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, String value)
    {
        return bind(position, new StringArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, String value)
    {
        return bind(name, new StringArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, int value)
    {
        return bind(position, new IntegerArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, int value)
    {
        return bind(name, new IntegerArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     * @param length   how long is the stream being bound?
     *
     * @return the same Query instance
     */
    public final SelfType bindASCIIStream(int position, InputStream value, int length)
    {
        return bind(position, new InputStreamArgument(value, length, true));
    }

    /**
     * Bind an argument by name
     *
     * @param name   token name to bind the paramater to
     * @param value  to bind
     * @param length bytes to read from value
     *
     * @return the same Query instance
     */
    public final SelfType bindASCIIStream(String name, InputStream value, int length)
    {
        return bind(name, new InputStreamArgument(value, length, true));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, BigDecimal value)
    {
        return bind(position, new BigDecimalArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, BigDecimal value)
    {
        return bind(name, new BigDecimalArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bindBinaryStream(int position, InputStream value, int length)
    {
        return bind(position, new InputStreamArgument(value, length, false));
    }

    /**
     * Bind an argument by name
     *
     * @param name   token name to bind the paramater to
     * @param value  to bind
     * @param length bytes to read from value
     *
     * @return the same Query instance
     */
    public final SelfType bindBinaryStream(String name, InputStream value, int length)
    {
        return bind(name, new InputStreamArgument(value, length, false));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Blob value)
    {
        return bind(position, new BlobArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Blob value)
    {
        return bind(name, new BlobArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, boolean value)
    {
        return bind(position, new BooleanArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, boolean value)
    {
        return bind(name, new BooleanArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, byte value)
    {
        return bind(position, new ByteArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, byte value)
    {
        return bind(name, new ByteArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, byte[] value)
    {
        return bind(position, new ByteArrayArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, byte[] value)
    {
        return bind(name, new ByteArrayArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     * @param length   number of characters to read
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Reader value, int length)
    {
        return bind(position, new CharacterStreamArgument(value, length));
    }

    /**
     * Bind an argument by name
     *
     * @param name   token name to bind the paramater to
     * @param value  to bind
     * @param length number of characters to read
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Reader value, int length)
    {
        return bind(name, new CharacterStreamArgument(value, length));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Clob value)
    {
        return bind(position, new ClobArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Clob value)
    {
        return bind(name, new ClobArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, java.sql.Date value)
    {
        return bind(position, new SqlDateArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, java.sql.Date value)
    {
        return bind(name, new SqlDateArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, java.util.Date value)
    {
        return bind(position, new JavaDateArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, java.util.Date value)
    {
        return bind(name, new JavaDateArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Double value)
    {
        return bind(position, new DoubleArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Double value)
    {
        return bind(name, new DoubleArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Float value)
    {
        return bind(position, new FloatArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Float value)
    {
        return bind(name, new FloatArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, long value)
    {
        return bind(position, new LongArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, long value)
    {
        return bind(name, new LongArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Object value)
    {
        return bind(position, new ObjectArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Object value)
    {
        return bind(name, new ObjectArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Time value)
    {
        return bind(position, new TimeArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Time value)
    {
        return bind(name, new TimeArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, Timestamp value)
    {
        return bind(position, new TimestampArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, Timestamp value)
    {
        return bind(name, new TimestampArgument(value));
    }

    /**
     * Bind an argument positionally
     *
     * @param position position to bind the paramater at, starting at 0
     * @param value    to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(int position, URL value)
    {
        return bind(position, new URLArgument(value));
    }

    /**
     * Bind an argument by name
     *
     * @param name  token name to bind the paramater to
     * @param value to bind
     *
     * @return the same Query instance
     */
    public final SelfType bind(String name, URL value)
    {
        return bind(name, new URLArgument(value));
    }

    /**
     * Bind NULL to be set for a given argument.
     *
     * @param name Named parameter to bind to
     * @param sqlType The sqlType must be set and is a value from <code>java.sql.Types</code>
     * @return the same statement instance
     */
    public final SelfType bindNull(String name, int sqlType) {
        return bind(name, new NullArgument(sqlType));
    }

    /**
     * Bind NULL to be set for a given argument.
     *
     * @param position position to bind NULL to, starting at 0
     * @param sqlType The sqlType must be set and is a value from <code>java.sql.Types</code>
     * @return the same statement instance
     */
    public final SelfType bindNull(int position, int sqlType) {
        return bind(position, new NullArgument(sqlType));
    }

    /**
     * Bind a value using a specific type from <code>java.sql.Types</code> via
     * PreparedStatement#setObject(int, Object, int)
     *
     * @param name Named parameter to bind at
     * @param value Value to bind
     * @param sqlType The sqlType from java.sql.Types
     * @return self
     */
    public final SelfType bindBySqlType(String name, Object value, int sqlType) {
        return bind(name, new SqlTypeArgument(value, sqlType));
    }

    private String wrapLookup(String sql)
    {
        try {
            return locator.locate(sql, this.getContext());
        }
        catch (Exception e) {
            throw new UnableToCreateStatementException("Exception thrown while looking for statement", e, context);
        }
    }

    protected <Result> Result internalExecute(final QueryPreperator prep,
                                              final QueryResultMunger<Result> munger,
                                              final QueryPostMungeCleanup cleanup)
    {
        final String located_sql = wrapLookup(sql);
        this.context.setLocatedSql(located_sql);
        rewritten = rewriter.rewrite(located_sql, getParameters(), this.context);
        this.context.setRewrittenSql(rewritten.getSql());
        ResultSet rs = null;
        try {
            try {
	            if (getClass().isAssignableFrom(Call.class)) {
		            stmt = statementBuilder.createCall(this.getConnection(), rewritten.getSql(), context);
	            }
	            else {
                    stmt = statementBuilder.create(this.getConnection(), rewritten.getSql(), context);
	            }
            }
            catch (SQLException e) {
                throw new UnableToCreateStatementException(e,context);
            }

            this.context.setStatement(stmt);
            try {
                rewritten.bind(getParameters(), stmt);
            }
            catch (SQLException e) {
                throw new UnableToExecuteStatementException("Unable to bind parameters to query", e, context);
            }

            try {
                prep.prepare(stmt);
            }
            catch (SQLException e) {
                throw new UnableToExecuteStatementException("Unable to prepare JDBC statement", e, context);
            }

            for (StatementCustomizer customizer : customizers) {
                try {
                    customizer.beforeExecution(stmt, context);
                }
                catch (SQLException e) {
                    throw new UnableToExecuteStatementException("Exception thrown in statement customization", e, context);
                }
            }

            try {
                final long start = System.currentTimeMillis();
                stmt.execute();
                log.logSQL(System.currentTimeMillis() - start,  rewritten.getSql());
            }
            catch (SQLException e) {
                throw new UnableToExecuteStatementException(e, context);
            }

            for (StatementCustomizer customizer : customizers) {
                try {
                    customizer.afterExecution(stmt, context);
                }
                catch (SQLException e) {
                    throw new UnableToExecuteStatementException("Exception thrown in statement customization", e, context);
                }
            }

            try {
                rs = stmt.getResultSet();
                return munger.munge(stmt);
            }
            catch (SQLException e) {
                throw new ResultSetException("Exception thrown while attempting to traverse the result set", e, context);
            }
        }
        finally {
            cleanup.cleanup(this, null, rs);
        }
    }

    void close() throws SQLException
    {
        this.statementBuilder.close(getConnection(), rewritten.getSql(), stmt);
    }

    protected SQLLog getLog()
    {
        return log;
    }
}
