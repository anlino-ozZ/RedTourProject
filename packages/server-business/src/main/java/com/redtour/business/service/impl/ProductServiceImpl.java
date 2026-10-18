package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.ProductRequest;
import com.redtour.business.dto.ProductResult;
import com.redtour.business.entity.Product;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.ProductMapper;
import com.redtour.business.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 特产/文创产品管理服务实现：景区归属隔离 + 分页 + 下线（status=0）+ 详情浏览量自增。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductMapper productMapper;

    @Override
    public PageResult<ProductResult> list(int page, int pageSize, String name, Long scenicAreaId, Integer status) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        String normalizedName = StringUtils.hasText(name) ? name.trim() : null;
        List<Product> rows = productMapper.findPage(scopedAreaId, normalizedName, status, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ProductResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, productMapper.count(scopedAreaId, normalizedName, status), page, pageSize);
    }

    @Override
    public ProductResult get(Long id) {
        Product product = loadProduct(id);
        authorizeProduct(product.getScenicAreaId());
        return toResult(product);
    }

    @Override
    public ProductResult create(ProductRequest request) {
        SysUser current = UserContext.getCurrentUser();
        Long areaId = resolveCreateScenicAreaId(current, request.getScenicAreaId());
        Product product = new Product();
        product.setScenicAreaId(areaId);
        product.setName(request.getName().trim());
        product.setPrice(request.getPrice());
        product.setDescription(blankToNull(request.getDescription()));
        product.setImage(blankToNull(request.getImage()));
        product.setStock(request.getStock() == null ? 0 : request.getStock());
        product.setCategory(request.getCategory().trim());
        product.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        productMapper.insert(product);
        if (product.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "产品创建失败");
        }
        return toResult(product);
    }

    @Override
    public ProductResult update(Long id, ProductRequest request) {
        Product product = loadProduct(id);
        authorizeProduct(product.getScenicAreaId());
        if (request.getName() != null) {
            product.setName(request.getName().trim());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }
        if (request.getDescription() != null) {
            product.setDescription(blankToNull(request.getDescription()));
        }
        if (request.getImage() != null) {
            product.setImage(blankToNull(request.getImage()));
        }
        if (request.getStock() != null) {
            product.setStock(request.getStock());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory().trim());
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
            }
            product.setStatus(request.getStatus());
        }
        productMapper.updateById(product);
        return toResult(product);
    }

    @Override
    public void delete(Long id) {
        Product product = loadProduct(id);
        authorizeProduct(product.getScenicAreaId());
        productMapper.delete(id);
    }

    @Override
    public ProductResult updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
        }
        Product product = loadProduct(id);
        authorizeProduct(product.getScenicAreaId());
        productMapper.updateStatus(id, status);
        product.setStatus(status);
        return toResult(product);
    }

    @Override
    public PageResult<ProductResult> publicList(int page, int pageSize, Long scenicAreaId, String category, String keyword) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        String normalizedCategory = StringUtils.hasText(category) ? category.trim() : null;
        String normalizedName = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<Product> rows = productMapper.findPublicPage(scenicAreaId, normalizedCategory, normalizedName, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ProductResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, productMapper.countPublic(scenicAreaId, normalizedCategory, normalizedName), page, pageSize);
    }

    @Override
    public ProductResult publicGet(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品ID必须为正整数");
        }
        Product product = productMapper.findPublicById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产品不存在或已下架");
        }
        // 浏览量自增（brief B3）
        productMapper.updateClickCount(id);
        Product fresh = productMapper.findPublicById(id);
        return toResult(fresh != null ? fresh : product);
    }

    private Product loadProduct(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "产品ID必须为正整数");
        }
        Product product = productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "产品不存在");
        }
        return product;
    }

    private ProductResult toResult(Product product) {
        ProductResult result = new ProductResult();
        result.setId(product.getId());
        result.setScenicAreaId(product.getScenicAreaId());
        result.setName(product.getName());
        result.setPrice(product.getPrice());
        result.setDescription(product.getDescription());
        result.setImage(product.getImage());
        result.setStock(product.getStock());
        result.setCategory(product.getCategory());
        result.setStatus(product.getStatus());
        result.setClickCount(product.getClickCount());
        result.setCreatedAt(product.getCreatedAt());
        result.setUpdatedAt(product.getUpdatedAt());
        return result;
    }

    /** admin 强制本人景区；super 透传筛选条件。 */
    private Long scopeScenicAreaId(Long scenicAreaId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            return scenicAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问产品数据");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    /** 新增时的归属景区解析：admin 强制本人景区；super 须指定。 */
    private Long resolveCreateScenicAreaId(SysUser current, Long requestedAreaId) {
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if ("super_admin".equals(current.getRole())) {
            if (requestedAreaId == null || requestedAreaId <= 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员创建产品须指定归属景区");
            }
            return requestedAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权创建产品");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    private void authorizeProduct(Long scenicAreaId) {
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

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private void validatePage(int page, int pageSize) {
        if (page <= 0 || pageSize <= 0 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分页参数不合法");
        }
    }
}
