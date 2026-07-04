<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>TechMart Online</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
<jsp:include page="/pages/header.jsp"/>

<main class="container">
    <section class="hero">
        <h1>Welcome to TechMart Online</h1>
        <p>Enterprise-grade e-commerce powered by Java EE 8</p>
        <a href="${pageContext.request.contextPath}/products" class="btn btn-primary">Browse Products</a>
        <a href="${pageContext.request.contextPath}/metrics" class="btn btn-secondary">View Performance Metrics</a>
    </section>

    <section class="features">
        <div class="feature-card">
            <h3>Real-Time Inventory</h3>
            <p>Singleton EJB with container-managed locking prevents overselling across 10,000+ concurrent users.</p>
        </div>
        <div class="feature-card">
            <h3>Async Order Processing</h3>
            <p>JMS queues decouple order placement from fulfilment. @Asynchronous email confirmations never slow down checkout.</p>
        </div>
        <div class="feature-card">
            <h3>Scalable Sessions</h3>
            <p>Stateful shopping cart beans with @StatefulTimeout keep memory usage bounded under peak load.</p>
        </div>
    </section>
</main>

<jsp:include page="/pages/footer.jsp"/>
</body>
</html>
