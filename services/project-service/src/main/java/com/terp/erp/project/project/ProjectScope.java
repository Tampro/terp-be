package com.terp.erp.project.project;

import com.terp.erp.security.CurrentUser;
import com.terp.erp.security.ErpPrincipal;
import com.terp.erp.security.OwnScope;

import java.util.Objects;
import java.util.Set;

/**
 * "Own records only" scope of projects and their tasks / bugs.
 * <ul>
 *   <li>Visibility: PROJECT_VIEW, PROJECT_MANAGE or PROJECT_TASK_MANAGE (full) see every project of the company;
 *       otherwise (PROJECT_VIEW_OWN, or only _OWN codes) a project I am on the team of or created.</li>
 *   <li>PROJECT_MANAGE_OWN: projects I created (there is no project-lead field).</li>
 *   <li>PROJECT_TASK_MANAGE_OWN: tasks assigned to me or created by me, bugs assigned to me or created by me, in a
 *       project I can see.</li>
 * </ul>
 * Another user's row answers 404 like one of another company.
 */
public final class ProjectScope {
    public static final String VIEW = "PROJECT_VIEW";
    public static final String VIEW_OWN = "PROJECT_VIEW_OWN";
    public static final String MANAGE = "PROJECT_MANAGE";
    public static final String MANAGE_OWN = "PROJECT_MANAGE_OWN";
    public static final String TASK_MANAGE = "PROJECT_TASK_MANAGE";
    public static final String TASK_MANAGE_OWN = "PROJECT_TASK_MANAGE_OWN";

    private ProjectScope() {
    }

    public static boolean seesAll() {
        ErpPrincipal p = CurrentUser.get();
        return p.has(VIEW) || p.has(MANAGE) || p.has(TASK_MANAGE);
    }

    public static boolean managesAll() {
        return CurrentUser.get().has(MANAGE);
    }

    public static boolean managesAllTasks() {
        return CurrentUser.get().has(TASK_MANAGE);
    }

    public static boolean onTeam(Project p) {
        Long me = OwnScope.userId();
        return (me != null && p.getUserIds().contains(me)) || createdByMe(p.getCreatedBy());
    }

    public static boolean ownsTask(Set<Long> assignees, String createdBy) {
        Long me = OwnScope.userId();
        return (me != null && assignees.contains(me)) || createdByMe(createdBy);
    }

    public static boolean ownsBug(Long assignee, String createdBy) {
        Long me = OwnScope.userId();
        return (me != null && me.equals(assignee)) || createdByMe(createdBy);
    }

    public static boolean createdByMe(String createdBy) {
        return Objects.equals(createdBy, OwnScope.sub());
    }
}
