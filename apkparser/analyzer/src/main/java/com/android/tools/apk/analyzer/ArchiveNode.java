/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.tools.apk.analyzer;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import java.util.List;
import javax.swing.tree.TreeNode;

public interface ArchiveNode extends TreeNode {
    /** Return the list of child nodes */
    @NonNull
    List<ArchiveNode> getChildren();

    /** Returns the parent of this node, null if this is the root */
    @Override
    @Nullable
    ArchiveNode getParent();

    /** Returns the data associated to this node. */
    @NonNull
    ArchiveEntry getData();
}
