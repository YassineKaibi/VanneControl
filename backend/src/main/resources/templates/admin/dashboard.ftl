<#import "layout.ftl" as layout>

<@layout.layout title="Dashboard" activePage="dashboard">
    <div class="dashboard-container">
        <div class="dashboard-header">
            <h1>Dashboard</h1>
            <p>System overview and recent activity</p>
        </div>

        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-icon">👥</div>
                <div class="stat-content">
                    <h3>${stats.totalUsers}</h3>
                    <p>Total Users</p>
                </div>
            </div>

            <div class="stat-card">
                <div class="stat-icon">👨‍💼</div>
                <div class="stat-content">
                    <h3>${stats.totalAdmins}</h3>
                    <p>Administrators</p>
                </div>
            </div>

            <div class="stat-card">
                <div class="stat-icon">📱</div>
                <div class="stat-content">
                    <h3>${stats.totalDevices}</h3>
                    <p>Connected Devices</p>
                </div>
            </div>

            <div class="stat-card">
                <div class="stat-icon">⏰</div>
                <div class="stat-content">
                    <h3>${stats.totalSchedules}</h3>
                    <p>Active Schedules</p>
                </div>
            </div>
        </div>

        <div class="quick-actions">
            <h2>Quick Actions</h2>
            <div class="action-buttons">
                <a href="/admin/users" class="btn btn-primary">
                    Manage Users
                </a>
                <a href="/admin/audit-logs" class="btn btn-secondary">
                    View Audit Logs
                </a>
            </div>
        </div>

        <div class="recent-activity">
            <h2>Recent Activity</h2>

            <#if recentLogs?? && (recentLogs?size > 0)>
                <table class="activity-table">
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>User</th>
                            <th>Action</th>
                            <th>Details</th>
                        </tr>
                    </thead>
                    <tbody>
                        <#list recentLogs as log>
                            <tr>
                                <td class="timestamp">${log.createdAt}</td>
                                <td>${log.userId}</td>
                                <td>
                                    <span class="badge">
                                        ${log.action}
                                    </span>
                                </td>
                                <td>${log.details!"-"}</td>
                            </tr>
                        </#list>
                    </tbody>
                </table>
            <#else>
                <p class="no-data">No recent activity</p>
            </#if>

            <div class="view-all">
                <a href="/admin/audit-logs" class="btn btn-link">View All Logs →</a>
            </div>
        </div>
    </div>
</@layout.layout>
