package com.techmart.web.servlet;

import com.techmart.ejb.stateless.ProductCatalogBean;
import com.techmart.model.Product;

import jakarta.ejb.EJB;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

@WebServlet(name = "ProductServlet", urlPatterns = "/products")
public class ProductServlet extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(ProductServlet.class.getName());

    @EJB
    private ProductCatalogBean productCatalog;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String keyword  = req.getParameter("q");
        String category = req.getParameter("cat");
        String idParam  = req.getParameter("id");

        try {
            if (idParam != null) {
                // Single product detail — reuse the existing grid rendering
                // in products.jsp by wrapping the single result in a list
                // (there is no separate detail template).
                Long productId = Long.parseLong(idParam);
                Product product = productCatalog.getProductById(productId);
                if (product == null) {
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Product not found");
                    return;
                }
                req.setAttribute("products", java.util.Collections.singletonList(product));

            } else if (keyword != null && !keyword.trim().isEmpty()) {
                // Keyword search
                List<Product> results = productCatalog.searchProducts(keyword);
                req.setAttribute("products", results);
                req.setAttribute("searchQuery", keyword);
                req.setAttribute("view", "list");

            } else if (category != null && !category.trim().isEmpty()) {
                // Category filter with pagination
                int page   = parseIntOrDefault(req.getParameter("page"), 1);
                int limit  = 20;
                int offset = (page - 1) * limit;
                List<Product> results = productCatalog.getProductsByCategory(category, offset, limit);
                req.setAttribute("products", results);
                req.setAttribute("category", category);
                req.setAttribute("currentPage", page);
                req.setAttribute("view", "list");

            } else {
                // All products
                List<Product> all = productCatalog.getAllProducts();
                req.setAttribute("products", all);
                req.setAttribute("view", "list");
            }

        } catch (NumberFormatException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid product ID");
            return;
        } catch (Exception e) {
            LOG.severe("ProductServlet error: " + e.getMessage());
            req.setAttribute("error", "Could not retrieve products: " + e.getMessage());
        }

        req.getRequestDispatcher("/pages/products.jsp").forward(req, resp);
    }

    private int parseIntOrDefault(String s, int defaultVal) {
        try { return Integer.parseInt(s); } catch (Exception e) { return defaultVal; }
    }
}
