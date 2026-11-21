package com.terp.erp.crm.record;

/** Value sets of leads, deals and their activity. */
public final class CrmEnums {
    private CrmEnums() {
    }

    /** What an activity row or link belongs to. */
    public enum ESubject { LEAD, DEAL }

    /** Deal outcome; the demo shows Active and Loss, the client view counts Active and Won deals. */
    public enum EDealStatus { ACTIVE, WON, LOSS }

    /** MEMBER = IAM user working on it; CLIENT = accounting customer (deals); LABEL; SOURCE; PRODUCT = inventory item. */
    public enum ELinkType { MEMBER, CLIENT, LABEL, SOURCE, PRODUCT }

    public enum ETaskPriority { LOW, MEDIUM, HIGH }

    // TODO(assumption): the manual names a task status but not its values — open / done.
    public enum ETaskStatus { OPEN, DONE }

    public enum ECallType { INBOUND, OUTBOUND }

    /** DISCUSSION comment, EMAIL logged (not sent), FILE of media-service, ACTIVITY written by the system. */
    public enum EEntryType { DISCUSSION, EMAIL, FILE, ACTIVITY }
}
