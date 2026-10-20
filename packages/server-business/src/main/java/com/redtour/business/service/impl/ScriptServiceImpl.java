package com.redtour.business.service.impl;

import com.redtour.business.common.PageResult;
import com.redtour.business.common.ResultCode;
import com.redtour.business.common.UserContext;
import com.redtour.business.dto.ScriptRequest;
import com.redtour.business.dto.ScriptResult;
import com.redtour.business.dto.ScriptCompleteResult;
import com.redtour.business.entity.Script;
import com.redtour.business.entity.SysUser;
import com.redtour.business.exception.BusinessException;
import com.redtour.business.mapper.ScriptMapper;
import com.redtour.business.mapper.ScriptProgressMapper;
import com.redtour.business.service.AchievementEvaluationService;
import com.redtour.business.service.ScriptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 剧本管理服务实现：景区归属隔离 + 分页 + 状态机（status 0/1）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptServiceImpl implements ScriptService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ScriptMapper scriptMapper;
    private final ScriptProgressMapper scriptProgressMapper;
    private final AchievementEvaluationService achievementEvaluationService;

    @Override
    public PageResult<ScriptResult> list(int page, int pageSize, String title, Long scenicAreaId, Integer status) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        Long scopedAreaId = scopeScenicAreaId(scenicAreaId);
        String normalizedTitle = StringUtils.hasText(title) ? title.trim() : null;
        List<Script> rows = scriptMapper.findPage(scopedAreaId, normalizedTitle, status, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ScriptResult> data = rows.stream().map(this::toResult).toList();
        return PageResult.of(data, scriptMapper.count(scopedAreaId, normalizedTitle, status), page, pageSize);
    }

    @Override
    public ScriptResult get(Long id) {
        Script script = loadScript(id);
        authorizeScript(script.getScenicAreaId());
        return toResult(script);
    }

    @Override
    public ScriptResult create(ScriptRequest request) {
        SysUser current = UserContext.getCurrentUser();
        Long areaId = resolveCreateScenicAreaId(current, request.getScenicAreaId());
        Script script = new Script();
        script.setScenicAreaId(areaId);
        script.setTitle(request.getTitle().trim());
        script.setDescription(blankToNull(request.getDescription()));
        script.setCoverImage(blankToNull(request.getCoverImage()));
        script.setRoles(blankToNull(request.getRoles()));
        script.setNodes(blankToNull(request.getNodes()));
        script.setStartNodeId(request.getStartNodeId());
        script.setAchievements(blankToNull(request.getAchievements()));
        script.setStatus(request.getStatus());
        scriptMapper.insert(script);
        if (script.getId() == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "剧本创建失败");
        }
        return toResult(script);
    }

    @Override
    public ScriptResult update(Long id, ScriptRequest request) {
        Script script = loadScript(id);
        authorizeScript(script.getScenicAreaId());
        if (request.getTitle() != null) {
            script.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            script.setDescription(request.getDescription());
        }
        if (request.getCoverImage() != null) {
            script.setCoverImage(request.getCoverImage());
        }
        if (request.getRoles() != null) {
            script.setRoles(request.getRoles());
        }
        if (request.getNodes() != null) {
            script.setNodes(request.getNodes());
        }
        if (request.getStartNodeId() != null) {
            script.setStartNodeId(request.getStartNodeId());
        }
        if (request.getAchievements() != null) {
            script.setAchievements(request.getAchievements());
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != 0 && request.getStatus() != 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
            }
            script.setStatus(request.getStatus());
        }
        scriptMapper.updateById(script);
        return toResult(script);
    }

    @Override
    public void delete(Long id) {
        Script script = loadScript(id);
        authorizeScript(script.getScenicAreaId());
        scriptMapper.delete(id);
    }

    @Override
    public ScriptResult updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态值不合法");
        }
        Script script = loadScript(id);
        authorizeScript(script.getScenicAreaId());
        scriptMapper.updateStatus(id, status);
        script.setStatus(status);
        return toResult(script);
    }

    @Override
    public PageResult<ScriptResult> publicList(int page, int pageSize, Long scenicAreaId) {
        validatePage(page, pageSize);
        int offset = (page - 1) * pageSize;
        List<Script> rows = scriptMapper.findPublicPage(scenicAreaId, offset, pageSize);
        if (rows == null) {
            rows = List.of();
        }
        List<ScriptResult> data = rows.stream().map(this::toResult).toList();
        // 游客端按景区过滤，复用 count 的 status=1 条件通过 count(scenicAreaId, null, 1)
        return PageResult.of(data, scriptMapper.count(scenicAreaId, null, 1), page, pageSize);
    }

    @Override
    public ScriptResult publicGet(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "剧本ID必须为正整数");
        }
        Script script = scriptMapper.findPublicById(id);
        if (script == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "剧本不存在或已下架");
        }
        return toResult(script);
    }

    @Override
    public ScriptCompleteResult complete(Long scriptId) {
        SysUser current = UserContext.getCurrentUser();
        if (current == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (scriptId == null || scriptId <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "剧本ID必须为正整数");
        }
        Script script = scriptMapper.findPublicById(scriptId);
        if (script == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "剧本不存在或已下架");
        }
        scriptProgressMapper.upsertCompleted(current.getId(), scriptId);
        List<String> unlocked = achievementEvaluationService.evaluateOnScriptComplete(
                current.getId(), scriptId, script.getScenicAreaId());
        ScriptCompleteResult result = new ScriptCompleteResult();
        result.setScriptId(scriptId);
        result.setCompleted(true);
        result.setAchievements(unlocked);
        return result;
    }

    private Script loadScript(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "剧本ID必须为正整数");
        }
        Script script = scriptMapper.findById(id);
        if (script == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "剧本不存在");
        }
        return script;
    }

    private ScriptResult toResult(Script script) {
        ScriptResult result = new ScriptResult();
        result.setId(script.getId());
        result.setScenicAreaId(script.getScenicAreaId());
        result.setTitle(script.getTitle());
        result.setDescription(script.getDescription());
        result.setCoverImage(script.getCoverImage());
        result.setRoles(script.getRoles());
        result.setNodes(script.getNodes());
        result.setStartNodeId(script.getStartNodeId());
        result.setAchievements(script.getAchievements());
        result.setStatus(script.getStatus());
        result.setCreatedAt(script.getCreatedAt());
        result.setUpdatedAt(script.getUpdatedAt());
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
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问剧本数据");
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
                throw new BusinessException(ResultCode.BAD_REQUEST, "超级管理员创建剧本须指定归属景区");
            }
            return requestedAreaId;
        }
        if (!"admin".equals(current.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权创建剧本");
        }
        if (current.getScenicAreaId() == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "当前管理员未分配景区");
        }
        return current.getScenicAreaId();
    }

    private void authorizeScript(Long scenicAreaId) {
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
