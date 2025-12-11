<#import "layout.ftl" as layout>

<@layout.layout title="User Devices - ${user.firstName!user.email}" activePage="users">
    <div class="user-devices-container">
        <div class="page-header">
            <div>
                <h1>Devices for ${user.firstName!""} ${user.lastName!""}</h1>
                <p class="text-muted">${user.email}</p>
            </div>
            <div class="header-actions">
                <a href="/admin/users/${user.id}/history" class="btn btn-primary">View Valve History</a>
                <a href="/admin/users/${user.id}" class="btn btn-secondary">← Back to User</a>
            </div>
        </div>

        <#-- Display success/error messages from query parameters -->
        <#if RequestParameters??>
            <#assign success = RequestParameters.success!"">
            <#assign error = RequestParameters.error!"">
        <#else>
            <#assign success = "">
            <#assign error = "">
        </#if>

        <#if success?has_content>
            <div class="alert alert-success">
                <#if success == "device_created">
                    ✓ Device created successfully!
                <#elseif success == "piston_controlled">
                    ✓ Piston controlled successfully!
                <#else>
                    ✓ Operation completed successfully!
                </#if>
            </div>
        </#if>

        <#if error?has_content>
            <div class="alert alert-error">
                <#if error == "missing_fields">
                    ✗ Please fill in all required fields.
                <#else>
                    ✗ Error: ${error}
                </#if>
            </div>
        </#if>

        <div class="create-device-section">
            <h2>Create New Device</h2>
            <form method="POST" action="/admin/users/${user.id}/devices/create" class="create-device-form">
                <div class="form-row">
                    <div class="form-group">
                        <label for="name">Device Name *</label>
                        <input
                            type="text"
                            id="name"
                            name="name"
                            placeholder="e.g., Living Room Controller"
                            required
                            class="form-control"
                        />
                    </div>

                    <div class="form-group">
                        <label for="mqttClientId">MQTT Client ID *</label>
                        <input
                            type="text"
                            id="mqttClientId"
                            name="mqttClientId"
                            placeholder="e.g., device-12345"
                            required
                            class="form-control"
                        />
                        <small class="form-hint">Must be globally unique across all devices</small>
                    </div>

                    <div class="form-actions">
                        <button type="submit" class="btn btn-primary">
                            Create Device
                        </button>
                    </div>
                </div>
            </form>
        </div>

        <div class="devices-section">
            <h2>Existing Devices</h2>
        </div>

        <#if devices?? && (devices?size > 0)>
            <#list devices as device>
                <div class="device-card">
                    <div class="device-header">
                        <div>
                            <h2>${device.name}</h2>
                            <p class="mono text-muted">ID: ${device.id}</p>
                            <p class="mono text-muted">MQTT Client: ${device.device_id}</p>
                        </div>
                        <div>
                            <span class="badge badge-${device.status}">
                                ${device.status?upper_case}
                            </span>
                        </div>
                    </div>

                    <div class="pistons-grid">
                        <#list device.pistons as piston>
                            <div class="piston-card piston-${piston.state}">
                                <div class="piston-info">
                                    <h3>Piston ${piston.piston_number}</h3>
                                    <span class="piston-status ${piston.state}">
                                        ${piston.state?upper_case}
                                    </span>
                                    <#if piston.last_triggered??>
                                        <p class="text-muted">Last: ${piston.last_triggered}</p>
                                    </#if>
                                </div>

                                <div class="piston-controls">
                                    <form
                                        method="POST"
                                        action="/admin/users/${user.id}/devices/${device.id}/pistons/${piston.piston_number}/control"
                                        style="display: inline;"
                                    >
                                        <input type="hidden" name="action" value="activate">
                                        <button
                                            type="submit"
                                            class="btn btn-success btn-sm"
                                            <#if piston.state == "active">disabled</#if>
                                        >
                                            Activate
                                        </button>
                                    </form>

                                    <form
                                        method="POST"
                                        action="/admin/users/${user.id}/devices/${device.id}/pistons/${piston.piston_number}/control"
                                        style="display: inline;"
                                    >
                                        <input type="hidden" name="action" value="deactivate">
                                        <button
                                            type="submit"
                                            class="btn btn-danger btn-sm"
                                            <#if piston.state == "inactive">disabled</#if>
                                        >
                                            Deactivate
                                        </button>
                                    </form>
                                </div>
                            </div>
                        </#list>
                    </div>
                </div>
            </#list>
        <#else>
            <div class="no-data-card">
                <p>This user has no devices registered.</p>
            </div>
        </#if>

        <#if telemetry?? && (telemetry?size > 0)>
            <div class="telemetry-section">
                <h2>Recent Activity</h2>
                <div class="table-container">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Timestamp</th>
                                <th>Device ID</th>
                                <th>Event Type</th>
                                <th>Details</th>
                            </tr>
                        </thead>
                        <tbody>
                            <#list telemetry as event>
                                <tr>
                                    <td class="timestamp">${event.createdAt}</td>
                                    <td class="mono">${event.deviceId}</td>
                                    <td>
                                        <span class="badge">
                                            ${event.eventType}
                                        </span>
                                    </td>
                                    <td>${event.payload!"-"}</td>
                                </tr>
                            </#list>
                        </tbody>
                    </table>
                </div>
            </div>
        </#if>
    </div>

    <style>
        .user-devices-container {
            padding: 20px;
        }

        .header-actions {
            display: flex;
            gap: 10px;
        }

        .device-card {
            background: white;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }

        .device-header {
            display: flex;
            justify-content: space-between;
            align-items: start;
            margin-bottom: 20px;
            padding-bottom: 15px;
            border-bottom: 1px solid #e5e7eb;
        }

        .pistons-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
            gap: 15px;
        }

        .piston-card {
            background: #f9fafb;
            border-radius: 6px;
            padding: 15px;
            border: 2px solid #e5e7eb;
        }

        .piston-card.piston-active {
            border-color: #10b981;
            background: #ecfdf5;
        }

        .piston-info {
            margin-bottom: 10px;
        }

        .piston-info h3 {
            margin: 0 0 5px 0;
            font-size: 16px;
        }

        .piston-status {
            display: inline-block;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 12px;
            font-weight: bold;
        }

        .piston-status.active {
            background: #10b981;
            color: white;
        }

        .piston-status.inactive {
            background: #6b7280;
            color: white;
        }

        .piston-controls {
            display: flex;
            gap: 5px;
        }

        .telemetry-section {
            margin-top: 30px;
            background: white;
            border-radius: 8px;
            padding: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }

        .no-data-card {
            background: white;
            border-radius: 8px;
            padding: 40px;
            text-align: center;
            color: #6b7280;
        }

        .badge-online {
            background: #10b981;
            color: white;
        }

        .badge-offline {
            background: #6b7280;
            color: white;
        }

        /* Alert messages */
        .alert {
            padding: 15px 20px;
            border-radius: 6px;
            margin-bottom: 20px;
            font-weight: 500;
        }

        .alert-success {
            background: #ecfdf5;
            border: 1px solid #10b981;
            color: #065f46;
        }

        .alert-error {
            background: #fef2f2;
            border: 1px solid #ef4444;
            color: #991b1b;
        }

        /* Create device form */
        .create-device-section {
            background: white;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }

        .create-device-section h2 {
            margin: 0 0 15px 0;
            font-size: 18px;
        }

        .create-device-form .form-row {
            display: grid;
            grid-template-columns: 1fr 1fr auto;
            gap: 15px;
            align-items: end;
        }

        .form-group {
            display: flex;
            flex-direction: column;
        }

        .form-group label {
            font-weight: 600;
            margin-bottom: 5px;
            font-size: 14px;
        }

        .form-control {
            padding: 8px 12px;
            border: 1px solid #d1d5db;
            border-radius: 4px;
            font-size: 14px;
        }

        .form-control:focus {
            outline: none;
            border-color: #3b82f6;
            box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
        }

        .form-hint {
            font-size: 12px;
            color: #6b7280;
            margin-top: 4px;
        }

        .form-actions {
            display: flex;
            align-items: flex-end;
        }

        .devices-section {
            margin-top: 0;
        }

        .devices-section h2 {
            font-size: 18px;
            margin-bottom: 15px;
        }

        /* Responsive design */
        @media (max-width: 768px) {
            .create-device-form .form-row {
                grid-template-columns: 1fr;
            }
        }
    </style>
</@layout.layout>
