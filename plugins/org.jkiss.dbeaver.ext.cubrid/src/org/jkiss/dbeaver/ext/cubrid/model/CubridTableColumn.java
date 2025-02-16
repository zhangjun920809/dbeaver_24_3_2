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
package org.jkiss.dbeaver.ext.cubrid.model;

import java.util.Arrays;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.DBException;
import org.jkiss.dbeaver.ext.cubrid.CubridConstants;
import org.jkiss.dbeaver.ext.generic.model.GenericTableBase;
import org.jkiss.dbeaver.ext.generic.model.GenericTableColumn;
import org.jkiss.dbeaver.model.exec.jdbc.JDBCResultSet;
import org.jkiss.dbeaver.model.impl.jdbc.JDBCUtils;
import org.jkiss.dbeaver.model.meta.Property;

public class CubridTableColumn extends GenericTableColumn
{
    final static String [] customTypes = {"ENUM", "JSON"};

    public CubridTableColumn(
            @NotNull GenericTableBase table,
            @Nullable String columnName,
            @Nullable String dataType,
            @Nullable Boolean autoIncrement,
            @Nullable JDBCResultSet dbResult)
            throws DBException {
        super(table);
        if (dbResult != null) {
            setName(columnName);
            setDataType(dataType);
            setPrecision(JDBCUtils.safeGetInteger(dbResult, "prec"));
            setMaxLength(getPrecision());
            setScale(JDBCUtils.safeGetInteger(dbResult, "scale"));
            setRequired(JDBCUtils.safeGetString(dbResult, "is_nullable").equals("NO"));
            setDescription(JDBCUtils.safeGetString(dbResult, CubridConstants.COMMENT));
            setDefaultValue(JDBCUtils.safeGetString(dbResult, "default_value"));
            setAutoIncrement(autoIncrement);
            setOrdinalPosition(JDBCUtils.safeGetInteger(dbResult, "ref_order"));
            setPersisted(true);
        }
    }

    public void setDataType(@NotNull String fullTypeName) throws DBException {
        String type =  Arrays.stream(customTypes).filter(item->fullTypeName.contains(item)).findFirst().orElse(null);
        if (type == null) {
            setFullTypeName(fullTypeName);
        } else {
            setTypeName(fullTypeName);
        }
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, updatable = true, order = 20, listProvider = ColumnTypeNameListProvider.class)
    public String getTypeName() {
        return super.getTypeName();
    }

    @Nullable
    @Override
    @Property(viewable = true, editable = true, updatable = true, order = 40)
    public long getMaxLength() {
        return super.getMaxLength();
    }

    @NotNull
    @Override
    @Property(viewable = true, editable = true, updatable = true, order = 50)
    public boolean isRequired() {
        return super.isRequired();
    }

    @Nullable
    @Override
    @Property(viewable = true, editable = true, updatable = true, order = 70)
    public String getDefaultValue() {
        return super.getDefaultValue();
    }

    @Nullable
    @Override
    public boolean isInUniqueKey() {
        return false;
    }

    @Nullable
    @Override
    public boolean isAutoGenerated() {
        return false;
    }

    @Nullable
    @Override
    public int getRadix() {
        return 0;
    }
}
