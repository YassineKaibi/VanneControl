<#import "layout.ftl" as layout>

<@layout.layout title="Audit Logs" activePage="logs">
    <div class="audit-logs-container">
        <div class="page-header">
            <h1>Audit Logs</h1>
            <p>Complete history of administrative actions</p>
        </div>

        <div class="log-stats">
            <p><strong>Total Entries:</strong> ${totalLogs}</p>
            <p><strong>Page:</strong> ${currentPage} of ${totalPages}</p>
        </div>

        <div class="table-container">
            <table class="data-table logs-table">
                <thead>
                    <tr>
                        <th>Timestamp</th>
                        <th>User</th>
                        <th>Action</th>
                        <th>Target</th>
                        <th>Details</th>
                    </tr>
                </thead>
                <tbody>
                    <#if logs?? && (logs?size > 0)>
                        <#list logs as log>
                            <tr>
                                <td class="timestamp">${log.createdAt}</td>
                                <td>
                                    <#if log.userFullName??>
                                        <strong>${log.userFullName}</strong>
                                        <br><small class="mono text-muted">${log.userId}</small>
                                    <#else>
                                        <span class="mono">${log.userId}</span>
                                    </#if>
                                </td>
                                <td>
                                    <span class="badge">
                                        ${log.action}
                                    </span>
                                </td>
                                <td>
                                    <#if log.targetUserId??>
                                        <#if log.targetUserFullName??>
                                            <strong>${log.targetUserFullName}</strong>
                                            <br><small class="mono text-muted">User: ${log.targetUserId}</small>
                                        <#else>
                                            User: ${log.targetUserId}
                                        </#if>
                                    <#elseif log.targetResourceType??>
                                        ${log.targetResourceType}: ${log.targetResourceId}
                                    <#else>
                                        -
                                    </#if>
                                </td>
                                <td>${log.details!"-"}</td>
                            </tr>
                        </#list>
                    <#else>
                        <tr>
                            <td colspan="5" class="no-data">No audit logs found</td>
                        </tr>
                    </#if>
                </tbody>
            </table>
        </div>

        <#if totalPages gt 1>
            <div class="pagination">
                <#if currentPage gt 1>
                    <a href="/admin/audit-logs?page=${currentPage - 1}&limit=100" class="btn btn-sm">
                        ← Previous
                    </a>
                </#if>

                <span class="page-info">
                    Page ${currentPage} of ${totalPages}
                </span>

                <#if currentPage lt totalPages>
                    <a href="/admin/audit-logs?page=${currentPage + 1}&limit=100" class="btn btn-sm">
                        Next →
                    </a>
                </#if>
            </div>
        </#if>
    </div>
</@layout.layout>
