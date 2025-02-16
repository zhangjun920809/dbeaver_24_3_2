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

package org.jkiss.dbeaver.ui.controls.lightgrid;

import org.jkiss.code.NotNull;
import org.jkiss.code.Nullable;
import org.jkiss.dbeaver.model.DBPImage;

/**
 * Grid cell hint
 */
public interface IGridHint {

    /**
     * Hint description label
     */
    @Nullable
    String getHintLabel();

    /**
     * Hint text
     */
    @Nullable
    String getText();

    /**
     * Icon
     */
    @Nullable
    DBPImage getIcon();

    /**
     * @return true if hint is an error notification
     */
    boolean isError();

    /**
     * @return true if hint is a disablement
     */
    boolean isReadOnly();

    /**
     * @return true if hint has action which can be executed by clicking on the icon
     */
    boolean hasAction();

    /**
     * Text for action hint tooltip
     */
    String getActionToolTip();

    /**
     * Perform action
     */
    void performAction(@NotNull IGridController grid, long state);

}
