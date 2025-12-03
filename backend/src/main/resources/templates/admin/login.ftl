<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Admin Login - Piston Control</title>
    <link rel="stylesheet" href="/admin/static/css/admin.css">
</head>
<body class="login-page">
    <div class="login-container">
        <div class="login-card">
            <div class="login-header">
                <h1>Piston Control</h1>
                <h2>Admin Dashboard</h2>
            </div>

            <#if error??>
                <div class="alert alert-error">
                    <#if error == "missing_fields">
                        Please enter both email and password.
                    <#elseif error == "invalid_credentials">
                        Invalid email or password.
                    <#elseif error == "not_authorized">
                        You do not have admin privileges.
                    <#else>
                        An error occurred. Please try again.
                    </#if>
                </div>
            </#if>

            <form method="POST" action="/admin/login" class="login-form">
                <div class="form-group">
                    <label for="email">Email Address</label>
                    <input
                        type="email"
                        id="email"
                        name="email"
                        placeholder="admin@example.com"
                        required
                        autofocus
                    >
                </div>

                <div class="form-group">
                    <label for="password">Password</label>
                    <input
                        type="password"
                        id="password"
                        name="password"
                        placeholder="••••••••"
                        required
                    >
                </div>

                <button type="submit" class="btn btn-primary btn-block">
                    Sign In
                </button>
            </form>

            <div class="login-footer">
                <p><small>For security reasons, only administrators can access this dashboard.</small></p>
            </div>
        </div>
    </div>
</body>
</html>
