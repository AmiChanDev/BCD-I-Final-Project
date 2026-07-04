<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<header class="site-header">
    <div class="header-inner">
        <a href="${pageContext.request.contextPath}/" class="logo">TechMart</a>
        <nav class="main-nav">
            <a href="${pageContext.request.contextPath}/products">Products</a>
            <a href="${pageContext.request.contextPath}/cart">Cart</a>
            <a href="${pageContext.request.contextPath}/orders">My Orders</a>
            <a href="${pageContext.request.contextPath}/metrics" class="metrics-link">Metrics</a>
        </nav>
        <form class="search-bar" action="${pageContext.request.contextPath}/products" method="get">
            <input type="text" name="q" placeholder="Search products..." />
            <button type="submit">Search</button>
        </form>
    </div>
</header>
