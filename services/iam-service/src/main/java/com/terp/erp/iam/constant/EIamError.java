package com.terp.erp.iam.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.terp.erp.exception.ErrorCode;

/** Never renumber or reuse a code: the FE and the app key on it. */
@Getter
@RequiredArgsConstructor
public enum EIamError implements ErrorCode {
    AUTH_001("Username or password is incorrect."),
    AUTH_002("The account is locked or does not belong to an active company."),
    AUTH_003("The refresh token is invalid or expired."),
    AUTH_004("The password must be changed before the first login."),
    AUTH_005("The authentication server is unavailable. Please try again later."),
    AUTH_006("This account is signed in but has no ERP profile. Contact your administrator."),
    AUTH_007("The new password does not meet the password policy."),
    AUTH_008("Too many failed attempts. The account is temporarily locked."),
    AUTH_009("The company subscription has expired. Ask your company administrator to renew it."),
    AUTH_010("The reCAPTCHA check failed. Please try again."),
    AUTH_011("The reCAPTCHA service is unavailable. Please try again later."),
    AUTH_012("Sign-in is not allowed from this IP address."),
    AUTH_013("Too many requests. Please try again later."),
    AUTH_014("The link is invalid or has expired."),
    AUTH_015("Self-registration is not enabled."),
    AUTH_016("The company email address has not been verified yet. Check the inbox for the verification link."),
    AUTH_017("The current password is incorrect."),
    IAM_001("The company does not exist."),
    IAM_002("The company code is already taken."),
    IAM_003("A company must be specified for this action."),
    IAM_004("The role does not exist."),
    IAM_005("The role code is already taken in this company."),
    IAM_006("A system role cannot be deleted or renamed."),
    IAM_007("The role is still assigned to users."),
    IAM_008("Unknown permission code(s): {0}."),
    IAM_009("The user does not exist."),
    IAM_010("The username is already taken."),
    IAM_011("One or more roles do not exist in this company."),
    IAM_012("The permissions of the company administrator role cannot be reduced."),
    IAM_013("You cannot change your own roles or status."),
    IAM_014("The user could not be created in the authentication server."),
    IAM_015("You cannot grant permissions you do not hold: {0}."),
    IAM_016("The company has no active administrator account."),
    IAM_017("This email is already used by another account."),
    IAM_018("The last active company administrator cannot be deleted."),
    IAM_019("The login record does not exist."),
    IAM_020("Enter an IPv4 / IPv6 address or a CIDR range: {0}."),
    IAM_021("This would block your own IP address {0}; add it first."),
    IAM_022("This IP address is already in the list."),
    IAM_023("The IP address entry does not exist."),
    IAM_024("The avatar must be a JPG, PNG or GIF image of your company, at most 2 MB."),
    IAM_025("The media service is unavailable. Please try again later."),
    IAM_026("The language {0} already exists."),
    IAM_027("The language {0} does not exist."),
    IAM_028("A system language (vi, en, ko) cannot be deleted."),
    IAM_029("The default language cannot be disabled or deleted. Choose another default language first."),
    IAM_030("Invalid label: {0}. Keys use letters, digits, dot, dash or underscore (max 200); texts at most 2000 characters; an import has 1 to 10000 keys; new keys come in through import.");

    private final String message;
}
