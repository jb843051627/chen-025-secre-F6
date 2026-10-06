package com.fc.v2.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.common.secre.SecreVerdict;
import com.fc.v2.model.auto.TSecreCarrier;

import java.util.List;

/**
 * 涉密载体册面 Service接口
 *
 * @author fuce
 * @date 2026-09-12
 */
public interface ITSecreCarrierService {

    /** 按主键查询 */
    TSecreCarrier selectTSecreCarrierById(Long id);

    /** 按条件查询列表（视野与分页由调用方统一处理） */
    List<TSecreCarrier> selectTSecreCarrierList(Wrapper<TSecreCarrier> queryWrapper);

    /** 新增（旧口契约：成立回 1，立不起来回 0） */
    int insertTSecreCarrier(TSecreCarrier record);

    /** 入册（同义新口）：与新增走同一处判路，打回时点名毛病落在哪一栏 */
    SecreVerdict registerTSecreCarrier(TSecreCarrier record);

    /** 修改（改口即重轧：已收改口要轧，换挂靠的名录也要轧） */
    int updateTSecreCarrier(TSecreCarrier record);

    /** 批量删除 */
    int deleteTSecreCarrierByIds(String ids);

    /** 按主键删除 */
    int deleteTSecreCarrierById(Long id);
}
