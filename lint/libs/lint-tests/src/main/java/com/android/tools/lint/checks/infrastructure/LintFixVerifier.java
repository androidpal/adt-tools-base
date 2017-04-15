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

package com.android.tools.lint.checks.infrastructure;

import static com.android.SdkConstants.DOT_XML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.testutils.TestUtils;
import com.android.tools.lint.Warning;
import com.android.tools.lint.detector.api.LintFix;
import com.android.tools.lint.detector.api.LintFix.ReplaceString;
import com.android.tools.lint.detector.api.LintFix.SetAttribute;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.utils.PositionXmlParser;
import com.android.utils.XmlUtils;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * Verifier which can simulate IDE quickfixes and check fix data
 */
public class LintFixVerifier {
    private final TestLintTask task;
    private final List<Warning> warnings;
    private int diffWindow = 0;
    private Boolean reformat;

    LintFixVerifier(TestLintTask task, List<Warning> warnings) {
        this.task = task;
        this.warnings = warnings;
    }

    /**
     * Sets up 2 lines of context in the diffs
     */
    public LintFixVerifier window() {
        diffWindow = 2;
        return this;
    }

    /**
     * Sets up a specific number of lines of contexts around diffs
     */
    public LintFixVerifier window(int size) {
        assertTrue(size >= 0 && size <= 100);
        diffWindow = size;
        return this;
    }

    /**
     * Sets whether lint should reformat before and after files before
     * diffing. If not set explicitly to true or false, it will default
     * to true for XML files that set/remove attributes and false
     * otherwise. (May not have any effect on other file types than XML.)
     */
    public LintFixVerifier reformatDiffs(boolean reformatDiffs) {
        this.reformat = reformatDiffs;
        return this;
    }

    /**
     * Checks what happens with the given fix in this result as applied to the given
     * test file, and making sure that the result is the new contents
     *
     * @param fix   the fix description, or null to pick the first one
     * @param after the file after applying the fix
     * @return this
     */
    public LintFixVerifier checkFix(@Nullable String fix, @NonNull TestFile after) {
        checkFixes(fix, after, null);
        return this;
    }

    /**
     * Applies the fixes and provides diffs of all the affected files, then
     * compares it against the expected result.
     *
     * @param expected the diff description resulting from applying the diffs
     * @return this
     */
    public LintFixVerifier expectFixDiffs(@NonNull String expected) {
        StringBuilder diff = new StringBuilder(100);
        checkFixes(null, null, diff);
        assertEquals(expected, diff.toString());
        return this;
    }

    @Nullable
    private TestFile findTestFile(@NonNull String path) {
        for (ProjectDescription project : task.projects) {
            for (TestFile file : project.files) {
                if (file.getTargetPath().equals(path)) {
                    return file;
                }
            }
        }

        return null;
    }

    private void checkFixes(@Nullable String fixName, @Nullable TestFile expectedFile,
            @Nullable StringBuilder diffs) {
        assertTrue(expectedFile != null || diffs != null);
        List<String> names = Lists.newArrayList();
        for (Warning warning : warnings) {
            LintFix data = warning.quickfixData;
            List<LintFix> list;
            if (data instanceof LintFix.LintFixGroup) {
                list = ((LintFix.LintFixGroup) data).fixes;
            } else {
                list = Collections.singletonList(data);
            }

            for (LintFix lintFix : list) {
                String targetPath = warning.path;
                TestFile file = findTestFile(targetPath);
                if (file == null) {
                    fail("Didn't find test file " + targetPath);
                }
                String before = file.getContents();
                assertNotNull(file.getTargetPath(), before);

                String after;
                Boolean reformat = this.reformat;
                if (lintFix instanceof ReplaceString) {
                    ReplaceString replaceFix = (ReplaceString) lintFix;
                    after = checkReplaceString(replaceFix, warning, before);
                } else if (lintFix instanceof SetAttribute) {
                    SetAttribute setFix = (SetAttribute) lintFix;
                    after = checkSetAttribute(setFix, before, warning);
                    if (reformat == null) {
                        reformat = true;
                    }
                } else {
                    continue;
                }

                if (after == null) {
                    continue;
                }

                if (expectedFile != null) {
                    assertEquals(expectedFile.getContents(), after);
                }

                if (diffs != null) {
                    if (reformat != null && reformat && warning.path.endsWith(DOT_XML)) {
                        try {
                            before = XmlPrettyPrinter.prettyPrint(
                                    XmlUtils.parseDocument(before, true), true);
                            after = XmlPrettyPrinter.prettyPrint(
                                    XmlUtils.parseDocument(after, true), true);
                        } catch (ParserConfigurationException | SAXException | IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    appendDiff(warning, lintFix.getDisplayName(), before, after, diffs);
                }

                String name = lintFix.getDisplayName();
                if (fixName != null && !fixName.equals(name)) {
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                    continue;
                }
                names.add(fixName);
            }
        }
    }

    private static String checkSetAttribute(
            @NonNull SetAttribute setFix,
            @NonNull String contents,
            @NonNull Warning warning) {
        Location location = warning.location;
        Position start = location.getStart();
        Position end = location.getEnd();
        assert start != null;
        assert end != null;

        try {
            org.w3c.dom.Document document;
            document = PositionXmlParser.parse(contents);

            Node node = PositionXmlParser.findNodeAtOffset(document, start.getOffset());
            assertNotNull("No node found at offset " + start.getOffset(), node);
            if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                node = ((Attr) node).getOwnerElement();
            } else if (node.getNodeType() != Node.ELEMENT_NODE) {
                // text, comments
                node = node.getParentNode();
            }
            if (node == null || node.getNodeType() != Node.ELEMENT_NODE) {
                fail("Didn't find element at offset " + start.getOffset()
                        + " (line " + start.getLine() + ", column "
                        + start.getColumn() + ") in " + warning.path
                        + ":\n" + contents);
            }
            Element element = (Element) node;
            String value = setFix.value;
            if (value == null) {
                if (setFix.namespace != null) {
                    element.removeAttributeNS(setFix.namespace,
                            setFix.attribute);
                } else {
                    element.removeAttribute(setFix.attribute);
                }
            } else {
                // Indicate the caret position by "|"
                if (setFix.dot >= 0
                        && setFix.dot <= value.length()) {
                    if (setFix.mark >= 0 && setFix.mark != setFix.dot) {
                        // Selection
                        assert setFix.mark < setFix.dot;
                        value = value.substring(0, setFix.mark) + "["
                                + value.substring(setFix.mark, setFix.dot)
                                + "]|" + value.substring(setFix.dot);
                    } else {
                        // Just caret
                        value = value.substring(0, setFix.dot) + "|" +
                                value.substring(setFix.dot);
                    }
                }

                if (setFix.namespace != null) {
                    // Workaround for the fact that the namespace-setter method
                    // doesn't seem to work on these documents
                    String prefix = document.lookupPrefix(setFix.namespace);
                    if (prefix != null) {
                        element.setAttribute(prefix + ":"
                                + setFix.attribute, value);
                    } else {
                        element.setAttributeNS(setFix.namespace,
                                setFix.attribute,
                                value);
                    }
                } else {
                    element.setAttribute(setFix.attribute, value);
                }
            }

            return XmlPrettyPrinter.prettyPrint(document, true);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String checkReplaceString(
            @NonNull ReplaceString replaceFix,
            @NonNull Warning warning,
            @NonNull String contents) {
        String oldPattern = replaceFix.oldPattern;
        String oldString = replaceFix.oldString;
        Location location = warning.location;

        Position start = location.getStart();
        Position end = location.getEnd();
        assert start != null;
        assert end != null;
        String locationRange = contents.substring(start.getOffset(), end.getOffset());
        int startOffset;
        int endOffset;

        if (oldString == null && oldPattern == null) {
            // Replace the whole range
            startOffset = start.getOffset();
            endOffset = end.getOffset();
        } else if (oldString != null) {
            int index = locationRange.indexOf(oldString);
            if (index != -1) {
                startOffset = start.getOffset() + index;
                endOffset = start.getOffset() + index + oldString.length();
            } else {
                fail("Did not find \"" + oldString + "\" in \"" + locationRange
                        + "\" as suggested in the quickfix");
                return null;
            }
        } else {
            //noinspection ConstantConditions
            assertTrue(oldPattern != null);
            Pattern pattern = Pattern.compile(oldPattern);
            Matcher matcher = pattern.matcher(locationRange);
            if (!matcher.find()) {
                fail("Did not match pattern \"" + oldPattern + "\" in \""
                        + locationRange
                        + "\" as suggested in the quickfix");
                return null;
            } else {
                startOffset = start.getOffset() + matcher.start(1);
                endOffset = start.getOffset() + matcher.end(1);
            }
        }

        return contents.substring(0, startOffset) + replaceFix.replacement +
                contents.substring(endOffset);
    }

    private void appendDiff(
            @NonNull Warning warning,
            @Nullable String fixDescription,
            @NonNull String before,
            @Nullable String after,
            @Nullable StringBuilder diffs) {
        if (after != null && diffs != null) {
            String diff = TestUtils.getDiff(before, after, diffWindow);
            if (!diff.isEmpty()) {
                String targetPath = warning.path;
                diffs.append("Fix for ").append(targetPath).append(" line ")
                        .append(warning.line).append(": ");
                if (fixDescription != null) {
                    diffs.append(fixDescription).append(":\n");
                }
                diffs.append(diff);
            }
        }
    }
}
