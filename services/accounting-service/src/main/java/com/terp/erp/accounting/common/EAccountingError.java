package com.terp.erp.accounting.common;

import com.terp.erp.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Never renumber or reuse a code: the FE keys on it. */
@Getter
@RequiredArgsConstructor
public enum EAccountingError implements ErrorCode {
    ACC_001("Choose the company to act on."),
    ACC_002("The customer does not exist."),
    ACC_003("This user account is already linked to another customer."),
    ACC_004("The vendor does not exist."),
    ACC_005("This user account is already linked to another vendor."),
    ACC_006("The bank account does not exist."),
    ACC_007("This account number is already used in the company."),
    ACC_008("The bank account is inactive."),
    ACC_009("The account type does not exist."),
    ACC_010("The ledger account does not exist."),
    ACC_011("This account code is already used in the company."),
    ACC_012("The parent account is invalid."),
    ACC_013("The account is in use (journal lines, child accounts, mappings, categories or bank accounts) "
            + "and cannot be deleted."),
    ACC_014("The revenue category does not exist."),
    ACC_015("The expense category does not exist."),
    ACC_016("This category code is already used in the company."),
    ACC_017("System account {0} is not mapped to an active account."),
    ACC_018("The journal entry is not balanced: total debit must equal total credit."),
    ACC_019("A journal entry needs lines, each with either a debit or a credit greater than zero."),
    ACC_020("Account {0} cannot be posted to: it is inactive or belongs to another company."),
    ACC_021("The journal entry does not exist."),
    ACC_022("The bank transaction does not exist."),
    ACC_023("Only a cleared bank transaction can be reconciled."),
    ACC_024("The bank transfer does not exist."),
    ACC_025("The source and destination bank accounts must differ."),
    ACC_026("Only a pending bank transfer can be changed or processed."),
    ACC_027("The revenue does not exist."),
    ACC_028("The expense does not exist."),
    ACC_029("Only a draft can be changed, approved or deleted."),
    ACC_030("Only an approved record can be posted."),
    ACC_031("This account type code is already used in the company."),
    ACC_032("The account type is used by accounts and cannot be deleted."),
    ACC_033("The category is used by revenues or expenses and cannot be deleted."),
    ACC_050("The payment does not exist."),
    ACC_051("Only a pending payment can be cleared, cancelled or deleted."),
    ACC_052("Invoice {0} is not an invoice of this customer or vendor."),
    ACC_053("Invoice {0} is not open for payment: only a posted or partly paid invoice can be paid."),
    ACC_054("The amount for invoice {0} is more than its balance {1}."),
    ACC_055("An invoice or a note can be listed only once in a payment."),
    ACC_056("Note {0} cannot be applied: it must be an approved note of this customer or vendor with a balance left."),
    ACC_057("The amount for note {0} is more than its balance {1}."),
    ACC_058("Notes applied ({0}) cannot be more than the invoices allocated ({1})."),
    ACC_059("A payment needs at least one invoice."),
    ACC_060("The commerce service is unavailable. Please try again later."),
    ACC_061("The credit or debit note does not exist."),
    ACC_062("Only a draft note can be approved or deleted."),
    ACC_063("Return {0} is not completed: only a completed return gives a note."),
    ACC_064("Return {0} already has note {1}."),
    ACC_065("The period must end on or after its start and last at most {0} days."),
    ACC_066("The journal entry is not balanced: debit {0}, credit {1}, difference {2}."),
    ACC_067("Only a draft journal entry can be edited, deleted or posted."),
    ACC_068("An automatic journal entry cannot be edited or deleted."),
    ACC_069("Only a posted entry that is not itself a reversal can be reversed, and only once."),
    ACC_070("The books are closed up to {0}: no entry can be dated on or before that day."),
    ACC_071("The reversal date cannot be before the original entry date {0}."),
    ACC_072("The balance sheet does not exist."),
    ACC_073("Only a draft balance sheet can be regenerated, edited, deleted or finalised."),
    ACC_074("Only two finalised balance sheets of the same company can be compared."),
    ACC_075("Financial year {0} is already closed."),
    ACC_076("Financial year {0} has {1} draft journal entries: post or delete them before closing."),
    ACC_077("Date {0} is not in financial year {1}.");

    private final String message;
}
