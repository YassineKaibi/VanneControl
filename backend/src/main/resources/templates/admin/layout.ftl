<#macro layout title activePage="">
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${title} - Piston Control Admin</title>
    <link rel="stylesheet" href="/admin/static/css/admin.css">
</head>
<body>
    <#if session??>
    <nav class="navbar">
        <div class="nav-container">
            <div class="nav-brand">
                <h1>Piston Control Admin</h1>
            </div>

            <ul class="nav-menu">
                <li><a href="/admin/dashboard" class="${(activePage == 'dashboard')?then('active', '')}">Dashboard</a></li>
                <li><a href="/admin/users" class="${(activePage == 'users')?then('active', '')}">Users</a></li>
                <li><a href="/admin/audit-logs" class="${(activePage == 'logs')?then('active', '')}">Audit Logs</a></li>
            </ul>

            <div class="nav-user">
                <span class="user-email">${session.email}</span>
                <a href="/admin/logout" class="btn-logout">Logout</a>
            </div>
        </div>
    </nav>
    </#if>

    <main class="main-content">
        <#nested>
    </main>

    <footer class="footer">
        <p>&copy; 2024 Piston Control IoT System | Admin Dashboard v1.0</p>
    </footer>

    <script src="/admin/static/js/admin.js"></script>
</body>
</html>
</#macro>
