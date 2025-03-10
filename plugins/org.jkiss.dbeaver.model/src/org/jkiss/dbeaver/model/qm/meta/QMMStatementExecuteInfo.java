/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2024 DBeaver Corp and others
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

package org.jkiss.dbeaver.model.qm.meta;

import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.exec.DBCExecutionPurpose;
import org.jkiss.dbeaver.model.sql.SQLDialect;

import java.sql.SQLException;

/**
* Statement execute info
*/
public class QMMStatementExecuteInfo extends QMMObject {

    private final QMMStatementInfo statement;
    private QMMTransactionSavepointInfo savepoint;
    private final String queryString;

    private long fetchRowCount;
    private long updateRowCount = -1;

    private int errorCode;
    private String errorMessage;

    private long fetchBeginTime;
    private long fetchEndTime;

    private boolean transactional;
    @Nullable
    private final String schema;
    @Nullable
    private final String catalog;

    private transient QMMStatementExecuteInfo previous;

    QMMStatementExecuteInfo(
        QMMStatementInfo statement,
        QMMTransactionSavepointInfo savepoint,
        String queryString,
        QMMStatementExecuteInfo previous,
        SQLDialect sqlDialect,
        @Nullable String schema,
        @Nullable String catalog)
    {
        super(QMMetaObjectType.STATEMENT_EXECUTE_INFO);
        this.statement = statement;
        this.previous = previous;
        this.savepoint = savepoint;
        this.queryString = queryString;
        this.schema = schema;
        this.catalog = catalog;
        if (savepoint != null) {
            savepoint.setLastExecute(this);
        }
        if (sqlDialect != null && queryString != null) {
            this.transactional = statement.getPurpose() != DBCExecutionPurpose.META && sqlDialect.isTransactionModifyingQuery(queryString);
        } else {
            this.transactional = false;
        }
    }

    public QMMStatementExecuteInfo(
        long openTime,
        long closeTime,
        QMMStatementInfo stmt,
        String queryString,
        long rowCount,
        int errorCode,
        String errorMessage,
        long fetchBeginTime,
        long fetchEndTime,
        boolean transactional,
        @Nullable String schema,
        @Nullable String catalog
    ) {
        super(QMMetaObjectType.STATEMENT_EXECUTE_INFO, openTime, closeTime);
        this.statement = stmt;
        this.queryString = queryString;
        this.fetchRowCount = rowCount;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.fetchBeginTime = fetchBeginTime;
        this.fetchEndTime = fetchEndTime;
        this.transactional = transactional;
        this.schema = schema;
        this.catalog = catalog;
    }

    void close(long rowCount, Throwable error)
    {
        if (error != null) {
            if (error instanceof SQLException) {
                this.errorCode = ((SQLException)error).getErrorCode();
            }
            this.errorMessage = error.getMessage();
            // SQL error makes ANY statement transactional (PG specific?)
            this.transactional = true;
        }
        this.updateRowCount = rowCount;
        if (!transactional) {
            this.transactional = this.updateRowCount >= 0;
        }
        super.close();
    }

    void beginFetch()
    {
        this.fetchBeginTime = getTimeStamp();
    }

    void endFetch(long rowCount)
    {
        this.fetchEndTime = getTimeStamp();
        this.fetchRowCount = rowCount;
    }

    public QMMStatementInfo getStatement()
    {
        return statement;
    }

    public QMMTransactionSavepointInfo getSavepoint()
    {
        return savepoint;
    }

    public String getQueryString()
    {
        return queryString;
    }

    public long getFetchRowCount() {
        return fetchRowCount;
    }

    public long getUpdateRowCount()
    {
        return updateRowCount;
    }

    public int getErrorCode()
    {
        return errorCode;
    }

    public String getErrorMessage()
    {
        return errorMessage;
    }

    public boolean hasError()
    {
        return errorCode != 0 || errorMessage != null;
    }

    public long getFetchBeginTime()
    {
        return fetchBeginTime;
    }

    public long getFetchEndTime()
    {
        return fetchEndTime;
    }

    @Nullable
    public String getSchema() {
        return schema;
    }

    @Nullable
    public String getCatalog() {
        return catalog;
    }

    public boolean isFetching()
    {
        return fetchBeginTime > 0 && fetchEndTime == 0;
    }

    public boolean isTransactional() {
        return transactional || updateRowCount > 0;
    }

    public QMMStatementExecuteInfo getPrevious()
    {
        return previous;
    }

    @Override
    public String toString() {
        return '"' + queryString + '"';
    }

    @Override
    public String getText() {
        return queryString;
    }

    @Override
    public long getDuration() {
        if (!isClosed()) {
            return -1;
        }
        long execTime = getCloseTime() - getOpenTime();
        long fetchTime = isFetching() ? 0 : getFetchEndTime() - getFetchBeginTime();
        return execTime + fetchTime;
    }

    @Override
    public QMMConnectionInfo getConnection() {
        return statement.getConnection();
    }

}
