package com.ltl.league.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.ltl.league.dto.EventTaskDtos;
import com.ltl.league.entity.EventTask;
import com.ltl.league.entity.EventTaskCategory;
import com.ltl.league.exception.BusinessException;
import com.ltl.league.mapper.EventTaskCategoryMapper;
import com.ltl.league.mapper.EventTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventTaskCategoryServiceTest {
    @Mock private EventTaskCategoryMapper categoryMapper;
    @Mock private EventTaskMapper taskMapper;

    private EventTaskCategoryService service;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), EventTask.class);
    }

    @BeforeEach
    void setUp() {
        service = new EventTaskCategoryService(categoryMapper, taskMapper);
        ReflectionTestUtils.setField(service, "currentSeason", "s2");
    }

    @Test
    void createInsertsCategoryWithDefaults() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());
        EventTaskDtos.AdminCategoryRequest request = new EventTaskDtos.AdminCategoryRequest();
        request.setName(" 日常任务 ");
        request.setSortOrder(2);

        EventTaskDtos.CategoryVO vo = service.create(request);

        assertEquals("日常任务", vo.getName());
        assertEquals(2, vo.getSortOrder());
        verify(categoryMapper).insert(argThat(category -> category.getDeleted() == 0
                && "日常任务".equals(category.getName())));
    }

    @Test
    void createRejectsDuplicateName() {
        EventTaskCategory existing = new EventTaskCategory();
        existing.setId(1L);
        existing.setName("日常任务");
        existing.setDeleted(0);
        when(categoryMapper.selectList(any())).thenReturn(List.of(existing));
        EventTaskDtos.AdminCategoryRequest request = new EventTaskDtos.AdminCategoryRequest();
        request.setName("日常任务");

        assertThrows(BusinessException.class, () -> service.create(request));
        verify(categoryMapper, never()).insert(any(EventTaskCategory.class));
    }

    @Test
    void deleteSoftDeletesCategoryAndUnclassifiesTasks() {
        EventTaskCategory category = new EventTaskCategory();
        category.setId(1L);
        category.setName("日常任务");
        category.setDeleted(0);
        when(categoryMapper.selectById(1L)).thenReturn(category);

        service.delete(1L);

        // deleted 是全局逻辑删除字段：必须走 deleteById，updateById 不会更新该列
        verify(categoryMapper).deleteById(1L);
        verify(categoryMapper, never()).updateById(any(EventTaskCategory.class));
        // 该分类下任务归为未分类：显式 UPDATE 置 NULL，而非逐条 updateById（会跳过 null 字段）
        verify(taskMapper).update(isNull(), argThat((LambdaUpdateWrapper<EventTask> wrapper) ->
                wrapper.getSqlSet() != null && wrapper.getSqlSet().contains("category_id")));
        verify(taskMapper, never()).updateById(any(EventTask.class));
    }

    @Test
    void deleteRejectsUnknownCategory() {
        when(categoryMapper.selectById(99L)).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.delete(99L));
        verify(categoryMapper, never()).deleteById(any(Long.class));
    }
}
