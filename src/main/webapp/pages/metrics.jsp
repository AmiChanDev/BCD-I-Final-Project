<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Performance Metrics — TechMart</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
    <!-- Auto-refresh every 10 seconds -->
    <meta http-equiv="refresh" content="10">
</head>
<body>
<jsp:include page="/pages/header.jsp"/>

<main class="container">
    <div class="metrics-header">
        <h2>Performance Metrics Dashboard</h2>
        <div class="metrics-meta">
            <span>Application started: <strong>${startTime}</strong></span>
            <span class="refresh-note">Auto-refreshes every 10s &nbsp;|&nbsp;
                <a href="${pageContext.request.contextPath}/metrics?json">JSON API</a>
            </span>
        </div>
    </div>

    <!-- Summary Table -->
    <section class="metrics-section">
        <h3>Component Operation Summary</h3>
        <c:choose>
            <c:when test="${not empty summary}">
                <table class="metrics-table">
                    <thead>
                        <tr>
                            <th>Component</th>
                            <th>Operation</th>
                            <th>Invocations</th>
                            <th>Avg Latency (ms)</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        <c:forEach var="row" items="${summary}">
                            <tr>
                                <td><code>${row.key.substring(0, row.key.lastIndexOf('.'))}</code></td>
                                <td>${row.key.substring(row.key.lastIndexOf('.') + 1)}</td>
                                <td class="num">${row.invocations}</td>
                                <td class="num latency ${row.avgLatencyMs > 100 ? 'latency-warn' : 'latency-ok'}">
                                    <fmt:formatNumber value="${row.avgLatencyMs}" pattern="#,##0.00"/>
                                </td>
                                <td>
                                    <span class="badge ${row.avgLatencyMs > 500 ? 'badge-danger' : row.avgLatencyMs > 100 ? 'badge-warn' : 'badge-ok'}">
                                        ${row.avgLatencyMs > 500 ? 'SLOW' : row.avgLatencyMs > 100 ? 'WARN' : 'OK'}
                                    </span>
                                </td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </c:when>
            <c:otherwise>
                <div class="empty-state">
                    <p>No metrics recorded yet. Browse products and place an order to generate data.</p>
                    <a href="${pageContext.request.contextPath}/products" class="btn btn-primary">Browse Products</a>
                </div>
            </c:otherwise>
        </c:choose>
    </section>

    <!-- Recent Events -->
    <section class="metrics-section">
        <h3>Recent Operation Log (last ${recent.size()} events)</h3>
        <c:if test="${not empty recent}">
            <div class="recent-log">
                <table class="metrics-table">
                    <thead>
                        <tr><th>Time</th><th>Component</th><th>Operation</th><th>Duration (ms)</th></tr>
                    </thead>
                    <tbody>
                        <%-- Show most recent first — reverse iterate via index --%>
                        <c:forEach var="m" items="${recent}" varStatus="loop" begin="${recent.size() > 20 ? recent.size()-20 : 0}">
                            <tr>
                                <td class="time">${m.recordedAt}</td>
                                <td><code>${m.component}</code></td>
                                <td>${m.metricName}</td>
                                <td class="num ${m.value > 500 ? 'latency-warn' : ''}">${m.value}</td>
                            </tr>
                        </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:if>
    </section>

    <!-- NFR Targets Reference -->
    <section class="metrics-section nfr-section">
        <h3>NFR Targets</h3>
        <table class="metrics-table">
            <thead><tr><th>NFR</th><th>Target</th><th>Mechanism</th></tr></thead>
            <tbody>
                <tr><td>Response Time</td><td>&lt; 200ms (P99)</td><td>Connection pooling, stateless bean pool, query indexes</td></tr>
                <tr><td>Throughput</td><td>10,000+ concurrent users</td><td>Stateless EJB pool + JMS async offload</td></tr>
                <tr><td>Availability</td><td>99.9% uptime</td><td>GlassFish clustering, MDB retry on failure, Open MQ DMQ</td></tr>
                <tr><td>Inventory Accuracy</td><td>Zero overselling</td><td>Singleton @Lock(WRITE) per reservation</td></tr>
                <tr><td>Order Delivery</td><td>At-least-once</td><td>JMS PERSISTENT + AUTO_ACKNOWLEDGE + DLQ</td></tr>
            </tbody>
        </table>
    </section>

</main>

<jsp:include page="/pages/footer.jsp"/>
</body>
</html>
