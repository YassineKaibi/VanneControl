<#import "layout.ftl" as layout>

<@layout.layout title="User Details" activePage="users">
    <div class="user-detail-container">
        <div class="page-header">
            <h1>User Details</h1>
            <div class="header-actions">
                <a href="/admin/users/${user.id}/history" class="btn btn-primary">
                    View Valve History
                </a>
                <a href="/admin/users/${user.id}/devices" class="btn btn-primary">
                    View Devices & Control
                </a>
                <a href="/admin/users" class="btn btn-secondary">← Back to Users</a>
            </div>
        </div>

        <#assign success = RequestParameters.success!>
        <#assign error = RequestParameters.error!>

        <#if success == "role_updated">
            <div class="alert alert-success">User role updated successfully.</div>
        <#elseif success == "history_cleared">
            <div class="alert alert-success">User history and statistics cleared successfully.</div>
        </#if>

        <#if error?has_content>
            <div class="alert alert-error">Error: ${error}</div>
        </#if>

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
                    action="/admin/users/${user.id}/clear-history"
                    class="clear-history-form"
                    onsubmit="return confirm('Are you sure you want to clear all history and statistics for this user? This will delete all telemetry data for their devices. This action cannot be undone.');"
                >
                    <button type="submit" class="btn btn-warning">
                        Clear History & Statistics
                    </button>
                </form>

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

        .clear-history-form {
            margin-bottom: 15px;
        }

        .btn-warning {
            background-color: #f0ad4e;
            color: white;
            border: none;
            padding: 10px 20px;
            cursor: pointer;
            border-radius: 4px;
            width: 100%;
        }

        .btn-warning:hover {
            background-color: #ec971f;
        }
    </style>
</@layout.layout>
