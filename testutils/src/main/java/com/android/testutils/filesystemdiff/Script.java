/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.testutils.filesystemdiff;

import com.android.utils.ILogger;
import java.util.ArrayList;
import java.util.List;

public class Script {
    private List<Action> mActions = new ArrayList<>();

    public List<Action> getActions() {
        return mActions;
    }

    public void addCreateEntry(FileSystemEntry source, FileSystemEntry destination) {
        assert(destination.getKind() == source.getKind());

        switch (source.getKind()) {
            case Directory:
                mActions.add(new CreateDirectoryAction((DirectoryEntry)source, (DirectoryEntry)destination));
                break;
            case SymbolicLink:
                mActions.add(new CreateSymbolicLinkAction((SymbolicLinkEntry)source, (SymbolicLinkEntry)destination));
                break;
            case File:
                mActions.add(new CreateFileAction((FileEntry)source, (FileEntry)destination));
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public void addDeleteEntry(FileSystemEntry entry) {
        switch (entry.getKind()) {
            case Directory:
                mActions.add(new DeleteDirectoryAction((DirectoryEntry)entry));
                break;
            case SymbolicLink:
                mActions.add(new DeleteSymbolicLinkAction((SymbolicLinkEntry)entry));
                break;
            case File:
                mActions.add(new DeleteFileAction((FileEntry)entry));
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    public void execute(ILogger logger) {
        execute(logger, new ActionExecutor());
    }

    public void execute(ILogger logger, ActionExecutor executor) {
        for (Action action : getActions()) {
            executor.execute(logger, action);
        }
    }
}
