package com.terp.erp.support.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Support-service errors (ERP manual 18). Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum ESupportError implements ErrorCode {
    SUP_001("Choose the company to act on."),
    SUP_002("The media service is unavailable. Please try again later."),
    SUP_003("The IAM service is unavailable. Please try again later."),
    SUP_004("The ticket does not exist."),
    SUP_005("The ticket category does not exist."),
    SUP_006("The ticket is closed: it cannot receive replies."),
    SUP_007("File {0} cannot be attached: it is not a file of this company."),
    SUP_008("At most {0} attachments are allowed."),
    SUP_009("This support portal is not available."),
    SUP_010("No ticket matches this ticket number and email."),
    SUP_011("The slug must be 3 to 50 lowercase letters, digits and single hyphens."),
    SUP_012("This slug is already used by another company."),
    SUP_013("The ticket category is used by tickets and cannot be deleted."),
    SUP_014("The knowledge base category does not exist."),
    SUP_015("The knowledge base article does not exist."),
    SUP_016("The FAQ does not exist."),
    SUP_017("The knowledge base category still holds articles and cannot be deleted."),
    SUP_018("The file is empty or is not a CSV file."),
    SUP_019("The CSV file is too large (at most {0} KB)."),
    SUP_020("The CSV file has more than {0} rows."),
    SUP_021("The CSV header must be exactly: {0}."),
    SUP_022("Row {0}: \"{1}\" is required."),
    SUP_023("Row {0}: \"{1}\" is too long (at most {2} characters)."),
    SUP_024("Row {0}: category \"{1}\" does not exist."),
    SUP_025("The contact message does not exist."),
    SUP_026("The page does not exist."),
    SUP_027("This page slug is already used in your portal."),
    SUP_028("The messenger needs a user account of a company."),
    SUP_029("User {0} is not an active user of your company."),
    SUP_030("You cannot send a message to yourself."),
    SUP_031("A message needs text or an attachment."),
    SUP_032("Row {0}: the number of columns differs from the header."),
    SUP_033("The CSV file is malformed: a quoted value is not closed."),
    SUP_034("The description must not be empty."),
    SUP_035("Custom field {0} does not exist or is not active."),
    SUP_036("The field \"{0}\" is given more than once."),
    SUP_037("The field \"{0}\" is required."),
    SUP_038("The field \"{0}\" must be a valid {1}."),
    SUP_039("The field \"{0}\": \"{1}\" is not one of its options."),
    SUP_040("A SELECT field needs at least one option."),
    SUP_041("The width must be 3, 4, 6 or 12."),
    SUP_042("The ticket field does not exist."),
    SUP_043("Your account has no email address, so it cannot raise a ticket.");

    private final String message;
}
