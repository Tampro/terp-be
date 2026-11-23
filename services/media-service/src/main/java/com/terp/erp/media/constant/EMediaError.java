package com.terp.erp.media.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EMediaError implements ErrorCode {
    MEDIA_001("The file does not exist."),
    MEDIA_002("The folder does not exist."),
    MEDIA_003("A folder with this name already exists."),
    MEDIA_004("This file type is not allowed: {0}."),
    MEDIA_005("The file is larger than the limit of {0}."),
    MEDIA_006("The file content does not match its type."),
    MEDIA_007("The link is invalid or has expired."),
    MEDIA_008("No file was sent."),
    MEDIA_009("The file could not be stored. Please try again."),
    MEDIA_010("Storage limit of {0} GB reached."),
    MEDIA_011("The storage limit could not be checked. Please try again later."),
    MEDIA_012("The {0} storage is not configured on this server."),
    MEDIA_013("The storage endpoint is not allowed: {0}."),
    MEDIA_014("The storage settings could not be read. Please try again later."),
    MEDIA_015("The storage test failed at step {0}.");

    private final String message;
}
