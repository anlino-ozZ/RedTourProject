package com.redtour.business.controller;

import com.redtour.business.common.ApiResponse;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.entity.SpotFavorite;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.SpotFavoriteMapper;
import com.redtour.business.mapper.SpotMapper;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 景点收藏接口（需登录，无 @RequireRole，由 AuthInterceptor 校验 JWT）。
 *
 * 路径 /api/v1/spots/{id}/favorite 为两段路径，不匹配 WebMvcConfig 白名单中的
 * /api/v1/spots/*（单段），故自动走 JWT 鉴权。POST 收藏、DELETE 取消收藏（均幂等）。
 */
@Validated
@RestController
@RequestMapping("/api/v1/spots")
@RequiredArgsConstructor
public class SpotFavoriteController {

    private final SpotFavoriteMapper spotFavoriteMapper;
    private final SpotMapper spotMapper;

    @PostMapping("/{id}/favorite")
    public ApiResponse<Map<String, Boolean>> favorite(
            @PathVariable @Positive(message = "景点ID必须为正整数") Long id) {
        Long userId = UserContext.getCurrentUserId();
        if (spotMapper.findById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "景点不存在");
        }
        if (spotFavoriteMapper.exists(userId, id) == 0) {
            SpotFavorite favorite = new SpotFavorite();
            favorite.setUserId(userId);
            favorite.setSpotId(id);
            int rows = spotFavoriteMapper.insert(favorite);
            if (rows <= 0) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "收藏失败");
            }
        }
        return ApiResponse.success(Map.of("favorited", true));
    }

    @DeleteMapping("/{id}/favorite")
    public ApiResponse<Map<String, Boolean>> unfavorite(
            @PathVariable @Positive(message = "景点ID必须为正整数") Long id) {
        Long userId = UserContext.getCurrentUserId();
        spotFavoriteMapper.delete(userId, id);
        return ApiResponse.success(Map.of("favorited", false));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleConstraintViolation(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("参数格式错误");
        return ApiResponse.error(400, message);
    }
}
