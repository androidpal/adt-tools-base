/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.apkzlib.zip;

import com.android.apkzlib.zip.utils.MsDosDateTimeUtils;
import com.google.common.base.Verify;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.Nonnull;

/**
 * The Central Directory Header contains information about files stored in the zip. Instances of
 * this class contain information for files that already are in the zip and, for which the data was
 * read from the Central Directory. But some instances of this class are used for new files.
 * Because instances of this class can refer to files not yet on the zip, some of the fields may
 * not be filled in, or may be filled in with default values.
 * <p>
 * Because compression decision is done lazily, some data is stored with futures.
 */
public class CentralDirectoryHeader implements Cloneable {

    /**
     * Default "version made by" field: upper byte needs to be 0 to set to MS-DOS compatibility.
     * Lower byte can be anything, really. We use 18 because aapt uses 17 :)
     */
    private static final int DEFAULT_VERSION_MADE_BY = 0x0018;

    /**
     * Name of the file.
     */
    @Nonnull
    private String mName;

    /**
     * CRC32 of the data. 0 if not yet computed.
     */
    private long mCrc32;

    /**
     * Size of the file uncompressed. 0 if the file has no data.
     */
    private long mUncompressedSize;

    /**
     * Code of the program that made the zip. We actually don't care about this.
     */
    private long mMadeBy;

    /**
     * General-purpose bit flag.
     */
    @Nonnull
    private GPFlags mGpBit;

    /**
     * Last modification time in MS-DOS format (see {@link MsDosDateTimeUtils#packTime(long)}).
     */
    private long mLastModTime;

    /**
     * Last modification time in MS-DOS format (see {@link MsDosDateTimeUtils#packDate(long)}).
     */
    private long mLastModDate;

    /**
     * Extra data field contents. This field follows a specific structure according to the
     * specification.
     */
    @Nonnull
    private ExtraField mExtraField;

    /**
     * File comment.
     */
    @Nonnull
    private byte[] mComment;

    /**
     * File internal attributes.
     */
    private long mInternalAttributes;

    /**
     * File external attributes.
     */
    private long mExternalAttributes;

    /**
     * Offset in the file where the data is located. This will be -1 if the header corresponds to
     * a new file that is not yet written in the zip and, therefore, has no written data.
     */
    private long mOffset;

    /**
     * Encoded file name.
     */
    private byte[] mEncodedFileName;

    /**
     * Compress information that may not have been computed yet due to lazy compression.
     */
    @Nonnull
    private Future<CentralDirectoryHeaderCompressInfo> mCompressInfo;

    /**
     * The file this header belongs to.
     */
    @Nonnull
    private final ZFile mFile;

    /**
     * Creates data for a file.
     *
     * @param name the file name
     * @param uncompressedSize the uncompressed file size
     * @param compressInfo computation that defines the compression information
     * @param flags flags used in the entry
     * @param zFile the file this header belongs to
     */
    CentralDirectoryHeader(
            @Nonnull String name,
            long uncompressedSize,
            @Nonnull Future<CentralDirectoryHeaderCompressInfo> compressInfo,
            @Nonnull GPFlags flags,
            @Nonnull ZFile zFile) {
        mName = name;
        mUncompressedSize = uncompressedSize;
        mCrc32 = 0;

        /*
         * Set sensible defaults for the rest.
         */
        mMadeBy = DEFAULT_VERSION_MADE_BY;

        mGpBit = flags;
        mLastModTime = MsDosDateTimeUtils.packCurrentTime();
        mLastModDate = MsDosDateTimeUtils.packCurrentDate();
        mExtraField = new ExtraField();
        mComment = new byte[0];
        mInternalAttributes = 0;
        mExternalAttributes = 0;
        mOffset = -1;
        mEncodedFileName = EncodeUtils.encode(name, mGpBit);
        mCompressInfo = compressInfo;
        mFile = zFile;
    }

    /**
     * Obtains the name of the file.
     *
     * @return the name
     */
    @Nonnull
    public String getName() {
        return mName;
    }

    /**
     * Obtains the size of the uncompressed file.
     *
     * @return the size of the file
     */
    public long getUncompressedSize() {
        return mUncompressedSize;
    }

    /**
     * Obtains the CRC32 of the data.
     *
     * @return the CRC32, 0 if not yet computed
     */
    public long getCrc32() {
        return mCrc32;
    }

    /**
     * Sets the CRC32 of the data.
     *
     * @param crc32 the CRC 32
     */
    void setCrc32(long crc32) {
        mCrc32 = crc32;
    }

    /**
     * Obtains the code of the program that made the zip.
     *
     * @return the code
     */
    public long getMadeBy() {
        return mMadeBy;
    }

    /**
     * Sets the code of the progtram that made the zip.
     *
     * @param madeBy the code
     */
    void setMadeBy(long madeBy) {
        mMadeBy = madeBy;
    }

    /**
     * Obtains the general-purpose bit flag.
     *
     * @return the bit flag
     */
    @Nonnull
    public GPFlags getGpBit() {
        return mGpBit;
    }

    /**
     * Obtains the last modification time of the entry.
     *
     * @return the last modification time in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packTime(long)})
     */
    public long getLastModTime() {
        return mLastModTime;
    }

    /**
     * Sets the last modification time of the entry.
     *
     * @param lastModTime the last modification time in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packTime(long)})
     */
    void setLastModTime(long lastModTime) {
        mLastModTime = lastModTime;
    }

    /**
     * Obtains the last modification date of the entry.
     *
     * @return the last modification date in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packDate(long)})
     */
    public long getLastModDate() {
        return mLastModDate;
    }

    /**
     * Sets the last modification date of the entry.
     *
     * @param lastModDate the last modification date in MS-DOS format (see
     * {@link MsDosDateTimeUtils#packDate(long)})
     */
    void setLastModDate(long lastModDate) {
        mLastModDate = lastModDate;
    }

    /**
     * Obtains the data in the extra field.
     *
     * @return the data (returns an empty array if there is none)
     */
    @Nonnull
    public ExtraField getExtraField() {
        return mExtraField;
    }

    /**
     * Sets the data in the extra field.
     *
     * @param extraField the data to set
     */
    public void setExtraField(@Nonnull ExtraField extraField) {
        setExtraFieldNoNotify(extraField);
        mFile.centralDirectoryChanged();
    }

    /**
     * Sets the data in the extra field, but does not notify {@link ZFile}. This method is invoked
     * when the {@link ZFile} knows the extra field is being set.
     *
     * @param extraField the data to set
     */
    void setExtraFieldNoNotify(@Nonnull ExtraField extraField) {
        mExtraField = extraField;
    }

    /**
     * Obtains the entry's comment.
     *
     * @return the comment (returns an empty array if there is no comment)
     */
    @Nonnull
    public byte[] getComment() {
        return mComment;
    }

    /**
     * Sets the entry's comment.
     *
     * @param comment the comment
     */
    void setComment(@Nonnull byte[] comment) {
        mComment = comment;
    }

    /**
     * Obtains the entry's internal attributes.
     *
     * @return the entry's internal attributes
     */
    public long getInternalAttributes() {
        return mInternalAttributes;
    }

    /**
     * Sets the entry's internal attributes.
     *
     * @param internalAttributes the entry's internal attributes
     */
    void setInternalAttributes(long internalAttributes) {
        mInternalAttributes = internalAttributes;
    }

    /**
     * Obtains the entry's external attributes.
     *
     * @return the entry's external attributes
     */
    public long getExternalAttributes() {
        return mExternalAttributes;
    }

    /**
     * Sets the entry's external attributes.
     *
     * @param externalAttributes the entry's external attributes
     */
    void setExternalAttributes(long externalAttributes) {
        mExternalAttributes = externalAttributes;
    }

    /**
     * Obtains the offset in the zip file where this entry's data is.
     *
     * @return the offset or {@code -1} if the file has no data in the zip and, therefore, data
     * is stored in memory
     */
    public long getOffset() {
        return mOffset;
    }

    /**
     * Sets the offset in the zip file where this entry's data is.
     *
     * @param offset the offset or {@code -1} if the file is new and has no data in the zip yet
     */
    void setOffset(long offset) {
        mOffset = offset;
    }

    /**
     * Obtains the encoded file name.
     *
     * @return the encoded file name
     */
    public byte[] getEncodedFileName() {
        return mEncodedFileName;
    }

    /**
     * Resets the deferred CRC flag in the GP flags.
     */
    void resetDeferredCrc() {
        /*
         * We actually create a new set of flags. Since the only information we care about is the
         * UTF-8 encoding, we'll just create a brand new object.
         */
        mGpBit = GPFlags.make(mGpBit.isUtf8FileName());
    }

    @Override
    protected CentralDirectoryHeader clone() throws CloneNotSupportedException {
        CentralDirectoryHeader cdr = (CentralDirectoryHeader) super.clone();
        cdr.mExtraField = mExtraField;
        cdr.mComment = Arrays.copyOf(mComment, mComment.length);
        cdr.mEncodedFileName = Arrays.copyOf(mEncodedFileName, mEncodedFileName.length);
        return cdr;
    }

    /**
     * Obtains the future with the compression information.
     *
     * @return the information
     */
    @Nonnull
    public Future<CentralDirectoryHeaderCompressInfo> getCompressionInfo() {
        return mCompressInfo;
    }

    /**
     * Equivalent to {@code getCompressionInfo().get()} but masking the possible exceptions and
     * guaranteeing non-{@code null} return.
     *
     * @return the result of the future
     * @throws IOException failed to get the information
     */
    @Nonnull
    public CentralDirectoryHeaderCompressInfo getCompressionInfoWithWait()
            throws IOException {
        try {
            CentralDirectoryHeaderCompressInfo info = getCompressionInfo().get();
            Verify.verifyNotNull(info, "info == null");
            return info;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while waiting for compression information.", e);
        } catch (ExecutionException e) {
            throw new IOException("Execution of compression failed.", e);
        }
    }
}