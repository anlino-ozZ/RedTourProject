package com.redtour.business.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.ProductOrderRequest;
import com.redtour.business.dto.ProductOrderResult;
import com.redtour.business.entity.Product;
import com.redtour.business.entity.ProductOrder;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.ProductOrderMapper;
import com.redtour.business.service.ProductOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 特产意向订单服务实现：景区归属隔离 + 下单快照 + 状态流转。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOrderServiceImpl implements ProductOrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONTACTED = "contacted";
    private static final String STATUS_CLOSED = "closed";

    private final ProductOrderMapper productOrderMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProductOrderResult createOrder(Long productId, ProductOrderRequest request) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (productId == null || productId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品ID不合法");
        }
        Product product = productOrderMapper.findProductById(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产品不存在");
        }
        int quantity = request.getQuantity() == null ? 1 : request.getQuantity();

        ProductOrder order = new ProductOrder();
        order.setScenicAreaId(product.getScenicAreaId());
        order.setProductId(productId);
        order.setProductSnapshot(buildSnapshot(product));
        order.setUserId(current.getId());
        order.setQuantity(quantity);
        order.setContactName(request.getContactName().trim());
        order.setContactPhone(request.getContactPhone().trim());
        order.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        order.setStatus(STATUS_PENDING);

        productOrderMapper.insert(order);
        if (order.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "订单创建失败");
        }
        return toResult(order);
    }

    @Override
    public PageResult<ProductOrderResult> adminList(int page, int pageSize, Long scenicAreaId, String status) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        String normalizedStatus = StringUtils.hasText(status) ? status.trim() : null;
        List<ProductOrder> rows = productOrderMapper.findPage(scopedAreaId, normalizedStatus, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ProductOrderResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, productOrderMapper.count(scopedAreaId, normalizedStatus), page, pageSize);
    }

    @Override
    public ProductOrderResult adminGet(Long id) {
        ProductOrder order = loadOrder(id);
        authorizeScenicArea(order.getScenicAreaId());
        return toResult(order);
    }

    @Override
    public ProductOrderResult adminUpdateStatus(Long id, String status, String handleRemark) {
        if (!STATUS_PENDING.equals(status) && !STATUS_CONTACTED.equals(status) && !STATUS_CLOSED.equals(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单状态值不合法");
        }
        ProductOrder order = loadOrder(id);
        authorizeScenicArea(order.getScenicAreaId());
        productOrderMapper.updateStatus(id, status, StringUtils.hasText(handleRemark) ? handleRemark.trim() : null);
        order.setStatus(status);
        order.setHandleRemark(handleRemark);
        return toResult(order);
    }

    private ProductOrder loadOrder(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单ID不合法");
        }
        ProductOrder order = productOrderMapper.findById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private ProductOrderResult toResult(ProductOrder order) {
        ProductOrderResult result = new ProductOrderResult();
        result.setId(order.getId());
        result.setScenicAreaId(order.getScenicAreaId());
        result.setProductId(order.getProductId());
        result.setProductSnapshot(order.getProductSnapshot());
        result.setUserId(order.getUserId());
        result.setQuantity(order.getQuantity());
        result.setContactName(order.getContactName());
        result.setContactPhone(order.getContactPhone());
        result.setRemark(order.getRemark());
        result.setStatus(order.getStatus());
        result.setHandleRemark(order.getHandleRemark());
        result.setCreatedAt(order.getCreatedAt());
        result.setUpdatedAt(order.getUpdatedAt());
        return result;
    }

    private String buildSnapshot(Product product) {
        try {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", product.getName());
            snapshot.put("price", product.getPrice());
            snapshot.put("image", product.getImage());
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("[订单] 产品快照序列化失败 productId={}", product.getId(), e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "产品快照生成失败");
        }
    }

    /** admin 强制本人景区；super 透传筛选条件（为 NULL 表示全量）。 */
    private Long scopeScenicAreaId(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return scenicAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问订单数据");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    private void authorizeScenicArea(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return;
        }
        if (!"admin".equals(current.getRole())
                || current.getScenicAreaId() == null
                || !current.getScenicAreaId().equals(scenicAreaId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问其他景区的数据");
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }
}
