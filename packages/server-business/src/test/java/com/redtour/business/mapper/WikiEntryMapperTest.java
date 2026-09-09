package com.redtour.business.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiEntryMapperTest {

    @Test
    void shouldUseScenicAreaFilterAndBoundedPagination() throws Exception {
        Method method = WikiEntryMapper.class.getMethod("findPage", Long.class, String.class, int.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value())
                .replaceAll("\\s+", " ").toUpperCase();
        assertTrue(sql.contains("WHERE SCENIC_AREA_ID = #{SCENICAREAID}"));
        assertTrue(sql.contains("LIMIT #{OFFSET}, #{PAGESIZE}"));
    }

    @Test
    void shouldDefinePendingAndCompileStateTransitions() throws Exception {
        String update = String.join(" ", WikiEntryMapper.class
                .getMethod("updateContent", com.redtour.business.entity.WikiEntry.class)
                .getAnnotation(Update.class).value()).toUpperCase();
        String compiling = String.join(" ", WikiEntryMapper.class
                .getMethod("markCompiling", Long.class)
                .getAnnotation(Update.class).value()).toUpperCase();
        String done = String.join(" ", WikiEntryMapper.class
                .getMethod("markDone", Long.class, String.class, String.class)
                .getAnnotation(Update.class).value()).toUpperCase();
        String failed = String.join(" ", WikiEntryMapper.class
                .getMethod("markFailed", Long.class)
                .getAnnotation(Update.class).value()).toUpperCase();
        assertTrue(update.contains("COMPILE_STATUS = 'PENDING'"));
        assertTrue(compiling.contains("COMPILE_STATUS = 'COMPILING'"));
        assertTrue(done.contains("COMPILE_STATUS = 'DONE'"));
        assertTrue(failed.contains("COMPILE_STATUS = 'FAILED'"));
    }

    @Test
    void shouldDeleteById() throws Exception {
        Method method = WikiEntryMapper.class.getMethod("deleteById", Long.class);
        assertTrue(method.getAnnotation(Delete.class).value()[0].toUpperCase().contains("DELETE FROM WIKI_ENTRY"));
    }
}
