<#import "layout.ftl" as layout>

<@layout.layout title="User Details" activePage="users">
    <div class="user-detail-container">
        <div class="page-header">
            <h1>User Details</h1>
            <div class="header-actions">
                <a href="/admin/users/${user.id}/devices" class="btn btn-primary">
                    View Devices & Control
                </a>
                <a href="/admin/users" class="btn btn-secondary">← Back to Users</a>
            </div>
        </div>

        <div class="user-detail-grid">
            <div class="info-card">
                <h2>User Information</h2>

                <div class="info-row">
                    <label>User ID:</label>
                    <span class="mono">${user.id}</span>
                </div>

                <div class="info-row">
                    <label>Email:</label>
                    <span>${user.email}</span>
                </div>

                <div class="info-row">
                    <label>Name:</label>
                    <span>
                        <#if user.firstName?? && user.lastName??>
                            ${user.firstName} ${user.lastName}
                        <#else>
                            <em>Not set</em>
                        </#if>
                    </span>
                </div>

                <div class="info-row">
                    <label>Phone:</label>
                    <span>${user.phoneNumber!"-"}</span>
                </div>

                <div class="info-row">
                    <label>Location:</label>
                    <span>${user.location!"-"}</span>
                </div>

                <div class="info-row">
                    <label>Role:</label>
                    <span class="badge badge-${user.role}">
                        ${user.role?upper_case}
                    </span>
                </div>
            </div>

            <div class="action-card">
                <h2>Role Management</h2>

                <form method="POST" action="/admin/users/${user.id}/role" class="role-form">
                    <div class="form-group">
                        <label for="role">Change User Role:</label>
                        <select id="role" name="role" class="form-control">
                            <option value="user" <#if user.role == "user">selected</#if>>User</option>
                            <option value="admin" <#if user.role == "admin">selected</#if>>Admin</option>
                        </select>
                    </div>

                    <button type="submit" class="btn btn-primary">
                        Update Role
                    </button>
                </form>

                <hr>

                <h3>Danger Zone</h3>
                <form
                    method="POST"
                    action="/admin/users/${user.id}/delete"
                    class="delete-form"
                    onsubmit="return confirm('Are you sure you want to delete this user? This action cannot be undone.');"
                >
                    <button type="submit" class="btn btn-danger">
                        Delete User
                    </button>
                </form>
            </div>
        </div>
    </div>

    <style>
        .header-actions {
            display: flex;
            gap: 10px;
        }
    </style>
</@layout.layout>
