<#import "layout.ftl" as layout>

<@layout.layout title="Valve History - ${user.firstName!user.email}" activePage="users">
    <div class="history-container">
        <div class="page-header">
            <div>
                <h1>Valve Activation/Deactivation History</h1>
                <p class="text-muted">${user.firstName!""} ${user.lastName!""} (${user.email})</p>
            </div>
            <div class="header-actions">
                <a href="/admin/users/${user.id}/devices" class="btn btn-secondary">← Back to Devices</a>
                <a href="/admin/users/${user.id}" class="btn btn-secondary">User Details</a>
            </div>
        </div>

        <!-- Filter Panel -->
        <div class="filter-card">
            <div class="filter-header">
                <h2>Filters</h2>
                <button id="clearFiltersBtn" class="btn btn-link">Clear All Filters</button>
            </div>

            <form id="filterForm" method="GET" action="/admin/users/${user.id}/history">
                <div class="filter-grid">
                    <!-- Piston Number Filter -->
                    <div class="filter-group">
                        <label for="pistonNumber">Piston Number</label>
                        <select name="pistonNumber" id="pistonNumber" class="form-control">
                            <option value="">All Pistons</option>
                            <#list 1..8 as i>
                                <option value="${i}" <#if filters.pistonNumber?? && filters.pistonNumber == i>selected</#if>>
                                    Piston ${i}
                                </option>
                            </#list>
                        </select>
                    </div>

                    <!-- Action Filter -->
                    <div class="filter-group">
                        <label for="action">Action Type</label>
                        <select name="action" id="action" class="form-control">
                            <option value="">All Actions</option>
                            <option value="activated" <#if filters.action?? && filters.action == "activated">selected</#if>>
                                Activated
                            </option>
                            <option value="deactivated" <#if filters.action?? && filters.action == "deactivated">selected</#if>>
                                Deactivated
                            </option>
                        </select>
                    </div>

                    <!-- Start Date Filter -->
                    <div class="filter-group">
                        <label for="startDate">Start Date</label>
                        <input
                            type="date"
                            name="startDate"
                            id="startDate"
                            class="form-control"
                            value="${filters.startDate!""}"
                        >
                    </div>

                    <!-- End Date Filter -->
                    <div class="filter-group">
                        <label for="endDate">End Date</label>
                        <input
                            type="date"
                            name="endDate"
                            id="endDate"
                            class="form-control"
                            value="${filters.endDate!""}"
                        >
                    </div>
                </div>

                <div class="filter-actions">
                    <button type="submit" class="btn btn-primary">Apply Filters</button>
                </div>
            </form>

            <!-- Active Filters Display -->
            <#if filters.pistonNumber?? || filters.action?? || filters.startDate?? || filters.endDate??>
                <div class="active-filters">
                    <span class="filter-label">Active filters:</span>
                    <#if filters.pistonNumber??>
                        <span class="filter-chip">
                            Piston ${filters.pistonNumber}
                            <a href="?<#if filters.action??>action=${filters.action}&</#if><#if filters.startDate??>startDate=${filters.startDate}&</#if><#if filters.endDate??>endDate=${filters.endDate}</#if>" class="remove-filter">&times;</a>
                        </span>
                    </#if>
                    <#if filters.action??>
                        <span class="filter-chip">
                            ${filters.action?capitalize}
                            <a href="?<#if filters.pistonNumber??>pistonNumber=${filters.pistonNumber}&</#if><#if filters.startDate??>startDate=${filters.startDate}&</#if><#if filters.endDate??>endDate=${filters.endDate}</#if>" class="remove-filter">&times;</a>
                        </span>
                    </#if>
                    <#if filters.startDate??>
                        <span class="filter-chip">
                            From: ${filters.startDate}
                            <a href="?<#if filters.pistonNumber??>pistonNumber=${filters.pistonNumber}&</#if><#if filters.action??>action=${filters.action}&</#if><#if filters.endDate??>endDate=${filters.endDate}</#if>" class="remove-filter">&times;</a>
                        </span>
                    </#if>
                    <#if filters.endDate??>
                        <span class="filter-chip">
                            To: ${filters.endDate}
                            <a href="?<#if filters.pistonNumber??>pistonNumber=${filters.pistonNumber}&</#if><#if filters.action??>action=${filters.action}&</#if><#if filters.startDate??>startDate=${filters.startDate}</#if>" class="remove-filter">&times;</a>
                        </span>
                    </#if>
                </div>
            </#if>
        </div>

        <!-- Results Count -->
        <div class="results-summary">
            <p>Showing ${history?size} result(s)</p>
        </div>

        <!-- History Table -->
        <#if history?? && (history?size > 0)>
            <div class="history-table-card">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th>Timestamp</th>
                            <th>Device ID</th>
                            <th>Piston</th>
                            <th>Action</th>
                            <th>Details</th>
                        </tr>
                    </thead>
                    <tbody>
                        <#list history as event>
                            <tr>
                                <td class="timestamp">${event.createdAt}</td>
                                <td class="mono">${event.deviceId}</td>
                                <td>
                                    <#if event.pistonId??>
                                        ${event.pistonId}
                                    <#else>
                                        -
                                    </#if>
                                </td>
                                <td>
                                    <span class="badge badge-${event.eventType}">
                                        ${event.eventType?upper_case}
                                    </span>
                                </td>
                                <td class="payload-cell">
                                    <#if event.payload??>
                                        <details>
                                            <summary>View Details</summary>
                                            <pre>${event.payload}</pre>
                                        </details>
                                    <#else>
                                        -
                                    </#if>
                                </td>
                            </tr>
                        </#list>
                    </tbody>
                </table>
            </div>
        <#else>
            <div class="no-data-card">
                <svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" fill="currentColor" viewBox="0 0 16 16">
                    <path d="M8 15A7 7 0 1 1 8 1a7 7 0 0 1 0 14zm0 1A8 8 0 1 0 8 0a8 8 0 0 0 0 16z"/>
                    <path d="M7.002 11a1 1 0 1 1 2 0 1 1 0 0 1-2 0zM7.1 4.995a.905.905 0 1 1 1.8 0l-.35 3.507a.552.552 0 0 1-1.1 0L7.1 4.995z"/>
                </svg>
                <p>No valve history found</p>
                <p class="text-muted">Try adjusting your filters or this user may not have any valve activations/deactivations yet.</p>
            </div>
        </#if>
    </div>

    <style>
        .history-container {
            padding: 20px;
            max-width: 1400px;
            margin: 0 auto;
        }

        .page-header {
            display: flex;
            justify-content: space-between;
            align-items: start;
            margin-bottom: 24px;
        }

        .page-header h1 {
            margin: 0 0 8px 0;
            font-size: 28px;
            font-weight: 700;
        }

        .header-actions {
            display: flex;
            gap: 10px;
        }

        .filter-card {
            background: white;
            border-radius: 8px;
            padding: 24px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }

        .filter-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 20px;
        }

        .filter-header h2 {
            margin: 0;
            font-size: 18px;
            font-weight: 600;
        }

        .filter-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            margin-bottom: 20px;
        }

        .filter-group {
            display: flex;
            flex-direction: column;
        }

        .filter-group label {
            font-weight: 500;
            margin-bottom: 6px;
            font-size: 14px;
            color: #374151;
        }

        .form-control {
            padding: 8px 12px;
            border: 1px solid #d1d5db;
            border-radius: 6px;
            font-size: 14px;
            transition: border-color 0.2s;
        }

        .form-control:focus {
            outline: none;
            border-color: #3b82f6;
            box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
        }

        .filter-actions {
            display: flex;
            justify-content: flex-end;
        }

        .active-filters {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            align-items: center;
            margin-top: 16px;
            padding-top: 16px;
            border-top: 1px solid #e5e7eb;
        }

        .filter-label {
            font-weight: 600;
            font-size: 13px;
            color: #6b7280;
        }

        .filter-chip {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: #e0e7ff;
            color: #3730a3;
            padding: 4px 12px;
            border-radius: 16px;
            font-size: 13px;
            font-weight: 500;
        }

        .remove-filter {
            color: #3730a3;
            text-decoration: none;
            font-size: 18px;
            font-weight: bold;
            line-height: 1;
            margin-left: 4px;
        }

        .remove-filter:hover {
            color: #1e1b4b;
        }

        .results-summary {
            margin-bottom: 16px;
        }

        .results-summary p {
            margin: 0;
            color: #6b7280;
            font-size: 14px;
        }

        .history-table-card {
            background: white;
            border-radius: 8px;
            overflow: hidden;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }

        .data-table {
            width: 100%;
            border-collapse: collapse;
        }

        .data-table thead {
            background: #f9fafb;
            border-bottom: 2px solid #e5e7eb;
        }

        .data-table th {
            padding: 12px 16px;
            text-align: left;
            font-size: 13px;
            font-weight: 600;
            color: #374151;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .data-table td {
            padding: 12px 16px;
            border-bottom: 1px solid #e5e7eb;
            font-size: 14px;
        }

        .data-table tbody tr:hover {
            background: #f9fafb;
        }

        .timestamp {
            color: #6b7280;
            font-size: 13px;
        }

        .mono {
            font-family: 'Courier New', monospace;
            font-size: 12px;
            color: #4b5563;
        }

        .badge {
            display: inline-block;
            padding: 4px 10px;
            border-radius: 12px;
            font-size: 11px;
            font-weight: 600;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }

        .badge-activated {
            background: #d1fae5;
            color: #065f46;
        }

        .badge-deactivated {
            background: #fee2e2;
            color: #991b1b;
        }

        .payload-cell details {
            cursor: pointer;
        }

        .payload-cell summary {
            color: #3b82f6;
            font-size: 13px;
            user-select: none;
        }

        .payload-cell summary:hover {
            text-decoration: underline;
        }

        .payload-cell pre {
            margin-top: 8px;
            padding: 12px;
            background: #f9fafb;
            border: 1px solid #e5e7eb;
            border-radius: 4px;
            font-size: 12px;
            overflow-x: auto;
            max-width: 400px;
        }

        .no-data-card {
            background: white;
            border-radius: 8px;
            padding: 60px 40px;
            text-align: center;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }

        .no-data-card svg {
            color: #d1d5db;
            margin-bottom: 16px;
        }

        .no-data-card p {
            margin: 8px 0;
            font-size: 16px;
        }

        .no-data-card p:first-of-type {
            color: #374151;
            font-weight: 500;
        }

        .btn-link {
            background: none;
            border: none;
            color: #3b82f6;
            cursor: pointer;
            font-size: 14px;
            text-decoration: none;
            padding: 0;
        }

        .btn-link:hover {
            text-decoration: underline;
        }
    </style>

    <script>
        document.getElementById('clearFiltersBtn').addEventListener('click', function(e) {
            e.preventDefault();
            window.location.href = '/admin/users/${user.id}/history';
        });

        // Convert date inputs to ISO format on submit
        document.getElementById('filterForm').addEventListener('submit', function(e) {
            const startDate = document.getElementById('startDate');
            const endDate = document.getElementById('endDate');

            if (startDate.value) {
                startDate.value = new Date(startDate.value + 'T00:00:00').toISOString();
            }

            if (endDate.value) {
                endDate.value = new Date(endDate.value + 'T23:59:59').toISOString();
            }
        });
    </script>
</@layout.layout>
