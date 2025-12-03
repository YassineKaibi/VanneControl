<#import "layout.ftl" as layout>

<@layout.layout title="User Management" activePage="users">
    <div class="users-container">
        <div class="page-header">
            <h1>User Management</h1>
            <p>Manage user accounts and permissions</p>
        </div>

        <div class="user-stats">
            <p><strong>Total Users:</strong> ${totalUsers}</p>
            <p><strong>Page:</strong> ${currentPage} of ${totalPages}</p>
        </div>

        <div class="table-container">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>Email</th>
                        <th>Name</th>
                        <th>Role</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    <#if users?? && (users?size > 0)>
                        <#list users as user>
                            <tr>
                                <td>${user.email}</td>
                                <td>
                                    <#if user.firstName?? && user.lastName??>
                                        ${user.firstName} ${user.lastName}
                                    <#else>
                                        <em>Not set</em>
                                    </#if>
                                </td>
                                <td>
                                    <span class="badge badge-${user.role}">
                                        ${user.role?upper_case}
                                    </span>
                                </td>
                                <td class="actions">
                                    <a href="/admin/users/${user.id}" class="btn btn-sm btn-primary">
                                        View
                                    </a>
                                </td>
                            </tr>
                        </#list>
                    <#else>
                        <tr>
                            <td colspan="4" class="no-data">No users found</td>
                        </tr>
                    </#if>
                </tbody>
            </table>
        </div>

        <#if totalPages gt 1>
            <div class="pagination">
                <#if currentPage gt 1>
                    <a href="/admin/users?page=${currentPage - 1}&limit=50" class="btn btn-sm">
                        ← Previous
                    </a>
                </#if>

                <span class="page-info">
                    Page ${currentPage} of ${totalPages}
                </span>

                <#if currentPage lt totalPages>
                    <a href="/admin/users?page=${currentPage + 1}&limit=50" class="btn btn-sm">
                        Next →
                    </a>
                </#if>
            </div>
        </#if>
    </div>
</@layout.layout>
