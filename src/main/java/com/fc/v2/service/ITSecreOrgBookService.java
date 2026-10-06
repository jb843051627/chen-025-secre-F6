package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.common.secre.SecreSyncReport;
import com.fc.v2.model.auto.TSecreOrgBook;

import java.util.List;

/**
 * 机构名录底册 Service接口
 *
 * @author fuce
 * @date 2026-10-06
 */
public interface ITSecreOrgBookService {

    /** 按主键查询 */
    TSecreOrgBook selectTSecreOrgBookById(Long id);

    /** 按条件查询列表 */
    List<TSecreOrgBook> selectTSecreOrgBookList(Wrapper<TSecreOrgBook> queryWrapper);

    /** 本机关自录一册（来路一律记「本机关自录」，代号不许与册面已有重复） */
    int insertTSecreOrgBook(TSecreOrgBook record);

    /** 改口（代号是挂行的底，不许跟着改） */
    int updateTSecreOrgBook(TSecreOrgBook record);

    /** 批量删除 */
    int deleteTSecreOrgBookByIds(String ids);

    /** 按主键删除 */
    int deleteTSecreOrgBookById(Long id);

    /**
     * 上级名录下发一份，与本机关底册对一遍：对不上以本机关这份作准，
     * 冲突逐条点名到代号，缘由落到本地名录那一份的备注里。
     */
    SecreSyncReport syncSuperiorBooks(List<TSecreOrgBook> superiorBooks);
}
