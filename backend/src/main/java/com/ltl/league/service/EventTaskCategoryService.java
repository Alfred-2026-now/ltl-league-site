package com.ltl.league.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ltl.league.dto.EventTaskDtos;
import com.ltl.league.entity.EventTask;
import com.ltl.league.entity.EventTaskCategory;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.EventTaskCategoryMapper;
import com.ltl.league.mapper.EventTaskMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 赛事任务分类：管理员在后台维护分类，并把任务归入不同分类；
 * 公开任务大厅按分类查看。
 */
@Service
public class EventTaskCategoryService {

    private final EventTaskCategoryMapper categoryMapper;
    private final EventTaskMapper taskMapper;

    @Value("${ltl.league.current-season:s1}")
    private String currentSeason;

    public EventTaskCategoryService(EventTaskCategoryMapper categoryMapper, EventTaskMapper taskMapper) {
        this.categoryMapper = categoryMapper;
        this.taskMapper = taskMapper;
    }

    /** 公开分类列表：附本赛季进行中任务数，按 sortOrder、id 排序 */
    public List<EventTaskDtos.CategoryVO> listForPublic() {
        return listWithCount();
    }

    /** 管理端分类列表：同样附进行中任务数，便于归类时参考 */
    public List<EventTaskDtos.CategoryVO> listForAdmin() {
        return listWithCount();
    }

    private List<EventTaskDtos.CategoryVO> listWithCount() {
        List<EventTaskCategory> categories = categoryMapper.selectList(new LambdaQueryWrapper<EventTaskCategory>()
                .eq(EventTaskCategory::getDeleted, 0)
                .orderByAsc(EventTaskCategory::getSortOrder)
                .orderByAsc(EventTaskCategory::getId));
        Map<Long, Long> counts = taskMapper.selectList(new LambdaQueryWrapper<EventTask>()
                        .select(EventTask::getCategoryId)
                        .eq(EventTask::getSeason, currentSeason)
                        .eq(EventTask::getStatus, EventTaskService.TASK_PUBLISHED)
                        .eq(EventTask::getDeleted, 0)
                        .isNotNull(EventTask::getCategoryId))
                .stream()
                .collect(Collectors.groupingBy(EventTask::getCategoryId, Collectors.counting()));
        return categories.stream().map(category -> {
            EventTaskDtos.CategoryVO vo = new EventTaskDtos.CategoryVO();
            vo.setId(category.getId());
            vo.setName(category.getName());
            vo.setSortOrder(category.getSortOrder());
            vo.setPublishedCount(counts.getOrDefault(category.getId(), 0L));
            vo.setCreatedAt(category.getCreatedAt());
            return vo;
        }).collect(Collectors.toList());
    }

    @Transactional
    public EventTaskDtos.CategoryVO create(EventTaskDtos.AdminCategoryRequest request) {
        String name = requireName(request == null ? null : request.getName());
        requireNameFree(name, null);
        EventTaskCategory category = new EventTaskCategory();
        category.setName(name);
        category.setSortOrder(normalizeSort(request.getSortOrder()));
        category.setDeleted(0);
        categoryMapper.insert(category);
        return toVO(category, 0L);
    }

    @Transactional
    public EventTaskDtos.CategoryVO update(Long categoryId, EventTaskDtos.AdminCategoryRequest request) {
        EventTaskCategory category = requireCategory(categoryId);
        String name = requireName(request == null ? null : request.getName());
        requireNameFree(name, categoryId);
        category.setName(name);
        category.setSortOrder(normalizeSort(request.getSortOrder()));
        categoryMapper.updateById(category);
        return toVO(category, null);
    }

    /** 删除分类：该分类下的任务自动归为未分类 */
    @Transactional
    public void delete(Long categoryId) {
        EventTaskCategory category = requireCategory(categoryId);
        category.setDeleted(1);
        categoryMapper.updateById(category);
        List<EventTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<EventTask>()
                .select(EventTask::getId)
                .eq(EventTask::getCategoryId, categoryId)
                .eq(EventTask::getDeleted, 0));
        for (EventTask task : tasks) {
            task.setCategoryId(null);
            taskMapper.updateById(task);
        }
    }

    public EventTaskCategory requireCategory(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException(400, "分类ID不能为空");
        }
        EventTaskCategory category = categoryMapper.selectById(categoryId);
        if (category == null || Objects.equals(category.getDeleted(), 1)) {
            throw new BusinessException(404, "任务分类不存在");
        }
        return category;
    }

    private String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException(400, "分类名称不能为空");
        }
        String trimmed = name.trim();
        if (trimmed.length() > 50) {
            throw new BusinessException(400, "分类名称不能超过50字");
        }
        return trimmed;
    }

    private void requireNameFree(String name, Long excludeId) {
        boolean taken = categoryMapper.selectList(new LambdaQueryWrapper<EventTaskCategory>()
                        .eq(EventTaskCategory::getName, name)
                        .eq(EventTaskCategory::getDeleted, 0))
                .stream().anyMatch(category -> !Objects.equals(category.getId(), excludeId));
        if (taken) {
            throw new BusinessException(400, "分类名称已存在：" + name);
        }
    }

    private Integer normalizeSort(Integer sortOrder) {
        return sortOrder == null ? 0 : Math.max(0, Math.min(sortOrder, 9999));
    }

    private EventTaskDtos.CategoryVO toVO(EventTaskCategory category, Long publishedCount) {
        EventTaskDtos.CategoryVO vo = new EventTaskDtos.CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSortOrder(category.getSortOrder());
        vo.setPublishedCount(publishedCount);
        vo.setCreatedAt(category.getCreatedAt());
        return vo;
    }
}
