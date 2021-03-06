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

import org.skife.jdbi.v2.tweak.StatementRewriter;
import org.skife.jdbi.v2.tweak.StatementLocator;

import java.sql.Connection;

/**
 * Represents a single statement in a prepared batch
 *
 * @see PreparedBatch
 */
public class PreparedBatchPart extends SQLStatement<PreparedBatchPart>
{
    private final PreparedBatch batch;

    PreparedBatchPart(PreparedBatch batch,
                      StatementLocator locator,
                      StatementRewriter rewriter,
                      Connection connection,
                      PreparedStatementCache cache,
                      String sql)
    {
        super(new Binding(), locator, rewriter, connection, cache, sql);
        this.batch = batch;
    }

    /**
     * Submit this statement to the batch, yielding the batch. The statement is already,
     * actually part of the batch before it is submitted. This method is really just
     * a convenient way to get the prepared batch back.
     *
     * @return the PreparedBatch which this is a part of
     */
    public PreparedBatch submit()
    {
        return batch;
    }

    /**
     * Submit this part of the batch and open a fresh one
     *
     * @return a fresh PrepatedBatchPart on the same batch
     */
    public PreparedBatchPart another()
    {
        return batch.add();
    }
}
