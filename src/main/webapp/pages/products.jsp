<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.techmart.model.Product, java.util.List" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Products — TechMart</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/style.css">
</head>
<body>
<jsp:include page="/pages/header.jsp"/>

<main class="container">

    <c:if test="${not empty error}">
        <div class="alert alert-error">${error}</div>
    </c:if>

    <!-- Search Results Heading -->
    <c:if test="${not empty searchQuery}">
        <h2>Search results for: <em>${searchQuery}</em></h2>
    </c:if>
    <c:if test="${not empty category}">
        <h2>Category: <em>${category}</em></h2>
    </c:if>
    <c:if test="${empty searchQuery and empty category}">
        <h2>All Products</h2>
    </c:if>

    <!-- Category Filter Bar -->
    <nav class="category-nav">
        <a href="${pageContext.request.contextPath}/products">All</a>
        <a href="${pageContext.request.contextPath}/products?cat=Electronics">Electronics</a>
        <a href="${pageContext.request.contextPath}/products?cat=Mobile">Mobile</a>
        <a href="${pageContext.request.contextPath}/products?cat=Tablets">Tablets</a>
        <a href="${pageContext.request.contextPath}/products?cat=Audio">Audio</a>
        <a href="${pageContext.request.contextPath}/products?cat=Accessories">Accessories</a>
    </nav>

    <!-- Product Grid -->
    <c:choose>
        <c:when test="${not empty products}">
            <div class="product-grid">
                <c:forEach var="p" items="${products}">
                    <div class="product-card">
                        <div class="product-info">
                            <span class="product-sku">${p.sku}</span>
                            <h3 class="product-name">${p.name}</h3>
                            <p class="product-desc">${p.description}</p>
                            <div class="product-meta">
                                <span class="price">$<fmt:formatNumber value="${p.price}" pattern="#,##0.00"/></span>
                                <span class="stock ${p.stockQuantity > 0 ? 'in-stock' : 'out-of-stock'}">
                                    <c:choose>
                                        <c:when test="${p.stockQuantity > 0}">${p.stockQuantity} in stock</c:when>
                                        <c:otherwise>Out of stock</c:otherwise>
                                    </c:choose>
                                </span>
                            </div>
                        </div>
                        <div class="product-actions">
                            <c:if test="${p.stockQuantity > 0}">
                                <form action="${pageContext.request.contextPath}/cart" method="post">
                                    <input type="hidden" name="action" value="add"/>
                                    <input type="hidden" name="productId" value="${p.id}"/>
                                    <input type="number" name="quantity" value="1" min="1" max="${p.stockQuantity}" class="qty-input"/>
                                    <button type="submit" class="btn btn-primary">Add to Cart</button>
                                </form>
                            </c:if>
                            <c:if test="${p.stockQuantity == 0}">
                                <button class="btn btn-disabled" disabled>Out of Stock</button>
                            </c:if>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </c:when>
        <c:otherwise>
            <div class="empty-state">
                <p>No products found.</p>
                <a href="${pageContext.request.contextPath}/products" class="btn btn-secondary">View All Products</a>
            </div>
        </c:otherwise>
    </c:choose>

</main>

<jsp:include page="/pages/footer.jsp"/>
</body>
</html>
