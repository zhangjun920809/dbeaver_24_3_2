/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2013-2015 Denis Forveille (titou10.titou10@gmail.com)
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
package org.jkiss.dbeaver.ext.db2.manager;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.db2.model.DB2Table;
import org.jkiss.dbeaver.ext.db2.model.DB2TableBase;
import org.jkiss.dbeaver.ext.db2.model.DB2TableColumn;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.edit.DBECommandContext;
import org.jkiss.dbeaver.model.edit.DBEObjectRenamer;
import org.jkiss.dbeaver.model.edit.DBEPersistAction;
import org.jkiss.dbeaver.model.exec.DBCExecutionContext;
import org.jkiss.dbeaver.model.impl.edit.SQLDatabasePersistAction;
import org.jkiss.dbeaver.model.impl.sql.edit.struct.SQLTableColumnManager;
import org.jkiss.dbeaver.model.runtime.DBRProgressMonitor;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.model.struct.cache.DBSObjectCache;
import org.jkiss.dbeaver.utils.GeneralUtils;
import org.jkiss.utils.CommonUtils;

import java.util.List;
import java.util.Map;

/**
 * DB2 Table Column Manager
 *
 * @author Denis Forveille
 */
public class DB2TableColumnManager extends SQLTableColumnManager<DB2TableColumn, DB2TableBase> implements DBEObjectRenamer<DB2TableColumn> {

    private static final String SQL_ALTER = "ALTER TABLE %s ALTER COLUMN %s ";
    private static final String SQL_COMMENT = "COMMENT ON COLUMN %s.%s IS '%s'";
    private static final String SQL_REORG = "CALL SYSPROC.ADMIN_CMD('REORG TABLE %s')";

    private static final String CLAUSE_SET_TYPE = " SET DATA TYPE ";
    private static final String CLAUSE_SET_NULL = " SET NOT NULL";
    private static final String CLAUSE_DROP_NULL = " DROP NOT NULL";

    private static final String CMD_ALTER = "Alter Column";
    private static final String CMD_COMMENT = "Comment on Column";
    private static final String CMD_REORG = "Reorg table";

    private static final String LINE_SEPARATOR = GeneralUtils.getDefaultLineSeparator();

    // -----------------
    // Business Contract
    // -----------------
    @Nullable
    @Override
    public DBSObjectCache<? extends DBSObject, DB2TableColumn> getObjectsCache(DB2TableColumn object) {
        return object.getParentObject().getContainer().getTableCache().getChildrenCache((DB2Table) object.getParentObject());
    }

    @Override
    public boolean canEditObject(DB2TableColumn object) {
        // Edit is only available for DB2Table and not for other kinds of tables (View, MQTs, Nicknames..)
        DB2TableBase db2TableBase = object.getParentObject();
        if ((db2TableBase != null) && (db2TableBase.getClass().equals(DB2Table.class))) {
            return true;
        } else {
            return false;
        }
    }

    // ------
    // Create
    // ------

    @Override
    protected DB2TableColumn createDatabaseObject(@NotNull DBRProgressMonitor monitor, @NotNull DBECommandContext context, Object container,
                                                  Object copyFrom, @NotNull Map<String, Object> options) throws DBException {
        DB2TableColumn column = new DB2TableColumn((DB2TableBase) container);
        column.setName(getNewColumnName(monitor, context, (DB2TableBase) container));
        return column;
    }

    // -----
    // Alter
    // -----
    @Override
    protected void addObjectModifyActions(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext executionContext, @NotNull List<DBEPersistAction> actionList, @NotNull ObjectChangeCommand command, @NotNull Map<String, Object> options) {
        DB2TableColumn db2Column = command.getObject();

        boolean hasColumnChanges = false;
        if (!command.getProperties().isEmpty()) {
            final String deltaSQL = computeDeltaSQL(monitor, command);
            if (!deltaSQL.isEmpty()) {
                hasColumnChanges = true;
                String sqlAlterColumn = String.format(SQL_ALTER, db2Column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL), deltaSQL);
                actionList.add(new SQLDatabasePersistAction(CMD_ALTER, sqlAlterColumn));
            }
        }

        // Comment
        if (CommonUtils.isNotEmpty(db2Column.getDescription())) {
            actionList.add(buildCommentAction(db2Column));
        }

        if (hasColumnChanges) {
            // Be Safe, Add a reorg action
            actionList.add(buildReorgAction(db2Column));
        }
    }

    @Override
    protected void addObjectCreateActions(
        @NotNull DBRProgressMonitor monitor,
        @NotNull DBCExecutionContext executionContext,
        @NotNull List<DBEPersistAction> actions,
        @NotNull ObjectCreateCommand command,
        @NotNull Map<String, Object> options
    ) throws DBException {
        super.addObjectCreateActions(monitor, executionContext, actions, command, options);
        if (!CommonUtils.isEmpty(command.getObject().getDescription())) {
            actions.add(buildCommentAction(command.getObject()));
        }
    }

    // -------
    // Helpers
    // -------
    private String computeDeltaSQL(DBRProgressMonitor monitor, ObjectChangeCommand command) {

        if (command.getProperties().isEmpty() ||
            (command.getProperties().size() == 1 && command.getProperty("description") != null)) {
            return "";
        }

/*
        if (log.isDebugEnabled()) {
            for (Map.Entry<Object, Object> entry : command.getProperties().entrySet()) {
                log.debug(entry.getKey() + "=" + entry.getValue());
            }
        }
*/

        DB2TableColumn column = command.getObject();

        StringBuilder sb = new StringBuilder(128);
        sb.append(column.getName());

        Boolean required = (Boolean) command.getProperty("required");
        if (required != null) {
            sb.append(LINE_SEPARATOR);
            if (required) {
                sb.append(CLAUSE_SET_NULL);
            } else {
                sb.append(CLAUSE_DROP_NULL);
            }
        }

        if (command.hasProperty("dataType") || command.hasProperty("maxLength") || command.hasProperty("scale")) {
            sb.append(LINE_SEPARATOR);
            sb.append(CLAUSE_SET_TYPE);
            DataTypeModifier.appendModifier(monitor, column, sb, command);
        }

        return sb.toString();
    }

    private DBEPersistAction buildCommentAction(DB2TableColumn db2Column) {
        String tableName = db2Column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL);
        String columnName = db2Column.getName();
        String comment = db2Column.getDescription();
        String commentSQL = String.format(SQL_COMMENT, tableName, columnName, comment);
        return new SQLDatabasePersistAction(CMD_COMMENT, commentSQL);
    }

    private DBEPersistAction buildReorgAction(DB2TableColumn db2Column) {
        String tableName = db2Column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL);
        String reorgSQL = String.format(SQL_REORG, tableName);
        return new SQLDatabasePersistAction(CMD_REORG, reorgSQL);
    }

    @Override
    public void renameObject(@NotNull DBECommandContext commandContext, @NotNull DB2TableColumn object, @NotNull Map<String, Object> options, @NotNull String newName) throws DBException {
        processObjectRename(commandContext, object, options, newName);
    }

    @Override
    protected void addObjectRenameActions(@NotNull DBRProgressMonitor monitor, @NotNull DBCExecutionContext executionContext, @NotNull List<DBEPersistAction> actions, @NotNull ObjectRenameCommand command, @NotNull Map<String, Object> options) {
        final DB2TableColumn column = command.getObject();

        actions.add(
            new SQLDatabasePersistAction(
                "Rename column",
                "ALTER TABLE " + column.getTable().getFullyQualifiedName(DBPEvaluationContext.DDL) + " RENAME COLUMN " +
                    DBUtils.getQuotedIdentifier(column.getDataSource(), command.getOldName()) + " TO " + DBUtils.getQuotedIdentifier(column.getDataSource(), command.getNewName()))
        );
    }
}
